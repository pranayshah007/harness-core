# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import base64
import datetime
import json
import os
import util
import re
from util import create_dataset, print_, if_tbl_exists, createTable, run_batch_query, COSTAGGREGATED, UNIFIED, \
    CEINTERNALDATASET, update_connector_data_sync_status, GCPCONNECTORINFOTABLE
from google.cloud import bigquery
from google.cloud import secretmanager
from google.oauth2 import service_account
from google.auth import impersonated_credentials
from google.cloud import bigquery_datatransfer_v1
from google.cloud import pubsub_v1
from google.api_core.exceptions import BadRequest

"""
This is the event when batch ingests:
{
    "accountId": "ustest_gcp_ng",
    "serviceAccount": "harness-ce-harness-kmpys@ccm-play.iam.gserviceaccount.com",
    "sourceGcpProjectId": "ce-qa-274307",
    "sourceDataSetId": "BillingReport_zeaak_fls425ieo7olzmug",
    "sourceDataSetRegion": "eu",
    "connectorId": "1234",
    "sourceGcpTableName": "gcp_billing_export_v1_01E207_52C4CA_2CF8E2"
}

Below is the event when BQ Data Transfer finishes for non us regions
{
	'dataSourceId': 'cross_region_copy',
	'destinationDatasetId': 'BillingReport_ustest_gcp_ng',
	'emailPreferences': {},
	'endTime': '2021-08-11T18:57:24.670486Z',
	'errorStatus': {},
	'name': 'projects/199539700734/locations/us/transferConfigs/615b6cc0-0000-2807-aed0-001a1143233a/runs/615b6cc2-0000-2807-aed0-001a1143233a',
	'notificationPubsubTopic': 'projects/ccm-play/topics/ce-gcp-billing-cf',
	'params': {
		'overwrite_destination_table': True,
		'source_dataset_id': 'BillingReport_zeaak_fls425ieo7olzmug',
		'source_project_id': 'ce-qa-274307'
	},
	'runTime': '2021-08-11T18:54:54.253699Z',
	'scheduleTime': '2021-08-11T18:54:54.312522Z',
	'state': 'SUCCEEDED',
	'updateTime': '2021-08-11T18:57:24.670501Z',
	'userId': '6106063769767823872'
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
KEY = "CCM_GCP_CREDENTIALS"
client = bigquery.Client(PROJECTID)
dt_client = bigquery_datatransfer_v1.DataTransferServiceClient()
publisher = pubsub_v1.PublisherClient()
GCPCFTOPIC = publisher.topic_path(PROJECTID, os.environ.get('GCPCFTOPIC', 'ce-gcp-billing-cf'))


def main(event, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    jsonData = json.loads(data)
    jsonData["cloudProvider"] = "GCP"
    print(jsonData)
    if jsonData.get("dataSourceId") == "cross_region_copy":
        # Event is from BQ DT service. Ingest in unified and preaggregated
        jsonData["datasetName"] = jsonData["destinationDatasetId"]
        fetch_acc_from_gcp_conn_info(jsonData)
        util.ACCOUNTID_LOG = jsonData["destinationDatasetId"].split("BillingReport_")[-1]
        get_unique_billingaccount_id(jsonData)
        jsonData["isFreshSync"] = isFreshSync(jsonData)
        if jsonData.get("isFreshSync"):
            jsonData["interval"] = '180'
        else:
            jsonData["interval"] = '3'
        ingest_into_preaggregated(jsonData)
        ingest_into_unified(jsonData)
        update_connector_data_sync_status(jsonData, PROJECTID, client)
        ingest_data_to_costagg(jsonData)
        return
    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    jsonData["tableName"] = "gcp_billing_export_%s" % jsonData["connectorId"]
    create_dataset(client, jsonData["datasetName"], jsonData.get("accountId"))
    dataset = client.dataset(jsonData["datasetName"])
    preAggragatedTableRef = dataset.table("preAggregated")
    preAggregatedTableTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "preAggregated")
    unifiedTableRef = dataset.table("unifiedTable")
    unifiedTableTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], "unifiedTable")

    if not if_tbl_exists(client, unifiedTableRef):
        print_("%s table does not exists, creating table..." % unifiedTableRef)
        createTable(client, unifiedTableRef)
    else:
        print_("%s table exists" % unifiedTableTableName)

    if not if_tbl_exists(client, preAggragatedTableRef):
        print_("%s table does not exists, creating table..." % preAggragatedTableRef)
        createTable(client, preAggragatedTableRef)
    else:
        print_("%s table exists" % preAggregatedTableTableName)

    get_impersonated_credentials(jsonData)

    # Sync dataset
    jsonData["isFreshSync"] = isFreshSync(jsonData)
    syncDataset(jsonData)
    print_("Completed")
    return


def get_impersonated_credentials(jsonData):
    # Get source credentials
    target_scopes = [
        'https://www.googleapis.com/auth/cloud-platform']
    json_acct_info = json.loads(get_secret_key())
    credentials = service_account.Credentials.from_service_account_info(json_acct_info)
    source_credentials = credentials.with_scopes(target_scopes)

    # Impersonate to target credentials
    target_credentials = impersonated_credentials.Credentials(
        source_credentials=source_credentials,
        target_principal=jsonData["serviceAccount"],
        target_scopes=target_scopes,
        lifetime=500)
    jsonData["credentials"] = target_credentials
    print_("source: %s, target: %s" % (target_credentials._source_credentials.service_account_email,
                                       target_credentials.service_account_email))


def get_secret_key():
    client = secretmanager.SecretManagerServiceClient()
    request = {"name": f"projects/{PROJECTID}/secrets/{KEY}/versions/latest"}
    response = client.access_secret_version(request)
    secret_string = response.payload.data.decode("UTF-8")
    return secret_string


def isFreshSync(jsonData):
    print_("Determining if we need to do fresh sync")
    if jsonData.get("dataSourceId"):
        # Check in preaggregated table for non US regions
        query = """  SELECT count(*) as count from %s.%s.preAggregated
                   WHERE starttime >= DATETIME_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY) AND cloudProvider = "GCP" ;
                   """ % (PROJECTID, jsonData["datasetName"])
    else:
        # Only applicable for US regions
        query = """  SELECT count(*) as count from %s.%s.%s
                   WHERE DATE(_PARTITIONTIME) >= DATE_SUB(CURRENT_DATE(), INTERVAL 10 DAY) AND usage_start_time >= DATETIME_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY);
                   """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            print_("  Number of records existing over past 3 days : %s" % (row["count"]))
            if row["count"] > 0:
                return False
            else:
                return True
    except Exception as e:
        # Table does not exist
        print_(e)
        print_("  Fresh sync is needed")
        return True


def syncDataset(jsonData):
    if jsonData["sourceDataSetRegion"].lower() != "us":
        doBQTransfer(jsonData)
        return
    print_("Loading into %s" % jsonData["tableName"])
    # for US region
    destination = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    if jsonData["isFreshSync"]:
        # Fresh sync. Sync only for 45 days.
        query = """  SELECT * FROM `%s.%s.%s` WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL 187 DAY) AND DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 187 DAY);
        """ % (jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"])
        # Configure the query job.
        print_(" Destination :%s" % destination)
        job_config = bigquery.QueryJobConfig(
            destination=destination,
            write_disposition=bigquery.job.WriteDisposition.WRITE_TRUNCATE,
            time_partitioning=bigquery.table.TimePartitioning(),
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
    else:
        # Sync past 3 days only. Specify columns here explicitely.
        query = """  DELETE FROM `%s` 
                WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL 10 DAY) and DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY); 
            INSERT INTO `%s` (billing_account_id,service,sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info)
                SELECT billing_account_id,service,sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info 
                FROM `%s.%s.%s`
                WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL 10 DAY) AND DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY);
        """ % (destination, destination,
               jsonData["sourceGcpProjectId"], jsonData["sourceDataSetId"], jsonData["sourceGcpTableName"])

        # Configure the query job.
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )

    imclient = bigquery.Client(credentials=jsonData["credentials"], project=PROJECTID)
    query_job = imclient.query(query, job_config=job_config)
    try:
        print_(query)
        print_(query_job.job_id)
        query_job.result()
    except BadRequest as e:
        print_(e)
        # Try doing fresh sync here. Mostly it is schema mismatch
        if jsonData.get("syncRetried"):
            print_(query)
            raise e
        jsonData["isFreshSync"] = True
        jsonData["syncRetried"] = True
        print_("Retrying with fresh sync")
        syncDataset(jsonData)
    except Exception as e:
        print_(query)
        raise e
    print_("  Loaded in %s" % jsonData["tableName"])

    if jsonData.get("isFreshSync"):
        jsonData["interval"] = '180'
    else:
        jsonData["interval"] = '3'
    get_unique_billingaccount_id(jsonData)
    ingest_into_preaggregated(jsonData)
    ingest_into_unified(jsonData)
    update_connector_data_sync_status(jsonData, PROJECTID, client)
    ingest_data_to_costagg(jsonData)


def doBQTransfer(jsonData):
    print_("Doing bq data transfer operation as the region is not 'US'")
    dtDisplayName = "ccm_gcp_dataset_copy_%s_%s" % (jsonData["accountId"], jsonData["connectorId"])
    # Get the full path to your project.
    parent = dt_client.common_project_path(PROJECTID)
    print_("  Supported Data Sources in US region: %s, dt job display name: %s" % (parent, dtDisplayName))

    # Iterate over all possible data transfer configs.
    # This only lists configs where destination dataset in is US.
    # https://github.com/googleapis/google-cloud-python/issues/8466
    for data_source in dt_client.list_transfer_configs(parent=parent):
        if data_source.data_source_id == "cross_region_copy":
            if data_source.display_name == dtDisplayName:
                print_(data_source.name)
                jsonData["dtName"] = data_source.name
                print_("  Existing data transfer name: %s, display name: %s" % (jsonData["dtName"], dtDisplayName))
                break

    imdt_client = bigquery_datatransfer_v1.DataTransferServiceClient(credentials=jsonData["credentials"])
    if jsonData.get("dtName") == None:
        # Fresh sync
        print_("  Creating transfer config for the first time")
        transfer_config = bigquery_datatransfer_v1.TransferConfig(
            destination_dataset_id=jsonData["datasetName"],
            display_name=dtDisplayName,
            data_source_id="cross_region_copy",
            schedule_options={
                "disable_auto_scheduling": True
            },
            notification_pubsub_topic=GCPCFTOPIC,
            params={
                "overwrite_destination_table": True,
                "source_project_id": jsonData["sourceGcpProjectId"],
                "source_dataset_id": jsonData["sourceDataSetId"]
            }
        )
        #print_(transfer_config)
        resp = imdt_client.create_transfer_config(transfer_config=transfer_config, parent=parent)
        print_("  Created transfer config %s" % resp)
        jsonData["dtName"] = resp.name
        update_datatransfer_job_config(jsonData)
    # Trigger manual transfer run
    print_("  Triggering manual transfer run")
    now = datetime.datetime.now(datetime.timezone.utc)
    request = bigquery_datatransfer_v1.types.StartManualTransferRunsRequest(
        {"parent": jsonData["dtName"], "requested_run_time": now})
    print_(imdt_client.start_manual_transfer_runs(request))
    print_("  Triggered manual transfer run")


def ingest_into_unified(jsonData):
    print_("Loading into unifiedTable table...")
    query = """  DELETE FROM `%s.unifiedTable` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP" 
                AND gcpBillingAccountId IN (%s);
           INSERT INTO `%s.unifiedTable` (product, cost, gcpProduct,gcpSkuId,gcpSkuDescription, startTime, gcpProjectId,
                region,zone,gcpBillingAccountId,cloudProvider, discount, labels)
                SELECT service.description AS product, cost AS cost, service.description AS gcpProduct, sku.id AS gcpSkuId,
                     sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId,
                     location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, (SELECT SUM(c.amount) FROM UNNEST(credits) c) as discount, labels AS labels
                FROM `%s.%s`
                WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) <= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL 1 DAY) AND
                     DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) ;
        """ % ( jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"], jsonData["datasetName"], jsonData["datasetName"],
                jsonData["tableName"], str(int(jsonData["interval"])+7), jsonData["interval"])

    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    query_job = client.query(query, job_config=job_config)
    print_(query)
    try:
        print_(query_job.job_id)
        query_job.result()
    except Exception as e:
        print_(query)
        raise e
    print_("  Loaded into unifiedTable table.")


def ingest_into_preaggregated(jsonData):
    print_("Loading into preaggregated table...")
    query = """  DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP"
                AND gcpBillingAccountId IN (%s);
           INSERT INTO `%s.preAggregated` (cost, gcpProduct,gcpSkuId,gcpSkuDescription,
             startTime,gcpProjectId,region,zone,gcpBillingAccountId,cloudProvider, discount) SELECT SUM(cost) AS cost, service.description AS gcpProduct,
             sku.id AS gcpSkuId, sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId,
             location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, SUM(IFNULL((SELECT SUM(c.amount) FROM UNNEST(credits) c), 0)) as discount
           FROM `%s.%s`
           WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) <= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL 1 DAY) AND
             DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY)
           GROUP BY service.description, sku.id, sku.description, startTime, project.id, location.region, location.zone, billing_account_id;
        """ % (jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"], jsonData["datasetName"], jsonData["datasetName"],
        jsonData["tableName"], str(int(jsonData["interval"])+7), jsonData["interval"])

    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    query_job = client.query(query, job_config=job_config)
    print_(query)
    try:
        print_(query_job.job_id)
        query_job.result()
    except Exception as e:
        print_(query)
        raise e
    print_("  Loaded into preAggregated table.")


def get_unique_billingaccount_id(jsonData):
    # Get unique billingAccountIds from main gcp table
    print_("Getting unique billingAccountIds from %s" % jsonData["tableName"])
    query = """  SELECT DISTINCT(billing_account_id) as billing_account_id FROM `%s.%s.%s` 
            WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL 10 DAY);
            """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    # Configure the query job.
    job_config = bigquery.QueryJobConfig(   
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    try:
        print_(query)
        query_job = client.query(query, job_config=job_config)
        results = query_job.result()  # wait for job to complete
        billingAccountIds = []
        for row in results:
            billingAccountIds.append(row.billing_account_id)
        jsonData["billingAccountIds"] = ", ".join(f"'{w}'" for w in billingAccountIds)
    except Exception as e:
        print_(query)
        print_("  Failed to retrieve distinct billingAccountIds", "WARN")
        jsonData["billingAccountIds"] = ""
        raise e
    print_("  Found unique billingAccountIds %s" % jsonData.get("billingAccountIds"))

def ingest_data_to_costagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE(day) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = 'GCP' AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId)
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "GCP" AS cloudProvider, '%s' as accountId
                FROM `%s`  
                WHERE DATE(startTime) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) and cloudProvider = "GCP" 
                GROUP BY day;
     """ % (table_name, jsonData["interval"], jsonData.get("accountId"), table_name, jsonData.get("accountId"), source_table, jsonData["interval"])

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH,
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )

    run_batch_query(client, query, job_config, timeout=180)

