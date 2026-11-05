# Grafana Cloud — DocFlow

## Data source

Configure a Grafana Cloud Prometheus data source for the staging stack. The
application exposes `/actuator/prometheus` only when `DOCFLOW_PROMETHEUS_ENABLED`
is `true`, and requires Basic Auth from `DOCFLOW_PROMETHEUS_USERNAME` and
`DOCFLOW_PROMETHEUS_PASSWORD`.

The scrape target is:

```text
https://docflow-backend-g2e2.onrender.com/actuator/prometheus
```

Do not commit the scrape credentials. Store them in Render and Grafana Cloud's
secret configuration.

## Dashboard and alerts

Import `docflow-dashboard.json` into Grafana Cloud and select the Prometheus data
source. The expressions in `alert-rules.yaml` encode the approved thresholds:

- DLQ above zero for 5 minutes;
- at least one `RESULT_UNKNOWN` document for 10 minutes;
- HTTP P95 above 2 seconds for 10 minutes;
- storage unavailable/result-unknown above zero for 5 minutes;
- health unavailable for 2 minutes.

Configure one webhook contact point pointing to the deployed Cloudflare Worker
relay described in `ops/cloudflare/web3forms-relay/README.md`.
