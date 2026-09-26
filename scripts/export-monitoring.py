"""Export real Prometheus readings from the temporary GitHub runner to static Pages."""
import datetime
import html
import json
import os
import time
import uuid
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(os.environ.get('SITE_OUTPUT', 'site-output')) / 'observability' / 'latest'
ROOT.mkdir(parents=True, exist_ok=True)
API = 'http://localhost:9090/api/v1/'
QUERIES = {
    'Availability SLI (5m)': 'payguard:sli_availability:ratio5m',
    'Error ratio (5m)': 'payguard:sli_error:ratio5m',
    'Error budget burn rate': 'payguard:slo_availability:burn_rate5m',
    'Requests within 250 ms': 'payguard:sli_latency_250ms:ratio5m',
    'Payments created': 'payguard_payment_total',
}

def get(path, params=None):
    url = API + path + ('?' + urllib.parse.urlencode(params) if params else '')
    with urllib.request.urlopen(url, timeout=10) as response:
        data = json.load(response)
    if data['status'] != 'success':
        raise RuntimeError(data)
    return data['data']

for attempt in range(40):
    try:
        targets = get('targets')['activeTargets']
        if any(t.get('health') == 'up' and t.get('labels', {}).get('job') == 'payguard-payment-service' for t in targets):
            break
    except Exception:
        pass
    time.sleep(3)
else:
    raise RuntimeError('Prometheus payment-service target did not become UP')


# Generate simulated payments after the Prometheus target is UP so rate windows
# contain real observations across multiple scrapes. No external provider is used.
for _ in range(5):
    payload = json.dumps({'customerId': 'CI-MONITORING', 'amount': 149900, 'currency': 'INR'}).encode()
    req = urllib.request.Request('http://localhost:8081/api/v1/payments', data=payload,
        headers={'Content-Type': 'application/json', 'Idempotency-Key': 'CI-MONITOR-' + uuid.uuid4().hex})
    with urllib.request.urlopen(req, timeout=10) as response:
        payment = json.load(response)
    if payment['status'] != 'SUCCESS':
        raise RuntimeError('Monitoring payment did not succeed')
    time.sleep(6)


# A five-minute rate requires multiple scrapes; wait until the availability series exists.
for attempt in range(30):
    if get('query', {'query': QUERIES['Availability SLI (5m)']})['result']:
        break
    time.sleep(3)
else:
    raise RuntimeError('Availability recording rule has no samples')

stamp = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds')
results = {label: get('query', {'query': expr})['result'] for label, expr in QUERIES.items()}
rules = get('rules')
record = {'captured_utc': stamp, 'job': 'payguard-payment-service', 'target_health': 'up', 'queries': QUERIES, 'results': results,
          'rule_groups': [{'name': g['name'], 'rules': [{'name': r['name'], 'health': r.get('health')} for r in g['rules']]} for g in rules['groups']]}
(ROOT / 'prometheus.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
rows = []
for label, expr in QUERIES.items():
    values = results[label]
    observed = ', '.join(html.escape(v['value'][1]) for v in values) if values else 'No sample in this run'
    rows.append(f'<tr><th>{html.escape(label)}</th><td><code>{html.escape(expr)}</code></td><td>{observed}</td></tr>')
rule_count = sum(len(g['rules']) for g in rules['groups'])
page = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hosted Prometheus evidence | PayGuard</title><link rel="stylesheet" href="../../style.css?v=3"><style>table{{width:100%;border-collapse:collapse}}th,td{{padding:1rem;text-align:left;border-bottom:1px solid #345064}}td{{overflow-wrap:anywhere}}.reading{{background:#13283a;border:1px solid #3c635e;border-radius:12px;padding:1.5rem}}img{{width:100%;border:1px solid #345064;border-radius:12px}}</style></head><body><header><a class="brand" href="../../index.html">◆ PayGuard</a><nav><a href="../../dashboard.html">Dashboard</a><a href="../../monitoring.html">Learn monitoring</a></nav></header><main><section class="page-hero"><div class="eyebrow">ACTUAL PROMETHEUS / GITHUB RUNNER</div><h1>Captured monitoring evidence.</h1><p>Captured {html.escape(stamp)}. The GitHub runner started the real API, Prometheus, and Grafana, then exported these readings. This is a dated capture, not a continuously running server.</p><div class="reading"><strong>Payment service scrape target: UP</strong><p>{rule_count} recording and alerting rules loaded.</p></div></section><section><h2>Prometheus instant queries</h2><div style="overflow:auto"><table><thead><tr><th>Signal</th><th>PromQL</th><th>Observed value</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div><p><a href="prometheus.json">Download raw Prometheus API response ↗</a></p></section><section><h2>Actual Grafana dashboard capture</h2><p>Rendered by the real Grafana instance on the same runner after Prometheus scraped the API.</p><a href="grafana.png"><img src="grafana.png" alt="Captured Grafana PayGuard SRE dashboard"></a></section></main><footer><a href="https://github.com/chandanvura/PayGuard/actions/workflows/hosted-monitoring.yml">Workflow history and reproducible steps ↗</a></footer></body></html>'''
(ROOT / 'prometheus.html').write_text(page, encoding='utf-8')
print(f'Captured {len(results)} Prometheus queries and {rule_count} rules at {stamp}', flush=True)
