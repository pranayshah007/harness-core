# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import json
import os
from google.cloud import scheduler
from google.cloud import pubsub_v1

"""
[{
    'action': 'create',
    'accountId': 'kmpySmUISimoRrJL6NL73w',
    'awsInfraAccountId': '132359207506',
    'awsCrossAccountExternalId': 'harness:1.08817434118E11:N5ZpBzxvRI2ayBYuaXjx8w',
    'awsCrossAccountRoleArn': 'arn:aws:iam::132359207506:role/HarnessCERole-h5j602yzhpk3',
    'connectorId': 'nikunjtestconnector1'
}]
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
INVENTORY_TYPE = ["ebs", "ec2", "rds"]
sc_client = scheduler.CloudSchedulerClient()
publisher = pubsub_v1.PublisherClient()
parent = f"projects/{PROJECTID}/locations/us-central1"


def main(event, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    pubsub_message = base64.b64decode(event['data']).decode('utf-8')
    print(pubsub_message)
    event_json = json.loads(pubsub_message)
    print(event_json)
    manage_scheduler_jobs(event_json)
    print("Completed")


def manage_scheduler_jobs(event_json):
    for event in event_json:
        for inventory_type in INVENTORY_TYPE:
            manage_inventory_scheduler_job(event, inventory_type)
            manage_inventory_load_scheduler_job(event, inventory_type)
            manage_inventory_metric_scheduler_job(event, inventory_type)


def manage_inventory_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-aws-%s-%s-%s" % (inventory_type, event["accountId"], event["connectorId"])
    schedule = "0 * * * *"  # Run every hour
    jsonData = {
        "accountId": event["accountId"],
        "roleArn": event["awsCrossAccountRoleArn"],
        "externalId": event["awsCrossAccountExternalId"]
    }
    topic_path = publisher.topic_path(PROJECTID, f"ce-awsdata-{inventory_type}-inventory-scheduler")
    job = {
        'name': name,
        'pubsub_target': {
            'topic_name': topic_path,
            'data': bytes(json.dumps(jsonData), 'utf-8')
        },
        'schedule': schedule,
        'time_zone': "UTC"
    }
    print(job)
    if event["action"] in ["create", "update"]:
        print("Creating %s" % name)
        upsert_job(job)
    elif event["action"] == "delete":
        print("Deleting %s" % name)
        delete_job(name)


def upsert_job(job):
    try:
        sc_client.update_job(job=job)
        print("Job updated.")
    except Exception as e:
        print(e)
        response = sc_client.create_job(
            request={
                "parent": parent,
                "job": job
            }
        )
        print('Job created: {}'.format(response.name))


def delete_job(name):
    try:
        sc_client.delete_job(name=name)
        print("Job deleted.")
    except Exception as e:
        print(e)



def manage_inventory_load_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-aws-%s-load-%s" % (inventory_type, event["accountId"])
    topic_path = publisher.topic_path(PROJECTID, f"ce-awsdata-{inventory_type}-inventory-load-scheduler")

    schedule = "15 * * * *"  # Run every hour
    jsonData = {
        "accountId": event["accountId"]
    }
    job = {
        'name': name,
        'pubsub_target': {
            'topic_name': topic_path,
            'data': bytes(json.dumps(jsonData), 'utf-8')
        },
        'schedule': schedule,
        'time_zone': "UTC"
    }

    if event["action"] in ["create", "update"]:
        print("Creating %s" % name)
        upsert_job(job)
    elif event["action"] == "delete":
        print("Skipping deleting %s as other connectors might still be using it" % name)
        #delete_job(name)


def manage_inventory_metric_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-aws-%s-metric-%s-%s" % (inventory_type, event["accountId"], event["connectorId"])
    schedule = "0 10 * * *"  # Run at 10 UTC daily
    if inventory_type == "ebs":
        topic_path = publisher.topic_path(PROJECTID, f"ce-awsdata-{inventory_type}-metrics-inventory-scheduler")
    elif inventory_type == "ec2":
        topic_path = publisher.topic_path(PROJECTID, f"ce-awsdata-{inventory_type}-metric-inventory-scheduler")
    else:
        return

    jsonData = {
        "accountId": event["accountId"],
        "roleArn": event["awsCrossAccountRoleArn"],
        "externalId": event["awsCrossAccountExternalId"]
    }
    job = {
        'name': name,
        'pubsub_target': {
            'topic_name': topic_path,
            'data': bytes(json.dumps(jsonData), 'utf-8')
        },
        'schedule': schedule,
        'time_zone': "UTC"
    }

    if event["action"] in ["create", "update"]:
        print("Creating %s" % name)
        upsert_job(job)
    elif event["action"] == "delete":
        print("Deleting %s" % name)
        delete_job(name)
