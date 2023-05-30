# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os
import base64
import json
from google.cloud import bigquery
from google.cloud import pubsub_v1
from google.cloud import storage
import datetime
from util import http_trigger_cf_v2_async

"""
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
AWSCFTOPIC = os.environ.get('AWSCFTOPIC', 'ce-aws-billing-cf')
TOPIC_PATH = publisher.topic_path(PROJECTID, AWSCFTOPIC)
AWSBILLINGBQCFNAME = f"projects/{PROJECTID}/locations/us-central1/functions/ce-aws-billing-bq-terraform"

def main(event, context):
    """Triggered when gcs data transfer job completes for AWS
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(f"Processing event json: {jsonData}")
    if event.get("attributes", {}).get("eventType", "") != "TRANSFER_OPERATION_SUCCESS":
        print("Failed transfer operation. Returning")
        return
    jsonData["bucket"] = jsonData.get("transferSpec", {}).get("gcsDataSink", {}).get("bucketName")

    folders_to_ingest = get_csv_paths(jsonData)
    if len(folders_to_ingest) > 0:
        trigger_bq_cf_for_processing(folders_to_ingest, jsonData)
    print("Completed")

def get_csv_paths(jsonData):
    """
    This function finds out maximum sized folders for each month folder
    :param jsonData:
    :return:
    """
    # Pagination is handled inbuilt
    blobs = client_gcs.list_blobs(jsonData["bucket"])
    unique_month_folders = {}
    json_folder_map = {}
    csv_folder_size_map = {}
    for blob in blobs:
        # Should be a valid CSV file. Add all exclusions here
        print(blob.name)
        if not blob.name.endswith(("Manifest.json", ".csv.gz", ".csv.zip", ".csv")) or blob.name.endswith("DefaultRule-AllBlobs.csv"):
            continue
        path = blob.name.split("/")
        print(path)
        # AROAXVZVVGMCF7KFQSJ37:o0yschY0RrGZJ2JFGEpvdw/mg7Qs7PuQxAgqg3aNzau0x/harness_cloud_cost_demo/20210501-20210601/.json
        # AROAXVZVVGMCF7KFQSJ37:o0yschY0RrGZJ2JFGEpvdw/mg7Qs7PuQxAgqg3aNzau0x/<example-report-name>/yyyymmdd-yyyymmdd/<assemblyId>/<example-report-name>-<file-number>.csv.<zip|gz>
        # Path length check
        if len(path) not in [5, 6]:
            continue

        folder_name = "/".join(path[:-1])
        if blob.name.endswith("Manifest.json"):
            json_folder_map[folder_name] = blob.name
        elif blob.name.endswith((".csv.gz", ".csv.zip", ".csv")):
            csv_folder_name = folder_name
            if not is_valid_month_folder(path[-2]): # versioned folder case
                try:
                    unique_month_folders["/".join(path[:-2])].append(csv_folder_name)
                except:
                    unique_month_folders["/".join(path[:-2])] = [csv_folder_name]
                try:
                    csv_folder_size_map[csv_folder_name] += blob.size
                except KeyError:
                    csv_folder_size_map[csv_folder_name] = blob.size
            else:
                unique_month_folders[csv_folder_name] = [csv_folder_name]
                try:
                    csv_folder_size_map[csv_folder_name] += blob.size
                except KeyError:
                    csv_folder_size_map[csv_folder_name] = blob.size

    print("Folders with JSON: %s \n Folders with CSVs: %s \n Unique month folders: %s" %
           (json_folder_map, csv_folder_size_map, unique_month_folders))
    # Find csv folder with highest size in each unique month folder
    folders_to_ingest = set()
    for mf in unique_month_folders:
        max_size = 0
        max_folder_name = None
        for vf in unique_month_folders[mf]:
            if csv_folder_size_map[vf] > max_size:
                max_size = csv_folder_size_map[vf]
                max_folder_name = vf
        folders_to_ingest.add(max_folder_name)
        print("Folder with max_size: %s  %s" % (max_folder_name, max_size))
    print("Folders to ingest: %s" % folders_to_ingest)
    return list(folders_to_ingest)

def is_valid_month_folder(folderstr):
    print(folderstr)
    try:
        report_month = folderstr.split("-")
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
        return False
    return True

def trigger_bq_cf_for_processing(folders_to_ingest, jsonData):
    active_months_in_gcs_bucket = {}
    for path in folders_to_ingest:
        ps = path.split("/")
        accountIdInPath = ps[0].split(":")[1] #AROAXVZVVGMCF7KFQSJ37:o0yschY0RrGZJ2JFGEpvdw
        if len(ps) == 4:
            monthfolder = ps[-1]  # last folder in path
        elif len(ps) == 5:
            monthfolder = ps[-2]  # second last folder in path
        report_year = monthfolder.split("-")[0][:4]
        report_month = monthfolder.split("-")[0][4:6]
        if accountIdInPath in active_months_in_gcs_bucket:
            active_months_in_gcs_bucket[accountIdInPath].append(f"{report_year}-{report_month}-01")
        else:
            active_months_in_gcs_bucket[accountIdInPath] = [f"{report_year}-{report_month}-01"]

    print("Sending events for respective folders")
    # sending a single event for triggering historical update of each accountId.
    processed_accounts = set()
    for path in folders_to_ingest:
        event_data = {}
        sp = path.split('/')
        event_data["accountId"] = sp[0].split(":")[1] #AROAXVZVVGMCF7KFQSJ37:o0yschY0RrGZJ2JFGEpvdw
        event_data["path"] = path
        event_data["bucket"] = jsonData["bucket"]
        if event_data["accountId"] not in processed_accounts:
            event_data["triggerHistoricalCostUpdateInPreferredCurrency"] = True
            event_data["disableHistoricalUpdateForMonths"] = active_months_in_gcs_bucket[event_data["accountId"]]
        http_trigger_cf_v2_async(AWSBILLINGBQCFNAME, event_data)
        processed_accounts.add(event_data["accountId"])
