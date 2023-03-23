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
import requests
from util import create_dataset, print_, if_tbl_exists, createTable, run_batch_query, COSTAGGREGATED, UNIFIED, \
    CEINTERNALDATASET, update_connector_data_sync_status, GCPCONNECTORINFOTABLE, CURRENCYCONVERSIONFACTORUSERINPUT, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES, send_event
from calendar import monthrange
from google.cloud import bigquery
from google.cloud import secretmanager
from google.oauth2 import service_account
from google.auth import impersonated_credentials, default
from google.cloud import bigquery_datatransfer_v1
from google.cloud import pubsub_v1
from google.cloud import functions_v2
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
    "sourceGcpTableName": "gcp_billing_export_v1_01E207_52C4CA_2CF8E2",
    "triggerHistoricalCostUpdateInPreferredCurrency": False,
    "deployMode": "ONPREM" # "SAAS",
    "useWorkloadIdentity": "False"
}

Below is the event we send from batch for triggering historical update if required.
{
    "accountId": "ustest_gcp_ng",
    "triggerHistoricalCostUpdateInPreferredCurrency": "True"
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
COSTCATEGORIESUPDATETOPIC = os.environ.get('COSTCATEGORIESUPDATETOPIC', 'ccm-bigquery-batch-update')
GCPCFTOPIC = publisher.topic_path(PROJECTID, os.environ.get('GCPCFTOPIC', 'ce-gcp-billing-cf'))
GCP_STANDARD_EXPORT_COLUMNS = ["billing_account_id", "usage_start_time", "usage_end_time", "export_time",
                               "cost", "currency", "currency_conversion_rate", "cost_type", "labels",
                               "system_labels", "credits", "usage", "invoice", "adjustment_info",
                               "service", "sku", "project", "location"]
GCP_DETAILED_EXPORT_COLUMNS = ["billing_account_id", "usage_start_time", "usage_end_time", "export_time",
                               "cost", "currency", "currency_conversion_rate", "cost_type", "labels",
                               "system_labels", "credits", "usage", "invoice", "adjustment_info",
                               "service", "sku", "project", "location", "resource"]


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
    jsonData["gcpBillingExportTablePartitionColumnName"] = "_PARTITIONTIME"
    if jsonData.get("dataSourceId") == "cross_region_copy":
        # Event is from BQ DT service. Ingest in gcp_cost_export, unified and preaggregated
        jsonData["datasetName"] = jsonData["destinationDatasetId"]
        get_preferred_currency(jsonData)
        fetch_acc_from_gcp_conn_info(jsonData)
        util.ACCOUNTID_LOG = jsonData["destinationDatasetId"].split("BillingReport_")[-1]

        # obtain/set partition column from the gcp_billing_export table to be used for all subsequent queries
        gcp_billing_export_bq_table_name = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
        try:
            gcp_billing_export_bq_table = client.get_table(gcp_billing_export_bq_table_name)
            jsonData[
                "gcpBillingExportTablePartitionColumnName"] = "_PARTITIONTIME" if gcp_billing_export_bq_table.time_partitioning.field is None else gcp_billing_export_bq_table.time_partitioning.field
        except:
            # table doesn't exist yet, use usage_start_time as partition column for new table
            # however, for cross_region_copy, table will always exist
            jsonData["gcpBillingExportTablePartitionColumnName"] = "usage_start_time"
        print_(f"Partition column for gcp_billing_export table: {jsonData['gcpBillingExportTablePartitionColumnName']}")

        get_unique_billingaccount_id(jsonData)
        jsonData["isFreshSync"] = isFreshSync(jsonData)
        if jsonData.get("isFreshSync"):
            jsonData["interval"] = '180'
        elif jsonData["ccmPreferredCurrency"]:
            jsonData["interval"] = str(datetime.datetime.utcnow().date().day - 1)
        else:
            jsonData["interval"] = '3'

        # currency specific methods
        insert_currencies_with_unit_conversion_factors_in_bq(jsonData)
        initialize_fx_rates_dict(jsonData)
        fetch_default_conversion_factors_from_API(jsonData)
        fetch_default_conversion_factors_from_billing_export(jsonData)
        fetch_custom_conversion_factors(jsonData)
        verify_existence_of_required_conversion_factors(jsonData)
        update_fx_rate_column_in_raw_table(jsonData)

        ingest_into_gcp_cost_export_table(jsonData)
        ingest_into_preaggregated(jsonData)
        ingest_into_unified(jsonData)
        update_connector_data_sync_status(jsonData, PROJECTID, client)
        ingest_data_to_costagg(jsonData)
        send_event(publisher.topic_path(PROJECTID, COSTCATEGORIESUPDATETOPIC), {
            "eventType": "COST_CATEGORY_UPDATE",
            "message": {
                "accountId": jsonData["accountId"],
                "startDate": "%s" % (datetime.datetime.today() - datetime.timedelta(days=int(jsonData["interval"]))).date(),
                "endDate": "%s" % datetime.datetime.today().date(),
                "cloudProvider": "GCP",
                "cloudProviderAccountIds": jsonData["billingAccountIdsList"]
            }
        })
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
    currencyConversionFactorUserInputTableRef = dataset.table(CURRENCYCONVERSIONFACTORUSERINPUT)
    currencyConversionFactorUserInputTableName = "%s.%s.%s" % (
        PROJECTID, jsonData["datasetName"], CURRENCYCONVERSIONFACTORUSERINPUT)

    if not if_tbl_exists(client, unifiedTableRef):
        print_("%s table does not exists, creating table..." % unifiedTableRef)
        createTable(client, unifiedTableRef)
    else:
        # alter_unified_table(jsonData)
        print_("%s table exists" % unifiedTableTableName)

    if not if_tbl_exists(client, preAggragatedTableRef):
        print_("%s table does not exists, creating table..." % preAggragatedTableRef)
        createTable(client, preAggragatedTableRef)
    else:
        print_("%s table exists" % preAggregatedTableTableName)

    if not if_tbl_exists(client, currencyConversionFactorUserInputTableRef):
        print_("%s table does not exists, creating table..." % currencyConversionFactorUserInputTableRef)
        createTable(client, currencyConversionFactorUserInputTableRef)
    else:
        print_("%s table exists" % currencyConversionFactorUserInputTableName)

    ds = f"{PROJECTID}.{jsonData['datasetName']}"
    table_ids = ["%s.%s" % (ds, "unifiedTable"),
                 "%s.%s" % (ds, "preAggregated")]
    # will be altering gcp_cost_export table after gcp_billing_export is created since exact table name might vary
    add_currency_preferences_columns_to_schema(client, table_ids)

    get_preferred_currency(jsonData)
    if jsonData.get("triggerHistoricalCostUpdateInPreferredCurrency") == "True" and jsonData["ccmPreferredCurrency"]:
        # trigger historical CF and exit
        trigger_historical_cost_update_in_preferred_currency(jsonData)
        return
    elif jsonData.get("triggerHistoricalCostUpdateInPreferredCurrency") == "True":
        # no historical costs to update since currency is not set. exit.
        return

    get_impersonated_credentials(jsonData)

    # obtain/set partition column from the gcp_billing_export table to be used for all subsequent queries
    gcp_billing_export_bq_table_name = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    try:
        gcp_billing_export_bq_table = client.get_table(gcp_billing_export_bq_table_name)
        jsonData[
            "gcpBillingExportTablePartitionColumnName"] = "_PARTITIONTIME" if gcp_billing_export_bq_table.time_partitioning.field is None else gcp_billing_export_bq_table.time_partitioning.field
    except:
        # table doesn't exist yet, use usage_start_time as partition column for new table
        jsonData["gcpBillingExportTablePartitionColumnName"] = "usage_start_time"
    print_(f"Partition column for gcp_billing_export table: {jsonData['gcpBillingExportTablePartitionColumnName']}")

    # Sync dataset
    jsonData["isFreshSync"] = isFreshSync(jsonData)
    syncDataset(jsonData)
    send_event(publisher.topic_path(PROJECTID, COSTCATEGORIESUPDATETOPIC), {
        "eventType": "COST_CATEGORY_UPDATE",
        "message": {
            "accountId": jsonData["accountId"],
            "startDate": "%s" % (datetime.datetime.today() - datetime.timedelta(days=int(jsonData["interval"]))).date(),
            "endDate": "%s" % datetime.datetime.today().date(),
            "cloudProvider": "GCP",
            "cloudProviderAccountIds": jsonData["billingAccountIdsList"]
        }
    })
    print_("Completed")
    return


def trigger_historical_cost_update_in_preferred_currency(jsonData):
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year
    if "disableHistoricalUpdateForMonths" not in jsonData or not jsonData["disableHistoricalUpdateForMonths"]:
        jsonData["disableHistoricalUpdateForMonths"] = [f"{currentYear}-{currentMonth}-01"]

    # get months for which historical update needs to be triggered, and custom conversion factors
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """SELECT month, conversionType, sourceCurrency, destinationCurrency, conversionFactor
                FROM `%s.%s`
                WHERE accountId="%s" AND destinationCurrency is NOT NULL AND isHistoricalUpdateRequired = TRUE
                AND cloudServiceProvider = "GCP"
                AND month NOT IN (%s);
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT, jsonData.get("accountId"),
                       ", ".join(f"DATE('{month}')" for month in jsonData["disableHistoricalUpdateForMonths"]))
    print_(query)
    historical_update_months = set()
    custom_factors_dict = {}
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            historical_update_months.add(str(row.month))
            if row.conversionType == "CUSTOM":
                custom_factors_dict[row.sourceCurrency] = float(row.conversionFactor)
    except Exception as e:
        print_(e)
        print_("Failed to fetch historical-update months for account", "WARN")

    # unset historicalUpdate flag for months in disableHistoricalUpdateForMonths
    query = """UPDATE `%s.%s` 
                SET isHistoricalUpdateRequired = FALSE
                WHERE cloudServiceProvider = "GCP" 
                AND month in (%s)
                AND accountId = '%s';
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                       ", ".join(f"DATE('{month}')" for month in jsonData["disableHistoricalUpdateForMonths"]),
                       jsonData.get("accountId"))
    print_(query)
    try:
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to unset isHistoricalUpdateRequired flags in CURRENCYCONVERSIONFACTORUSERINPUT table", "WARN")
        # updates on table are disallowed after streaming insert from currency APIs. retry in next run.
        return

    # trigger historical update CF if required
    if list(historical_update_months):
        trigger_payload = {
            "accountId": jsonData.get("accountId"),
            "cloudServiceProvider": "GCP",
            "months": list(historical_update_months),
            "userInputFxRates": custom_factors_dict
        }
        url = get_cf_v2_uri(
            f"projects/{PROJECTID}/locations/us-central1/functions/ce-gcp-historical-currency-update-bq-terraform")
        try:
            # Set up metadata server request
            # See https://cloud.google.com/compute/docs/instances/verifying-instance-identity#request_signature
            metadata_server_token_url = 'http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience='
            token_request_url = metadata_server_token_url + url
            token_request_headers = {'Metadata-Flavor': 'Google'}

            # Fetch the token
            token_response = requests.get(token_request_url, headers=token_request_headers)
            jwt = token_response.content.decode("utf-8")

            # Provide the token in the request to the receiving function
            receiving_function_headers = {'Authorization': f'bearer {jwt}'}
            r = requests.post(url, json=trigger_payload, timeout=30, headers=receiving_function_headers)
        except Exception as e:
            print_("Post-request timeout reached when triggering historical update CF.")
            pass


