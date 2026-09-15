"""Scoped nonprod CRN Lambda deployment. Never deploys web apps, ASG or Judge.

prepare: IAM, private networking, logs; deploy: package + disabled schedule;
enable: start one-minute schedule; retire-worker: stop ECS and revoke its secrets.
"""
import argparse
import io
import json
import time
import zipfile
from pathlib import Path
import boto3
from botocore.exceptions import ClientError

ACCOUNT = '720544133575'
REGION = 'us-east-1'
NAME = 'challenge-platform-crn-nonprod'
WORKER = 'challenge-platform-crn-worker-nonprod'
VPC = 'vpc-006989005fd6bcd30'
SUBNETS = ['subnet-0bb52d204fdb76a48', 'subnet-0847000ec1ebcb722']
DATABASE_SGS = ['sg-090f3ed99c601c925', 'sg-0356cb8695872490f']
FUNCTION_ARN = f'arn:aws:lambda:{REGION}:{ACCOUNT}:function:{NAME}'
ROLE = NAME + '-execution'
ROLE_ARN = f'arn:aws:iam::{ACCOUNT}:role/{ROLE}'
GROUP = '/aws/lambda/' + NAME
TAGS = {'Project': 'challenge-platform', 'Environment': 'nonprod', 'Feature': 'crn'}
aws = boto3.Session(region_name=REGION)


def identity():
    caller = aws.client('sts').get_caller_identity()
    assert caller['Account'] == ACCOUNT, 'Wrong AWS account'
    return caller


def secret_arns():
    sm = aws.client('secretsmanager')
    return {kind: sm.describe_secret(SecretId='challenge-platform/nonprod/crn/' + kind)['ARN']
            for kind in ['fion', 'challenge']}


def security_group(ec2, suffix):
    name = NAME + suffix
    groups = ec2.describe_security_groups(Filters=[{'Name': 'vpc-id', 'Values': [VPC]},
        {'Name': 'group-name', 'Values': [name]}])['SecurityGroups']
    if groups:
        assert dict((t['Key'], t['Value']) for t in groups[0].get('Tags', []))['Feature'] == 'crn'
        return groups[0]['GroupId']
    group = ec2.create_security_group(GroupName=name, Description='Private CRN Lambda ' + suffix,
        VpcId=VPC, TagSpecifications=[{'ResourceType': 'security-group',
        'Tags': [{'Key': k, 'Value': v} for k, v in TAGS.items()]}])['GroupId']
    ec2.revoke_security_group_egress(GroupId=group,
        IpPermissions=[{'IpProtocol': '-1', 'IpRanges': [{'CidrIp': '0.0.0.0/0'}]}])
    return group


def allow(ec2, group, other, port, incoming=False):
    try:
        method = ec2.authorize_security_group_ingress if incoming else ec2.authorize_security_group_egress
        method(GroupId=group, IpPermissions=[{'IpProtocol': 'tcp', 'FromPort': port, 'ToPort': port,
            'UserIdGroupPairs': [{'GroupId': other, 'Description': 'CRN private matching'}]}])
    except ClientError as error:
        if error.response['Error']['Code'] != 'InvalidPermission.Duplicate': raise


