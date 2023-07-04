# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import io
import json
import base64
import os
import re
from google.cloud import bigquery
from google.cloud import secretmanager
from botocore.config import Config
import datetime
import boto3
import util
from util import create_dataset, if_tbl_exists, createTable, print_, TABLE_NAME_FORMAT
from aws_util import assumed_role_session, STATIC_REGION

PROJECTID_SECONDARY = os.environ.get('GCP_PROJECT_SECONDARY', 'ccm-play')

def getEbsVolumesData(jsonData):
    my_config = Config(
        region_name='us-west-2',  # initial region to call aws api first time
        retries={
            'max_attempts': 2,
            'mode': 'standard'
        }
    )
    EBS_VOLUMES_DATA_MAP = []
    try:
        print_("Assuming role")
        key, secret, token = assumed_role_session(jsonData)
    except Exception as e:
        print_(e, "WARN")
        return EBS_VOLUMES_DATA_MAP, set()

    ec2 = boto3.client('ec2', config=my_config, aws_access_key_id=key,
                       aws_secret_access_key=secret, aws_session_token=token)
    try:
        response = ec2.describe_regions()
        REGIONS = [item["RegionName"] for item in response['Regions']]
    except Exception as e:
        # We probably do not have describe region permission in CF template for this customer.
        print_(e, "WARN")
        print_("Using static region list")
        REGIONS = STATIC_REGION

    unique_volume_ids = set()
    for region in REGIONS:
        ec2 = boto3.client('ec2', region_name=region, aws_access_key_id=key,
                           aws_secret_access_key=secret, aws_session_token=token)

        # Describe snapshots call
        volume_snapshots_mapping = getSnapshots(ec2, region, jsonData['linkedAccountId'])

        # Describe volumes call
        print_("Getting all volumes for region - %s" % region)
        paginator = ec2.get_paginator('describe_volumes')
        page_iterator = paginator.paginate()
        volume_count = 0
        volume_inserted = dict()
        try:
            for volumes in page_iterator:
                for volume in volumes['Volumes']:
                    EBS_VOLUMES_DATA_MAP.append(getVolumeRow(volume, region, jsonData["linkedAccountId"], volume_snapshots_mapping))
                    volume_count += 1
                    volume_inserted[volume['VolumeId']] = True
                    unique_volume_ids.add(volume['VolumeId'])
        except Exception as e:
            print_(e, "WARN")
            print_("Error in getting volumes for %s" % region)
            continue

        # Insert rows for volumes which have been deleted but its snaphots are still present
        for volume in volume_snapshots_mapping:
            if volume_inserted.get(volume) is None:
                EBS_VOLUMES_DATA_MAP.append(getVolumeRowForDeletedVolume(volume, region, jsonData["linkedAccountId"], volume_snapshots_mapping[volume]))

        print_("Found %s volumes in region %s" % (volume_count, region))

    return EBS_VOLUMES_DATA_MAP, unique_volume_ids

def getSnapshots(client, region, linkedAccountId):
    paginator = client.get_paginator('describe_snapshots')
    page_iterator = paginator.paginate(OwnerIds=[linkedAccountId])
    snapshot_count = 0
    volume_has_snapshot = dict()
    volume_snapshot_mapping = dict()
    try:
        for snapshots in page_iterator:
            for snapshot in snapshots['Snapshots']:
                volumeId = snapshot['VolumeId']
                if volume_has_snapshot.get(volumeId) is None:
                    volume_has_snapshot[volumeId] = True
                    volume_snapshot_mapping[volumeId] = []
                volume_snapshot_mapping[volumeId].append(getSnapshotDetails(snapshot))
                snapshot_count += 1
    except Exception as e:
        print_(e, "WARN")
        print_("Error in getting volumes for %s" % region)

    print_("Found %s snapshots in region %s" % (snapshot_count, region))
    return volume_snapshot_mapping

def getSnapshotDetails(snapshotData):
    return {
        "createTime": str(snapshotData.get('StartTime')),
        "snapshotId": snapshotData.get('SnapshotId'),
        "status": snapshotData.get('State'),
        "description": snapshotData.get('Description'),
        "encrypted": snapshotData.get('Encrypted')
    }

def getVolumeRow(volumeData, region, linkedAccountId, snapshots):
    return {
        "lastUpdatedAt": str(datetime.datetime.utcnow()),
        "volumeId": volumeData['VolumeId'],
        "createTime": str(volumeData['CreateTime']),
        "availabilityZone": volumeData['AvailabilityZone'],
        "region": region,
        "encrypted": volumeData.get('Encrypted'),
        "size": volumeData.get('Size'),
        "state": volumeData.get('State'),
        "iops": volumeData.get('Iops'),
        "volumeType": volumeData.get('VolumeType'),
        "multiAttachedEnabled": volumeData.get('MultiAttachEnabled'),
        "detachedAt": None,
        "deleteTime": None,
        "snapshotId": volumeData.get('SnapshotId'),
        "kmsKeyId": volumeData.get('KmsKeyId'),
        "attachments": getAttachments(volumeData),
        "tags": getTags(volumeData),
        "snapshots": snapshots.get(volumeData['VolumeId']),
        "linkedAccountId": linkedAccountId,
        "linkedAccountIdPartition": int(linkedAccountId) % 10000
    }

