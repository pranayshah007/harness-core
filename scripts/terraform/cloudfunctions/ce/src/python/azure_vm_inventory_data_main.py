# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import io
import json
import os
import re
from datetime import datetime

from google.cloud import bigquery

import util
from util import create_dataset, TABLE_NAME_FORMAT, if_tbl_exists, print_, createTable

import azure.mgmt.resourcegraph as arg
from azure.identity import ClientSecretCredential
from azure_util import get_secret_key


"""
{
    "accountId": "kmpySmUISimoRrJL6NL73w",
    "tenantId": "b229b2bb-5f33-4d22-bce0-730f6474e906",
    "subscriptionsList": ["20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0"]
}
"""

UNASSIGNED_ATTRIBUTE = "UNASSIGNED_ATTRIBUTE"


def get_tags(vm):
    tags = []
    if 'tags' in vm and vm['tags'] is not None:
        for key in vm['tags']:
            tags.append({
                "key": key,
                "value": vm['tags'][key]
            })

    if len(tags) == 0:
        return None

    return tags


def get_vm_attribute(vm, property_list, return_list=False):
    attribute = UNASSIGNED_ATTRIBUTE
    for property in property_list:
        if attribute == UNASSIGNED_ATTRIBUTE:
            attribute = vm[property] if property in vm else None
        else:
            attribute = attribute[property] if property in attribute else None
        if not attribute:
            print(f"Attribute {property_list} was not found for VM({vm['id']}).")
            return None if not return_list else []
    return attribute


def get_data_to_insert(vm_list):
    data = {}
    print(f"Preparing {len(vm_list)} VM rows to be inserted.")
    for vm in vm_list:
        if vm["subscriptionId"] not in data:
            data[vm["subscriptionId"]] = []
        data[vm["subscriptionId"]].append({
            "vmId": get_vm_attribute(vm, ["id"]),
            "name": get_vm_attribute(vm, ["name"]),
            "tenantId": get_vm_attribute(vm, ["tenantId"]),
            "type": get_vm_attribute(vm, ["type"]),
            "location": get_vm_attribute(vm, ["location"]),
            "resourceGroup": get_vm_attribute(vm, ["resourceGroup"]),
            "subscriptionId": get_vm_attribute(vm, ["subscriptionId"]),
            "managedBy": get_vm_attribute(vm, ["managedBy"]),
            "creationTime": get_vm_attribute(vm, ["properties", "timeCreated"]),
            "provisioningState": get_vm_attribute(vm, ["properties", "provisioningState"]),
            "kind": get_vm_attribute(vm, ["kind"]),
            "networkInterfaces": len(get_vm_attribute(vm, ["properties", "networkProfile", "networkInterfaces"], True)),
            "osType": get_vm_attribute(vm, ["properties", "storageProfile", "osDisk", "osType"]),
            "publisher": get_vm_attribute(vm, ["properties", "storageProfile", "imageReference", "publisher"]),
            "offer": get_vm_attribute(vm, ["properties", "storageProfile", "imageReference", "offer"]),
            "sku": get_vm_attribute(vm, ["properties", "storageProfile", "imageReference", "sku"]),  # plan
            "displayStatus": (get_vm_attribute(vm, ["properties", "extended", "instanceView", "powerState", "displayStatus"]) or
                              get_vm_attribute(vm, ["properties", "provisioningState"])),
            "lastUpdatedAt": str(datetime.utcnow()),
            "vmSize": get_vm_attribute(vm, ["properties", "hardwareProfile", "vmSize"]),
            "computerName": get_vm_attribute(vm, ["properties", "osProfile", "computerName"]),
            "hyperVGeneration": get_vm_attribute(vm, ["properties", "extended", "instanceView", "hyperVGeneration"]),
            "tags": get_tags(vm),
            "publicIps": get_vm_attribute(vm, ["publicIps"]),
            "privateIps": get_vm_attribute(vm, ["privateIps"])
        })
    return data


def insert_data_in_table(client, rows, table_name):
    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_TRUNCATE,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON
    )

    rows_json = u'%s' % ('\n'.join([json.dumps(row) for row in rows]))
    data_as_file = io.StringIO(rows_json)

    job = client.load_table_from_file(data_as_file, table_name, job_config=job_config)
    print_(job.job_id)
    job.result()


def custom_response(pipeline_response, deserialized, *kwargs):
    resource = deserialized
    quota_remaining = None
    quota_resets_after = None
    try:
        headers = pipeline_response.http_response.internal_response.headers
        quota_remaining = headers._store['x-ms-user-quota-remaining']
        quota_resets_after = headers._store['x-ms-user-quota-resets-after']
    except AttributeError:
        pass
    setattr(resource, 'user_quota_remaining', quota_remaining)
    setattr(resource, 'user_quota_resets_after', quota_resets_after)
    return resource


