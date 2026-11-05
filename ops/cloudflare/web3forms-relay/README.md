# Grafana → Web3Forms relay

Grafana Cloud sends its native alert JSON to this Worker. The Worker validates a
Bearer token, removes unbounded payload data, and submits a compact message to
Web3Forms.

## Deploy

From this directory:

```bash
npx wrangler secret put GRAFANA_WEBHOOK_TOKEN
npx wrangler secret put WEB3FORMS_ACCESS_KEY
npx wrangler deploy
```

Configure the Grafana Cloud webhook contact point with:

- URL: the deployed Worker URL;
- Authorization header: `Bearer <GRAFANA_WEBHOOK_TOKEN>`;
- notification grouping left enabled to avoid alert noise.

Never commit either secret. Test the contact point from Grafana before assigning
it to the DocFlow alert rules.
