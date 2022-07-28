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
    'gcpInfraProjectId': 'ccm-play',
    'gcpInfraServiceAccountEmail': 'harness-ce-harness-kmpys@ccm-play.iam.gserviceaccount.com',
    'connectorId': 'abhijeettestconnector1'
}]
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
INVENTORY_TYPE = ["disk", "instance"]
sc_client = scheduler.CloudSchedulerClient()
publisher = pubsub_v1.PublisherClient()
regions = ["us-central1", "us-east1"]
parent = None


def main(event, context):
    """
    Triggered from a message on a Cloud Pub/Sub topic.
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


def manage_scheduler_jobs(event_json):
    global parent
    for event in event_json:
        for inventory_type in INVENTORY_TYPE:
            for region in regions:
                possible_parent = f"projects/{PROJECTID}/locations/{region}"
                try:
                    request = scheduler.GetJobRequest(
                        name=f"{possible_parent}/jobs/ce-gcp-%s-data-%s" % (inventory_type, event["accountId"])
                    )
                    response = sc_client.get_job(request=request)
                    parent = possible_parent
                    print(f"Cloud Scheduler ce-gcp-%s-data-%s already exists in {region}. Using it." % (inventory_type, event["accountId"]))
                    break
                except Exception as e:
                    print(e)
                    # job was not found in this region
            print(f"parent: {parent}")
            if not parent:
                # job was not found in any region, update region to any region whose quota is not exhausted: us-east1
                print(f"Cloud Scheduler ce-gcp-%s-data-%s not found in any region. Using us-east1 region for this scheduler." % (inventory_type, event["accountId"]))
                parent = f"projects/{PROJECTID}/locations/us-east1"

            manage_inventory_data_scheduler_job(event, inventory_type)
            manage_inventory_data_load_scheduler_job(event, inventory_type)
            parent = None


def manage_inventory_data_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-gcp-%s-data-%s" % (inventory_type, event["accountId"])
    topic_path = publisher.topic_path(PROJECTID, f"ce-gcp-{inventory_type}-inventory-data-scheduler")

    schedule = "10 * * * *"  # Run every hour
    jsonData = {
        "accountId": event["accountId"],
        "projectId": event["gcpInfraProjectId"],
        "serviceAccount": event["gcpInfraServiceAccountEmail"]
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


def manage_inventory_data_load_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-gcp-%s-data-load-%s" % (inventory_type, event["accountId"])
    topic_path = publisher.topic_path(PROJECTID, f"ce-gcp-{inventory_type}-inventory-data-load-scheduler")

    schedule = "25 * * * *"  # Run every hour
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
        print("Deleting %s" % name)
        delete_job(name)