def get_cf_v2_uri(cf_name):
    functions_v2_client = functions_v2.FunctionServiceClient()
    request = functions_v2.GetFunctionRequest(
        name=cf_name
    )
    response = functions_v2_client.get_function(request=request)
    return response.service_config.uri


def get_preferred_currency(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    jsonData["ccmPreferredCurrency"] = None
    query = """SELECT destinationCurrency
                FROM `%s.%s` where destinationCurrency is NOT NULL LIMIT 1;
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT)
    try:
        print_(query)
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["ccmPreferredCurrency"] = row.destinationCurrency.upper()
            print_("Found preferred-currency for account: %s" % (jsonData["ccmPreferredCurrency"]))
            break
    except Exception as e:
        print_(e)
        print_("Failed to fetch preferred currency for account", "WARN")

    if not jsonData["ccmPreferredCurrency"]:
        print_("No preferred-currency found for account")


def insert_currencies_with_unit_conversion_factors_in_bq(jsonData):
    # we are inserting these rows for showing active month's source_currencies to user
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year

    # update 1.0 rows in currencyConversionFactorDefault table only for current month
    date_start = "%s-%s-01" % (currentYear, currentMonth)
    date_end = "%s-%s-%s" % (currentYear, currentMonth, monthrange(int(currentYear), int(currentMonth))[1])

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId = '%s' AND cloudServiceProvider = "GCP" AND sourceCurrency = destinationCurrency 
                AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month < DATE('%s');
               INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
               (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
               conversionFactor,month,conversionSource,createdAt,updatedAt)
                   SELECT distinct 
                   '%s' as accountId,
                   "GCP" as cloudServiceProvider,
                   currency as sourceCurrency,
                   currency as destinationCurrency,
                   1.0 as conversionFactor,
                   DATE('%s') as month,
                   "BILLING_EXPORT_SRC_CCY" as conversionSource,
                   TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
                   FROM `%s.%s.%s`
                   WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY)  
                   AND DATE(usage_start_time) >= '%s' and DATE(usage_start_time) <= '%s'
                   AND currency not in (select distinct sourceCurrency FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                   WHERE accountId = '%s' AND cloudServiceProvider = "GCP" AND sourceCurrency = destinationCurrency 
                   AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month = DATE('%s'));
    """ % (PROJECTID,
           jsonData.get("accountId"),
           date_start,
           PROJECTID,
           jsonData.get("accountId"),
           date_start,
           current_timestamp, current_timestamp,
           PROJECTID, jsonData["datasetName"], jsonData["tableName"],
           jsonData["gcpBillingExportTablePartitionColumnName"],
           str(datetime.datetime.utcnow().date().day - 1),
           date_start, date_end,
           PROJECTID,
           jsonData.get("accountId"),
           date_start)
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    print_(query)
    query_job = client.query(query, job_config=job_config)
    try:
        query_job.result()
    except Exception as e:
        print_(e)
        print_(f"Failed to execute query: {query}", "WARN")
        # raise e


def initialize_fx_rates_dict(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return
    jsonData["fx_rates_srcCcy_to_destCcy"] = {}

    query = """SELECT distinct DATE_TRUNC(DATE(usage_start_time), month) as billing_month, currency
                FROM `%s.%s.%s` 
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
                """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                       jsonData["gcpBillingExportTablePartitionColumnName"],
                       str(int(jsonData["interval"]) + 7),
                       jsonData["interval"])
    try:
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = client.query(query, job_config=job_config)
        results = query_job.result()  # wait for job to complete
        for row in results:
            if str(row.billing_month) not in jsonData["fx_rates_srcCcy_to_destCcy"]:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.billing_month)] = {row.currency.upper(): None}
            else:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.billing_month)][row.currency.upper()] = None
    except Exception as e:
        print_(e)
        print_("Failed to list distinct GCP source-currencies for account", "WARN")


