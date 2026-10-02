#!/usr/bin/env python3
"""Refuse accidental publication of app inputs, decompiled code, logs, or credentials."""
from pathlib import Path
import re
import subprocess

names = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
for name in filter(None, names):
    p = Path(name)
    assert p.suffix.lower() not in {'.apk', '.apkm', '.apks', '.xapk', '.dex', '.mpp', '.mpe', '.log', '.jks', '.keystore', '.pem'}, name
    assert not any(part in {'decoded', 'jadx', 'analysis', 'private', 'raw-logs'} for part in p.parts), name
    if name == 'gradle/wrapper/gradle-wrapper.jar':
        continue  # Official Gradle build infrastructure, not an app/patch binary.
    data = p.read_bytes()
    assert b'\0' not in data, f'Unexpected binary: {name}'
    text = data.decode('utf-8')
    assert not re.search(r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)', text), name
    assert not re.search(r'(?m)^(?:/\* JADX INFO:|\.class public L)', text), f'Decompiled source: {name}'
print('Source-only publication check passed.')
