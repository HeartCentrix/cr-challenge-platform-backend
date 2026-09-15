import json
import unittest
from unittest.mock import MagicMock, patch
import lambda_function
import worker


class LambdaTests(unittest.TestCase):
    def invoke(self, results, remaining=60000):
        db = MagicMock()
        db.cursor.return_value = MagicMock(spec=worker.pg8000.dbapi.Cursor)
        db.cursor.return_value.fetchone.return_value = (0, 0)
        context = MagicMock()
        context.get_remaining_time_in_millis.return_value = remaining
        with patch.object(worker, 'connect', return_value=db), \
             patch.object(worker, 'process_one', side_effect=results) as process:
            result = lambda_function.handler({}, context)
        return result, process, db

    def test_empty_queue_stops_immediately(self):
        result, process, db = self.invoke(['idle'])
        self.assertEqual(result['checked'], 0)
        self.assertEqual(process.call_count, 1)
        db.close.assert_called_once()

    def test_processes_all_results_until_empty(self):
        result, process, db = self.invoke(['checked', 'error', 'checked', 'idle'])
        self.assertEqual((result['checked'], result['error']), (2, 1))

    def test_leaves_time_for_transaction_cleanup(self):
        result, process, db = self.invoke([], remaining=34000)
        process.assert_not_called()

    def test_dependency_errors_are_sanitized_and_clear_secrets(self):
        worker.SECRET_CACHE['test'] = (1, {})
        with patch.object(worker, 'connect', side_effect=Exception('private input')):
            with self.assertRaisesRegex(RuntimeError, '^CRN dependencies unavailable; pending requests retained$'):
                lambda_function.handler({}, MagicMock())
        self.assertFalse(worker.SECRET_CACHE)

    def test_secrets_are_cached_and_expire(self):
        client = MagicMock()
        client.get_secret_value.return_value = {'SecretString': json.dumps({'host': 'test'})}
        worker.SECRET_CACHE.clear()
        with patch.dict('os.environ', {'FION_SECRET_ARN': 'test-secret'}), \
             patch('boto3.client', return_value=client), \
             patch('time.monotonic', side_effect=[0, 299, 301, 301]):
            for _ in range(3): self.assertEqual(worker.database_secret('fion'), {'host': 'test'})
        self.assertEqual(client.get_secret_value.call_count, 2)
        worker.SECRET_CACHE.clear()


if __name__ == '__main__': unittest.main()
