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
import util
import re

from botocore.config import Config
from google.cloud import bigquery

from util import create_dataset, if_tbl_exists, createTable, print_, TABLE_NAME_FORMAT
from aws_util import assumed_role_session, STATIC_REGION

"""
{
	"accountId": "vZYBQdFRSlesqo3CMB90Ag",
	"roleArn": "arn:aws:iam::448640225317:role/harnessContinuousEfficiencyRole",
	"externalId": "harness:891928451355:wFHXHD0RRQWoO8tIZT5YVw"
}
"""

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
    awsEc2InventoryTableRef = dataset.table("awsEc2Inventory")
    awsEc2InventoryTableRefTemp = dataset.table("awsEc2Inventory_%s" % jsonData.get("linkedAccountId"))
    awsEc2InventoryTableNameTemp = TABLE_NAME_FORMAT % (
        PROJECTID, jsonData["accountIdBQ"], "awsEc2Inventory_%s" % jsonData.get("linkedAccountId"))

    if not if_tbl_exists(client, awsEc2InventoryTableRef):
        print_("%s table does not exists, creating table..." % awsEc2InventoryTableRef)
        createTable(client, awsEc2InventoryTableRef)
    else:
        pass
        # Call this only when there is any change.
        #alterTable(client, jsonData)
    if not if_tbl_exists(client, awsEc2InventoryTableRefTemp):
        print_("%s table does not exists, creating table..." % awsEc2InventoryTableRefTemp)
        createTable(client, awsEc2InventoryTableRefTemp)
        # Add alter table in else clause if needed

    ec2_data_map = get_ec2_data(jsonData)
    print_(ec2_data_map)
    print_("Total instances for which describe-instance data was fetched: %s" % len(ec2_data_map))
    if len(ec2_data_map) == 0:
        print_("No EC2s found")
        return

    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
    )

    b = u'%s' % ('\n'.join([json.dumps(record) for record in ec2_data_map]))
    data_as_file = io.StringIO(b)
    job = client.load_table_from_file(data_as_file, awsEc2InventoryTableNameTemp, job_config=job_config)
    print_(job.job_id)
    job.result()
    print_("Completed")


def get_ec2_data(jsonData):
    ec2_data_map = []
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
        return ec2_data_map

    ec2 = boto3.client('ec2', config=my_config, aws_access_key_id=key,
                       aws_secret_access_key=secret, aws_session_token=token)

    try:
        response = ec2.describe_regions()
        regions = [item["RegionName"] for item in response['Regions']]
    except Exception as e:
        # We probably do not have describe region permission in CF template for this customer.
        print_(e, "WARN")
        print_("Using static region list")
        regions = STATIC_REGION

    for region in regions:
        instance_count = 0
        ec2 = boto3.client('ec2', region_name=region, aws_access_key_id=key,
                           aws_secret_access_key=secret, aws_session_token=token)
        print_("Getting all instances for region - %s" % region)
        paginator = ec2.get_paginator('describe_instances')
        page_iterator = paginator.paginate()
        try:
            # Start iterating over the paginator. Populate ec2_data_map
            for instances in page_iterator:
                uniqueinstanceids_region = prepare_ec2_data_map(instances, ec2_data_map, region)
                instance_count += len(uniqueinstanceids_region)
        except Exception as e:
            print_(e)
            print_("Error calling describe instances for %s" % region, "WARN")
            continue

        print_("Found %s instances in region %s" % (instance_count, region))
    return ec2_data_map


def prepare_ec2_data_map(allinstances, ec2_data_map, region):
    uniqueinstanceids_region = set()
    for instances_ in allinstances["Reservations"]:
        owner_account = instances_["OwnerId"]
        reservation_id = instances_.get("ReservationId")
        for instance in instances_["Instances"]:
            try:
                launchTime = instance["LaunchTime"]
                if isinstance(instance["LaunchTime"], datetime.datetime):
                    launchTime = instance["LaunchTime"].__str__()
                ec2_data_map.append({
                    "linkedAccountId": owner_account,
                    "instanceId": instance["InstanceId"],
                    "instanceType": instance["InstanceType"],
                    "region": region,
                    "availabilityZone": instance["Placement"]["AvailabilityZone"],
                    "tenancy": instance["Placement"]["Tenancy"],
                    "publicIpAddress": instance.get("PublicIpAddress"),
                    "state": instance["State"]["Name"],
                    "labels": instance.get("Tags"),
                    "lastUpdatedAt": datetime.datetime.utcnow().__str__(),
                    "instanceLaunchedAt": launchTime,
                    "volumeIds": get_volume_ids(instance.get("BlockDeviceMappings", [])),
                    "reservationId": reservation_id,
                    "instanceLifeCycle": instance.get("InstanceLifecycle", "scheduled"),
                    "stateTransitionReason": instance.get("StateTransitionReason"),
                    "linkedAccountIdPartition": int(owner_account) % 10000
                })
                uniqueinstanceids_region.add(instance["InstanceId"])
            except Exception as e:
                print_("Error in instance data :\n %s" % instance, 'WARN')
                print_(e)
    return uniqueinstanceids_region


def get_volume_ids(blockdevicemapping):
    volume_ids = []
    for devicemap in blockdevicemapping:
        vol = devicemap.get("Ebs", {}).get("VolumeId", "")
        if vol:
            volume_ids.append(vol)
    return volume_ids


def alterTable(client, jsonData):
    """
    Any new column added here should also be added in the merge statement above

    :param client:
    :param jsonData:
    :return: none
    """
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    for table in ['awsEc2Inventory', "awsEc2Inventory_%s" % jsonData.get("linkedAccountId")]:
        print_("Altering %s Data Table" % table)
        query = "ALTER TABLE `%s.%s` " \
                "ADD COLUMN IF NOT EXISTS volumeIds ARRAY<STRING>, " \
                "ADD COLUMN IF NOT EXISTS instanceLifeCycle STRING, " \
                "ADD COLUMN IF NOT EXISTS reservationId STRING," \
                "ADD COLUMN IF NOT EXISTS stateTransitionReason STRING, " \
                "ADD COLUMN IF NOT EXISTS linkedAccountIdPartition INT64;" % (ds, table)

        try:
            query_job = client.query(query)
            query_job.result()
        except Exception as e:
            print_(query)
            print_(e)
        else:
            print_("Finished Altering %s Table" % table)
