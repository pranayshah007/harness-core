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
from util import print_

"""
Scheduler event:
{
    "accountId": "kmpySmUISimoRrJL6NL73w"
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
    jsonData["sourceTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "gcpDiskInventory_*")
    jsonData["targetTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "gcpDiskInventory")

    load_into_main_table(jsonData)
    update_disk_state(jsonData)
    print_("Completed")


def update_disk_state(jsonData):
    """
    Updates the state to deleted for those disks which were not updated in past 2 hours.
    :return: None
    """
    last_updated_at = datetime.date.today() - datetime.timedelta(hours=2)
    query = "UPDATE `%s` set status='DELETED' WHERE status != 'DELETED' and lastUpdatedAt < '%s';" % (
        jsonData["targetTableId"], last_updated_at)

    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e)
    else:
        print_("Finished updating gcpDiskInventory table for any deleted disks")


def load_into_main_table(jsonData):
    last_updated_at = datetime.datetime.utcnow()
    query = """MERGE `%s` T
                USING `%s` S
                ON T.id = S.id and T.creationTime = S.creationTime 
                WHEN MATCHED THEN
                  UPDATE SET sizeGb = s.sizeGb, status = s.status, options = s.options,
                  type = s.type, provisionedIops = s.provisionedIops, 
                  snapshots = s.snapshots, labels = s.labels, users = s.users,
                  lastAttachTimestamp = s.lastAttachTimestamp, lastDetachTimestamp = s.lastDetachTimestamp,
                  lastUpdatedAt = '%s'
                WHEN NOT MATCHED THEN
                  INSERT (id, name, creationTime, zone, 
                    region, projectId, sizeGb, status, sourceSnapshot, sourceSnapshotId,
                    sourceStorageObject, options, sourceImage, sourceImageId, selfLink, type, labels, users,
                    physicalBlockSizeBytes, sourceDisk, sourceDiskId, provisionedIops, satisfiesPzs, snapshots,
                    lastAttachTimestamp, lastDetachTimestamp, lastUpdatedAt) 
                  VALUES(id, name, creationTime, zone, 
                    region, projectId, sizeGb, status, sourceSnapshot, sourceSnapshotId,
                    sourceStorageObject, options, sourceImage, sourceImageId, selfLink, type, labels, users,
                    physicalBlockSizeBytes, sourceDisk, sourceDiskId, provisionedIops, satisfiesPzs, snapshots,
                    lastAttachTimestamp, lastDetachTimestamp, lastUpdatedAt)
                """ % (jsonData["targetTableId"], jsonData["sourceTableId"], last_updated_at)
    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e, "WARN")
    else:
        print_("Finished merging into main gcpDiskInventory table")
