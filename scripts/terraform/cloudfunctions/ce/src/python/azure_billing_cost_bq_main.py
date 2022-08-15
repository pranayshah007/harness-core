# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import base64
import os
from google.cloud import bigquery
import bq_schema
from util import run_batch_query, createTable

"""
Event format:
{
    "tableId": "ccm-play.BillingReport_efofrubhttupezjuqxlhbg.azureBilling_2020_12_HarnessIncHarnessQA",
    "azure_column_mapping": {
        "azureSubscriptionGuid": "subscriptionguid",
        "azureResourceGroup": "resourcegroup",
        "startTime": "usagedatetime",
        "azureResourceRate": "resourcerate",
        "cost": "pretaxcost",
        "azureInstanceId": "instanceid"
    }
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
AZURE_BILLING_TABLE_COLUMNS = []


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

    fetch_existing_columns_from_azure_billing_table(jsonData["tableId"].split('.')[1], jsonData["tableId"].split('.')[-1])

    azure_cost_table_id = jsonData["tableId"].replace("azureBilling_", "azurecost_")

    create_empty_azure_cost_table(azure_cost_table_id)
    insert_data_into_azure_cost_table(azure_cost_table_id, jsonData["tableId"], jsonData["azure_column_mapping"])


def insert_data_into_azure_cost_table(azure_cost_table_id, azure_billing_table_id, azure_column_mapping):
    query = create_insert_query(azure_cost_table_id, azure_billing_table_id, azure_column_mapping)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)


def update_query_strings(azure_cost_table_column, azure_billing_table_column, insert_query, select_query):
    if (not AZURE_BILLING_TABLE_COLUMNS) or (azure_billing_table_column in AZURE_BILLING_TABLE_COLUMNS):
        insert_query += (", " if insert_query else "")
        insert_query += azure_cost_table_column

        select_query += (", " if select_query else "")
        select_query += f"{azure_billing_table_column} as {azure_cost_table_column}"
    return insert_query, select_query


def fetch_existing_columns_from_azure_billing_table(dataset_name, table_name):
    global AZURE_BILLING_TABLE_COLUMNS
    ds = "%s.%s" % (PROJECTID, dataset_name)
    query = """SELECT column_name
            FROM %s.INFORMATION_SCHEMA.COLUMNS
            WHERE table_name="%s";
            """ % (ds, table_name)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        AZURE_BILLING_TABLE_COLUMNS = set()
        for row in results:
            AZURE_BILLING_TABLE_COLUMNS.add(row.column_name.lower())
    except Exception as e:
        print_("Failed to retrieve available columns", "WARN")
    AZURE_BILLING_TABLE_COLUMNS = list(AZURE_BILLING_TABLE_COLUMNS)


def create_insert_query(azure_cost_table_id, azure_billing_table_id, azure_column_mapping):
    # form query strings
    select_columns_string = ""
    insert_columns_string = ""
    for column_dict in bq_schema.azure_cost_table_schema:
        column = column_dict["name"]
        if column in azure_column_mapping:
            azure_billing_table_column = azure_column_mapping[column]
            print(f"Column {column} is mapped to {azure_billing_table_column}, searching for {azure_billing_table_column} in {azure_billing_table_id} table.")
            insert_columns_string, select_columns_string = update_query_strings(column, azure_billing_table_column, insert_columns_string, select_columns_string)
        else:
            print(f"No mapping found in pubsub message for column: {column}. Will search for {column.lower()} in {azure_billing_table_id} table.")
            azure_billing_table_column = column.lower()
            insert_columns_string, select_columns_string = update_query_strings(column, azure_billing_table_column, insert_columns_string, select_columns_string)

    return """INSERT INTO `%s` (%s)
            SELECT %s FROM `%s`""" % (azure_cost_table_id, insert_columns_string, select_columns_string, azure_billing_table_id)


def create_empty_azure_cost_table(table_id):
    # delete table if exists already, and create new table using desired schema
    client.delete_table(table_id, not_found_ok=True)

    dataset = client.dataset(table_id.split('.')[1])
    azureCostTableRef = dataset.table(table_id.split('.')[-1])
    createTable(client, azureCostTableRef)
