const MAX_MESSAGE_LENGTH = 12000;

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return json({ error: "method_not_allowed" }, 405);
    }

    const authorization = request.headers.get("Authorization") || "";
    if (!env.GRAFANA_WEBHOOK_TOKEN || authorization !== `Bearer ${env.GRAFANA_WEBHOOK_TOKEN}`) {
      return json({ error: "unauthorized" }, 401);
    }

    if (!env.WEB3FORMS_ACCESS_KEY) {
      return json({ error: "relay_not_configured" }, 500);
    }

    let alertPayload;
    try {
      alertPayload = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const message = formatAlert(alertPayload).slice(0, MAX_MESSAGE_LENGTH);
    const response = await fetch(env.WEB3FORMS_API_URL || "https://api.web3forms.com/submit", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        access_key: env.WEB3FORMS_ACCESS_KEY,
        subject: `[DocFlow] ${alertPayload?.status || "alerta"}`,
        from_name: "DocFlow Grafana Cloud",
        message,
        botcheck: ""
      })
    });

    if (!response.ok) {
      return json({ error: "web3forms_rejected", status: response.status }, 502);
    }

    return json({ delivered: true });
  }
};

function formatAlert(payload) {
  const alerts = Array.isArray(payload?.alerts) ? payload.alerts : [];
  const lines = [
    `Status: ${safe(payload?.status)}`,
    `Receiver: ${safe(payload?.receiver)}`,
    `Alerts: ${alerts.length}`
  ];

  for (const alert of alerts.slice(0, 20)) {
    lines.push(`- ${safe(alert?.status)} ${safe(alert?.labels?.alertname || alert?.annotations?.summary)}`);
    if (alert?.annotations?.description) {
      lines.push(`  ${safe(alert.annotations.description)}`);
    }
    if (alert?.annotations?.runbook_url) {
      lines.push(`  Runbook: ${safe(alert.annotations.runbook_url)}`);
    }
  }

  return lines.join("\n");
}

function safe(value) {
  return String(value ?? "").replace(/[\r\n]+/g, " ").slice(0, 1000);
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}
