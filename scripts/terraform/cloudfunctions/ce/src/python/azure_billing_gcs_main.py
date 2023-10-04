# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os
import base64
import json
from google.cloud import bigquery
from google.cloud import scheduler
from google.cloud import pubsub_v1
from google.cloud import storage
from util import http_trigger_cf_v2_async
import datetime

"""
GCS upload event
{
	'bucket': 'azurecustomerbillingdata-qa',
	'contentType': 'text/csv',
	'crc32c': 'txz7fA==',
	'etag': 'CKro0cDewO4CEAE=',
	'generation': '1611909414810666',
	'id': 'azurecustomerbillingdata-qa/kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv/1611909414810666',
	'kind': 'storage#object',
	'md5Hash': '/wftWBBbhax7CKIlPjL/DA==',
	'mediaLink': 'https://www.googleapis.com/download/storage/v1/b/azurecustomerbillingdata-qa/o/kmpySmUISimoRrJL6NL73w%2FJUKVZIGKQzCVKXYbDhmM_g%2F20210101-20210131%2Fcereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv?generation=1611909414810666&alt=media',
	'metageneration': '1',
	'name': 'kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv',
	'selfLink': 'https://www.googleapis.com/storage/v1/b/azurecustomerbillingdata-qa/o/kmpySmUISimoRrJL6NL73w%2FJUKVZIGKQzCVKXYbDhmM_g%2F20210101-20210131%2Fcereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv',
	'size': '3085577',
	'storageClass': 'STANDARD',
	'timeCreated': '2021-01-29T08:36:54.878Z',
	'timeStorageClassUpdated': '2021-01-29T08:36:54.878Z',
	'updated': '2021-01-29T08:36:54.878Z'
}

DataTransfer Job Event
{
    "name": "transferOperations/transferJobs-6173126636227806220-1621337078812975",
    "projectId": "ccm-play",
    "transferSpec": {
        "gcsDataSink": {
            "bucketName": "2nikunjtestbucket"
        },
        "objectConditions": {
            "maxTimeElapsedSinceLastModification": "3600s"
        },
        "transferOptions": {
            "overwriteObjectsAlreadyExistingInSink": true
        },
        "azureBlobStorageDataSource": {
            "storageAccount": "cecustomerbillingdatadev",
            "container": "billingdatacontainer"
        }
    },
    "startTime": "2021-05-18T11:34:36.145409750Z",
    "endTime": "2021-05-18T11:34:46.546806279Z",
    "status": "SUCCESS",
    "counters": {},
    "transferJobName": "transferJobs/6173126636227806220",
    "notificationConfig": {
        "pubsubTopic": "projects/ccm-play/topics/nikunjtesttopic",
        "eventTypes": ["TRANSFER_OPERATION_SUCCESS", "TRANSFER_OPERATION_FAILED", "TRANSFER_OPERATION_ABORTED"],
        "payloadFormat": "JSON"
    }
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
publisher = pubsub_v1.PublisherClient()
client_bq = bigquery.Client(PROJECTID)
client_gcs = storage.Client(PROJECTID)
AZURECFTOPIC = os.environ.get('AZURECFTOPIC', f'projects/{PROJECTID}/topics/nikunjtesttopic')
TOPIC_PATH = publisher.topic_path(PROJECTID, AZURECFTOPIC)
AZUREBILLINGBQCFNAME = f"projects/{PROJECTID}/locations/us-central1/functions/ce-azure-billing-bq-terraform"

def main(event, context):
    """Triggered when gcs data transfer job completes for Azure
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(f"Processing event json:\n {jsonData}")
    if event.get("attributes", {}).get("eventType", "") != "TRANSFER_OPERATION_SUCCESS":
        print("Failed transfer operation. Returning")
        return
    jsonData["bucket"] = jsonData.get("transferSpec", {}).get("gcsDataSink", {}).get("bucketName")

    unique_cur_paths = get_csv_paths(jsonData)
    if len(unique_cur_paths) > 0:
        trigger_bq_cf_for_processing(unique_cur_paths, jsonData)
    else:
        print("No events to send")
    #create_scheduler_job(jsonData) #deprecated


