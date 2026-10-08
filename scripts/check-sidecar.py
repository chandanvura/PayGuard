"""Check the real sidecar in a temporary hosted Compose stack."""
import http.client
import time
import urllib.error
import urllib.request

URL = 'http://127.0.0.1:9101'
for attempt in range(30):
    try:
        with urllib.request.urlopen(URL + '/healthz', timeout=3) as response:
            assert response.read().strip() == b'ok'
        break
    except (urllib.error.URLError, http.client.HTTPException, OSError, AssertionError):
        if attempt == 29:
            raise
        time.sleep(2)

with urllib.request.urlopen(URL + '/metrics', timeout=3) as response:
    metrics = response.read().decode()
assert 'payguard_sidecar_up 1\n' in metrics, metrics
assert 'payguard_sidecar_checks_total ' in metrics, metrics
print('Sidecar health and real probe metrics verified.')
