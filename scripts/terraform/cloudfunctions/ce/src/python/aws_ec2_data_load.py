# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import json
import os
import util
import re
import datetime

from google.cloud import bigquery
from util import print_, run_batch_query

"""
Scheduler event:
{
    "accountId": "vZYBQdFRSlesqo3CMB90Ag"
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)


def main(event, context):
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    print(jsonData)
    # Set accountid for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    jsonData["sourceTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "awsEc2Inventory_*")
    jsonData["targetTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "awsEc2Inventory")

    load_into_main_table(jsonData)
    update_ec2_state(jsonData)
    print_("Completed")


def update_ec2_state(jsonData):
    """
    Updates the state to terminated for those EC2s which were not updated in past 1 day.
    :return: None
    """
    lastUpdatedAt = datetime.date.today()  # - timedelta(days = 1)
    query = "UPDATE `%s` set state='terminated' WHERE state='running' and lastUpdatedAt < '%s';" % (
        jsonData["targetTableId"], lastUpdatedAt)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished updating awsEc2Inventory table for any terminated instances")


def load_into_main_table(jsonData):
    lastUpdatedAt = datetime.datetime.utcnow()
    query = """MERGE `%s` T
                USING `%s` S
                ON T.InstanceId = S.InstanceId and T.linkedAccountIdPartition = s.linkedAccountIdPartition
                WHEN MATCHED THEN
                  UPDATE SET publicIpAddress = s.publicIpAddress, state = s.state, lastUpdatedAt = '%s', 
                    stateTransitionReason = s.stateTransitionReason, volumeIds = s.volumeIds
                WHEN NOT MATCHED THEN
                  INSERT (linkedAccountId, instanceId, instanceType, 
                    region, availabilityZone, tenancy, publicIpAddress, state, labels,
                     lastUpdatedAt, instanceLaunchedAt, instanceLifeCycle, volumeIds, reservationId, stateTransitionReason, linkedAccountIdPartition) 
                  VALUES(linkedAccountId, instanceId, instanceType, 
                    region, availabilityZone, tenancy, publicIpAddress, state, labels, 
                    lastUpdatedAt, instanceLaunchedAt, instanceLifeCycle, volumeIds, reservationId, stateTransitionReason, linkedAccountIdPartition) 
                """ % (jsonData["targetTableId"], jsonData["sourceTableId"], lastUpdatedAt)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished merging into main awsEc2Inventory table")