def fetch_default_conversion_factors_from_API(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year
    date_start = "%s-%s-01" % (currentYear, currentMonth)
    date_end = "%s-%s-%s" % (currentYear, currentMonth, monthrange(int(currentYear), int(currentMonth))[1])
    current_fx_rates_from_api = None

    for billing_month in jsonData["fx_rates_srcCcy_to_destCcy"]:
        # fetch conversion factors from the API for all sourceCurrencies vs USD and destCcy vs USD for that DATE.
        # cdn.jsdelivr.net api returns currency-codes in lowercase
        print_(f"Hitting fxRate API for the month: {billing_month}")
        try:
            response = requests.get(
                f"https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/{billing_month}/currencies/usd.json")
            fx_rates_from_api = response.json()
            if billing_month == date_start:
                current_fx_rates_from_api = response.json()
        except Exception as e:
            print_(e)
            print_("fxRate API failed. Using backup fx_rates.", "WARN")
            fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[date_start]
            if billing_month == date_start:
                current_fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[date_start]

        for srcCurrency in fx_rates_from_api["usd"]:
            if srcCurrency.upper() not in CURRENCY_LIST:
                continue
            # ensure precision of fx rates while performing operations
            try:
                # 1 usd = x src
                # 1 usd = y dest
                # 1 src = (y/x) dest
                if srcCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month]:
                    jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][srcCurrency.upper()] = \
                        fx_rates_from_api["usd"][jsonData["ccmPreferredCurrency"].lower()] / fx_rates_from_api["usd"][
                            srcCurrency]
            except Exception as e:
                print_(e, "WARN")
                print_(f"fxRate for {srcCurrency} to {jsonData['ccmPreferredCurrency']} was not found in API response.")

    # update currencyConversionFactorDefault table for current month's currencies
    currency_pairs_from_api = ", ".join(
        [f"'USD_{srcCurrency.upper()}'" for srcCurrency in current_fx_rates_from_api["usd"]])
    select_query = ""
    for currency in current_fx_rates_from_api["usd"]:
        if currency.upper() not in CURRENCY_LIST:
            continue
        if select_query:
            select_query += " UNION ALL "
        select_query += """
        SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
        'USD' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
        "API" as conversionSource,
        TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
        """ % (currency.upper(), current_fx_rates_from_api["usd"][currency], date_start,
               current_timestamp, current_timestamp)

        # flip source and destination
        if currency.upper() != "USD":
            select_query += " UNION ALL "
            select_query += """
            SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
            '%s' as sourceCurrency, 'USD' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
            "API" as conversionSource,
            TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
            """ % (currency.upper(), 1.0 / current_fx_rates_from_api["usd"][currency], date_start,
                   current_timestamp, current_timestamp)

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId IS NULL AND conversionSource = "API" 
                AND (CONCAT(sourceCurrency,'_',destinationCurrency) in (%s) OR 
                    CONCAT(destinationCurrency,'_',sourceCurrency) in (%s));
                    
               INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
               (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
               conversionFactor,month,conversionSource,createdAt,updatedAt)
                   (%s)
    """ % (PROJECTID,
           currency_pairs_from_api,
           currency_pairs_from_api,
           PROJECTID,
           select_query)
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    print_(query)
    query_job = client.query(query, job_config=job_config)
    try:
        query_job.result()
    except Exception as e:
        print_(e)
        print_(query)
        # raise e


def fetch_default_conversion_factors_from_billing_export(jsonData):
    if jsonData["ccmPreferredCurrency"] is None:
        return

    fx_rates_from_billing_export = []
    query = """SELECT distinct DATE_TRUNC(DATE(usage_start_time), month) as billing_month, currency, currency_conversion_rate 
                from `%s.%s.%s` 
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
                """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                       jsonData["gcpBillingExportTablePartitionColumnName"],
                       str(int(jsonData["interval"]) + 7),
                       jsonData["interval"])
    try:
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = client.query(query, job_config=job_config)
        results = query_job.result()  # wait for job to complete
        for row in results:
            fx_rates_from_billing_export.append({
                "sourceCurrency": row.currency.upper(),
                "destinationCurrency": "USD",
                "fxRate": 1.0 / float(row.currency_conversion_rate),
                "billing_month": row.billing_month
            })
            if row.currency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"] and jsonData[
                "ccmPreferredCurrency"] == "USD":
                jsonData["fx_rates_srcCcy_to_destCcy"][row.billing_month][row.currency.upper()] = 1.0 / float(
                    row.currency_conversion_rate)
    except Exception as e:
        print_(e)
        print_("Failed to fetch conversion-factors from the BILLING_EXPORT", "WARN")

    # update currencyConversionFactorDefault table with the conversion factors obtained from billing export
    current_timestamp = datetime.datetime.utcnow()

    currency_pairs_from_billing_export = ", ".join(
        [f"'{row['billing_month']}_{row['sourceCurrency']}_{row['destinationCurrency']}'" for row in
         fx_rates_from_billing_export])
    select_query = ""
    for row in fx_rates_from_billing_export:
        if select_query:
            select_query += " UNION ALL "
        select_query += """
        SELECT '%s' as accountId, 'GCP' as cloudServiceProvider,
        '%s' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
        "BILLING_EXPORT" as conversionSource,
        TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
        """ % (jsonData.get("accountId"), row['sourceCurrency'], row['destinationCurrency'],
               row['fxRate'], row['billing_month'], current_timestamp, current_timestamp)

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId = '%s' AND conversionSource = "BILLING_EXPORT" 
                AND cloudServiceProvider = 'GCP' 
                AND CONCAT(month,'_',sourceCurrency,'_',destinationCurrency) in (%s);
                    
               INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
               (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
               conversionFactor,month,conversionSource,createdAt,updatedAt)
                   (%s)
    """ % (PROJECTID,
           jsonData.get("accountId"), currency_pairs_from_billing_export,
           PROJECTID,
           select_query)
    job_config = bigquery.QueryJobConfig(
        query_parameters=[
            bigquery.ScalarQueryParameter(
                "run_date",
                "DATE",
                datetime.datetime.utcnow().date(),
            )
        ]
    )
    print_(query)
    query_job = client.query(query, job_config=job_config)
    try:
        query_job.result()
    except Exception as e:
        print_(e)
        print_(query)
        # raise e


