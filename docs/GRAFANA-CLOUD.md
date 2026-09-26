# Real Grafana dashboard online

The [public portfolio dashboard](https://chandanvura.github.io/PayGuard/dashboard.html) is an educational replay. This guide publishes the **actual Grafana dashboard** using Grafana Cloud's free tier. Prometheus stays local and forwards selected metrics with `remote_write`; the Grafana dashboard is hosted online. The laptop must be running for **new** samples to arrive. Historical samples remain visible within the Cloud retention window.

## 1. Grafana Cloud account

Create/sign in to a free Grafana Cloud stack. In the Cloud Portal, open **Prometheus → Details** and copy its remote write endpoint (HTTPS URL ending `/api/prom/push`) and numeric metrics instance ID. Create an access policy token with **metrics:write** scope. Do not paste the token into a GitHub issue, chat, or commit.

## 2. Configure the local Prometheus sender (Windows PowerShell)

```powershell
cd C:\Users\srich\OneDrive\Desktop\PayGuard
.\scripts\enable-cloud-metrics.ps1 -RemoteWriteUrl 'https://prometheus-...grafana.net/api/prom/push' -MetricsInstanceId 'YOUR_NUMERIC_ID'
.\scripts\start.ps1
```

The script prompts privately for the token. It creates two ignored local files: `.secrets/grafana-cloud-metrics-token` and `observability/prometheus/prometheus-cloud.yml`. `docker-compose.cloud.yml` mounts those files only into Prometheus. The normal Compose deployment continues to work without Cloud configuration. Only PayGuard, HTTP request, and `up` series are sent to limit cardinality.

Verify locally at `http://localhost:9090/targets` that the payment target is UP. In Grafana Cloud Explore, query `payguard:sli_availability:ratio5m` after several minutes. Recording rules are evaluated by local Prometheus and forwarded with the other selected series.

## 3. Import the actual dashboard

In the hosted Grafana stack: **Dashboards → New → Import dashboard → Upload JSON**. Choose `observability/grafana/payguard-cloud-dashboard.json` and map `DS_PROMETHEUS` to the Grafana Cloud Prometheus data source. The Cloud copy defaults to a 24-hour range so the latest local session remains visible after shutdown.

## 4. Publish a read-only link

Open the imported dashboard, select **Share → Share externally → Anyone with the link**, confirm that its panels and metric labels are safe to make public, and copy the resulting URL. Visitors can see only the saved dashboard queries through that link. Put that URL on the PayGuard website once tested in a signed-out browser.

Grafana Cloud shares the **Grafana dashboard**, not the entire Prometheus UI. Do not expose `localhost:9090`, your Docker socket, or Prometheus's query API publicly. The local Prometheus UI remains for your own debugging.

## 5. Stop and revoke

`.\scripts\stop.ps1` stops local metric collection; Cloud history remains until retention expires. You can pause or revoke the shared dashboard in Grafana Cloud. Revoke the access policy token in Cloud if it is exposed, then rerun the configuration script with a new token.
