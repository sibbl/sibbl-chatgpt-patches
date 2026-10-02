# Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
import unittest
from trace_stream_parser import TraceStreamParser

class ParserTests(unittest.TestCase):
    def parser(self):return TraceStreamParser({'PAGE_PASSWORD','NATIVE_STEP_OK'})
    def test_split_lines(self):
        p=self.parser();self.assertEqual([],p.feed(b'PAGE_PASS'));self.assertEqual(['PAGE_PASSWORD'],p.feed(b'WORD\nNATIVE_'));self.assertEqual(['NATIVE_STEP_OK'],p.feed(b'STEP_OK\n'))
    def test_multiple_lines_crlf_and_duplicates(self):
        p=self.parser();self.assertEqual(['PAGE_PASSWORD']*2,p.feed(b'PAGE_PASSWORD\r\nPAGE_PASSWORD\n'))
    def test_rotation_has_no_prefix_assumption(self):
        p=self.parser();self.assertEqual(['NATIVE_STEP_OK'],p.feed(b'NATIVE_STEP_OK\n'));self.assertEqual(['PAGE_PASSWORD','NATIVE_STEP_OK'],p.feed(b'--------- beginning of main\nPAGE_PASSWORD\nNATIVE_STEP_OK\n'))
    def test_unknown_and_non_ascii_lines_are_never_emitted(self):
        p=self.parser();self.assertEqual([],p.feed(b'UNEXPECTED_TEST_LINE\nPAGE_PASSWORD extra\n\xff\n'));self.assertEqual(3,p.rejected)
    def test_oversize_fragment_is_dropped_without_unbounded_buffer(self):
        p=self.parser();self.assertEqual([],p.feed(b'x'*10000));self.assertLessEqual(len(p.buffer),256);self.assertEqual(['PAGE_PASSWORD'],p.feed(b'PAGE_PASSWORD\nPAGE_PASSWORD\n'))
    def test_disconnect_does_not_emit_partial_event_or_silently_reconnect(self):
        p=self.parser();p.feed(b'PAGE_PASS');p.finish();self.assertTrue(p.closed)
        with self.assertRaises(RuntimeError):p.feed(b'WORD\n')
        q=self.parser();self.assertEqual([],q.feed(b'WORD\n'));self.assertEqual(['PAGE_PASSWORD'],q.feed(b'PAGE_PASSWORD\n'))
    def test_control_frames_are_separate_from_events(self):
        p=self.parser();self.assertEqual([],p.feed(b'__SIBBL_TRACE_READY__\n__SIBBL_TRACE_END_1__\n'));self.assertTrue(p.ready);self.assertEqual(1,p.end_code)
    def test_invalid_end_status_is_not_accepted(self):
        p=self.parser();p.feed(b'__SIBBL_TRACE_END_999__\n');self.assertIsNone(p.end_code)

if __name__=='__main__':unittest.main()