def get_csv_paths(jsonData):
    blobs = client_gcs.list_blobs(jsonData["bucket"])
    unique_cur_paths = set()
    for blob in blobs:
        # Should be a valid CSV file. Add all exclusions here
        if blob.name.endswith("DefaultRule-AllBlobs.csv"):
            continue
        if blob.name.endswith("_manifest.json"):
            # This is partitioned CSV folder
            path = blob.name.split("/")
            # Verify path length
            # 0Z0vv0uwRoax_oZ62jBFfg/tKFNTih2SPyIdWt_yJMZvg/8445d4f3-c4d8-4c5e-a6f1-743cee2c9e84/HarnessExport/20220501-20220531/202205182219/0685ce72-f617-4593-b95c-d00d9bc9b207/000001.csv
            if len(path) not in [8]:
                continue
            # verify third last should be month folder
            try:
                report_month = path[-4].split("-")
                startstr = report_month[0]
                endstr = report_month[1]
                if (len(startstr) != 8) or (len(endstr) != 8):
                    raise
                if not int(endstr) > int(startstr):
                    raise
                # Check for valid dates
                datetime.datetime.strptime(startstr, '%Y%m%d')
                datetime.datetime.strptime(endstr, '%Y%m%d')
            except Exception as e:
                # Any error we should not take this path for processing
                print(e)
                continue
            unique_cur_paths.add('/'.join(path[:-3]))
        if blob.name.endswith(".csv") or blob.name.endswith(".csv.gz"):
            path = blob.name.split("/")
            # We will have multiple connectors per account in Harness in NG.
            # verifications.  azure path format is vZYBQdFRSlesqo3CMB90Ag/myqO-niJS46aVm3b646SKA/cereportnikunj/20210201-20210228/cereportnikunj_f1a6618a-d21e-4b74-a35b-55ef47ea0e68.csv
            if len(path) not in [4, 5, 6]:
                continue
            # verify second last should be month folder
            try:
                report_month = path[-2].split("-")
                startstr = report_month[0]
                endstr = report_month[1]
                if (len(startstr) != 8) or (len(endstr) != 8):
                    raise
                if not int(endstr) > int(startstr):
                    raise
                # Check for valid dates
                datetime.datetime.strptime(startstr, '%Y%m%d')
                datetime.datetime.strptime(endstr, '%Y%m%d')
            except Exception as e:
                # Any error we should not take this path for processing
                print(e)
                continue
            unique_cur_paths.add('/'.join(path[:-1]))

    print("Found unique folders with CSVs: %s" % unique_cur_paths)
    return list(unique_cur_paths)

def trigger_bq_cf_for_processing(unique_cur_paths, jsonData):
    active_months_in_gcs_bucket = {}
    for path in unique_cur_paths:
        ps = path.split("/")
        accountIdInPath = ps[0]
        path_length = len(ps)
        if path_length in [3, 4]:
            monthfolder = ps[-1]  # last folder in path
        elif path_length == 5:
            monthfolder = ps[-1]  # last folder in path
        report_year = monthfolder.split("-")[0][:4]
        report_month = monthfolder.split("-")[0][4:6]
        if accountIdInPath in active_months_in_gcs_bucket:
            active_months_in_gcs_bucket[accountIdInPath].append(f"{report_year}-{report_month}-01")
        else:
            active_months_in_gcs_bucket[accountIdInPath] = [f"{report_year}-{report_month}-01"]
    # sending a single event for triggering historical update of each accountId.
    processed_accounts = set()
    for path in unique_cur_paths:
        event_data = {}
        sp = path.split('/')
        event_data["accountId"] = sp[0]
        event_data["path"] = path
        event_data["bucket"] = jsonData["bucket"]
        if event_data["accountId"] not in processed_accounts:
            event_data["triggerHistoricalCostUpdateInPreferredCurrency"] = True
            event_data["disableHistoricalUpdateForMonths"] = active_months_in_gcs_bucket[event_data["accountId"]]
        http_trigger_cf_v2_async(AZUREBILLINGBQCFNAME, event_data)
        processed_accounts.add(event_data["accountId"])


def create_scheduler_job(jsonData):
    # Create a client.
    client = scheduler.CloudSchedulerClient()
    # Construct the fully qualified location path.
    parent = f"projects/{PROJECTID}/locations/us-central1"
    name = f"{parent}/jobs/ce-azuredata-{jsonData['accountIdOrig']}-{jsonData['tableSuffix']}"
    now = datetime.datetime.utcnow()
    triggerTime = now + datetime.timedelta(minutes=10)
    schedule = "%d %d %d %d *" % (triggerTime.minute, triggerTime.hour, triggerTime.day, triggerTime.month)
    topic = "projects/%s/topics/ce-azuredata-scheduler" % PROJECTID

    # Construct the request body.
    job = {
        'name': name,
        'pubsub_target': {
            'topic_name': topic,
            'data': bytes(json.dumps(jsonData), 'utf-8')
        },
        'schedule': schedule,
        'time_zone': "UTC"
    }

    try:
        # Update existing jobs with the same name if any.
        client.update_job(job=job)
        print("Job updated.")
    except Exception as e:
        print("%s. This can be ignored" % e, "WARN")
        # Use the client to send the job creation request.
        response = client.create_job(
            request={
                "parent": parent,
                "job": job
            }
        )
        print('Created job: {}'.format(response.name))
    return