def get_vms(query, tenant_id, subscription_ids):
    vm_list = []
    credential = ClientSecretCredential(tenant_id=tenant_id,
                                        client_id=get_secret_key("CE_AZURE_CLIENT_ID_GCPSM"),
                                        client_secret=get_secret_key("CE_AZURE_CLIENT_SECRET_GCPSM"))

    arg_client = arg.ResourceGraphClient(credential)

    arg_query_options = arg.models.QueryRequestOptions(result_format="objectArray", top=1000, skip=0)
    arg_query = arg.models.QueryRequest(subscriptions=subscription_ids, query=query, options=arg_query_options)
    arg_results = arg_client.resources(arg_query, cls=custom_response)
    vm_list += arg_results.data

    print("Azure Resource Graph user_quota_remaining: ", arg_results.user_quota_remaining[1])
    print("Azure Resource Graph user_quota_resets_after (hh:mm:ss): ", arg_results.user_quota_resets_after[1])

    while arg_results.skip_token:
        arg_query_options = arg.models.QueryRequestOptions(result_format="objectArray", skip_token=arg_results.skip_token)
        arg_query = arg.models.QueryRequest(subscriptions=subscription_ids, query=query, options=arg_query_options)
        arg_results = arg_client.resources(arg_query, cls=custom_response)
        vm_list += arg_results.data

        print("Azure Resource Graph user_quota_remaining: ", arg_results.user_quota_remaining[1])
        print("Azure Resource Graph user_quota_resets_after: ", arg_results.user_quota_resets_after[1])

    return vm_list


def get_arg_query():
    return """
    Resources
    | where type =~ 'microsoft.compute/virtualmachines'
    | extend vmId = tolower(tostring(id)), vmName = name
    | join (Resources
        | where type =~ 'microsoft.network/networkinterfaces'
        | mv-expand ipconfig=properties.ipConfigurations
        | project vmId = tolower(tostring(properties.virtualMachine.id)), privateIp = ipconfig.properties.privateIPAddress, publicIpId = tostring(ipconfig.properties.publicIPAddress.id)
        | join kind=leftouter (Resources
            | where type =~ 'microsoft.network/publicipaddresses'
            | project publicIpId = id, publicIp = properties.ipAddress
        ) on publicIpId
        | project-away publicIpId, publicIpId1
        | summarize privateIps = make_list(privateIp), publicIps = make_list(publicIp) by vmId
    ) on vmId
    | project-away vmId, vmId1, vmName
    | sort by name asc
    """


def main(request):
    print(request)
    jsonData = request.get_json(force=True)
    print(jsonData)

    jsonData["projectName"] = os.environ.get('GCP_PROJECT', 'ccm-play')
    client = bigquery.Client(jsonData["projectName"])

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")

    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    create_dataset(client, jsonData["datasetName"])
    dataset = client.dataset(jsonData["datasetName"])

    # Setting table names for main and temp tables
    azure_vm_inventory_table_ref = dataset.table("azureVMInventory")
    azure_vm_inventory_table_name = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "azureVMInventory")

    azure_vm_inventory_temp_table_ref = {}
    azure_vm_inventory_temp_table_name = {}
    for subscriptionId in jsonData["subscriptionsList"]:
        try:
            azure_vm_inventory_temp_table_ref[subscriptionId] = dataset.table("azureVMInventory_%s" % subscriptionId)
            azure_vm_inventory_temp_table_name[subscriptionId] = TABLE_NAME_FORMAT % (
                jsonData["projectName"], jsonData["accountIdBQ"], "azureVMInventory_%s" % subscriptionId)
        except Exception as e:
            print(f"Exception occurred while getting TableReference for temp table of subscription {subscriptionId}: {str(e)}")


    # Creating tables if they don't exist
    if not if_tbl_exists(client, azure_vm_inventory_table_ref):
        print_("%s table does not exists, creating table..." % azure_vm_inventory_table_ref)
        createTable(client, azure_vm_inventory_table_ref)

    for subscriptionId in jsonData["subscriptionsList"]:
        try:
            if not if_tbl_exists(client, azure_vm_inventory_temp_table_ref[subscriptionId]):
                print_("%s table does not exists, creating table..." % azure_vm_inventory_temp_table_ref[subscriptionId])
                createTable(client, azure_vm_inventory_temp_table_ref[subscriptionId])
        except Exception as e:
            print(f"Exception occurred while creating temp table for subscription {subscriptionId}: {str(e)}")

    vm_list = get_vms(query=get_arg_query(), tenant_id=jsonData["tenantId"], subscription_ids=jsonData["subscriptionsList"])
    data = get_data_to_insert(vm_list)
    print("Obtained VM data to be inserted into BQ.")

    for subscriptionId in jsonData["subscriptionsList"]:
        try:
            if subscriptionId in data and subscriptionId in azure_vm_inventory_temp_table_name:
                insert_data_in_table(client, data[subscriptionId], azure_vm_inventory_temp_table_name[subscriptionId])
        except Exception as e:
            print(f"Exception occurred while inserting inventory data in temp table for subscription {subscriptionId}: {str(e)}")
    return "Successfully executed Cloud Function: ce-azure-vm-inventory-data-terraform"
