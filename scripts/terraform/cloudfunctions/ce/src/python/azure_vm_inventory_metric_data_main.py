# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import json
import io
import os
import util
import re

from datetime import datetime, date, time, timedelta
from google.cloud import bigquery
from util import create_dataset, if_tbl_exists, createTable, print_, TABLE_NAME_FORMAT
from azure.identity import ClientSecretCredential
from azure_util import get_secret_key
from datetime import timedelta
from azure.monitor.query import MetricsQueryClient, MetricAggregationType
from azure.mgmt.compute import ComputeManagementClient

"""
{
    "accountId": "kmpySmUISimoRrJL6NL73w"
}
"""


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

    # This is available only in runtime python 3.7, go 1.11
    jsonData["projectName"] = os.environ.get('GCP_PROJECT', 'ccm-play')

    client = bigquery.Client(jsonData["projectName"])

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]

    create_dataset(client, jsonData["datasetName"])
    dataset = client.dataset(jsonData["datasetName"])
    # Make sure the table name in util also matches
    azureVMInventoryMetricTableRef = dataset.table("azureVMInventoryMetric")
    azureVMInventoryMetricTableName = TABLE_NAME_FORMAT % (
        jsonData["projectName"], jsonData["accountIdBQ"], "azureVMInventoryMetric")

    if not if_tbl_exists(client, azureVMInventoryMetricTableRef):
        print_("%s table does not exists, creating table..." % azureVMInventoryMetricTableRef)
        if not createTable(client, azureVMInventoryMetricTableRef):
            # No need to fetch CPU data at this point
            return

    vm_data_map, added_at = get_vm_cpu_and_memory_data(jsonData)
    print_("Total VMs for which CPU and Memory data was fetched: %s" % (len(vm_data_map)/2))
    if len(vm_data_map) == 0:
        print_("No VMs found to update CPU and Memory for")
        return
    job_config = bigquery.LoadJobConfig(
        write_disposition=bigquery.WriteDisposition.WRITE_APPEND,
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
    )
    b = u'%s' % ('\n'.join([json.dumps(vm_data_map[record]) for record in vm_data_map]))
    data_as_file = io.StringIO(b)
    job = client.load_table_from_file(data_as_file, azureVMInventoryMetricTableName, job_config=job_config)
    print_(job.job_id)
    job.result()

    print_("Completed")


def get_azure_tenant_ids(jsonData):
    """
    Returns unique VM Ids for each TenantID from BQ table
    :param jsonData:
    :return:
    """
    azure_tenantId_vms_map = {}
    client = bigquery.Client(jsonData["projectName"])
    azureVMInventoryTableName = TABLE_NAME_FORMAT % (jsonData["projectName"], jsonData["accountIdBQ"], "azureVMInventory")
    query = """
            SELECT distinct tenantId, vmId FROM %s where displayStatus="VM running";
            """ % (azureVMInventoryTableName)

    query_job = client.query(query)
    results = query_job.result()  # wait for job to complete
    for row in results:
        try:
            azure_tenantId_vms_map[row.tenantId].append(row.vmId)
        except KeyError:
            azure_tenantId_vms_map[row.tenantId] = [row.vmId]
    return azure_tenantId_vms_map


def custom_response(pipeline_response, deserialized, *kwargs):
    resource = deserialized
    remaining_subscription_reads = None
    try:
        headers = pipeline_response.http_response.internal_response.headers
        remaining_subscription_reads = headers._store['x-ms-ratelimit-remaining-subscription-reads']
        setattr(resource, 'x-ms-ratelimit-remaining-subscription-reads', remaining_subscription_reads)
        print(f"x-ms-ratelimit-remaining-subscription-reads: {remaining_subscription_reads[1]}")
    except Exception as e:
        pass
    return resource

def get_vm_size(vm_id_split, compute_client):
    resource_group_name = vm_id_split[4]
    vm_name = vm_id_split[-1]
    vm = compute_client.virtual_machines.get(resource_group_name, vm_name)
    vm_size = vm.hardware_profile.vm_size
    return vm_size

def get_vm_size_in_bytes(vm_size, vm_size_bytes_map, compute_client, region):
    if vm_size not in vm_size_bytes_map:
        vm_sizes = compute_client.virtual_machine_sizes.list(region)
        memory_in_bytes = next((size.memory_in_mb for size in vm_sizes if size.name == vm_size), 1) * 1049000
        vm_size_bytes_map[vm_size] = memory_in_bytes
    return vm_size_bytes_map[vm_size]

