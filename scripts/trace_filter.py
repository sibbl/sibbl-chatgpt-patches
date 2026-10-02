# Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
"""Build a bounded device-side filter. This module never connects to a device itself."""
import re
import shlex
from pathlib import Path


def allowlist():
    entries = Path(__file__).with_name('trace-allowlist.txt').read_text().splitlines()
    if not entries or len(entries) != len(set(entries)) or not all(re.fullmatch(r'[A-Z0-9_]{1,128}', e) for e in entries):
        raise ValueError('Invalid diagnostic allowlist')
    return frozenset(entries)


def exact_uid(output, package):
    if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+', package):
        raise ValueError('Invalid exact package')
    match = re.fullmatch(r'package:' + re.escape(package) + r' uid:([0-9]+)\s*', output)
    if not match:
        raise ValueError('Exact package UID unavailable')
    return int(match.group(1))


def device_command(uid, since, seconds):
    if type(uid) is not int or uid < 10000 or type(seconds) is not int or not 1 <= seconds <= 900:
        raise ValueError('Invalid bounded capture scope')
    if not re.fullmatch(r'\d{2}-\d{2} \d{2}:\d{2}:\d{2}[.]000', since):
        raise ValueError('Invalid start timestamp')
    inner = ("set -o pipefail\nlogcat -b main --uid=" + str(uid) + " -v raw -T " + shlex.quote(since) +
             " -s 'SibblAuthTrace:I' '*:S' 2>/dev/null | {\nprintf '__SIBBL_TRACE_READY__\\n'\n" +
             "while IFS= read -r marker; do\ncase \"$marker\" in\n" + '|'.join(sorted(allowlist())) +
             ") printf '%s\\n' \"$marker\" ;;\nesac\ndone\n}\nreader_status=$?\n" +
             "printf '__SIBBL_TRACE_END_%s__\\n' \"$reader_status\"\n")
    return 'timeout ' + str(seconds) + ' sh -c ' + shlex.quote(inner)
