"""Durable CRN worker. Fion is read-only; only boolean existence flags leave it."""
import json
import os
import re
import ssl
import time
from contextlib import closing
from pathlib import Path

import pg8000.dbapi

TARGETS = {
    'fion': ('codereport-db-nonprod.cwrqsqgat0sw.us-east-1.rds.amazonaws.com', 'codereport_crn_dev', 'challenge_crn_reader'),
    'challenge': ('challenge-platform-db-nonprod.cwrqsqgat0sw.us-east-1.rds.amazonaws.com', 'challenge_platform', 'challenge_crn_writer'),
}
SECRET_CACHE = {}


def database_secret(kind):
    """Lambda reads its own secrets privately; cache no longer than five minutes."""
    arn = os.environ[kind.upper() + '_SECRET_ARN']
    cached = SECRET_CACHE.get(arn)
    if cached and time.monotonic() < cached[0]:
        return cached[1]
    import boto3
    from botocore.config import Config
    client = boto3.client('secretsmanager', endpoint_url=os.environ.get('SECRETS_ENDPOINT_URL'),
        config=Config(connect_timeout=3, read_timeout=3,
        retries={'max_attempts': 1, 'mode': 'standard'}))
    value = json.loads(client.get_secret_value(SecretId=arn)['SecretString'])
    SECRET_CACHE[arn] = (time.monotonic() + 300, value)
    return value


def normalize_email(value):
    return (value or '').strip().lower()


def normalize_phone(value):
    # Ignore presentation characters, never guess country codes or compare suffixes.
    digits = re.sub(r'[^0-9]', '', value or '')
    return digits if 7 <= len(digits) <= 15 else ''


def connect(kind):
    secret = database_secret(kind)
    if (secret['host'], secret['dbname'], secret['username']) != TARGETS[kind]:
        raise RuntimeError('Unexpected CRN database configuration')
    return pg8000.dbapi.connect(host=secret['host'], port=5432, database=secret['dbname'],
        user=secret['username'], password=secret['password'], timeout=10,
        ssl_context=ssl.create_default_context(cafile=str(Path(__file__).with_name('global-bundle.pem'))))


def lookup(email, phone):
    email, phone = normalize_email(email), normalize_phone(phone)
    if not email and not phone:
        return False, False
    with closing(connect('fion')) as source:
        with closing(source.cursor()) as cursor:
            cursor.execute('SET TRANSACTION READ ONLY')
            cursor.execute("SET LOCAL statement_timeout='8000ms'")
            cursor.execute("""SELECT
              EXISTS(SELECT 1 FROM codereport.mst_user_profile WHERE %s<>'' AND lower(btrim(email))=%s),
              EXISTS(SELECT 1 FROM codereport.mst_user_profile WHERE %s<>'' AND regexp_replace(phone_number,'[^0-9]','','g')=%s)
            """, (email, email, phone, phone))
            return tuple(cursor.fetchone())


def process_one(target, match=lookup):
    """Lock one committed request. Concurrent workers skip it; crashes release the lock."""
    with closing(target.cursor()) as cursor:
        cursor.execute("SET LOCAL statement_timeout='10000ms'")
        cursor.execute("""SELECT m.candidate_id,m.version,c.email_raw,c.phone_raw FROM challenge_platform.candidate_crn_match m
          JOIN challenge_platform.candidate c ON c.id=m.candidate_id
          WHERE m.status<>'CHECKED' AND m.attempts<5 AND m.next_attempt_at<=clock_timestamp()
          ORDER BY m.next_attempt_at,m.candidate_id LIMIT 1 FOR UPDATE OF m SKIP LOCKED""")
        row = cursor.fetchone()
        if row is None:
            target.rollback()
            return 'idle'
        candidate_id, version, email, phone = row
        try:
            email_match, phone_match = match(email, phone)
        except Exception:
            cursor.execute("""UPDATE challenge_platform.candidate_crn_match SET status='ERROR',attempts=attempts+1,
              next_attempt_at=clock_timestamp()+least(3600,power(2,attempts)*60)*interval '1 second'
              WHERE candidate_id=%s AND version=%s""", (candidate_id, version))
            target.commit()
            return 'error'
        cursor.execute("""UPDATE challenge_platform.candidate_crn_match SET status='CHECKED',email_match=%s,
          phone_match=%s,checked_at=clock_timestamp(),attempts=attempts+1 WHERE candidate_id=%s AND version=%s""",
          (email_match, phone_match, candidate_id, version))
        target.commit()
        return 'checked'