def fetch_custom_conversion_factors(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    # using latest entry in CURRENCYCONVERSIONFACTORUSERINPUT table for each {src, dest, month}
    # last user entry for a currency-pair might be several months before reportMonth

    for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
        ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
        year, month = billing_month_start.split('-')[0], billing_month_start.split('-')[1]
        billing_month_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
        query = """WITH latest_custom_rate_rows as 
                    (SELECT sourceCurrency, destinationCurrency, max(updatedAt) as latestUpdatedAt
                    FROM `%s.%s` 
                    WHERE accountId="%s" 
                    and cloudServiceProvider="GCP" 
                    and destinationCurrency='%s' 
                    and month <= '%s'
                    group by sourceCurrency, destinationCurrency)
                    
                    SELECT customFxRates.sourceCurrency as sourceCurrency, 
                    customFxRates.conversionFactor as fx_rate, 
                    customFxRates.conversionType as conversion_type 
                    FROM `%s.%s` customFxRates
                    left join latest_custom_rate_rows 
                    on (customFxRates.sourceCurrency=latest_custom_rate_rows.sourceCurrency 
                    and customFxRates.destinationCurrency=latest_custom_rate_rows.destinationCurrency 
                    and customFxRates.updatedAt=latest_custom_rate_rows.latestUpdatedAt) 
                    WHERE latest_custom_rate_rows.latestUpdatedAt is not null 
                    and customFxRates.accountId="%s" 
                    and customFxRates.cloudServiceProvider="GCP" 
                    and customFxRates.destinationCurrency='%s' 
                    and customFxRates.month <= '%s';
                    """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                           jsonData.get("accountId"),
                           jsonData["ccmPreferredCurrency"],
                           billing_month_end,
                           ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                           jsonData.get("accountId"),
                           jsonData["ccmPreferredCurrency"],
                           billing_month_end)
        try:
            print_(query)
            query_job = client.query(query)
            results = query_job.result()  # wait for job to complete
            for row in results:
                if row.sourceCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"][
                    billing_month_start] and row.conversion_type == "CUSTOM":
                    jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start][row.sourceCurrency.upper()] = float(
                        row.fx_rate)
        except Exception as e:
            print_(e)
            print_("Failed to fetch custom conversion-factors for account", "WARN")


