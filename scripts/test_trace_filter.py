import unittest
from trace_filter import allowlist, device_command, exact_uid
from trace_stream_parser import TraceStreamParser

class FilterTests(unittest.TestCase):
    def test_exact_package_only(self):
        self.assertEqual(12345, exact_uid('package:com.example.clone uid:12345\n', 'com.example.clone'))
        for value in ['package:com.example.clone.other uid:12345', 'package:com.example.clone uid:12345\npackage:com.example.clone.other uid:12346', '']:
            with self.assertRaises(ValueError): exact_uid(value, 'com.example.clone')
    def test_uid_time_and_duration_cannot_broaden_or_inject(self):
        for uid, since, seconds in [('12345; id', '10-02 12:34:56.000', 900), (0, '10-02 12:34:56.000', 900), (12345, '$(id)', 900), (12345, '10-02 12:34:56.000', 901)]:
            with self.assertRaises(ValueError): device_command(uid, since, seconds)
        command = device_command(12345, '10-02 12:34:56.000', 900)
        self.assertIn('--uid=12345', command)
        self.assertTrue(command.startswith('timeout 900 '))
        self.assertIn('SibblAuthTrace:I', command)
        self.assertIn('case', command)
    def test_every_release_marker_survives_arbitrary_chunking(self):
        parser = TraceStreamParser(allowlist())
        expected = sorted(allowlist()); data = ('\n'.join(expected) + '\n').encode()
        output = []
        for start in range(0, len(data), 7): output.extend(parser.feed(data[start:start + 7]))
        self.assertEqual(expected, output)
        self.assertEqual([], parser.feed(b'NATIVE_AFTER_MAP_TOP_CODE_secret@example.test\n'))

if __name__ == '__main__': unittest.main()