def update_datatransfer_job_config(jsonData):
    query = """INSERT INTO `%s.%s.%s` (accountId, connectorId, dataTransferConfig, createdAt, sourceGcpTableName) 
                VALUES ('%s', '%s', '%s', '%s', '%s')
            """ % ( PROJECTID, CEINTERNALDATASET, GCPCONNECTORINFOTABLE,
                    jsonData["accountId"], jsonData["connectorId"], jsonData["dtName"], datetime.datetime.utcnow(), jsonData["sourceGcpTableName"])

    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_("  Failed to update connector info", "WARN")
        raise e

def fetch_acc_from_gcp_conn_info(jsonData):
    # 	'name': 'projects/199539700734/locations/us/transferConfigs/615b6cc0-0000-2807-aed0-001a1143233a/runs/615b6cc2-0000-2807-aed0-001a1143233a',
    dt_name = "/".join(jsonData["name"].split("/")[:-2])
    query = """SELECT accountId, connectorId, sourceGcpTableName FROM `%s.%s.%s` 
                WHERE dataTransferConfig='%s';
            """ % ( PROJECTID, CEINTERNALDATASET, GCPCONNECTORINFOTABLE,
                    dt_name)

    try:
        print_(query)
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["accountId"] = row.accountId
            jsonData["connectorId"] = row.connectorId
            jsonData["sourceGcpTableName"] = row.sourceGcpTableName
            jsonData["tableName"] = row.sourceGcpTableName
            break
    except Exception as e:
        raise e
    print_("retrieved info from gcpConnectrInfoTable")