def prepare():
    identity()
    arns = secret_arns()
    iam = aws.client('iam'); ec2 = aws.client('ec2'); logs = aws.client('logs')
    trust = {'Version': '2012-10-17', 'Statement': [{'Effect': 'Allow',
        'Principal': {'Service': 'lambda.amazonaws.com'}, 'Action': 'sts:AssumeRole'}]}
    try: iam.get_role(RoleName=ROLE)
    except iam.exceptions.NoSuchEntityException:
        iam.create_role(RoleName=ROLE, AssumeRolePolicyDocument=json.dumps(trust),
            Tags=[{'Key': k, 'Value': v} for k, v in TAGS.items()])
    eni_actions = ['ec2:CreateNetworkInterface', 'ec2:DescribeNetworkInterfaces',
        'ec2:DescribeSubnets', 'ec2:DeleteNetworkInterface',
        'ec2:AssignPrivateIpAddresses', 'ec2:UnassignPrivateIpAddresses']
    policy = {'Version': '2012-10-17', 'Statement': [
        {'Effect': 'Allow', 'Action': ['logs:CreateLogStream', 'logs:PutLogEvents'],
         'Resource': f'arn:aws:logs:{REGION}:{ACCOUNT}:log-group:{GROUP}:*'},
        {'Effect': 'Allow', 'Action': 'secretsmanager:GetSecretValue', 'Resource': list(arns.values()),
         'Condition': {'ArnEquals': {'lambda:SourceFunctionArn': FUNCTION_ARN}}},
        {'Effect': 'Allow', 'Action': eni_actions, 'Resource': '*'},
        # Lambda control plane needs ENIs; the function's own code must not create them.
        {'Effect': 'Deny', 'Action': eni_actions, 'Resource': '*',
         'Condition': {'ArnEquals': {'lambda:SourceFunctionArn': FUNCTION_ARN}}}
    ]}
    iam.put_role_policy(RoleName=ROLE, PolicyName='crn-lambda-scoped', PolicyDocument=json.dumps(policy))
    worker_sg = security_group(ec2, '-lambda-sg')
    endpoint_sg = security_group(ec2, '-secrets-sg')
    for group in DATABASE_SGS:
        allow(ec2, worker_sg, group, 5432)
        allow(ec2, group, worker_sg, 5432, incoming=True)
    allow(ec2, worker_sg, endpoint_sg, 443)
    allow(ec2, endpoint_sg, worker_sg, 443, incoming=True)
    endpoints = ec2.describe_vpc_endpoints(Filters=[{'Name': 'vpc-id', 'Values': [VPC]},
        {'Name': 'service-name', 'Values': [f'com.amazonaws.{REGION}.secretsmanager']}])['VpcEndpoints']
    endpoint_policy = {'Version': '2012-10-17', 'Statement': [{'Effect': 'Allow',
        'Principal': '*', 'Action': 'secretsmanager:GetSecretValue', 'Resource': list(arns.values()),
        'Condition': {'ArnEquals': {'aws:PrincipalArn': ROLE_ARN}}}]}
    if endpoints:
        assert len(endpoints) == 1 and dict((t['Key'], t['Value']) for t in endpoints[0].get('Tags', [])).get('Feature') == 'crn', 'Do not modify an unrelated endpoint'
        endpoint = endpoints[0]['VpcEndpointId']
    else:
        # One endpoint AZ is a deliberate nonprod cost/availability tradeoff.
        endpoint = ec2.create_vpc_endpoint(VpcEndpointType='Interface', VpcId=VPC,
            ServiceName=f'com.amazonaws.{REGION}.secretsmanager', SubnetIds=SUBNETS[:1],
            SecurityGroupIds=[endpoint_sg], PrivateDnsEnabled=False,
            PolicyDocument=json.dumps(endpoint_policy), TagSpecifications=[{'ResourceType': 'vpc-endpoint',
                'Tags': [{'Key': k, 'Value': v} for k, v in {**TAGS, 'Name': NAME + '-secrets'}.items()]}])['VpcEndpoint']['VpcEndpointId']
    try: logs.create_log_group(logGroupName=GROUP, tags=TAGS)
    except logs.exceptions.ResourceAlreadyExistsException: pass
    logs.put_retention_policy(logGroupName=GROUP, retentionInDays=14)
    return arns, worker_sg, endpoint


def package(dependencies, ca):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
        for path in Path(dependencies).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts and path.suffix != '.pyc':
                out.write(path, path.relative_to(dependencies).as_posix())
        for name in ['lambda_function.py', 'worker.py']:
            out.write(Path(__file__).with_name(name), name)
        out.write(ca, 'global-bundle.pem')
    return buffer.getvalue()


