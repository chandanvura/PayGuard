"""Exercise a live PayGuard instance and write recruiter-readable CI evidence."""
import json
import os
import time
import urllib.request
import uuid
from pathlib import Path

BASE = os.environ.get('PAYGUARD_URL', 'http://localhost:8081').rstrip('/')
OUT = Path(os.environ.get('PAYGUARD_EVIDENCE', 'demo-evidence.md'))


def request(path, payload=None, key=None):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {'Content-Type': 'application/json'} if data else {}
    if key:
        headers['Idempotency-Key'] = key
    with urllib.request.urlopen(urllib.request.Request(BASE + path, data=data, headers=headers), timeout=10) as response:
        return json.load(response)


def check(label, actual, expected):
    if actual != expected:
        raise AssertionError(f'{label}: expected {expected}, got {actual}')
    print(f'PASS {label}: {actual}', flush=True)


results = []
try:
    for attempt in range(60):
        try:
            health = request('/actuator/health')
            check('application health', health['status'], 'UP')
            results.append(('Application health', 'UP'))
            break
        except Exception:
            if attempt == 59:
                raise
            time.sleep(2)

    body = {'customerId': 'CI-DEMO', 'amount': 149900, 'currency': 'INR'}
    normal = request('/api/v1/payments', body, 'CI-' + uuid.uuid4().hex)
    check('normal payment', normal['status'], 'SUCCESS')
    results.append(('Normal payment', 'SUCCESS'))

    key = 'DEMO-TIMEOUT-' + uuid.uuid4().hex
    uncertain = request('/api/v1/payments', body, key)
    check('timeout after charge', uncertain['status'], 'UNKNOWN')
    results.append(('Provider timeout after charge', 'UNKNOWN'))

    retry = request('/api/v1/payments', body, key)
    check('idempotent retry ID', retry['id'], uncertain['id'])
    results.append(('Duplicate retry', 'Same payment ID'))

    for attempt in range(30):
        recovered = request('/api/v1/payments/' + uncertain['id'])
        if recovered['status'] == 'SUCCESS':
            break
        time.sleep(2)
    check('automatic reconciliation', recovered['status'], 'SUCCESS')
    results.append(('Automatic reconciliation', 'UNKNOWN → SUCCESS'))

    metrics = urllib.request.urlopen(BASE + '/actuator/prometheus', timeout=10).read().decode()
    if 'payguard_' not in metrics:
        raise AssertionError('PayGuard metrics missing')
    results.append(('Prometheus endpoint', 'PayGuard metrics exposed'))
finally:
    lines = ['# PayGuard reliability demonstration', '',
             'Executed against a temporary PostgreSQL 17 and Spring Boot service on a GitHub runner.',
             'The fake provider is a simulation; no real money is charged.', '',
             '| Check | Observed result |', '|---|---|']
    lines += [f'| {name} | {value} |' for name, value in results]
    lines += ['', 'See the workflow run logs for commands, timing, and failures.']
    OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(OUT.read_text(), flush=True)