def getVolumeRowForDeletedVolume(volumeId, region, linkedAccountId, snapshots):
    return {
        "lastUpdatedAt": str(datetime.datetime.utcnow()),
        "volumeId": volumeId,
        "createTime": None,
        "availabilityZone": None,
        "region": region,
        "encrypted": None,
        "size": None,
        "state": "deleted",
        "iops": None,
        "volumeType": None,
        "multiAttachedEnabled": None,
        "detachedAt": None,
        "deleteTime": None,
        "snapshotId": None,
        "kmsKeyId": None,
        "attachments": None,
        "tags": None,
        "snapshots": snapshots,
        "linkedAccountId": linkedAccountId,
        "linkedAccountIdPartition": int(linkedAccountId) % 10000
    }

def getAttachments(volumeData):
    attachments = []
    if 'Attachments' in volumeData and volumeData['Attachments'] is not None:
        for attachment in volumeData['Attachments']:
            attachments.append({
                "attachTime": str(attachment['AttachTime']),
                "device": attachment['Device'],
                "instanceId": attachment['InstanceId'],
                "state": attachment['State'],
                "volumeId": attachment['VolumeId'],
                "deleteOnTermination": attachment['DeleteOnTermination']
            })

    if len(attachments) == 0:
        return None

    return attachments

def getTags(volumeData):
    tags = []
    if 'Tags' in volumeData and volumeData['Tags'] is not None:
        for tag in volumeData['Tags']:
            tags.append({
                "key": tag['Key'],
                "value": tag['Value']
            })

    if len(tags) == 0:
        return None

    return tags

# For inserting volume data in temp table
def insertDataInTempTable(client, rows, awsEbsInventoryTempTableName):
    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
    )

    rowsJson = u'%s' % ('\n'.join([json.dumps(row) for row in rows]))
    data_as_file = io.StringIO(rowsJson)

    job = client.load_table_from_file(data_as_file, awsEbsInventoryTempTableName, job_config=job_config)
    print_(job.job_id)
    job.result()

# For merging temp table with main table
def insertIntoMainTable(client, awsEbsInventoryTableName, awsEbsInventoryTempTableName, uniqueVolumeIds, linkedAccountId):
    query = """ DELETE FROM `%s` WHERE volumeId IN (%s) AND linkedAccountIdPartition = MOD(%s,10000);
                INSERT INTO `%s` SELECT * FROM `%s` WHERE linkedAccountIdPartition = MOD(%s,10000);
                """ % (awsEbsInventoryTableName, uniqueVolumeIds, linkedAccountId,
                       awsEbsInventoryTableName, awsEbsInventoryTempTableName, linkedAccountId)

    query_job = client.query(query)
    query_job.result()

# Updating rows in main table for volumes which have been deleted
def updateDeletedVolumesInMainTable(client, awsEbsInventoryTableName, currentTime, linkedAccountId):
    query = """
        UPDATE %s SET attachments = NULL, state = "deleted", lastUpdatedAt = '%s', deleteTime = '%s' WHERE lastUpdatedAt < '%s' AND state != "deleted"
        AND linkedAccountIdPartition=MOD(%s,10000);
        """ % (awsEbsInventoryTableName, currentTime, currentTime, currentTime, linkedAccountId)

    query_job = client.query(query)
    query_job.result()

def main(event, context):
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(jsonData)

    # This is available only in runtime python 3.7, go 1.11
    jsonData["projectName"] = os.environ.get('GCP_PROJECT', 'ccm-play')
    client = bigquery.Client(PROJECTID_SECONDARY)
    
    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountIdOrig")

    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    dataset = create_dataset(client, jsonData["datasetName"])

    # Getting linked accountId
    jsonData["linkedAccountId"] = jsonData["roleArn"].split(":")[4]

    # Setting table names for main and temp tables
    awsEbsInventoryTableRef = dataset.table("awsEbsInventory")
    awsEbsInventoryTempTableRef = dataset.table("awsEbsInventoryTemp_%s" % jsonData.get("linkedAccountId"))
    awsEbsInventoryTableName = TABLE_NAME_FORMAT % (jsonData["projectName"], jsonData["accountIdBQ"], "awsEbsInventory")
    awsEbsInventoryTempTableName = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "awsEbsInventoryTemp_%s" % jsonData.get("linkedAccountId"))

    # Creating tables if they don't exist
    if not if_tbl_exists(client, awsEbsInventoryTableRef):
        print_("%s table does not exists, creating table..." % awsEbsInventoryTableRef)
        createTable(client, awsEbsInventoryTableRef)
    if not if_tbl_exists(client, awsEbsInventoryTempTableRef):
        print_("%s table does not exists, creating table..." % awsEbsInventoryTempTableRef)
        createTable(client, awsEbsInventoryTempTableRef)

    # Updating bq tables
    currentTime = datetime.datetime.utcnow()
    ebsVolumesDataMap, uniqueVolumeIds = getEbsVolumesData(jsonData)
    if len(uniqueVolumeIds) != 0:
        uniqueVolumeIds = ", ".join(f"'{w}'" for w in uniqueVolumeIds)
        insertDataInTempTable(client, ebsVolumesDataMap, awsEbsInventoryTempTableName)
        # insertIntoMainTable(client, awsEbsInventoryTableName, awsEbsInventoryTempTableName, uniqueVolumeIds, jsonData["linkedAccountId"])
        # updateDeletedVolumesInMainTable(client, awsEbsInventoryTableName, currentTime, jsonData["linkedAccountId"])

    print_("Completed")
