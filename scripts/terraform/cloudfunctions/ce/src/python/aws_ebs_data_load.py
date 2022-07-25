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
    jsonData["sourceTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "awsEbsInventoryTemp_*")
    jsonData["targetTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "awsEbsInventory")

    load_into_main_table(jsonData)
    update_ebs_state(jsonData)
    print_("Completed")


def update_ebs_state(jsonData):
    """
    Updates the state to deleted for those volumes which were not updated in past 1 day.
    :return: None
    """
    lastUpdatedAt = datetime.date.today()  # - timedelta(days = 1)
    query = "UPDATE %s SET attachments = NULL, snapshots = NULL, state = 'deleted', lastUpdatedAt = '%s'," \
            " deleteTime = '%s' WHERE lastUpdatedAt < '%s' AND ( state != 'deleted' OR createTime IS NULL )" % (
                jsonData["targetTableId"], lastUpdatedAt, lastUpdatedAt, lastUpdatedAt)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished updating awsEbsInventory table for any deleted volumes")


def load_into_main_table(jsonData):
    lastUpdatedAt = datetime.datetime.utcnow()
    query = """
        MERGE `%s` T
        USING `%s` S
        ON T.volumeId = S.volumeId AND T.linkedAccountId = S.linkedAccountId AND T.region = S.region
        WHEN MATCHED THEN
            UPDATE SET volumeType = s.volumeType, size = s.size, state = s.state, iops = s.iops,
            attachments = s.attachments, tags = s.tags, snapshots = s.snapshots, lastUpdatedAt = '%s',
            detachedAt = 
                CASE
                    WHEN t.state = "in-use" AND s.state = "available" THEN '%s'
                    WHEN t.state = "available" AND s.state = "in-use" THEN NULL
                    ELSE t.detachedAt
                END
        WHEN NOT MATCHED THEN
            INSERT (lastUpdatedAt, volumeId, createTime, 
            availabilityZone, region, encrypted, size, state, iops, volumeType, multiAttachedEnabled, 
            detachedAt, deleteTime, snapshotId, kmsKeyId, attachments, tags, snapshots, linkedAccountId, linkedAccountIdPartition) 
            VALUES(lastUpdatedAt, volumeId, createTime, 
            availabilityZone, region, encrypted, size, state, iops, volumeType, multiAttachedEnabled, 
            detachedAt, deleteTime, snapshotId, kmsKeyId, attachments, tags, snapshots, linkedAccountId, linkedAccountIdPartition) 
    """ % (jsonData["targetTableId"], jsonData["sourceTableId"], lastUpdatedAt, lastUpdatedAt)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished merging into main awsEbsInventory table")
