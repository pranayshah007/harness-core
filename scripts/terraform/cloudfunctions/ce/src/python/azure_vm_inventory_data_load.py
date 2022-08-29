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
    jsonData["sourceTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "azureVMInventory_*")
    jsonData["targetTableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "azureVMInventory")

    load_into_main_table(jsonData)
    update_vm_state(jsonData)
    print_("Completed")


def update_vm_state(jsonData):
    """
    Updates the displayStatus to `VM Deleted` for those VMs which were not updated in past 1 day.
    :return: None
    """
    lastUpdatedAt = datetime.date.today()  # - timedelta(days = 1)
    query = "UPDATE `%s` set displayStatus='VM Deleted' WHERE displayStatus!='VM Deleted' and lastUpdatedAt < '%s';" % (
        jsonData["targetTableId"], lastUpdatedAt)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished updating azureVMInventory table for any deleted VMs")


def load_into_main_table(jsonData):
    last_updated_at = datetime.datetime.utcnow()
    query = """MERGE `%s` T
                USING `%s` S
                ON T.vmId = S.vmId and T.creationTime = s.creationTime
                WHEN MATCHED THEN
                  UPDATE SET networkInterfaces = s.networkInterfaces, provisioningState = s.provisioningState,
                  lastUpdatedAt = '%s', displayStatus = s.displayStatus, tags = s.tags,
                  publicIps = s.publicIps, privateIps = s.privateIps
                WHEN NOT MATCHED THEN
                  INSERT (vmId, name, tenantId, type, location, resourceGroup, subscriptionId, managedBy, 
                  creationTime, provisioningState, kind, networkInterfaces, osType, publisher, offer, sku,
                   displayStatus, lastUpdatedAt, vmSize, computerName, hyperVGeneration, tags, publicIps, privateIps) 
                  VALUES(vmId, name, tenantId, type, location, resourceGroup, subscriptionId, managedBy, 
                  creationTime, provisioningState, kind, networkInterfaces, osType, publisher, offer, sku,
                   displayStatus, lastUpdatedAt, vmSize, computerName, hyperVGeneration, tags, publicIps, privateIps) 
                """ % (jsonData["targetTableId"], jsonData["sourceTableId"], last_updated_at)

    run_batch_query(client, query, None, timeout=180)
    print_("Finished merging into main azureVMInventory table")
