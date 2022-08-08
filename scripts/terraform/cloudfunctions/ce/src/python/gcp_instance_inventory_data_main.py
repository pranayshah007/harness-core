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


def get_labels(instance):
    labels = []
    if 'labels' in instance and instance['labels'] is not None:
        for label in instance['labels']:
            labels.append({
                "key": label,
                "value": instance['labels'][label]
            })

    if len(labels) == 0:
        return None

    return labels


def get_disks(instance):
    disks = []
    if 'disks' in instance and instance['disks'] is not None:
        for disk in instance['disks']:
            if 'source' in disk:
                disks.append(disk['source'].split('/')[-1])

    if len(disks) == 0:
        return None

    return disks


def get_network_interfaces(instance):
    network_interfaces = []
    if 'networkInterfaces' in instance and instance['networkInterfaces'] is not None:
        for network_interface in instance['networkInterfaces']:
            network_interfaces.append({
                "networkIP": str(network_interface['networkIP']),
                "name": network_interface['name'],
                "accessConfigs": get_access_configs(network_interface)
            })

    if len(network_interfaces) == 0:
        return None

    return network_interfaces


def get_access_configs(network_interface):
    access_configs = []
    if 'accessConfigs' in network_interface and network_interface['accessConfigs'] is not None:
        for access_config in network_interface['accessConfigs']:
            access_configs.append({
                "type": access_config.get('type'),
                "name": access_config.get('name'),
                "natIP": access_config.get('natIP')
            })

    if len(access_configs) == 0:
        return None

    return access_configs


def get_status(instance):
    status = instance.get('status')
    if status == 'TERMINATED':
        return 'STOPPED'
    else:
        return status


def get_data_to_insert(instance, zone, region, project_id):
    return {
        "instanceId": instance.get('id'),
        "name": instance.get('name'),
        "creationTime": instance.get('creationTimestamp'),
        "zone": zone,
        "region": region,
        "machineType": instance.get('machineType').split('/')[-1],
        "projectId": project_id,
        "status": get_status(instance),
        "canIpForward": instance.get('canIpForward'),
        "selfLink": instance.get('selfLink'),
        "startRestricted": instance.get('startRestricted'),
        "deletionProtection": instance.get('deletionProtection'),
        "networkInterfaces": get_network_interfaces(instance),
        "labels": get_labels(instance),
        "disks": get_disks(instance),
        "lastStartTimestamp": instance.get('lastStartTimestamp'),
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
    gcp_instance_inventory_table_ref = dataset.table("gcpInstanceInventory")
    gcp_instance_inventory_temp_table_ref = dataset.table("gcpInstanceInventory_%s" % jsonData["projectId"])
    gcp_instance_inventory_table_name = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "gcpInstanceInventory")
    gcp_instance_inventory_temp_table_name = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "gcpInstanceInventory_%s" % jsonData["projectId"])

    # Creating tables if they don't exist
    if not if_tbl_exists(client, gcp_instance_inventory_table_ref):
        print_("%s table does not exists, creating table..." % gcp_instance_inventory_table_ref)
        createTable(client, gcp_instance_inventory_table_ref)

    if not if_tbl_exists(client, gcp_instance_inventory_temp_table_ref):
        print_("%s table does not exists, creating table..." % gcp_instance_inventory_temp_table_ref)
        createTable(client, gcp_instance_inventory_temp_table_ref)

    credentials = get_impersonated_credentials(jsonData)
    zones, zones_to_region_mapping = get_zones(jsonData["projectId"], credentials)
    service = discovery.build('compute', 'v1', credentials=credentials)
    data = []
    for zone in zones_to_region_mapping:
        request = service.instances().list(project=jsonData["projectId"], zone=zone)
        while request is not None:
            response = request.execute()
            if 'items' in response:
                for instance in response['items']:
                    data.append(get_data_to_insert(instance, zone, STATIC_ZONES_MAPPING[zone], jsonData["projectId"]))
            request = service.instances().list_next(previous_request=request, previous_response=response)

    insert_data_in_table(client, data, gcp_instance_inventory_temp_table_name)
