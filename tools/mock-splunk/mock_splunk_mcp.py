#!/usr/bin/env python3
"""Mock Splunk MCP server backed by APM-Payments log files.

Speaks the Model Context Protocol over stdio (newline-delimited JSON-RPC 2.0) and
answers a subset of Splunk's search language (SPL) by reading Spring Boot log files.
Standard library only, so it runs anywhere python3 (3.8+) is available.

Environment variables:
  MOCK_SPLUNK_LOG_FILES       Log files to index, separated by commas
                              (default: sample-logs/apm-payments.log next to this file).
  MOCK_SPLUNK_SHIFT_TO_NOW    "true" (default) shifts event timestamps so the newest event
                              is "now", making relative time ranges like -15m work on old logs.

Run `python3 mock_splunk_mcp.py --search 'index=apm level=WARN | stats count by field'`
to try a search from the command line without an MCP client.
"""

import argparse
import fnmatch
import json
import math
import os
import re
import sys
from collections import Counter, OrderedDict
from datetime import datetime, timedelta, timezone

SERVER_NAME = "mock-splunk"
SERVER_VERSION = "0.1.0"
DEFAULT_PROTOCOL_VERSION = "2025-06-18"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_LOG_FILE = os.path.join(SCRIPT_DIR, "sample-logs", "apm-payments.log")
INDEX = "apm"
SOURCETYPE = "spring_boot"
HOST = "apm-payments"
MAX_RESULTS_CAP = 1000

LINE_PATTERN = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(?P<level>[A-Z]+)\s+(?P<pid>\d+)"
    r"\s+---\s+\[\s*(?P<thread>[^\]]+?)\]\s+(?P<logger>\S+)\s+:\s(?P<message>.*)$"
)

MESSAGE_PATTERNS = [
    (
        "validation_error",
        re.compile(
            r"^Validation failed for orderReference = (?P<orderReference>\S+): "
            r"field '(?P<field>[^']+)' (?P<reason>.*) \(rejected value: '(?P<rejected_value>.*)'\)$"
        ),
    ),
    (
        "request_rejected",
        re.compile(
            r"^Rejected (?P<http_method>\w+) (?P<uri>\S+) with HTTP (?P<status>\d+): "
            r"Fault\{code='(?P<fault_code>\d+)', message='(?P<fault_message>.*?)', errors="
        ),
    ),
    (
        "unreadable_body",
        re.compile(r"^Unreadable request body: (?P<fault_message>.*?) \[(?P<errors>.*)\]$"),
    ),
    (
        "unsupported_media_type",
        re.compile(r"^Content-Type '(?P<content_type>[^']*)' is not supported"),
    ),
    (
        "method_not_allowed",
        re.compile(r"^HTTP method (?P<http_method>\w+) is not supported for (?P<uri>\S+)$"),
    ),
    ("request_received", re.compile(r"^Received APM payment request: ApmPaymentRequest\{(?P<body>.*)\}$")),
    (
        "request_accepted",
        re.compile(
            r"^APM payment request for orderReference = (?P<orderReference>\S+) "
            r"was received with transactionId = (?P<transactionId>\S+)$"
        ),
    ),
]

REQUEST_FIELD_PATTERN = re.compile(r"(\w+)=(?:'([^']*)'|([^,]*))")
FIELD_ERROR_PATTERN = re.compile(r"^(?P<field>\S+) (?P<reason>.*) \(rejected value: (?P<rejected_value>.*)\)$")


# --------------------------------------------------------------------------- log parsing


def log_files():
    configured = os.environ.get("MOCK_SPLUNK_LOG_FILES", "").strip()
    if not configured:
        return [DEFAULT_LOG_FILE]
    return [os.path.abspath(os.path.expanduser(p.strip())) for p in configured.split(",") if p.strip()]


def parse_message_fields(message):
    for event_type, pattern in MESSAGE_PATTERNS:
        match = pattern.search(message)
        if not match:
            continue
        fields = {"event_type": event_type}
        groups = match.groupdict()
        body = groups.pop("body", None)
        fields.update({k: v for k, v in groups.items() if v is not None})
        if body is not None:
            for key, quoted, plain in REQUEST_FIELD_PATTERN.findall(body):
                fields[key] = quoted if quoted else plain.strip()
        field_error = FIELD_ERROR_PATTERN.match(fields.get("errors", ""))
        if field_error:
            fields.update(field_error.groupdict())
        return fields
    return {}