def deploy(dependencies, ca):
    arns, group, endpoint = prepare()
    client = aws.client('lambda')
    endpoint_info = aws.client('ec2').describe_vpc_endpoints(VpcEndpointIds=[endpoint])['VpcEndpoints'][0]
    endpoint_url = 'https://' + next(entry['DnsName'] for entry in endpoint_info['DnsEntries']
        if '.secretsmanager.' in entry['DnsName'] and '-us-east-1a.' not in entry['DnsName'])
    config = dict(FunctionName=NAME, Runtime='python3.13', Role=ROLE_ARN,
        Handler='lambda_function.handler', Timeout=60, MemorySize=256,
        Description='Private CRN matching from durable Challenge requests; Fion read-only',
        Environment={'Variables': {**{kind.upper() + '_SECRET_ARN': arn for kind, arn in arns.items()},
            'SECRETS_ENDPOINT_URL': endpoint_url}},
        VpcConfig={'SubnetIds': SUBNETS, 'SecurityGroupIds': [group], 'Ipv6AllowedForDualStack': False})
    code = package(dependencies, ca)
    try: client.get_function(FunctionName=NAME)
    except client.exceptions.ResourceNotFoundException:
        for attempt in range(6):
            try:
                client.create_function(**config, Code={'ZipFile': code}, Architectures=['arm64'], Tags=TAGS)
                break
            except client.exceptions.InvalidParameterValueException as error:
                if 'cannot be assumed' not in str(error) or attempt == 5: raise
                time.sleep(5)
    else:
        client.update_function_code(FunctionName=NAME, ZipFile=code, Architectures=['arm64'])
        client.get_waiter('function_updated_v2').wait(FunctionName=NAME)
        client.update_function_configuration(**config)
    client.put_function_concurrency(FunctionName=NAME, ReservedConcurrentExecutions=1)
    client.put_function_event_invoke_config(FunctionName=NAME, MaximumRetryAttempts=0, MaximumEventAgeInSeconds=120)
    events = aws.client('events')
    rule = events.put_rule(Name=NAME + '-minute', ScheduleExpression='rate(1 minute)',
        State='DISABLED', Description='Drain committed CRN requests once per minute',
        Tags=[{'Key': k, 'Value': v} for k, v in TAGS.items()])['RuleArn']
    try:
        client.add_permission(FunctionName=NAME, StatementId='crn-minute-schedule',
            Action='lambda:InvokeFunction', Principal='events.amazonaws.com', SourceArn=rule, SourceAccount=ACCOUNT)
    except client.exceptions.ResourceConflictException: pass
    result = events.put_targets(Rule=NAME + '-minute', Targets=[{'Id': 'crn', 'Arn': FUNCTION_ARN,
        'Input': '{"source":"crn-minute-schedule"}',
        'RetryPolicy': {'MaximumRetryAttempts': 2, 'MaximumEventAgeInSeconds': 120}}])
    assert result['FailedEntryCount'] == 0
    logs = aws.client('logs')
    logs.put_metric_filter(logGroupName=GROUP, filterName='crn-needs-attention',
        filterPattern='{ ($.event = "crn_lambda_batch") && (($.error > 0) || ($.exhausted > 0) || ($.overdue > 0)) }',
        metricTransformations=[{'metricName': 'NeedsAttention', 'metricNamespace': 'ChallengePlatform/CRN',
            'metricValue': '1', 'defaultValue': 0}])
    cloudwatch = aws.client('cloudwatch')
    for suffix, namespace, metric, dimensions in [
        ('errors', 'AWS/Lambda', 'Errors', [{'Name': 'FunctionName', 'Value': NAME}]),
        ('matching', 'ChallengePlatform/CRN', 'NeedsAttention', [])]:
        cloudwatch.put_metric_alarm(AlarmName=NAME + '-' + suffix, Namespace=namespace, MetricName=metric,
            Dimensions=dimensions, Statistic='Sum', Period=60, EvaluationPeriods=1,
            Threshold=1, ComparisonOperator='GreaterThanOrEqualToThreshold', TreatMissingData='notBreaching',
            AlarmDescription='CRN requests retained; inspect aggregate logs and pending/error rows',
            Tags=[{'Key': k, 'Value': v} for k, v in TAGS.items()])
    print(json.dumps({'function': NAME, 'endpoint': endpoint, 'schedule': 'DISABLED'}), flush=True)


def retire_worker():
    caller = identity()
    assert caller['Arn'].startswith(f'arn:aws:iam::{ACCOUNT}:user/'), 'Use an explicit deployment operator'
    ecs = aws.client('ecs')
    ecs.update_service(cluster='codereport-nonprod', service=WORKER, desiredCount=0)
    # Prevent ECS task/host credentials from reading the two dedicated secrets,
    # even if a broader unrelated IAM policy is attached elsewhere.
    sm = aws.client('secretsmanager')
    for arn in secret_arns().values():
        existing = sm.get_resource_policy(SecretId=arn).get('ResourcePolicy')
        policy = json.loads(existing) if existing else {'Version': '2012-10-17', 'Statement': []}
        policy['Statement'] = [s for s in policy['Statement'] if s.get('Sid') != 'CrnIsolatedRuntimeOnly']
        policy['Statement'].append({'Sid': 'CrnIsolatedRuntimeOnly', 'Effect': 'Deny', 'Principal': '*',
            'Action': 'secretsmanager:GetSecretValue', 'Resource': '*',
            'Condition': {'ArnNotEquals': {'aws:PrincipalArn': [ROLE_ARN, caller['Arn'], f'arn:aws:iam::{ACCOUNT}:root']}}})
        sm.put_resource_policy(SecretId=arn, ResourcePolicy=json.dumps(policy), BlockPublicPolicy=True)
    iam = aws.client('iam')
    try: iam.delete_role_policy(RoleName=WORKER + '-execution', PolicyName='crn-worker-scoped')
    except iam.exceptions.NoSuchEntityException: pass
    print('ECS worker desired count zero; dedicated secrets restricted to Lambda and deployment operator. Rotate old credentials next.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'deploy', 'enable', 'retire-worker'])
    parser.add_argument('--dependencies'); parser.add_argument('--ca')
    args = parser.parse_args()
    identity()
    if args.action == 'prepare':
        arns, group, endpoint = prepare()
        print(json.dumps({'securityGroup': group, 'endpoint': endpoint}))
    elif args.action == 'deploy': deploy(args.dependencies, args.ca)
    elif args.action == 'enable':
        assert aws.client('lambda').get_function_configuration(FunctionName=NAME)['State'] == 'Active'
        aws.client('events').enable_rule(Name=NAME + '-minute')
        print('One-minute CRN Lambda schedule enabled.')
    else: retire_worker()
