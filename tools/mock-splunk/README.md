# Mock Splunk MCP server

A fake Splunk connector for Devin (or any MCP client) that answers Splunk-style searches from
APM-Payments log files instead of a real Splunk instance. Use it to try Devin Oncall / Devin
sessions against realistic logs without running Splunk.

- `mock_splunk_mcp.py` - MCP server over stdio, Python 3.8+ standard library only.
- `sample-logs/apm-payments.log` - log captured from APM-Payments while running the valid request and
  all 13 negative scenarios from the root README.
- `test_mock_splunk_mcp.py` - tests (`python3 -m unittest -v test_mock_splunk_mcp` from this folder).

## Tools

| Tool                       | What it does                                                                      |
| -------------------------- | --------------------------------------------------------------------------------- |
| `splunk_search`            | Runs an SPL query (subset, see below) with optional `earliest_time`/`latest_time`  |
| `splunk_list_indexes`      | Lists the `apm` index, event count, time span and source files                     |
| `splunk_get_field_summary` | Distinct counts and top values of extracted fields                                 |
| `splunk_list_alerts`       | Two saved alerts (rejected requests, validation errors) evaluated over `-15m`      |

Supported SPL:
- Search: free text, `"quoted phrases"`, `*` wildcards, `field=value`, `field!=value`, numeric
  comparisons (`status>=400`), `NOT`, `OR`, `earliest=-15m`, `latest=now`.
- Commands: `stats` (`count`, `dc`, `values`, `min`, `max`, `avg`, `sum` ... `by` fields), `top`,
  `timechart span=1m`, `sort`, `dedup`, `table`, `fields`, `head`, `tail`, `rename`.

Every event has `index=apm`, `sourcetype=spring_boot`, `host=apm-payments`, `source`, `level`,
`pid`, `thread`, `logger`, `message`, plus fields extracted from APM-Payments messages:

| `event_type`             | Extra fields                                                                |
| ------------------------ | --------------------------------------------------------------------------- |
| `validation_error`       | `orderReference`, `field`, `reason`, `rejected_value`                       |
| `request_rejected`       | `http_method`, `uri`, `status`, `fault_code`, `fault_message`               |
| `unreadable_body`        | `fault_message`, `errors`, and `field`/`reason`/`rejected_value` if present |
| `unsupported_media_type` | `content_type`                                                              |
| `method_not_allowed`     | `http_method`, `uri`                                                        |
| `request_received`       | `merchantId`, `orderReference`, `amount`, `currency`, `paymentMethod`, ...  |
| `request_accepted`       | `orderReference`, `transactionId`                                           |

By default timestamps are shifted so the newest event is "now", so relative ranges like `-15m`
and the saved alerts work with the old sample log. Set `MOCK_SPLUNK_SHIFT_TO_NOW=false` to keep the
original timestamps.

## Try it from the command line

```
cd tools/mock-splunk
python3 mock_splunk_mcp.py --search 'index=apm event_type=validation_error | stats count by field | sort -count'
python3 mock_splunk_mcp.py --search 'index=apm event_type=request_rejected | stats count by status, fault_code'
python3 mock_splunk_mcp.py --search 'index=apm level=WARN orderReference=ORDER-123 | table _time field rejected_value reason'
python3 mock_splunk_mcp.py --alerts
```

## Connect it to Devin

1. Go to [Customize > MCPs](https://app.devin.ai/customize?tab=mcps), open **Add MCP** and choose
   **Add custom MCP**.
2. Name it `Mock Splunk`, choose the **STDIO** transport and set:
   - **Command**: `python3`
   - **Arguments**: `/home/ubuntu/repos/APM-Transactions/tools/mock-splunk/mock_splunk_mcp.py`
   - **Environment variables** (optional): `MOCK_SPLUNK_LOG_FILES` - comma-separated log files to use
     instead of the sample, e.g. `/home/ubuntu/repos/APM-Transactions/logs/apm-payments.log` to
     search the logs of a locally running service.

   STDIO servers run inside the Devin session's machine, so the APM-Transactions repo must be cloned
   there (it is, when it's part of the environment setup); the path above assumes the default
   `~/repos` location.
3. Save, then click **Use MCP** to start a session and check that the four `splunk_*` tools work.
4. For Oncall, add `Mock Splunk` under the responder's **Connectors**, then post a test alert in the
   responder's trigger channel, e.g.:
   > [Splunk alert] APM-Payments rejected requests: 13 requests rejected in the last 15 minutes
   > (search: `index=apm event_type=request_rejected`). Please investigate.

## Generate new sample logs

Start APM-Payments, run the curl commands from the root README's "Negative test scenarios"
section, then copy `logs/apm-payments.log` over `sample-logs/apm-payments.log` (or point
`MOCK_SPLUNK_LOG_FILES` at it).
