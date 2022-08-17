# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import json
import os
from google.cloud import scheduler, pubsub_v1, functions_v2

"""
[{
    "action": "create",
    "accountId": "kmpySmUISimoRrJL6NL73w",
    "azureInfraTenantId": "b229b2bb-5f33-4d22-bce0-730f6474e906",
    "azureInfraSubscriptionId": "12d2db62-5aa9-471d-84bb-faa489b3e319",
    "connectorId": "abhijeettestconnector1"
}]
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
INVENTORY_TYPE = ["vm"]
sc_client = scheduler.CloudSchedulerClient()
publisher = pubsub_v1.PublisherClient()
parent = f"projects/{PROJECTID}/locations/us-east1"


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


def get_http_target_payload_of_job(job_name):
    try:
        request = scheduler.GetJobRequest(name=job_name)
        response = sc_client.get_job(request=request)
        return json.loads(response.http_target.body.decode('utf-8'))["subscriptionsList"]
    except Exception as e:
        print(e)
        return []


def get_updated_subscriptions_list(event, subscriptions_list):
    if event["action"] in ["create", "update"]:
        subscriptions_list.append(event["azureInfraSubscriptionId"])
    elif event["action"] == "delete":
        subscriptions_list = [subscriptionId for subscriptionId in subscriptions_list
                              if subscriptionId != event["azureInfraSubscriptionId"]]
    return list(set(subscriptions_list))


def get_cf_v2_uri(cf_name):
    client = functions_v2.FunctionServiceClient()
    request = functions_v2.GetFunctionRequest(
        name=cf_name
    )

    response = client.get_function(request=request)
    return response.service_config.uri


def manage_scheduler_jobs(event_json):
    for event in event_json:
        for inventory_type in INVENTORY_TYPE:
            manage_inventory_data_scheduler_job(event, inventory_type)
            manage_inventory_data_load_scheduler_job(event, inventory_type)
            manage_inventory_metric_data_scheduler_job(event, inventory_type)


def manage_inventory_metric_data_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-azure-%s-metric-data-%s" % (inventory_type, event["accountId"])
    topic_path = publisher.topic_path(PROJECTID, f"ce-azure-{inventory_type}-inventory-metric-data-scheduler")

    schedule = "0 10 * * *"  # Run at 10 UTC daily

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


def manage_inventory_data_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-azure-%s-data-%s-%s" % (inventory_type, event["accountId"], event["azureInfraTenantId"])
    schedule = "0 * * * *"  # Run every hour

    print("Previous Subscriptions List: ", get_http_target_payload_of_job(name))
    subscriptions_list = get_updated_subscriptions_list(event, get_http_target_payload_of_job(name))
    print("Updated Subscriptions List: ", subscriptions_list)

    jsonData = {
        "accountId": event["accountId"],
        "tenantId": event["azureInfraTenantId"],
        "subscriptionsList": subscriptions_list
    }
    job = {
        'name': name,
        'http_target': {
            'uri': get_cf_v2_uri(f"projects/{PROJECTID}/locations/us-central1/functions/ce-azure-vm-inventory-data-terraform"),
            "http_method": "POST",
            'body': bytes(json.dumps(jsonData), 'utf-8'),
            "oidc_token": {
                "service_account_email": f"{PROJECTID}@appspot.gserviceaccount.com"
            }
        },
        'schedule': schedule,
        'time_zone': "UTC"
    }

    if event["action"] in ["create", "update", "delete"] and subscriptions_list:
        print("Creating %s" % name)
        upsert_job(job)
    elif event["action"] == "delete" and not subscriptions_list:
        print("Deleting %s" % name)
        delete_job(name)


def manage_inventory_data_load_scheduler_job(event, inventory_type):
    name = f"{parent}/jobs/ce-azure-%s-data-load-%s" % (inventory_type, event["accountId"])
    topic_path = publisher.topic_path(PROJECTID, f"ce-azure-{inventory_type}-inventory-data-load-scheduler")

    schedule = "45 * * * *"  # Run every hour
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
