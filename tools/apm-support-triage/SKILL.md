---
name: apm-support-alert-triage
description: >-
  Scan the `apm-support` Slack channel for posted alerts and investigate each one
  in the APM-Payments logs via the Mock Splunk MCP server (`splunk_search`,
  `splunk_get_field_summary`, `splunk_list_alerts` over `index=apm`), then
  escalate to the xMatters on-call group when manual intervention is required.
  Use when asked to triage, investigate, or summarize alerts from the
  apm-support channel.
---

# APM-Support alert triage

Investigate alerts posted to the `apm-support` Slack channel by searching the
APM-Payments logs through the Mock Splunk MCP server.

## Prerequisites

- **Slack connector** (required; there is no Slack integration in this repo): any
  Slack MCP server or Slack API tool that can read channel history **and post
  replies**, e.g. the Slack MCP from the Devin marketplace. It must have **read and
  write access to the `apm-support` channel** (`conversations.history` plus
  `chat.postMessage` for the summary reply). If no Slack connector is available,
  stop and ask the user to connect
  one — do not fabricate channel contents.
- **Mock Splunk MCP** connected per
  [../mock-splunk/README.md](../mock-splunk/README.md) — it must expose
  `splunk_search`, `splunk_list_indexes`, `splunk_get_field_summary` and
  `splunk_list_alerts` over `index=apm`.
- **xMatters webhook** (required for escalation): the `XMATTERS_WEBHOOK_URL`
  environment variable — an org secret holding the xMatters inbound
  integration webhook. Send notifications with a plain HTTPS `POST` of JSON
  to it (e.g. `curl -X POST "$XMATTERS_WEBHOOK_URL" -H 'Content-Type: application/json' -d @payload.json`).
  Never print or log the URL. If the variable is unset or the POST fails, the
  agent must instead surface the escalation in its Slack summary — clearly
  flagging that human/on-call intervention is required and linking the
  xMatters runbook:
  https://apm.gh.worldpay.io/operations/monitoring/xmatters/groups_and_rotas#override-rota-for-a-specific-period-of-time
  — and must **not** fabricate that an xMatters notification was sent.

## Procedure

1. **Read the channel.** Fetch recent messages from `apm-support`. Default
   lookback: **last 24h** (configurable — use whatever window the user gives).
2. **Identify alerts.** A message is an alert if it:
   - contains `[Splunk alert]` (case-insensitive) or similar alert markers,
   - contains severity keywords (`critical`, `error`, `warning`, `firing`,
     `rejected`, `failed`), or
   - embeds an SPL clause like `` search: `index=apm ...` ``.
   Skip plain conversation, questions, and already-resolved notices.
3. **Map each alert to a query.**
   - If the message embeds `` search: `<spl>` ``, use that SPL verbatim.
   - If it names an `event_type` (e.g. `request_rejected`, `validation_error`),
     use `index=apm event_type=<type>`.
   - Otherwise map the alert text to the closest `event_type` from the table in
     `tools/mock-splunk/README.md` (e.g. "rejected requests" →
     `request_rejected`, "validation errors" → `validation_error`, "unsupported
     media type" → `unsupported_media_type`, "method not allowed" →
     `method_not_allowed`, "unreadable body / bad JSON" → `unreadable_body`).
4. **Investigate in Splunk.** Default time range `-15m` to match the saved
   alerts; widen it if the alert cites a different window or if results are
   empty. Useful calls:
   - `splunk_list_alerts` — see which saved alerts are currently firing.
   - `splunk_search` — counts, breakdowns, and representative `_raw` lines.
   - `splunk_get_field_summary` — top values of `field`, `fault_code`, `status`,
     `orderReference`, `uri`, `paymentMethod` to find patterns.
5. **Summarize per alert:** what triggered it, event count over the window,
   breakdown by `status`/`fault_code`/`field`, affected `orderReference`s (and
   `transactionId`s when present), the likely root cause from `reason` /
   `fault_message`, and whether the alert is still firing.
