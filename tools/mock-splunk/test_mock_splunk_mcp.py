import json
import os
import subprocess
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import mock_splunk_mcp as splunk  # noqa: E402

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mock_splunk_mcp.py")


class SearchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.events = splunk.load_events([splunk.DEFAULT_LOG_FILE])

    def search(self, query, **kwargs):
        return splunk.run_search(query, events=self.events, **kwargs)

    def test_extracts_validation_error_fields(self):
        results = self.search('index=apm event_type=validation_error field=currency rejected_value=USD')[
            "results"
        ]
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["orderReference"], "ORDER-123")
        self.assertEqual(results[0]["reason"], "is not supported by IDEAL (supported: [EUR])")

    def test_stats_count_by_status(self):
        results = self.search("index=apm event_type=request_rejected | stats count by status")["results"]
        self.assertEqual(
            results,
            [{"status": "400", "count": 11}, {"status": "405", "count": 1}, {"status": "415", "count": 1}],
        )

    def test_numeric_comparison_and_not(self):
        result = self.search("index=apm event_type=request_rejected status>=405 NOT status=415")
        self.assertEqual(result["result_count"], 1)
        self.assertEqual(result["results"][0]["fault_code"], "4050")

    def test_or_and_wildcards(self):
        result = self.search("index=apm field=amount OR field=country* | stats count")
        self.assertEqual(result["results"], [{"count": 8}])

    def test_unreadable_body_extracts_field(self):
        results = self.search(
            "index=apm event_type=unreadable_body field=paymentMethod | table rejected_value"
        )["results"]
        self.assertEqual(results, [{"rejected_value": "BITCOIN"}])

    def test_successful_request_fields(self):
        results = self.search("index=apm event_type=request_received | table paymentMethod currency countryCode")[
            "results"
        ]
        self.assertEqual(results, [{"paymentMethod": "IDEAL", "currency": "EUR", "countryCode": "NL"}])

    def test_top_sort_head(self):
        top = self.search("index=apm event_type=validation_error | top limit=1 field")["results"]
        self.assertEqual(top[0]["field"], "amount")
        head = self.search("index=apm level=WARN | sort -_time | head 3")
        self.assertEqual(len(head["results"]), 3)

    def test_time_range_relative_to_shifted_events(self):
        self.assertGreater(self.search("index=apm", earliest_time="-15m")["result_count"], 0)
        self.assertEqual(self.search("index=apm", latest_time="-1d")["result_count"], 0)

    def test_unsupported_command_is_reported(self):
        with self.assertRaises(splunk.SearchError):
            self.search("index=apm | eval x=1")

    def test_alerts_trigger_on_sample_log(self):
        alerts = splunk.list_alerts()["alerts"]
        self.assertTrue(all(alert["triggered"] for alert in alerts))


class McpProtocolTest(unittest.TestCase):
    def test_stdio_session(self):
        requests = [
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18"}},
            {"jsonrpc": "2.0", "method": "notifications/initialized"},
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list"},
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "splunk_search",
                    "arguments": {"query": "index=apm event_type=validation_error | stats count"},
                },
            },
            {
                "jsonrpc": "2.0",
                "id": 4,
                "method": "tools/call",
                "params": {"name": "splunk_search", "arguments": {"query": "index=apm | bogus"}},
            },
            {"jsonrpc": "2.0", "id": 5, "method": "unknown/method"},
        ]
        output = subprocess.run(
            [sys.executable, SCRIPT],
            input="\n".join(json.dumps(r) for r in requests) + "\n",
            capture_output=True,
            text=True,
            check=True,
            timeout=30,
        ).stdout
        responses = {r["id"]: r for r in map(json.loads, output.splitlines())}
        self.assertEqual(sorted(responses), [1, 2, 3, 4, 5])
        self.assertEqual(responses[1]["result"]["serverInfo"]["name"], "mock-splunk")
        self.assertEqual(
            [t["name"] for t in responses[2]["result"]["tools"]],
            ["splunk_search", "splunk_list_indexes", "splunk_get_field_summary", "splunk_list_alerts"],
        )
        payload = json.loads(responses[3]["result"]["content"][0]["text"])
        self.assertEqual(payload["results"], [{"count": 16}])
        self.assertTrue(responses[4]["result"]["isError"])
        self.assertEqual(responses[5]["error"]["code"], -32601)


if __name__ == "__main__":
    unittest.main()
