"""Private, scheduled CRN matcher. Invocation payloads never contain candidate PII."""
import json
from contextlib import closing
import worker


def handler(event, context):
    counts = {'checked': 0, 'error': 0}
    try:
        with closing(worker.connect('challenge')) as target:
            # Leave time for two connects, a source lookup and transaction cleanup.
            # Bounded work and reserved concurrency protect both databases.
            for _ in range(100):
                if context.get_remaining_time_in_millis() < 35000:
                    break
                result = worker.process_one(target)
                if result == 'idle':
                    break
                counts[result] += 1
            with closing(target.cursor()) as cursor:
                cursor.execute("""SELECT count(*) FILTER (WHERE attempts>=5),
                  count(*) FILTER (WHERE requested_at<clock_timestamp()-interval '10 minutes')
                  FROM challenge_platform.candidate_crn_match WHERE status<>'CHECKED'""")
                exhausted, overdue = cursor.fetchone()
            target.rollback()
        report = {'event': 'crn_lambda_batch', **counts, 'exhausted': exhausted, 'overdue': overdue}
        print(json.dumps(report))
        return report
    except Exception as error:
        worker.SECRET_CACHE.clear()
        # Never expose DB errors, SQL parameters, event data or credentials in logs.
        diagnostic = {'event': 'crn_lambda_unavailable', 'failureClass': type(error).__name__}
        if hasattr(error, 'response') and isinstance(error.response, dict):
            diagnostic['awsCode'] = error.response.get('Error', {}).get('Code', 'Unknown')
        print(json.dumps(diagnostic))
        raise RuntimeError('CRN dependencies unavailable; pending requests retained') from None
