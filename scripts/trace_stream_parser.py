# Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
"""Bounded parser: only fixed allowlisted events leave feed(); no line contents in errors."""
import re

class TraceStreamParser:
    def __init__(self, allowed):
        self.allowed=frozenset(allowed)
        self.buffer=bytearray()
        self.discarding=False
        self.closed=False
        self.ready=False
        self.end_code=None
        self.rejected=0

    def feed(self, chunk):
        if self.closed:raise RuntimeError('Cannot reuse a closed stream parser')
        events=[]
        for byte in chunk:
            if byte==10:
                if not self.discarding:
                    try:line=bytes(self.buffer).decode('ascii').removesuffix('\r')
                    except UnicodeDecodeError:line=None
                    if line in self.allowed:events.append(line)
                    elif line=='__SIBBL_TRACE_READY__':self.ready=True
                    elif line and re.fullmatch(r'__SIBBL_TRACE_END_[0-9]{1,3}__',line):
                        code=int(line.removeprefix('__SIBBL_TRACE_END_').removesuffix('__'))
                        if code<=255:self.end_code=code
                        else:self.rejected+=1
                    else:self.rejected+=1
                self.buffer.clear();self.discarding=False
            elif not self.discarding:
                self.buffer.append(byte)
                if len(self.buffer)>256:
                    self.buffer.clear();self.discarding=True;self.rejected+=1
        return events

    def finish(self):
        self.closed=True
        if self.buffer or self.discarding:self.rejected+=1
        self.buffer.clear();self.discarding=False