def load_events(paths=None):
    events = []
    for path in paths or log_files():
        if not os.path.isfile(path):
            continue
        with open(path, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                line = line.rstrip("\n")
                match = LINE_PATTERN.match(line)
                if not match:
                    if events and line.strip():
                        events[-1]["_raw"] += "\n" + line
                    continue
                timestamp = datetime.strptime(match.group("ts"), "%Y-%m-%d %H:%M:%S.%f")
                event = {
                    "_time": timestamp.replace(tzinfo=timezone.utc),
                    "_raw": line,
                    "index": INDEX,
                    "sourcetype": SOURCETYPE,
                    "source": path,
                    "host": HOST,
                    "level": match.group("level"),
                    "pid": match.group("pid"),
                    "thread": match.group("thread"),
                    "logger": match.group("logger"),
                    "message": match.group("message"),
                }
                event.update(parse_message_fields(match.group("message")))
                events.append(event)
    if events and os.environ.get("MOCK_SPLUNK_SHIFT_TO_NOW", "true").lower() != "false":
        shift = datetime.now(timezone.utc) - max(e["_time"] for e in events)
        for event in events:
            event["_time"] += shift
    events.sort(key=lambda e: e["_time"], reverse=True)
    return events


# --------------------------------------------------------------------------- time ranges

RELATIVE_TIME = re.compile(r"^([+-])(\d+)(s|sec|m|min|h|hr|d|day|w)?(?:@\w+)?$")
UNIT_SECONDS = {
    None: 1, "s": 1, "sec": 1, "m": 60, "min": 60, "h": 3600, "hr": 3600,
    "d": 86400, "day": 86400, "w": 604800,
}


def parse_time(value, now):
    if value is None or str(value).strip() == "":
        return None
    value = str(value).strip()
    if value == "now":
        return now
    if value == "0":
        return None
    match = RELATIVE_TIME.match(value)
    if match:
        sign, amount, unit = match.groups()
        delta = timedelta(seconds=int(amount) * UNIT_SECONDS[unit])
        return now - delta if sign == "-" else now + delta
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        try:
            return datetime.fromtimestamp(float(value), timezone.utc)
        except ValueError:
            raise SearchError("Unsupported time value: " + value)
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


# --------------------------------------------------------------------------- SPL subset


class SearchError(Exception):
    pass


TOKEN_PATTERN = re.compile(
    r'\s*(?:(?P<kv>[\w.]+)\s*(?P<op>!=|>=|<=|=|>|<)\s*(?P<val>"(?:[^"\\]|\\.)*"|[^\s()]+)'
    r'|(?P<quoted>"(?:[^"\\]|\\.)*")|(?P<paren>[()])|(?P<word>[^\s()]+))'
)


def unquote(value):
    if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
        return value[1:-1].replace('\\"', '"')
    return value


def split_pipeline(query):
    segments, current, in_quotes = [], [], False
    for char in query:
        if char == '"':
            in_quotes = not in_quotes
        if char == "|" and not in_quotes:
            segments.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    segments.append("".join(current).strip())
    return segments


def wildcard_match(pattern, value):
    return fnmatch.fnmatchcase(str(value).lower(), str(pattern).lower())


def to_number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def make_term(token):
    if token.group("kv"):
        key, op, raw = token.group("kv"), token.group("op"), unquote(token.group("val"))

        def predicate(event):
            if key not in event:
                return False
            actual = format_value(event[key])
            if op == "=":
                return wildcard_match(raw, actual)
            if op == "!=":
                return not wildcard_match(raw, actual)
            left, right = to_number(actual), to_number(raw)
            if left is None or right is None:
                return False
            return {">": left > right, "<": left < right, ">=": left >= right, "<=": left <= right}[op]

        return predicate
    text = unquote(token.group("quoted") or token.group("word"))
    pattern = text if "*" in text else "*" + text + "*"
    return lambda event: wildcard_match(pattern, event["_raw"])


def parse_search(segment):
    """Returns (predicate, earliest, latest). Supports terms, key=value, NOT, OR, AND."""
    if segment.lower().startswith("search "):
        segment = segment[7:]
    groups, negate_next, join_or = [], False, False
    earliest = latest = None
    for token in TOKEN_PATTERN.finditer(segment):
        if token.group("paren"):
            continue
        word = token.group("word")
        if word in ("AND",):
            continue
        if word == "NOT":
            negate_next = True
            continue
        if word == "OR":
            join_or = True
            continue
        if token.group("kv") in ("earliest", "latest") and token.group("op") == "=":
            if token.group("kv") == "earliest":
                earliest = unquote(token.group("val"))
            else:
                latest = unquote(token.group("val"))
            continue
        term = make_term(token)
        if negate_next:
            term = (lambda inner: lambda event: not inner(event))(term)
            negate_next = False
        if join_or and groups:
            groups[-1].append(term)
        else:
            groups.append([term])
        join_or = False

    def predicate(event):
        return all(any(term(event) for term in group) for group in groups)

    return predicate, earliest, latest


def format_value(value):
    if isinstance(value, datetime):
        return value.isoformat(timespec="milliseconds")
    return value


def split_fields(text):
    return [f for f in re.split(r"[\s,]+", text.strip()) if f]


AGGREGATION = re.compile(r"(count|dc|distinct_count|values|min|max|avg|sum)(?:\(([\w.]+)\))?(?:\s+as\s+([\w.]+))?", re.I)


def aggregate(func, field, rows):
    func = func.lower()
    if func == "count":
        return len(rows) if not field else sum(1 for r in rows if field in r)
    values = [format_value(r[field]) for r in rows if field in r]
    if func in ("dc", "distinct_count"):
        return len(set(values))
    if func == "values":
        return sorted(set(str(v) for v in values))
    numbers = [n for n in (to_number(v) for v in values) if n is not None]
    if not numbers:
        return None
    if func == "min":
        return min(numbers)
    if func == "max":
        return max(numbers)
    if func == "sum":
        return sum(numbers)
    return round(sum(numbers) / len(numbers), 4)


def parse_aggregations(text):
    aggregations = []
    for match in AGGREGATION.finditer(text):
        func, field, alias = match.groups()
        if func.lower() != "count" and not field:
            raise SearchError(func + " requires a field, e.g. " + func + "(field)")
        name = alias or (func if not field else func + "(" + field + ")")
        aggregations.append((func, field, name))
    if not aggregations:
        raise SearchError("No aggregation found; use e.g. 'stats count by field'")
    return aggregations


def command_stats(rows, args):
    by_split = re.split(r"\s+by\s+", args, maxsplit=1, flags=re.I)
    aggregations = parse_aggregations(by_split[0])
    by_fields = split_fields(by_split[1]) if len(by_split) > 1 else []
    groups = OrderedDict()
    for row in rows:
        if any(f not in row for f in by_fields):
            continue
        key = tuple(str(format_value(row[f])) for f in by_fields)
        groups.setdefault(key, []).append(row)
    if not by_fields and not groups:
        groups[()] = []
    result = []
    for key in sorted(groups):
        out = OrderedDict(zip(by_fields, key))
        for func, field, name in aggregations:
            out[name] = aggregate(func, field, groups[key])
        result.append(out)
    return result


def parse_options(args):
    options = dict(re.findall(r"(\w+)=(\S+)", args))
    rest = re.sub(r"\w+=\S+", "", args).strip()
    return options, rest


def command_top(rows, args):
    options, rest = parse_options(args)
    limit = int(options.get("limit", 10))
    fields = split_fields(rest)
    if not fields:
        raise SearchError("top requires a field")
    counts = Counter(
        tuple(str(format_value(r[f])) for f in fields) for r in rows if all(f in r for f in fields)
    )
    total = sum(counts.values()) or 1
    result = []
    for key, count in counts.most_common(limit if limit > 0 else None):
        out = OrderedDict(zip(fields, key))
        out["count"] = count
        out["percent"] = round(100.0 * count / total, 2)
        result.append(out)
    return result


def command_timechart(rows, args):
    options, rest = parse_options(args)
    span = options.get("span", "1m")
    match = re.match(r"^(\d+)(s|m|h|d)$", span)
    if not match:
        raise SearchError("Unsupported span: " + span)
    seconds = int(match.group(1)) * UNIT_SECONDS[match.group(2)]
    by_split = re.split(r"\s+by\s+", rest, maxsplit=1, flags=re.I)
    func, field, name = parse_aggregations(by_split[0] or "count")[0]
    by_field = by_split[1].strip() if len(by_split) > 1 else None
    buckets = OrderedDict()
    for row in sorted(rows, key=lambda r: r["_time"]):
        epoch = row["_time"].timestamp()
        start = datetime.fromtimestamp(math.floor(epoch / seconds) * seconds, timezone.utc)
        buckets.setdefault(start, []).append(row)
    result = []
    for start, bucket in buckets.items():
        out = OrderedDict([("_time", start)])
        if by_field:
            for value in sorted({str(format_value(r[by_field])) for r in bucket if by_field in r}):
                subset = [r for r in bucket if str(format_value(r.get(by_field))) == value]
                out[value] = aggregate(func, field, subset)
        else:
            out[name] = aggregate(func, field, bucket)
        result.append(out)
    return result


def sort_key(value):
    number = to_number(format_value(value))
    return (0, number, "") if number is not None else (1, 0, str(format_value(value)))


def command_sort(rows, args):
    tokens = split_fields(args)
    limit = None
    if tokens and tokens[0].isdigit():
        limit = int(tokens.pop(0))
    result = list(rows)
    for token in reversed(tokens):
        descending = token.startswith("-")
        field = token.lstrip("+-")
        present = [r for r in result if field in r]
        missing = [r for r in result if field not in r]
        result = sorted(present, key=lambda r: sort_key(r[field]), reverse=descending) + missing
    return result[:limit] if limit else result


def command_dedup(rows, args):
    fields = split_fields(args)
    seen, result = set(), []
    for row in rows:
        key = tuple(str(format_value(row.get(f))) for f in fields)
        if key not in seen:
            seen.add(key)
            result.append(row)
    return result


def command_table(rows, args):
    fields = split_fields(args)
    if fields and fields[0] == "-":
        removed = set(fields[1:])
        return [OrderedDict((k, v) for k, v in r.items() if k not in removed) for r in rows]
    return [OrderedDict((f, r.get(f)) for f in fields) for r in rows]


def command_head(rows, args):
    return rows[: int(args.strip() or 10)]


def command_tail(rows, args):
    count = int(args.strip() or 10)
    return list(reversed(rows[-count:])) if count else []


def command_rename(rows, args):
    pairs = re.findall(r"([\w.]+)\s+as\s+([\w.]+)", args, flags=re.I)
    result = []
    for row in rows:
        out = OrderedDict(row)
        for old, new in pairs:
            if old in out:
                out[new] = out.pop(old)
        result.append(out)
    return result


COMMANDS = {
    "stats": command_stats,
    "top": command_top,
    "timechart": command_timechart,
    "sort": command_sort,
    "dedup": command_dedup,
    "table": command_table,
    "fields": command_table,
    "head": command_head,
    "tail": command_tail,
    "rename": command_rename,
}


def run_search(query, earliest_time=None, latest_time=None, max_results=100, events=None):
    if not query or not query.strip():
        raise SearchError("query must not be empty")
    segments = split_pipeline(query.strip())
    predicate, inline_earliest, inline_latest = parse_search(segments[0])
    now = datetime.now(timezone.utc)
    earliest = parse_time(inline_earliest or earliest_time, now)
    latest = parse_time(inline_latest or latest_time, now)
    rows = [
        e
        for e in (events if events is not None else load_events())
        if (earliest is None or e["_time"] >= earliest)
        and (latest is None or e["_time"] <= latest)
        and predicate(e)
    ]
    for segment in segments[1:]:
        name, _, args = segment.partition(" ")
        command = COMMANDS.get(name.lower())
        if command is None:
            raise SearchError(
                "Unsupported command '" + name + "'. Supported: " + ", ".join(sorted(COMMANDS))
            )
        rows = command(rows, args)
    max_results = max(1, min(int(max_results or 100), MAX_RESULTS_CAP))
    return {
        "query": query,
        "earliest_time": format_value(earliest) if earliest else "0 (all time)",
        "latest_time": format_value(latest) if latest else "now",
        "result_count": len(rows),
        "truncated": len(rows) > max_results,
        "results": [
            OrderedDict((k, format_value(v)) for k, v in row.items()) for row in rows[:max_results]
        ],
    }


# --------------------------------------------------------------------------- other tools

ALERTS = [
    {
        "name": "APM-Payments rejected requests",
        "description": "Requests rejected with 4xx by RequestExceptionHandler",
        "search": "index=apm event_type=request_rejected | stats count by status, fault_code",
        "count_search": "index=apm event_type=request_rejected",
        "earliest_time": "-15m",
        "threshold": 5,
        "severity": "high",
    },
    {
        "name": "APM-Payments validation errors",
        "description": "Individual field validation failures",
        "search": "index=apm event_type=validation_error | stats count by field",
        "count_search": "index=apm event_type=validation_error",
        "earliest_time": "-15m",
        "threshold": 10,
        "severity": "medium",
    },
]


def list_indexes():
    events = load_events()
    if not events:
        return {"indexes": [], "log_files": log_files()}
    return {
        "indexes": [
            {
                "name": INDEX,
                "event_count": len(events),
                "earliest_event": format_value(events[-1]["_time"]),
                "latest_event": format_value(events[0]["_time"]),
                "sourcetypes": [SOURCETYPE],
                "sources": sorted({e["source"] for e in events}),
            }
        ],
        "log_files": log_files(),
    }


def field_summary(query=None, earliest_time=None, latest_time=None):
    events = run_search(query or "index=" + INDEX, earliest_time, latest_time, MAX_RESULTS_CAP)[
        "results"
    ]
    counters = OrderedDict()
    for event in events:
        for key, value in event.items():
            if key in ("_raw", "_time", "message"):
                continue
            counters.setdefault(key, Counter())[str(value)] += 1
    return {
        "event_count": len(events),
        "fields": [
            {
                "field": key,
                "event_count": sum(counter.values()),
                "distinct_count": len(counter),
                "top_values": [{"value": v, "count": c} for v, c in counter.most_common(5)],
            }
            for key, counter in sorted(counters.items())
        ],
    }


def list_alerts():
    events = load_events()
    result = []
    for alert in ALERTS:
        count = run_search(alert["count_search"], alert["earliest_time"], events=events)["result_count"]
        breakdown = run_search(alert["search"], alert["earliest_time"], events=events)["results"]
        result.append(
            OrderedDict(
                [
                    ("name", alert["name"]),
                    ("description", alert["description"]),
                    ("severity", alert["severity"]),
                    ("search", alert["search"]),
                    ("earliest_time", alert["earliest_time"]),
                    ("condition", "count > " + str(alert["threshold"])),
                    ("count", count),
                    ("triggered", count > alert["threshold"]),
                    ("breakdown", breakdown),
                ]
            )
        )
    return {"alerts": result}


# --------------------------------------------------------------------------- MCP server

TIME_PROPERTIES = {
    "earliest_time": {
        "type": "string",
        "description": "Start of the time range: relative (-15m, -1h, -7d), ISO 8601, or 0 for all time. Default: all time.",
    },
    "latest_time": {
        "type": "string",
        "description": "End of the time range: relative, ISO 8601, or now. Default: now.",
    },
}

TOOLS = [
    {
        "name": "splunk_search",
        "description": (
            "Run a Splunk search (SPL subset) over APM-Payments logs in index=apm. Search terms: "
            "free text, \"quoted phrases\", wildcards (*), field=value, field!=value, numeric "
            "comparisons (status>=400), NOT, OR, earliest=/latest=. Pipe commands: stats "
            "(count, dc, values, min, max, avg, sum ... by fields), top, timechart span=1m, sort, "
            "dedup, table, fields, head, tail, rename. Extracted fields include level, logger, "
            "thread, event_type (validation_error, request_rejected, unreadable_body, "
            "unsupported_media_type, method_not_allowed, request_received, request_accepted), "
            "orderReference, field, reason, rejected_value, http_method, uri, status, fault_code, "
            "fault_message, transactionId, merchantId, paymentMethod, currency, countryCode."
        ),
        "inputSchema": {
            "type": "object",
            "properties": dict(
                {
                    "query": {
                        "type": "string",
                        "description": "SPL query, e.g. index=apm level=WARN event_type=validation_error | stats count by field",
                    },
                    "max_results": {
                        "type": "integer",
                        "description": "Maximum number of results to return (default 100, max 1000).",
                    },
                },
                **TIME_PROPERTIES
            ),
            "required": ["query"],
        },
    },
    {
        "name": "splunk_list_indexes",
        "description": "List available indexes with event counts, time span, and source log files.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "splunk_get_field_summary",
        "description": "Summarize extracted fields (distinct counts and top values) for events matching a search.",
        "inputSchema": {
            "type": "object",
            "properties": dict(
                {"query": {"type": "string", "description": "Optional SPL filter (default: index=apm)."}},
                **TIME_PROPERTIES
            ),
        },
    },
    {
        "name": "splunk_list_alerts",
        "description": "List saved alerts for APM-Payments, evaluated now, with trigger status and breakdown.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def call_tool(name, arguments):
    if name == "splunk_search":
        return run_search(
            arguments.get("query", ""),
            arguments.get("earliest_time"),
            arguments.get("latest_time"),
            arguments.get("max_results", 100),
        )
    if name == "splunk_list_indexes":
        return list_indexes()
    if name == "splunk_get_field_summary":
        return field_summary(
            arguments.get("query"), arguments.get("earliest_time"), arguments.get("latest_time")
        )
    if name == "splunk_list_alerts":
        return list_alerts()
    raise KeyError(name)


def handle_request(message):
    """Returns a JSON-RPC response dict, or None for notifications."""
    method = message.get("method")
    request_id = message.get("id")
    params = message.get("params") or {}
    if request_id is None:
        return None

    def result(value):
        return {"jsonrpc": "2.0", "id": request_id, "result": value}

    def error(code, text):
        return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": text}}

    if method == "initialize":
        return result(
            {
                "protocolVersion": params.get("protocolVersion", DEFAULT_PROTOCOL_VERSION),
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
                "instructions": "Mock Splunk over APM-Payments log files. Use index=apm.",
            }
        )
    if method == "ping":
        return result({})
    if method == "tools/list":
        return result({"tools": TOOLS})
    if method == "tools/call":
        name = params.get("name")
        try:
            payload = call_tool(name, params.get("arguments") or {})
        except KeyError:
            return error(-32602, "Unknown tool: " + str(name))
        except (SearchError, ValueError) as exc:
            return result({"content": [{"type": "text", "text": "Search error: " + str(exc)}], "isError": True})
        return result({"content": [{"type": "text", "text": json.dumps(payload, indent=2)}], "isError": False})
    if method in ("resources/list", "prompts/list"):
        return result({method.split("/")[0]: []})
    return error(-32601, "Method not found: " + str(method))


def serve(stdin=sys.stdin, stdout=sys.stdout):
    for line in stdin:
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except json.JSONDecodeError:
            response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Parse error"}}
        else:
            batch = message if isinstance(message, list) else [message]
            responses = [r for r in (handle_request(m) for m in batch) if r is not None]
            if not responses:
                continue
            response = responses if isinstance(message, list) else responses[0]
        stdout.write(json.dumps(response) + "\n")
        stdout.flush()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--search", help="Run one SPL query, print JSON results and exit.")
    parser.add_argument("--earliest", help="earliest_time for --search")
    parser.add_argument("--latest", help="latest_time for --search")
    parser.add_argument("--alerts", action="store_true", help="Print evaluated alerts and exit.")
    args = parser.parse_args()
    if args.search:
        try:
            print(json.dumps(run_search(args.search, args.earliest, args.latest), indent=2))
        except SearchError as exc:
            sys.exit("Search error: " + str(exc))
    elif args.alerts:
        print(json.dumps(list_alerts(), indent=2))
    else:
        serve()


if __name__ == "__main__":
    main()
