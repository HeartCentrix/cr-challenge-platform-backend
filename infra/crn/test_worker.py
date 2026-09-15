import unittest
from unittest.mock import MagicMock, patch
import worker


class WorkerTests(unittest.TestCase):
    def target(self, row):
        target=MagicMock()
        cursor=MagicMock(spec=worker.pg8000.dbapi.Cursor)
        target.cursor.return_value=cursor
        cursor.fetchone.return_value=row
        return target,cursor

    def test_normalization(self):
        self.assertEqual(worker.normalize_email(' User@Example.COM '),'user@example.com')
        self.assertEqual(worker.normalize_phone('+1 (202) 555-0196'),'12025550196')
        self.assertNotEqual(worker.normalize_phone('+91 2025550196'),worker.normalize_phone('+1 2025550196'))
        for value in ['',None,'---','123','9'*16]: self.assertEqual(worker.normalize_phone(value),'')

    def test_empty_queue_never_queries_fion(self):
        target,cursor=self.target(None)
        lookup=MagicMock()
        self.assertEqual(worker.process_one(target,lookup),'idle')
        lookup.assert_not_called();target.commit.assert_not_called();target.rollback.assert_called_once()
        self.assertIn('FOR UPDATE OF m SKIP LOCKED',cursor.execute.call_args_list[1].args[0])

    def test_all_match_combinations_are_saved_independently(self):
        for flags in [(True,True),(True,False),(False,True),(False,False)]:
            target,cursor=self.target((42,2,'example@example.invalid','+12025550196'))
            lookup=MagicMock(return_value=flags)
            self.assertEqual(worker.process_one(target,lookup),'checked')
            self.assertEqual(cursor.execute.call_args.args[1],(*flags,42,2))
            target.commit.assert_called_once()

    def test_lookup_failure_retries_without_false_negative_flags(self):
        target,cursor=self.target((42,2,'example@example.invalid','+12025550196'))
        self.assertEqual(worker.process_one(target,MagicMock(side_effect=RuntimeError('sensitive message'))),'error')
        update=cursor.execute.call_args.args[0]
        self.assertIn("status='ERROR'",update)
        self.assertNotIn('email_match=',update)
        target.commit.assert_called_once()

    def test_blank_identity_cannot_match_missing_fion_identity(self):
        with patch.object(worker,'connect') as connect:
            self.assertEqual(worker.lookup('',''),(False,False))
            connect.assert_not_called()

    def test_source_query_is_read_only_and_returns_only_exists_flags(self):
        source,cursor=self.target((True,False))
        with patch.object(worker,'connect',return_value=source):
            self.assertEqual(worker.lookup(' USER@EXAMPLE.COM ','+1 (202) 555-0196'),(True,False))
        calls=cursor.execute.call_args_list
        self.assertEqual(calls[0].args[0],'SET TRANSACTION READ ONLY')
        self.assertEqual(calls[2].args[1],('user@example.com','user@example.com','12025550196','12025550196'))
        source.commit.assert_not_called()


if __name__=='__main__':unittest.main()