def get_vm_cpu_and_memory_data(jsonData):
    """
    We need to compute CPU for currently running VMs.
    This function iterates through all running VMs and calls metric API to fetch CPU utilization data.
    """
    vm_data_map = {}
    azure_tenantId_vms_map = get_azure_tenant_ids(jsonData)
    added_at = datetime.utcnow().__str__()
    vm_size_bytes_map = {}
    print_("Getting CPU & Memory data")

    for tenant_id in azure_tenantId_vms_map:
        print(f"Querying metrics for all running VMs for tenantId = {tenant_id}")
        credential = ClientSecretCredential(tenant_id=tenant_id,
                                            client_id=get_secret_key("CE_AZURE_CLIENT_ID_GCPSM"),
                                            client_secret=get_secret_key("CE_AZURE_CLIENT_SECRET_GCPSM"))
        client = MetricsQueryClient(credential)
        metric_start_time = datetime.combine(date.today() - timedelta(days=1), time())
        metric_end_time = datetime.combine(date.today(), time())
        for vm_id in azure_tenantId_vms_map[tenant_id]:
            try:
                print(f"Querying CPU metrics data for VM: {vm_id}..")
                cpu_response = client.query_resource(
                    vm_id,
                    metric_names=["Percentage CPU"],
                    timespan=timedelta(hours=24),
                    granularity=timedelta(hours=24),
                    aggregations=[MetricAggregationType.AVERAGE, MetricAggregationType.MINIMUM, MetricAggregationType.MAXIMUM],
                    cls=custom_response
                )

                cpu_metric = cpu_response.metrics[0]
                cpu_time_series_element = cpu_metric.timeseries[0]
                cpu_metric_value = cpu_time_series_element.data[0]
                vm_data_map[vm_id + "CPU"] = {
                    "metricName": cpu_metric.name,
                    "average": cpu_metric_value.average,
                    "minimum": cpu_metric_value.minimum,
                    "maximum": cpu_metric_value.maximum,
                    "vmId": vm_id,
                    "addedAt": added_at,
                    "metricStartTime": metric_start_time.__str__(),
                    "metricEndTime": metric_end_time.__str__()
                }
                print(f"vmId({vm_id}): minCPU={cpu_metric_value.minimum}%, maxCPU={cpu_metric_value.maximum}%, avgCPU={cpu_metric_value.average}%")

                region = cpu_response.resource_region

                vm_id_split = vm_id.split('/')
                subscription_id = vm_id_split[2]
                compute_client = ComputeManagementClient(credential, subscription_id)
                vm_size = get_vm_size(vm_id_split, compute_client)

                vm_size_in_bytes = get_vm_size_in_bytes(vm_size, vm_size_bytes_map, compute_client, region)

                print(f"Querying memory metrics data for VM: {vm_id}..")
                memory_response = client.query_resource(
                    vm_id,
                    metric_names=["Available Memory Bytes"],
                    timespan=timedelta(hours=24),
                    granularity=timedelta(hours=24),
                    aggregations=[MetricAggregationType.AVERAGE, MetricAggregationType.MINIMUM, MetricAggregationType.MAXIMUM],
                    cls=custom_response
                )

                memory_metric = memory_response.metrics[0]
                memory_time_series_element = memory_metric.timeseries[0]
                memory_metric_value = memory_time_series_element.data[0]
                memory_metric_value_average = ((vm_size_in_bytes - memory_metric_value.average) / vm_size_in_bytes) * 100
                memory_metric_value_minimum = ((vm_size_in_bytes - memory_metric_value.maximum) / vm_size_in_bytes) * 100
                memory_metric_value_maximum = ((vm_size_in_bytes - memory_metric_value.minimum) / vm_size_in_bytes) * 100
                vm_data_map[vm_id + "Memory"] = {
                    "metricName": "Percentage Memory",
                    "average": memory_metric_value_average,
                    "minimum": memory_metric_value_minimum,
                    "maximum": memory_metric_value_maximum,
                    "vmId": vm_id,
                    "addedAt": added_at,
                    "metricStartTime": metric_start_time.__str__(),
                    "metricEndTime": metric_end_time.__str__()
                }
                print(f"vmId({vm_id}): minMemory={memory_metric_value_minimum}%, maxMemory={memory_metric_value_maximum}%, avgMemory={memory_metric_value_average}%")

            except Exception as e:
                print_(e, "ERROR")

    return vm_data_map, added_at