def verify_existence_of_required_conversion_factors(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if not jsonData["ccmPreferredCurrency"]:
        return

    for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
        if not all(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start].values()):
            print_(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start])
            print_("Required fx rate not found for at least one currency pair", "ERROR")
            # throw error here. CF execution can't proceed from here


def update_fx_rate_column_in_raw_table(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    # add fxRateSrcToDest column if not exists
    print_("Altering raw gcp_billing_export Table - adding fxRateSrcToDest column")
    query = "ALTER TABLE `%s.%s.%s` \
        ADD COLUMN IF NOT EXISTS fxRateSrcToDest FLOAT64;" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering gcp_billing_export Table")

    # update value of fxRateSrcToDest column using dict
    fx_rate_case_when_query = "CASE "
    for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
        for sourceCurrency in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start]:
            fx_rate_case_when_query += f" WHEN ( DATE_TRUNC(DATE(usage_start_time), month) = '{billing_month_start}' and currency = '{sourceCurrency}' ) THEN CAST({jsonData['fx_rates_srcCcy_to_destCcy'][billing_month_start][sourceCurrency]} AS FLOAT64) "
    fx_rate_case_when_query += f" ELSE CAST(1.0 AS FLOAT64) END"

    query = """UPDATE `%s.%s.%s` 
                SET fxRateSrcToDest = (%s) 
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) 
                AND currency IS NOT NULL;
                """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"], fx_rate_case_when_query,
                       jsonData["gcpBillingExportTablePartitionColumnName"],
                       str(int(jsonData["interval"]) + 7),
                       jsonData["interval"])
    try:
        job_config = bigquery.QueryJobConfig(
            query_parameters=[
                bigquery.ScalarQueryParameter(
                    "run_date",
                    "DATE",
                    datetime.datetime.utcnow().date(),
                )
            ]
        )
        print_(query)
        query_job = client.query(query, job_config=job_config)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to update fxRateSrcToDest column in raw table %s.%s.%s" % (
            PROJECTID, jsonData["datasetName"], jsonData["tableName"]), "WARN")