6. **Include impact.** Every summary must state the blast radius, not just the
   counts:
   - **Scope** — how many distinct `orderReference`s / `merchantId`s /
     `paymentMethod`s are hit vs. total traffic in the window (compare
     `request_received`/`request_accepted` counts to rejected counts to estimate
     the failure rate).
   - **Who is affected** — one merchant/order vs. many (e.g. a single client
     hammering the endpoint vs. broad organic failures).
   - **User-facing effect** — e.g. payments rejected at checkout, retries
     amplifying volume, or only test traffic.
   - **Severity call** — whether it looks like organic production failures or
     scripted/test traffic (all failure modes at once on one `orderReference`
     usually means the negative test scenarios were run).
7. **Always post the summary.** Post it back to `apm-support` as a **threaded
   reply** to the alert message — every time, not only on request. Keep it
   compact: trigger, counts/breakdown, impact, root cause, still-firing verdict.
8. **Escalate to on-call when manual intervention is needed.** After posting
   the summary, send a notification to the xMatters on-call group when any of
   these criteria hold:
   - **Merchant must fix their integration** — a consistent/repeated pattern
     of `validation_error` or `request_rejected` events with `fault_code`
     `4000`/`4001`, `unsupported_media_type` (`status=415`), or
     `method_not_allowed` (`status=405`) concentrated on one or more
     `merchantId`s — i.e. the merchant is calling the API incorrectly (bad
     `field` values, wrong content-type, wrong HTTP method, unknown
     `paymentMethod`). The notification must name the affected
     `merchantId`(s), the failing `field`(s)/`reason`(s), the `fault_code`,
     and representative `orderReference`s so the merchant can be contacted to
     fix their integration.
   - **Platform/server-side problem** — any `5xx` / Internal Server Error
     events (none exist in the current sample logs, but treat this as
     future-proof), or a sharp spike in failures spread across many
     `merchantId`s suggesting an APM-Payments outage rather than a single
     misbehaving client.
   - **Unexplained alert still firing** — an alert that remains firing after
     triage and cannot be explained as scripted/test traffic. Per the
     severity-call guidance, all failure modes hitting a single
     `orderReference` usually means the negative test scenarios were run —
     do **not** escalate those.
   The notification should include: the trigger, affected
   `merchantId`(s)/`orderReference`(s), root cause, whether it is a
   merchant-integration issue vs. a platform issue, severity, and a link to
   the xMatters runbook for overriding the on-call rota if needed:
   https://apm.gh.worldpay.io/operations/monitoring/xmatters/groups_and_rotas#override-rota-for-a-specific-period-of-time
   Send it with `curl -X POST "$XMATTERS_WEBHOOK_URL" -H 'Content-Type: application/json'`
   and a JSON body like `{"alert": "<title>", "summary": "<findings>", "impact": "<impact>", "recommendation": "<action>", "severity": "<high|medium|low>"}`.
   If the variable is unset or the POST fails, clearly flag in the Slack
   summary that human/on-call escalation is required and include the runbook
   link — never silently skip the escalation and never claim a notification
   was sent when it was not.

## Example SPL

```
index=apm event_type=request_rejected | stats count by status, fault_code
index=apm event_type=validation_error | stats count by field
index=apm event_type=validation_error field=currency | stats count by rejected_value, reason
index=apm level=WARN orderReference=ORDER-123 | table _time event_type field rejected_value reason fault_message
index=apm event_type=request_rejected | timechart span=1m count
index=apm event_type=unreadable_body earliest=-15m | stats count by fault_message
index=apm event_type=method_not_allowed | stats count by http_method, uri
index=apm event_type=unsupported_media_type | stats count by content_type
```

See [../mock-splunk/README.md](../mock-splunk/README.md) for the full SPL subset
and the extracted-field reference.
