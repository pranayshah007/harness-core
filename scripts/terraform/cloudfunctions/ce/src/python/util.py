# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import bq_schema
import time
import datetime

from google.cloud import bigquery
from google.cloud.exceptions import NotFound

ACCOUNTID_LOG = ""
TABLE_NAME_FORMAT = "%s.BillingReport_%s.%s"

# TABLE NAMES
CLUSTERDATAAGGREGATED = "clusterDataAggregated"
CLUSTERDATA = "clusterData"
CLUSTERDATAHOURLY = "clusterDataHourly"
CLUSTERDATAHOURLYAGGREGATED = "clusterDataHourlyAggregated"
PREAGGREGATED = "preAggregated"
AWSEC2INVENTORYMETRIC = "awsEc2InventoryMetric"
AWSEC2INVENTORY = "awsEc2Inventory"
AWSEBSINVENTORYMETRICS = "awsEbsInventoryMetrics"
AWSEBSINVENTORY = "awsEbsInventory"
AWSRDSINVENTORY = "awsRdsInventory"
UNIFIED = "unifiedTable"
AWSCURPREFIX = "awscur"
COSTAGGREGATED = "costAggregated"
CEINTERNALDATASET = "CE_INTERNAL"
GCPINSTANCEINVENTORY = "gcpInstanceInventory"
GCPDISKINVENTORY = "gcpDiskInventory"
CONNECTORDATASYNCSTATUSTABLE = "connectorDataSyncStatus"
GCPCONNECTORINFOTABLE = "gcpConnectorInfo"

def print_(message, severity="INFO"):
    # Set account id in the beginning of your CF call
    try:
        print(json.dumps({"accountId":ACCOUNTID_LOG, "severity":severity, "message": message}))
    except:
        print(message)

def create_dataset(client, datasetName, accountid=""):
    dataset_id = "{}.{}".format(client.project, datasetName)
    dataset = bigquery.Dataset(dataset_id)
    dataset.location = "US"
    # Do not change this format for description
    dataset.description = "Dataset for [ AccountId: %s ]" % (accountid)

    # Send the dataset to the API for creation, with an explicit timeout.
    # Raises google.api_core.exceptions.Conflict if the Dataset already
    # exists within the project.
    try:
        dataset = client.create_dataset(dataset, timeout=30)  # Make an API request.
        print_("Created dataset {}.{}".format(client.project, dataset.dataset_id))
    except Exception as e:
        print_("Dataset {} description {}, already exists {}".format(dataset_id, dataset.description, e), "WARN")
        if not dataset.description:
            # Do not change this format for description
            dataset.description = "Dataset for [ AccountId: %s ]" % (accountid)
            try:
                client.update_dataset(dataset, ["description"])
            except:
                pass


def if_tbl_exists(client, table_ref):
    try:
        client.get_table(table_ref)
        return True
    except NotFound:
        return False