def get_impersonated_credentials(jsonData):
    # Get source credentials
    if jsonData["deployMode"] == "ONPREM":
        # Impersonation not required in onprem mode
        return

    target_scopes = [
        'https://www.googleapis.com/auth/cloud-platform']
    if jsonData["useWorkloadIdentity"] == "True":
        source_credentials, project = default()
        # Google ADC
    else:
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
                   WHERE DATE(%s) >= DATE_SUB(CURRENT_DATE(), INTERVAL 10 DAY) AND usage_start_time >= DATETIME_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY);
                   """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                          jsonData["gcpBillingExportTablePartitionColumnName"])
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
        if jsonData["gcpBillingExportTablePartitionColumnName"] == "usage_start_time":
            job_config = bigquery.QueryJobConfig(
                destination=destination,
                write_disposition=bigquery.job.WriteDisposition.WRITE_TRUNCATE,
                time_partitioning=bigquery.table.TimePartitioning(
                    field=jsonData["gcpBillingExportTablePartitionColumnName"]),
                query_parameters=[
                    bigquery.ScalarQueryParameter(
                        "run_date",
                        "DATE",
                        datetime.datetime.utcnow().date(),
                    )
                ]
            )
        else:
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
        # keeping this 3 days for currency customers also
        # only tables other than gcp_billing_export require to be updated with current month currency factors
        # Sync past 3 days only. Specify columns here explicitely.

        # check whether the raw billing table is standard_export or detailed_export
        isBillingExportDetailed = check_if_billing_export_is_detailed(jsonData)
        query = """  DELETE FROM `%s` 
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL 10 DAY) and DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY); 
            INSERT INTO `%s` (billing_account_id, %s service,sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info)
                SELECT billing_account_id, %s service,sku,usage_start_time,usage_end_time,project,labels,system_labels,location,export_time,cost,currency,currency_conversion_rate,usage,credits,invoice,cost_type,adjustment_info 
                FROM `%s.%s.%s`
                WHERE DATE(_PARTITIONTIME) >= DATE_SUB(@run_date, INTERVAL 10 DAY) AND DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY);
        """ % (destination, jsonData["gcpBillingExportTablePartitionColumnName"], destination,
               "resource," if isBillingExportDetailed else "",
               "resource," if isBillingExportDetailed else "",
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

    if jsonData["deployMode"] == "ONPREM":
        # Uses Google ADC
        imclient = bigquery.Client(project=PROJECTID)
    else:
        # for SAAS
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
    elif jsonData["ccmPreferredCurrency"]:
        jsonData["interval"] = str(datetime.datetime.utcnow().date().day - 1)
    else:
        jsonData["interval"] = '3'
    get_unique_billingaccount_id(jsonData)

    # currency preferences specific methods
    insert_currencies_with_unit_conversion_factors_in_bq(jsonData)
    initialize_fx_rates_dict(jsonData)
    fetch_default_conversion_factors_from_API(jsonData)
    fetch_default_conversion_factors_from_billing_export(jsonData)
    fetch_custom_conversion_factors(jsonData)
    verify_existence_of_required_conversion_factors(jsonData)
    update_fx_rate_column_in_raw_table(jsonData)

    ingest_into_gcp_cost_export_table(jsonData)
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

    if jsonData["deployMode"] == "ONPREM":
        # Uses Google ADC
        imdt_client = bigquery_datatransfer_v1.DataTransferServiceClient()
    else:
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
        # print_(transfer_config)
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


def prepare_select_query(jsonData, columns_list):
    if jsonData["ccmPreferredCurrency"]:
        select_query = ""
        for column in columns_list:
            if select_query:
                select_query += ", "
            if column == "cost":
                select_query += "(cost * fxRateSrcToDest) as cost"
            elif column == "credits":
                select_query += "ARRAY (SELECT as struct credit.name as name, " \
                                "(credit.amount * fxRateSrcToDest) as amount, " \
                                "credit.full_name as full_name, credit.id as id, " \
                                "credit.type as type FROM UNNEST(credits) AS credit) as credits"
            else:
                select_query += f"{column} as {column}"
        return select_query
    else:
        return ", ".join(f"{w}" for w in columns_list)


def ingest_into_gcp_cost_export_table(jsonData):
    # first, create gcp_cost_export table if not exists yet
    dataset = client.dataset(jsonData["datasetName"])
    intermediary_table_name = jsonData["tableName"].replace("gcp_billing_export", "gcp_cost_export", 1) if jsonData[
        "tableName"].startswith("gcp_billing_export") else f"gcp_cost_export_{jsonData['tableName']}"
    gcpCostExportTableRef = dataset.table(intermediary_table_name)
    gcpCostExportTableTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], intermediary_table_name)
    if not if_tbl_exists(client, gcpCostExportTableRef):
        print_("%s table does not exists, creating table..." % gcpCostExportTableRef)
        createTable(client, gcpCostExportTableRef)
    else:
        print_("%s table exists" % gcpCostExportTableTableName)

    # check whether the raw billing table is standard_export or detailed_export
    isBillingExportDetailed = check_if_billing_export_is_detailed(jsonData)

    # ingest into gcp_cost_export table
    print_("Loading into %s table..." % gcpCostExportTableTableName)
    billing_export_columns = GCP_DETAILED_EXPORT_COLUMNS if isBillingExportDetailed else GCP_STANDARD_EXPORT_COLUMNS
    insert_columns_query = ", ".join(f"{w}" for w in billing_export_columns)
    select_columns_query = prepare_select_query(jsonData, billing_export_columns)
    query = """  DELETE FROM `%s` WHERE DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL %s DAY)  
                AND billing_account_id IN (%s);
           INSERT INTO `%s` (%s, fxRateSrcToDest, ccmPreferredCurrency)
                SELECT %s, %s as fxRateSrcToDest, %s as ccmPreferredCurrency  
                FROM `%s.%s.%s`
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND 
                DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY);
        """ % (gcpCostExportTableTableName, jsonData["interval"], jsonData["billingAccountIds"],
               gcpCostExportTableTableName, insert_columns_query,
               select_columns_query,
               ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
               (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData[
                   "ccmPreferredCurrency"] else "cast(null as string)"),
               PROJECTID, jsonData["datasetName"], jsonData["tableName"],
               jsonData["gcpBillingExportTablePartitionColumnName"],
               str(int(jsonData["interval"]) + 7),
               jsonData["interval"])

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
    print_("  Loaded into intermediary gcp_cost_export table.")


def check_if_billing_export_is_detailed(jsonData):
    print_("Checking if raw billing export (%s) is detailed / has resource column" % jsonData["tableName"])
    query = """  SELECT column_name FROM `%s.%s.INFORMATION_SCHEMA.COLUMNS` 
            WHERE table_name = '%s' and column_name = "resource";
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
        for row in results:
            if row.column_name == "resource":
                return True
    except Exception as e:
        print_(e)
        print_(query)
        print_("  Failed to retrieve columns from the ingested billing_export table", "WARN")
    return False


