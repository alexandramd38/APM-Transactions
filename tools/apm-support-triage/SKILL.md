---
name: apm-support-alert-triage
description: >-
  Scan the `apm-support` Slack channel for posted alerts and investigate each one
  in the APM-Payments logs via the Mock Splunk MCP server (`splunk_search`,
  `splunk_get_field_summary`, `splunk_list_alerts` over `index=apm`). Use when asked
  to triage, investigate, or summarize alerts from the apm-support channel.
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
