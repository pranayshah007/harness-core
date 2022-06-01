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
    jsonData["sourceTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "gcpInstanceInventory_*")
    jsonData["targetTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "gcpInstanceInventory")

    load_into_main_table(jsonData)
    update_instance_state(jsonData)
    print_("Completed")


def update_instance_state(jsonData):
    """
    Updates the state to terminated for those instances which were not updated in past 1 day.
    :return: None
    """
    last_updated_at = datetime.date.today() - datetime.timedelta(days=1)
    query = "UPDATE `%s` set status='DELETED' WHERE status != 'DELETED' and lastUpdatedAt < '%s';" % (
        jsonData["targetTableId"], last_updated_at)

    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e)
    else:
        print_("Finished updating gcpInstanceInventory table for any terminated instances")


def load_into_main_table(jsonData):
    last_updated_at = datetime.datetime.utcnow()
    query = """MERGE `%s` T
                USING `%s` S
                ON T.instanceId = S.instanceId and T.creationTime = s.creationTime
                WHEN MATCHED THEN
                  UPDATE SET networkInterfaces = s.networkInterfaces, status = s.status, lastUpdatedAt = '%s', 
                  labels = s.labels, disks = s.disks, lastStartTimestamp = s.lastStartTimestamp
                WHEN NOT MATCHED THEN
                  INSERT (instanceId, name, creationTime, zone, 
                    region, machineType, projectId, status, canIpForward,
                     selfLink, startRestricted, deletionProtection, networkInterfaces, labels, disks,
                      lastStartTimestamp, lastUpdatedAt) 
                  VALUES(instanceId, name, creationTime, zone, 
                    region, machineType, projectId, status, canIpForward,
                     selfLink, startRestricted, deletionProtection, networkInterfaces, labels, disks, lastStartTimestamp,
                      lastUpdatedAt) 
                """ % (jsonData["targetTableId"], jsonData["sourceTableId"], last_updated_at)
    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e, "WARN")
    else:
        print_("Finished merging into main gcpInstanceInventory table")
