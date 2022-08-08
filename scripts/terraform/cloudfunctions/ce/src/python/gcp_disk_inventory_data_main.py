# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import io
import json
import os
import re
from datetime import datetime

from google.cloud import bigquery
from googleapiclient import discovery

import util
from gcp_util import STATIC_ZONES_MAPPING, get_impersonated_credentials
from util import create_dataset, TABLE_NAME_FORMAT, if_tbl_exists, print_, createTable

"""
{
    "accountId": "kmpySmUISimoRrJL6NL73w",
    "serviceAccount": "harness-ce-harness-kmpys@ccm-play.iam.gserviceaccount.com",
    "projectId": "ccm-play"
}
"""


def get_zones(project_id, credentials):
    print(f"Getting zones for project_id: {project_id}")
    zones = []
    zones_to_region_mapping = dict()
    try:
        service = discovery.build('compute', 'v1', credentials=credentials)
        request = service.regions().list(project=project_id)
        while request is not None:
            response = request.execute()
            for region in response['items']:
                region_name = region['name']
                zones_list = region['zones']
                for zone in zones_list:
                    zone_name = zone.split('/')[-1]
                    zones.append(zone_name)
                    zones_to_region_mapping[zone_name] = region_name
            request = service.regions().list_next(previous_request=request, previous_response=response)
    except Exception as e:
        zones_to_region_mapping = STATIC_ZONES_MAPPING
        for key in zones_to_region_mapping:
            zones.append(key)
        print("Exception in getting zones for project: " + str(e))

    return zones, zones_to_region_mapping


def get_labels(disk):
    labels = []
    if 'labels' in disk and disk['labels'] is not None:
        for label in disk['labels']:
            labels.append({
                "key": label,
                "value": disk['labels'][label]
            })

    if len(labels) == 0:
        return None

    return labels


def get_users(disk):
    users = []
    if 'users' in disk and disk['users'] is not None:
        for user in disk['users']:
            users.append(user.split('/')[-1])

    if len(users) == 0:
        return None

    return users


def get_field(disk, field):
    if disk.get(field) is not None:
        return disk.get(field).split('/')[-1]
    else:
        return None


def get_snapshots(credentials, jsonData):
    service = discovery.build('compute', 'v1', credentials=credentials)
    disk_to_snapshots_mapping = dict()

    request = service.snapshots().list(project=jsonData["projectId"])
    while request is not None:
        response = request.execute()
        if 'items' in response:
            for snapshot in response['items']:
                print(snapshot)
                disk_id = snapshot.get('sourceDiskId')
                if disk_id not in disk_to_snapshots_mapping:
                    disk_to_snapshots_mapping[disk_id] = []
                disk_to_snapshots_mapping[disk_id].append(snapshot)
        request = service.instances().list_next(previous_request=request, previous_response=response)

    return disk_to_snapshots_mapping


def get_snapshot_data_to_insert(snapshots):
    data = []
    if snapshots is not None:
        for snapshot in snapshots:
            data.append({
                "id": snapshot.get('id'),
                "name": snapshot.get('name'),
                "creationTime": snapshot.get('creationTimestamp'),
                "diskSizeGb": snapshot.get('diskSizeGb'),
                "status": snapshot.get('status'),
                "storageBytes": snapshot.get('storageBytes'),
                "storageBytesStatus": snapshot.get('storageBytesStatus'),
                "sourceStorageObject": snapshot.get('sourceStorageObject'),
                "autoCreated": snapshot.get('autoCreated'),
                "downloadBytes": snapshot.get('downloadBytes'),
                "chainName": snapshot.get('chainName'),
                "satisfiesPzs": get_field(snapshot, 'satisfiesPzs')
            })
    else:
        return None
    return data


def get_data_to_insert(disk, zone, region, project_id, disk_to_snapshots_mapping):
    return {
        "id": disk.get('id'),
        "name": disk.get('name'),
        "creationTime": disk.get('creationTimestamp'),
        "zone": zone,
        "region": region,
        "projectId": project_id,
        "sizeGb": disk.get('sizeGb'),
        "status": disk.get('status'),
        "sourceSnapshot": disk.get('sourceSnapshot'),
        "sourceSnapshotId": disk.get('sourceSnapshotId'),
        "sourceStorageObject": disk.get('sourceStorageObject'),
        "options": disk.get('options'),
        "sourceImage": get_field(disk, 'sourceImage'),
        "sourceImageId": disk.get('sourceImageId'),
        "selfLink": disk.get('selfLink'),
        "type": get_field(disk, 'type'),
        "labels": get_labels(disk),
        "users": get_users(disk),
        "physicalBlockSizeBytes": disk.get('physicalBlockSizeBytes'),
        "sourceDisk": disk.get('sourceDisk'),
        "sourceDiskId": disk.get('sourceDiskId'),
        "provisionedIops": disk.get('provisionedIops'),
        "satisfiesPzs": disk.get('satisfiesPzs'),
        "snapshots": get_snapshot_data_to_insert(disk_to_snapshots_mapping.get(disk.get('id'))),
        "lastAttachTimestamp": disk.get('lastAttachTimestamp'),
        "lastDetachTimestamp": disk.get('lastDetachTimestamp'),
        "lastUpdatedAt": str(datetime.utcnow())
    }