def ingest_into_unified(jsonData):
    print_("Loading into unifiedTable table...")
    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""
    query = """  DELETE FROM `%s.unifiedTable` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP" 
                AND gcpBillingAccountId IN (%s);
           INSERT INTO `%s.unifiedTable` (product, cost, gcpProduct,gcpSkuId,gcpSkuDescription, startTime, gcpProjectId,
                region,zone,gcpBillingAccountId,cloudProvider, discount, labels, fxRateSrcToDest, ccmPreferredCurrency)
                SELECT service.description AS product, (cost %s) AS cost, service.description AS gcpProduct, sku.id AS gcpSkuId,
                     sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId,
                     location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, (SELECT SUM(c.amount %s) FROM UNNEST(credits) c) as discount, labels AS labels,
                     %s as fxRateSrcToDest, %s as ccmPreferredCurrency 
                FROM `%s.%s`
                WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) <= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL 1 DAY) AND
                     DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY) ;
        """ % (jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"], jsonData["datasetName"],
               fx_rate_multiplier_query, fx_rate_multiplier_query,
               ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
               (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData[
                   "ccmPreferredCurrency"] else "cast(null as string)"),
               jsonData["datasetName"], jsonData["tableName"], jsonData["gcpBillingExportTablePartitionColumnName"],
               str(int(jsonData["interval"]) + 7), jsonData["interval"])

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
    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""
    query = """  DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL %s DAY) AND cloudProvider = "GCP"
                AND gcpBillingAccountId IN (%s);
           INSERT INTO `%s.preAggregated` (cost, gcpProduct,gcpSkuId,gcpSkuDescription,
             startTime,gcpProjectId,region,zone,gcpBillingAccountId,cloudProvider, discount, fxRateSrcToDest, ccmPreferredCurrency) 
             SELECT SUM(cost %s) AS cost, service.description AS gcpProduct,
             sku.id AS gcpSkuId, sku.description AS gcpSkuDescription, TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId,
             location.region AS region, location.zone AS zone, billing_account_id AS gcpBillingAccountId, "GCP" AS cloudProvider, SUM(IFNULL((SELECT SUM(c.amount %s) FROM UNNEST(credits) c), 0)) as discount,
             %s as fxRateSrcToDest, %s as ccmPreferredCurrency 
           FROM `%s.%s`
           WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL %s DAY) AND DATE(usage_start_time) <= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL 1 DAY) AND
             DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL %s DAY)
           GROUP BY service.description, sku.id, sku.description, startTime, project.id, location.region, location.zone, billing_account_id;
        """ % (jsonData["datasetName"], jsonData["interval"], jsonData["billingAccountIds"], jsonData["datasetName"],
               fx_rate_multiplier_query, fx_rate_multiplier_query,
               ("max(fxRateSrcToDest)" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
               (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData[
                   "ccmPreferredCurrency"] else "cast(null as string)"),
               jsonData["datasetName"], jsonData["tableName"], jsonData["gcpBillingExportTablePartitionColumnName"],
               str(int(jsonData["interval"]) + 7), jsonData["interval"])

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
            WHERE DATE(%s) >= DATE_SUB(@run_date, INTERVAL 10 DAY);
            """ % (PROJECTID, jsonData["datasetName"], jsonData["tableName"],
                   jsonData["gcpBillingExportTablePartitionColumnName"])
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
        jsonData["billingAccountIdsList"] = billingAccountIds
    except Exception as e:
        print_(query)
        print_("  Failed to retrieve distinct billingAccountIds", "WARN")
        jsonData["billingAccountIds"] = ""
        jsonData["billingAccountIdsList"] = []
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
     """ % (
        table_name, jsonData["interval"], jsonData.get("accountId"), table_name, jsonData.get("accountId"),
        source_table,
        jsonData["interval"])

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
            """ % (PROJECTID, CEINTERNALDATASET, GCPCONNECTORINFOTABLE,
                   jsonData["accountId"], jsonData["connectorId"], jsonData["dtName"], datetime.datetime.utcnow(),
                   jsonData["sourceGcpTableName"])

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
            """ % (PROJECTID, CEINTERNALDATASET, GCPCONNECTORINFOTABLE,
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


def alter_unified_table(jsonData):
    print_("Altering unifiedTable Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.unifiedTable` \
        ADD COLUMN IF NOT EXISTS costCategory ARRAY<STRUCT<costCategoryName STRING, costBucketName STRING>>;" % ds

    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering unifiedTable Table")