def createTable(client, table_ref):
    tableName = table_ref.table_id
    print_("Creating %s table" % tableName)
    schema = []
    if tableName == CLUSTERDATA or tableName == CLUSTERDATAHOURLY:
        fieldset = bq_schema.clusterDataTableFields
        partition = bigquery.RangePartitioning(
            field="starttime",
            range_=bigquery.PartitionRange(start=1514745000000, end=1893436200000, interval=86400000)
        )
    elif tableName == CLUSTERDATAAGGREGATED or tableName == CLUSTERDATAHOURLYAGGREGATED:
        fieldset = bq_schema.clusterDataAggregatedFields
        partition = bigquery.RangePartitioning(
            field="starttime",
            range_=bigquery.PartitionRange(start=1514745000000, end=1893436200000, interval=86400000)
        )
    elif tableName == PREAGGREGATED:
        fieldset = bq_schema.preAggreagtedTableSchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="startTime"
        )
    elif tableName == AWSEC2INVENTORYMETRIC:
        fieldset = bq_schema.awsEc2InventoryCPUSchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="addedAt"
        )
    elif tableName.startswith(AWSEC2INVENTORY):
        fieldset = bq_schema.awsEc2InventorySchema
        partition = bigquery.RangePartitioning(
            range_=bigquery.PartitionRange(start=0, end=10000, interval=1),
            field="linkedAccountIdPartition"
        )
    elif tableName == AWSEBSINVENTORYMETRICS:
        fieldset = bq_schema.awsEbsInventoryMetricsSchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="addedAt"
        )
    elif tableName.startswith(AWSEBSINVENTORY):
        fieldset = bq_schema.awsEbsInventorySchema
        partition = bigquery.RangePartitioning(
            range_=bigquery.PartitionRange(start=0, end=10000, interval=1),
            field="linkedAccountIdPartition"
        )
    elif tableName.startswith(AWSRDSINVENTORY):
        fieldset = bq_schema.awsRdsInventorySchema
        partition = bigquery.RangePartitioning(
            range_=bigquery.PartitionRange(start=0, end=10000, interval=1),
            field="linkedAccountIdPartition"
        )
    elif tableName.startswith(AWSCURPREFIX):
        fieldset = bq_schema.aws_cur_table_schema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="usagestartdate"
        )
    elif tableName == UNIFIED:
        fieldset = bq_schema.unifiedTableTableSchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="startTime"  # name of column to use for partitioning
        )
    elif tableName == COSTAGGREGATED:
        fieldset = bq_schema.costAggregatedSchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="day"  # name of column to use for partitioning
        )
    elif tableName.startswith(GCPINSTANCEINVENTORY):
        fieldset = bq_schema.gcpInstanceInventorySchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="creationTime"
        )
    elif tableName.startswith(GCPDISKINVENTORY):
        fieldset = bq_schema.gcpDiskInventorySchema
        partition = bigquery.TimePartitioning(
            type_=bigquery.TimePartitioningType.DAY,
            field="creationTime"
        )

    for field in fieldset:
        if field.get("type") == "RECORD":
            nested_fields = []
            for nested_field in field["fields"]:
                if nested_field.get("type") == "RECORD":
                    inner_nested_fields = [bigquery.SchemaField(inner_nested_field["name"], inner_nested_field["type"],
                                                                mode=inner_nested_field.get("mode", "")) for inner_nested_field in nested_field["fields"]]
                    nested_fields.append(bigquery.SchemaField(nested_field["name"], nested_field["type"],
                                                              mode=nested_field["mode"], fields=inner_nested_fields))
                else:
                    nested_fields.append(bigquery.SchemaField(nested_field["name"], nested_field["type"], mode=nested_field.get("mode", "")))
            schema.append(bigquery.SchemaField(field["name"], field["type"], mode=field["mode"], fields=nested_fields))
        else:
            schema.append(bigquery.SchemaField(field["name"], field["type"], mode=field.get("mode", "")))
    if not schema:
        print_("Could not find any schema for table %s : %s" % (tableName, schema))
        return False
    table = bigquery.Table("%s.%s.%s" % (table_ref.project, table_ref.dataset_id, tableName), schema=schema)

    if tableName in [UNIFIED, PREAGGREGATED, AWSEC2INVENTORYMETRIC, AWSEBSINVENTORYMETRICS, COSTAGGREGATED] or \
            tableName.startswith(GCPINSTANCEINVENTORY) or tableName.startswith(GCPDISKINVENTORY) or \
            tableName.startswith(AWSCURPREFIX):
        table.time_partitioning = partition
    elif tableName.startswith(AWSEC2INVENTORY) or tableName.startswith(AWSEBSINVENTORY) or \
            tableName in [CLUSTERDATA, CLUSTERDATAAGGREGATED, CLUSTERDATAHOURLY, CLUSTERDATAHOURLYAGGREGATED]:
        table.range_partitioning = partition

    try:
        table = client.create_table(table)  # Make an API request.
        print_("Created table {}.{}.{}".format(table.project, table.dataset_id, table.table_id))
    except Exception as e:
        print_("Error while creating table\n {}".format(e), "WARN")
        return False
    return True


def run_batch_query(client, query, job_config, timeout=120):
    """
    Util method which runs a BQ query in batch mode.
    :param client:
    :param query:
    :param job_config:
    :param timeout:
    :return:
    """
    if not job_config:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
    elif isinstance(job_config, bigquery.QueryJobConfig):
        # Explicitly set BATCH priority
        job_config.priority = bigquery.QueryPriority.BATCH

    query_job = client.query(query, job_config=job_config)
    print_(query)
    try:
        print_(query_job.job_id)
        count = 0
        while True:
            query_job = client.get_job(
                query_job.job_id, location=query_job.location
            )
            print_("Job {} is currently in state {}".format(query_job.job_id, query_job.state))
            if query_job.state in ["DONE", "SUCCESS"] or count >= timeout/5: # 2 minutes
                err = query_job.error_result
                if err:
                    print_(err, "ERROR")
                break
            else:
                time.sleep(5)
                count += 1
        if query_job.state not in ["DONE", "SUCCESS"]:
            raise Exception("Timeout waiting for job in pending state")
    except Exception as e:
        print_(query, "ERROR")
        print_(e)

def update_connector_data_sync_status(jsonData, PROJECTID, client):
    query = """INSERT INTO `%s.%s.%s` (accountId, connectorId, lastSuccessfullExecutionAt, jobType, cloudProviderId) 
                VALUES ('%s', '%s', '%s', '%s', '%s')
            """ % ( PROJECTID, CEINTERNALDATASET, CONNECTORDATASYNCSTATUSTABLE,
                    jsonData["accountId"], jsonData["connectorId"], datetime.datetime.utcnow(), 'cloudfunction', jsonData['cloudProvider']
    )

    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_("  Failed to update connector data sync status", "WARN")
        raise e
