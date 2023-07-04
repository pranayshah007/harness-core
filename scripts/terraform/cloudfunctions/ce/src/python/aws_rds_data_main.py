# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import boto3
import base64
import datetime
import json
import io
import os

from google.oauth2 import service_account

import util
import re

from botocore.config import Config
from google.cloud import bigquery

from util import create_dataset, if_tbl_exists, createTable, print_, TABLE_NAME_FORMAT
from aws_util import assumed_role_session, STATIC_REGION

# jsonData = {
# 	"accountId": "kmpySmUISimoRrJL6NL73w",
# 	"roleArn": "arn:aws:iam::132359207506:role/RDSInventoryRole",
# 	"externalId": "harness:108817434118:kmpySmUISimoRrJL6NL73w"
# }

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
PROJECTID_SECONDARY = os.environ.get('GCP_PROJECT_SECONDARY', 'ccm-play')
client = bigquery.Client(PROJECTID_SECONDARY)


def main(event, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(jsonData)

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    dataset = create_dataset(client, jsonData["datasetName"])
    jsonData["linkedAccountId"] = jsonData["roleArn"].split(":")[4]
    awsRDSInventoryTableRef = dataset.table("awsRdsInventory")
    awsRDSInventoryTableRefTemp = dataset.table("awsRdsInventory_%s" % jsonData.get("linkedAccountId"))
    awsRDSInventoryTableNameTemp = TABLE_NAME_FORMAT % (
        PROJECTID, jsonData["accountIdBQ"], "awsRdsInventory_%s" % jsonData.get("linkedAccountId"))

    if not if_tbl_exists(client, awsRDSInventoryTableRef):
        print_("%s table does not exists, creating table..." % awsRDSInventoryTableRef)
        createTable(client, awsRDSInventoryTableRef)

    if not if_tbl_exists(client, awsRDSInventoryTableRefTemp):
        print_("%s table does not exists, creating table..." % awsRDSInventoryTableRefTemp)
        createTable(client, awsRDSInventoryTableRefTemp)

    rds_data_map = get_rds_data(jsonData)
    print_(rds_data_map)
    print_("Total RDS instances for which describe-instance data was fetched: %s" % len(rds_data_map))
    if len(rds_data_map) == 0:
        print_("No RDS Instances found")
        return

    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
    )

    b = u'%s' % ('\n'.join([json.dumps(record) for record in rds_data_map]))
    data_as_file = io.StringIO(b)
    job = client.load_table_from_file(data_as_file, awsRDSInventoryTableNameTemp, job_config=job_config)
    print_(job.job_id)
    job.result()
    print_("Completed")


def get_rds_data(jsonData):
    rds_data_map = []
    my_config = Config(
        region_name='us-west-2',  # initial region to call aws api first time
        retries={
            'max_attempts': 2,
            'mode': 'standard'
        }
    )

    try:
        print_("Assuming role")
        jsonData["projectName"] = PROJECTID
        key, secret, token = assumed_role_session(jsonData)
    except Exception as e:
        print_(e, "WARN")
        return rds_data_map

    rds = boto3.client('rds', config=my_config, aws_access_key_id=key,
                       aws_secret_access_key=secret, aws_session_token=token)

    try:
        response = rds.describe_source_regions()
        regions = [item["RegionName"] for item in response['SourceRegions']]
    except Exception as e:
        # We probably do not have describe region permission in CF template for this customer.
        print_(e, "WARN")
        print_("Using static region list")
        regions = STATIC_REGION
    linked_account_id = jsonData["linkedAccountId"]
    for region in regions:
        instance_count = 0
        rds = boto3.client('rds', region_name=region, aws_access_key_id=key,
                           aws_secret_access_key=secret, aws_session_token=token)
        print_("Getting all instances for region - %s" % region)
        paginator = rds.get_paginator('describe_db_instances')
        page_iterator = paginator.paginate()
        try:
            # Start iterating over the paginator. Populate rds_data_map
            for instances in page_iterator:
                uniqueinstanceids_region = prepare_rds_data_map(instances, rds_data_map, region, linked_account_id)
                instance_count += len(uniqueinstanceids_region)
        except Exception as e:
            print_(e)
            print_("Error calling describe instances for %s" % region, "WARN")
            continue

        print_("Found %s instances in region %s" % (instance_count, region))
    return rds_data_map


def prepare_rds_data_map(allinstances, rds_data_map, region, linked_account_id):
    uniqueinstanceids_region = set()
    for instance in allinstances["DBInstances"]:
        try:
            launch_time = instance.get("InstanceCreateTime")
            if isinstance(instance.get("InstanceCreateTime"), datetime.datetime):
                launch_time = instance.get("InstanceCreateTime").__str__()
            rds_data_map.append({
                "linkedAccountId": linked_account_id,
                "region": region,
                "DBInstanceIdentifier": instance.get("DBInstanceIdentifier"),
                "DBInstanceClass": instance.get("DBInstanceClass"),
                "Engine": instance.get("Engine"),
                "EngineVersion": instance.get("EngineVersion"),
                "DBInstanceStatus": instance.get("DBInstanceStatus"),
                "AllocatedStorage": instance.get("AllocatedStorage"),
                "Iops": instance.get("Iops"),
                "AvailabilityZone": instance.get("AvailabilityZone"),
                "MultiAZ": instance.get("MultiAZ"),
                "PubliclyAccessible": instance.get("PubliclyAccessible"),
                "StorageType": instance.get("StorageType"),
                "DBClusterIdentifier": instance.get("DBClusterIdentifier"),
                "StorageEncrypted": instance.get("StorageEncrypted"),
                "KmsKeyId": instance.get("KmsKeyId"),
                "DBInstanceArn": instance.get("DBInstanceArn"),
                "MaxAllocatedStorage": instance.get("MaxAllocatedStorage"),
                "DeletionProtection": instance.get("DeletionProtection"),
                "InstanceCreateTime": launch_time,
                "lastUpdatedAt": datetime.datetime.utcnow().__str__(),
                "tags": instance.get("TagList"),
                "linkedAccountIdPartition": int(linked_account_id) % 10000
            })
            uniqueinstanceids_region.add(instance["DBInstanceIdentifier"])
        except Exception as e:
            print_("Error in instance data :\n %s" % instance, 'WARN')
            print_(e)
    return uniqueinstanceids_region