def get_disk_row_for_deleted_disk(disk_id, project_id, snapshots):
    disk_name = None
    if snapshots is not None and len(snapshots) > 0:
        disk_name = get_field(snapshots[0], 'sourceDisk')

    return {
        "id": disk_id,
        "name": disk_name,
        "creationTime": str(datetime(1970, 1, 1)),
        "zone": None,
        "region": None,
        "projectId": project_id,
        "sizeGb": None,
        "status": 'DELETED',
        "sourceSnapshot": None,
        "sourceSnapshotId": None,
        "sourceStorageObject": None,
        "options": None,
        "sourceImage": None,
        "sourceImageId": None,
        "selfLink": None,
        "type": None,
        "labels": None,
        "users": None,
        "physicalBlockSizeBytes": None,
        "sourceDisk": None,
        "sourceDiskId": None,
        "provisionedIops": None,
        "satisfiesPzs": None,
        "snapshots": get_snapshot_data_to_insert(snapshots),
        "lastAttachTimestamp": None,
        "lastDetachTimestamp": None,
        "lastUpdatedAt": str(datetime.utcnow())
    }


def insert_data_in_table(client, rows, table_name):
    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
    )

    rows_json = u'%s' % ('\n'.join([json.dumps(row) for row in rows]))
    data_as_file = io.StringIO(rows_json)

    job = client.load_table_from_file(data_as_file, table_name, job_config=job_config)
    print_(job.job_id)
    job.result()


def main(event, context):
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(jsonData)

    # This is available only in runtime python 3.7, go 1.11
    jsonData["projectName"] = os.environ.get('GCP_PROJECT', 'ccm-play')
    client = bigquery.Client(jsonData["projectName"])

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")

    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    create_dataset(client, jsonData["datasetName"])
    dataset = client.dataset(jsonData["datasetName"])

    # Setting table names for main and temp tables
    gcp_disks_inventory_table_ref = dataset.table("gcpDiskInventory")
    gcp_disks_inventory_temp_table_ref = dataset.table("gcpDiskInventory_%s" % jsonData["projectId"])
    gcp_disks_inventory_table_name = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "gcpDiskInventory")
    gcp_disks_inventory_temp_table_name = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "gcpDiskInventory_%s" % jsonData["projectId"])

    # Creating tables if they don't exist
    if not if_tbl_exists(client, gcp_disks_inventory_table_ref):
        print_("%s table does not exists, creating table..." % gcp_disks_inventory_table_ref)
        createTable(client, gcp_disks_inventory_table_ref)

    if not if_tbl_exists(client, gcp_disks_inventory_temp_table_ref):
        print_("%s table does not exists, creating table..." % gcp_disks_inventory_temp_table_ref)
        createTable(client, gcp_disks_inventory_temp_table_ref)

    credentials = get_impersonated_credentials(jsonData)
    zones, zones_to_region_mapping = get_zones(jsonData["projectId"], credentials)
    disk_to_snapshots_mapping = get_snapshots(credentials, jsonData)
    service = discovery.build('compute', 'v1', credentials=credentials)
    data = []
    disk_inserted = dict()
    for zone in zones_to_region_mapping:
        request = service.disks().list(project=jsonData["projectId"], zone=zone)
        while request is not None:
            response = request.execute()
            if 'items' in response:
                for disk in response['items']:
                    disk_inserted[disk.get('id')] = True
                    data.append(get_data_to_insert(disk, zone, STATIC_ZONES_MAPPING[zone], jsonData["projectId"],
                                                   disk_to_snapshots_mapping))
            request = service.instances().list_next(previous_request=request, previous_response=response)

    for disk in disk_to_snapshots_mapping:
        if disk_inserted.get(disk) is None:
            data.append(get_disk_row_for_deleted_disk(disk, jsonData["projectId"], disk_to_snapshots_mapping.get(disk)))

    insert_data_in_table(client, data, gcp_disks_inventory_temp_table_name)
