# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import os
import re
import datetime
import util
import requests
import uuid

from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CEINTERNALDATASET, CURRENCYCONVERSIONFACTORUSERINPUT, update_connector_data_sync_status, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES, send_event, \
    flatten_label_keys_in_table, LABELKEYSTOCOLUMNMAPPING, run_bq_query_with_retries, add_msp_markup_column_to_schema,\
    MSPMARKUP, ACCOUNTS_ENABLED_WITH_ADDITIONAL_AWS_FIELDS_IN_UNIFIED_TABLE
from calendar import monthrange
from google.cloud import bigquery
from google.cloud import storage
from google.cloud import functions_v2
from google.cloud import pubsub_v1

"""
{
	"accountId": "nvsv7gjbtzya3cgsgxnocg",
	"accountIdOrig": "nVS...",
	"bucket": "nikunjtestbucket",
	"datasetName": "BillingReport_nvsv7gjbtzya3cgsgxnocg",
	"fileName": "AROAY2UX4LR3HUT7WH7DG:NVsV7gjbTZyA3CgSgXNOcg/PGnxKAheSKWY30YHcgSNLg/Harness/20201101-20201201/Harness-Manifest.json",
	"projectId": "ccm-play",
	"tableName": "awsCurTable_2020_11",
	"tableSuffix" : "2020_11"
}

{   
    "accountId": "o0yschY0RrGZJ2JFGEpvdw",
    "path": "AROAXVZVVGMCF7KFQSJ37:o0yschY0RrGZJ2JFGEpvdw/mg7Qs7PuQxAgqg3aNzau0x/harness_cloud_cost_demo/20210501-20210601" or 
            "AROAY2UX4LR3HUT7WH7DG:NVsV7gjbTZyA3CgSgXNOcg/PGnxKAheSKWY30YHcgSNLg/Harness/20201101-20201201/<versioned>/",
    "bucket": "awscustomerbillingdata-dev",
    "skipManifestCheck" : false,
    "keepFiles": false,
    "triggerHistoricalCostUpdateInPreferredCurrency": True,
    "disableHistoricalUpdateForMonths": ['2022-12-01', '2022-11-01']
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)
publisher = pubsub_v1.PublisherClient()
COSTCATEGORIESUPDATETOPIC = os.environ.get('COSTCATEGORIESUPDATETOPIC', 'ccm-bigquery-batch-update')


def main(request):
    """
    Triggered from an HTTP Request.
    """
    print(request)
    jsonData = request.get_json(force=True)

    # Set accountid for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    util.CF_EXECUTION_ID = uuid.uuid4()
    print_(request)
    print_(jsonData)

    jsonData["cloudProvider"] = "AWS"
    ps = jsonData["path"].split("/")
    if len(ps) == 4:
        monthfolder = ps[-1]  # last folder in path
        jsonData["cleanuppath"] = jsonData["path"]
    elif len(ps) == 5:
        monthfolder = ps[-2]  # second last folder in path
        jsonData["cleanuppath"] = "/".join(ps[:-1])

    jsonData["reportYear"] = monthfolder.split("-")[0][:4]
    jsonData["reportMonth"] = monthfolder.split("-")[0][4:6]

    jsonData["connectorId"] = ps[1]  # second from beginning is connector id in mongo

    accountIdBQ = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % (accountIdBQ)
    jsonData["awsCurTableSuffix"] = "%s_%s" % (jsonData["reportYear"], jsonData["reportMonth"])
    jsonData["tableSuffix"] = "%s_%s_%s" % (jsonData["connectorId"], jsonData["reportYear"], jsonData["reportMonth"])
    jsonData["tableName"] = f"awsBilling_{jsonData['tableSuffix']}"
    jsonData["tableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])

    if not create_dataset_and_tables(jsonData):
        return "CF completed execution."
    ingest_data_from_csv(jsonData)
    set_available_columns(jsonData)
    get_unique_accountids(jsonData)

    get_preferred_currency(jsonData)
    insert_currencies_with_unit_conversion_factors_in_bq(jsonData)
    initialize_fx_rates_dict(jsonData)
    fetch_default_conversion_factors_from_API(jsonData)
    fetch_custom_conversion_factors(jsonData)
    verify_existence_of_required_conversion_factors(jsonData)
    update_fx_rate_column_in_raw_table(jsonData)

    get_msp_markups(jsonData)
    update_msp_markups_in_raw_table(jsonData)

    ingest_data_to_awscur(jsonData)
    ingest_data_to_preagg(jsonData)
    ingest_data_to_unified(jsonData)
    update_connector_data_sync_status(jsonData, PROJECTID, client)
    # ingest_data_to_costagg(jsonData)
    if jsonData.get("triggerHistoricalCostUpdateInPreferredCurrency") and jsonData["ccmPreferredCurrency"]:
        trigger_historical_cost_update_in_preferred_currency(jsonData)

    send_event(publisher.topic_path(PROJECTID, COSTCATEGORIESUPDATETOPIC), {
        "eventType": "COST_CATEGORY_UPDATE",
        "message": {
            "accountId": jsonData["accountId"],
            "startDate": "%s-%s-01" % (jsonData["reportYear"], jsonData["reportMonth"]),
            "endDate": "%s-%s-%s" % (jsonData["reportYear"], jsonData["reportMonth"], monthrange(int(jsonData["reportYear"]), int(jsonData["reportMonth"]))[1]),
            "cloudProvider": "AWS",
            "cloudProviderAccountIds": jsonData["usageaccountidlist"]
        }
    })

    print_("Completed")
    return "CF executed successfully."


def trigger_historical_cost_update_in_preferred_currency(jsonData):
    if not jsonData["disableHistoricalUpdateForMonths"]:
        jsonData["disableHistoricalUpdateForMonths"] = [f"{jsonData['reportYear']}-{jsonData['reportMonth']}-01"]

    # get months for which historical update needs to be triggered, and custom conversion factors
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """SELECT month, conversionType, sourceCurrency, destinationCurrency, conversionFactor
                FROM `%s.%s`
                WHERE accountId="%s" AND destinationCurrency is NOT NULL AND isHistoricalUpdateRequired = TRUE
                AND cloudServiceProvider = "AWS"
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
                WHERE cloudServiceProvider = "AWS" 
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
            "cloudServiceProvider": "AWS",
            "months": list(historical_update_months),
            "userInputFxRates": custom_factors_dict
        }
        url = get_cf_v2_uri(f"projects/{PROJECTID}/locations/us-central1/functions/ce-aws-historical-currency-update-bq-terraform")
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
            print_(e)
            print_("Encountered an exception during triggering historical update for AWS (Ignore if timeout exception).")


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
                FROM `%s.%s`
                WHERE accountId="%s" and destinationCurrency is NOT NULL LIMIT 1;
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT, jsonData.get("accountId"))
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["ccmPreferredCurrency"] = row.destinationCurrency.upper()
            print_("Found preferred-currency for account %s: %s" % (jsonData.get("accountId"), jsonData["ccmPreferredCurrency"]))
            break
    except Exception as e:
        print_(e)
        print_("Failed to fetch preferred currency for account", "WARN")

    if not jsonData["ccmPreferredCurrency"]:
        print_("No preferred-currency found for account %s" % jsonData.get("accountId"))


def get_msp_markups(jsonData):
    jsonData["markups"] = None
    table = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, MSPMARKUP)
    query = """SELECT condition
                FROM `%s`
                WHERE accountId="%s";
                """ % (table, jsonData.get("accountId"))
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["markups"] = row.condition
            break
    except Exception as e:
        print_(e)
        print_("Failed to fetch markups for account", "WARN")


def update_msp_markups_in_raw_table(jsonData):
    print_("Altering raw awsBilling Table - adding markup column")
    query = "ALTER TABLE `%s` \
        ADD COLUMN IF NOT EXISTS mspMarkupMultiplier FLOAT64;" % (jsonData["tableId"])
    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering awsBilling Table")

    if jsonData["markups"] is not None:
        markups = jsonData["markups"].replace("awsUsageaccountid", "usageaccountid").replace("awsServicecode", "servicename")
        query = """UPDATE `%s` 
                    SET mspMarkupMultiplier = %s
                    WHERE TRUE;""" % (jsonData["tableId"], markups)
        print_(query)
        try:
            query_job = client.query(query)
            query_job.result()  # wait for job to complete
        except Exception as e:
            print_(e)
            print_("Failed to update mspMarkup column in raw table %s" % (jsonData["tableId"]), "WARN")


def update_fx_rate_column_in_raw_table(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    # add fxRateSrcToDest column if not exists
    print_("Altering raw awsBilling Table - adding fxRateSrcToDest column")
    query = "ALTER TABLE `%s` \
        ADD COLUMN IF NOT EXISTS fxRateSrcToDest FLOAT64;" % (jsonData["tableId"])
    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering awsBilling Table")

    # update value of fxRateSrcToDest column using dict
    fx_rate_case_when_query = "CASE currencycode "
    for sourceCurrency in jsonData["fx_rates_srcCcy_to_destCcy"]:
        fx_rate_case_when_query += f" WHEN '{sourceCurrency}' THEN CAST({jsonData['fx_rates_srcCcy_to_destCcy'][sourceCurrency]} AS FLOAT64) "
    fx_rate_case_when_query += f" WHEN 'NONE' THEN CAST(1.0 AS FLOAT64) ELSE CAST(1.0 AS FLOAT64) END"

    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s) WHERE currencycode IS NOT NULL;
                """ % (jsonData["tableId"], fx_rate_case_when_query)
    print_(query)
    try:
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to update fxRateSrcToDest column in raw table %s" % (jsonData["tableId"]), "WARN")


def insert_currencies_with_unit_conversion_factors_in_bq(jsonData):
    # we are inserting these rows for showing active month's source_currencies to user
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year

    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)

    # update 1.0 rows in currencyConversionFactorDefault table only for current month
    if str(year) != str(currentYear) or str(month) != str(currentMonth):
        return

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId = '%s' AND cloudServiceProvider = "AWS" AND sourceCurrency = destinationCurrency 
                AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month < DATE('%s');
               INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
               (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
               conversionFactor,month,conversionSource,createdAt,updatedAt)
                   SELECT distinct 
                   '%s' as accountId,
                   "AWS" as cloudServiceProvider,
                   currencycode as sourceCurrency,
                   currencycode as destinationCurrency,
                   1.0 as conversionFactor,
                   DATE('%s') as month,
                   "BILLING_EXPORT_SRC_CCY" as conversionSource,
                   TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
                   FROM `%s`
                   where currencycode not in (select distinct sourceCurrency FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                   WHERE accountId = '%s' AND cloudServiceProvider = "AWS" AND sourceCurrency = destinationCurrency 
                   AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month = DATE('%s'));
    """ % (PROJECTID,
           jsonData.get("accountId"),
           date_start,
           PROJECTID,
           jsonData.get("accountId"),
           date_start,
           current_timestamp, current_timestamp,
           jsonData["tableId"],
           PROJECTID,
           jsonData.get("accountId"),
           date_start)
    print_(query)
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

    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    query = """SELECT distinct currencycode
                FROM `%s`;
                """ % (jsonData["tableId"])
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["fx_rates_srcCcy_to_destCcy"][row.currencycode.upper()] = None
    except Exception as e:
        print_("Failed to list distinct AWS source-currencies for account", "WARN")


def fetch_default_conversion_factors_from_API(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])

    # fetch conversion factors from the API for all sourceCurrencies vs USD and destCcy vs USD for that DATE.
    # cdn.jsdelivr.net api returns currency-codes in lowercase
    try:
        response = requests.get(f"https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/{date_start}/currencies/usd.json")
        fx_rates_from_api = response.json()
    except Exception as e:
        print_(e)
        fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[date_start]
    # 1 usd = x src
    # 1 usd = y dest
    # 1 src = (y/x) dest

    for srcCurrency in fx_rates_from_api["usd"]:
        # ensure precision of fx rates while performing operations
        try:
            if srcCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"]:
                jsonData["fx_rates_srcCcy_to_destCcy"][srcCurrency.upper()] = \
                    fx_rates_from_api["usd"][jsonData["ccmPreferredCurrency"].lower()] / fx_rates_from_api["usd"][srcCurrency]

        except Exception as e:
            print_(e, "WARN")
            print_(f"fxRate for {srcCurrency} to {jsonData['ccmPreferredCurrency']} was not found in API response.")

    # update currencyConversionFactorDefault table if reportMonth is current month
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year
    if str(year) != str(currentYear) or str(month) != str(currentMonth):
        return

    currency_pairs_from_api = ", ".join([f"'USD_{srcCurrency.upper()}'" for srcCurrency in fx_rates_from_api["usd"]])
    select_query = ""
    for currency in fx_rates_from_api["usd"]:
        if currency.upper() not in CURRENCY_LIST:
            continue
        if select_query:
            select_query += " UNION ALL "
        select_query += """
        SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
        'USD' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
        "API" as conversionSource,
        TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
        """ % (currency.upper(), fx_rates_from_api["usd"][currency], date_start,
               current_timestamp, current_timestamp)

        if currency.upper() != "USD":
            select_query += " UNION ALL "
            select_query += """
            SELECT cast(null as string) as accountId, cast(null as string) as cloudServiceProvider,
            '%s' as sourceCurrency, 'USD' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
            "API" as conversionSource,
            TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
            """ % (currency.upper(), 1.0 / fx_rates_from_api["usd"][currency], date_start,
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
    print_(query)
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
    try:
        query_job.result()
    except Exception as e:
        print_(query)
        # raise e


def fetch_custom_conversion_factors(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return

    # reportMonth might not be current ongoing month, so we can't use the overall latest fxRate
    # using latest entry until reportMonth in CURRENCYCONVERSIONFACTORUSERINPUT table
    # also, last user entry for a currency-pair might be several months before reportMonth

    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    query = """WITH latest_custom_rate_rows as 
                (SELECT sourceCurrency, destinationCurrency, max(updatedAt) as latestUpdatedAt
                FROM `%s.%s` 
                WHERE accountId="%s" 
                and cloudServiceProvider="AWS" 
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
                and customFxRates.cloudServiceProvider="AWS" 
                and customFxRates.destinationCurrency='%s' 
                and customFxRates.month <= '%s';
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                       jsonData.get("accountId"),
                       jsonData["ccmPreferredCurrency"],
                       date_end,
                       ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                       jsonData.get("accountId"),
                       jsonData["ccmPreferredCurrency"],
                       date_end)
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            if row.sourceCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"] and row.conversion_type == "CUSTOM":
                jsonData["fx_rates_srcCcy_to_destCcy"][row.sourceCurrency.upper()] = float(row.fx_rate)
    except Exception as e:
        print_("Failed to fetch custom conversion-factors for account", "WARN")


def verify_existence_of_required_conversion_factors(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if not jsonData["ccmPreferredCurrency"]:
        return
    if not all(jsonData["fx_rates_srcCcy_to_destCcy"].values()):
        print_(jsonData["fx_rates_srcCcy_to_destCcy"])
        print_("Required fx rate not found for at least one currency pair", "ERROR")
    # throw error here. CF execution can't proceed from here


def create_dataset_and_tables(jsonData):
    create_dataset(client, jsonData["datasetName"], jsonData.get("accountId"))
    dataset = client.dataset(jsonData["datasetName"])
    if not create_table_from_manifest(jsonData):
        return False

    aws_cur_table_ref = dataset.table("awscur_%s" % (jsonData["awsCurTableSuffix"]))
    pre_aggragated_table_ref = dataset.table(PREAGGREGATED)
    unified_table_ref = dataset.table(UNIFIED)
    currencyConversionFactorUserInput_table_ref = dataset.table(CURRENCYCONVERSIONFACTORUSERINPUT)
    label_keys_to_column_mapping_table_ref = dataset.table(LABELKEYSTOCOLUMNMAPPING)

    for table_ref in [aws_cur_table_ref, pre_aggragated_table_ref, unified_table_ref,
                      currencyConversionFactorUserInput_table_ref, label_keys_to_column_mapping_table_ref]:
        if not if_tbl_exists(client, table_ref):
            print_("%s table does not exists, creating table..." % table_ref)
            createTable(client, table_ref)
        else:
            # Enable these only when needed.
            # if table_ref == aws_cur_table_ref:
            #     alter_awscur_table(jsonData)
            # elif table_ref == unified_table_ref:
            #     alter_unified_table(jsonData)
            print_("%s table exists" % table_ref)

    ds = f"{PROJECTID}.{jsonData['datasetName']}"
    table_ids = ["%s.awscur_%s" % (ds, jsonData["awsCurTableSuffix"]),
                 "%s.%s" % (ds, "unifiedTable"),
                 "%s.%s" % (ds, "preAggregated")]
    add_currency_preferences_columns_to_schema(client, table_ids)
    add_msp_markup_column_to_schema(client, table_ids)

    return True


def create_table_from_manifest(jsonData):
    # Read the CSV from GCS as string
    manifestdata = {}
    blob_to_delete = None
    try:
        blobs = storage_client.list_blobs(
            jsonData["bucket"], prefix=jsonData["path"])
        for blob in blobs:
            # To avoid picking up sub directory json files
            if jsonData["path"] != "/".join(blob.name.split("/")[:-1]):
                print_(blob.name)
                continue
            if blob.name.endswith("Manifest.json"):
                print_(blob.name)
                data = blob.download_as_string()
                data = data.decode('utf-8')
                manifestdata = json.loads(data)
                print_("Found manifest data: %s" % manifestdata)
                blob_to_delete = blob
                break
    except Exception as e:
        print_(e)
        return False

    # Prepare table schema from manifest json
    reg = re.compile("[^a-zA-Z0-9_]")
    map_tags = {}
    schema = []
    for column in manifestdata.get("columns", []):
        name = column["name"].lower().strip()
        name_converted = name
        if reg.search(name):
            # This must be a TAG ex. aws:autoscaling:groupName
            name_converted = re.sub(reg, "_", name)
            name = "TAG_" + name_converted
        name_for_map = name_converted
        try:
            name = name + "_" + str(map_tags[name_for_map])
            map_tags[name_for_map] += 1
        except:
            map_tags[name_for_map] = 1
        data_type = get_mapped_data_column(column["type"])
        schema.append(bigquery.SchemaField(name, data_type, "NULLABLE"))

    # Create table
    try:
        if len(schema) != 0:
            print_("Schema: %s" % schema)
            # Delete older table only when new manifest format is available
            client.delete_table(jsonData["tableId"], not_found_ok=True)
            table = client.create_table(bigquery.Table(jsonData["tableId"], schema=schema))
            print_("Created table from blob {} {}.{}.{}".format(blob_to_delete.name, table.project, table.dataset_id,
                                                                table.table_id))
            if jsonData.get("keepFiles") is True:
                print_("keepFiles is true. Not deleting manifest.")
            else:
                blob_to_delete.delete()
                print_("Deleted Manifest Json {}".format(blob_to_delete.name))
        else:
            print_("No Manifest found. No table to create")
            if jsonData.get("skipManifestCheck") in [False, None]:
                return False
            return True
    except Exception as e:
        print_("Error while creating table\n {}".format(e), "ERROR")
        return False

    return True


def get_mapped_data_column(data_type):
    if data_type == "String":
        modified_data_type = "STRING"
    elif data_type == "OptionalString":
        modified_data_type = "STRING"
    elif data_type == "Interval":
        modified_data_type = "STRING"
    elif data_type == "DateTime":
        modified_data_type = "TIMESTAMP"
    elif data_type == "BigDecimal":
        modified_data_type = "FLOAT"
    elif data_type == "OptionalBigDecimal":
        modified_data_type = "FLOAT"
    else:
        modified_data_type = "STRING"
    return modified_data_type


def ingest_data_from_csv(jsonData):
    job_config = bigquery.LoadJobConfig(
        skip_leading_rows=1,
        field_delimiter=",",
        ignore_unknown_values=True,
        source_format="CSV",
        allow_quoted_newlines=True,
        allow_jagged_rows=True
    )
    uris = ["gs://" + jsonData["bucket"] + "/" + jsonData["path"] + "/*.csv",
            "gs://" + jsonData["bucket"] + "/" + jsonData["path"] + "/*.csv.gz",
            "gs://" + jsonData["bucket"] + "/" + jsonData["path"] + "/*.csv.zip"]
    print_("Ingesting all CSVs from %s" % jsonData["path"])
    table = "%s.%s" % (jsonData["datasetName"], jsonData["tableName"])
    print_("Loading into %s table..." % table)
    load_job = client.load_table_from_uri(
        uris,
        table,
        job_config=job_config
    )  # Make an API request.
    try:
        load_job.result()  # Wait for the job to complete.
    except Exception as e:
        print_(e)

    table = client.get_table(jsonData["tableId"])
    print_("Total {} rows in table {}".format(table.num_rows, jsonData["tableId"]))
    blobs = storage_client.list_blobs(
        jsonData["bucket"], prefix=jsonData["cleanuppath"]
    )
    if jsonData.get("keepFiles") is True:
        print_("keepFiles is true. Not deleting csvs.")
        return
    print_("Cleaning up all csvs in this path: %s" % jsonData["cleanuppath"])
    for blob in blobs:
        blob.delete()
        print_("Blob {} deleted.".format(blob.name))

def set_available_columns(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """SELECT column_name, data_type
                FROM %s.INFORMATION_SCHEMA.COLUMNS
                WHERE table_name="%s";
                """ % (ds, jsonData["tableName"])
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        columns = set()
        for row in results:
            columns.add(row.column_name.lower())
        jsonData["available_columns"] = columns
        print_("Retrieved available columns: %s" % columns)
    except Exception as e:
        print_("Failed to retrieve available columns", "WARN")
        jsonData["available_columns"] = []
        raise e

def ingest_data_to_awscur(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    # In the new BigQuery dataset, create a reference to a new table for
    # storing the query results.
    tableName = "%s.awscur_%s" % (ds, jsonData["awsCurTableSuffix"])
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s table..." % tableName)

    tags_query = """( SELECT ARRAY_AGG(STRUCT( regexp_replace(REGEXP_EXTRACT(unpivotedData, '[^"]*'), 'TAG_' , '') AS key ,
        regexp_replace(REGEXP_EXTRACT(unpivotedData, r':\"[^"]*'), ':"', '') AS value ))
        FROM UNNEST(( SELECT REGEXP_EXTRACT_ALL(json, 'TAG_' || r'[^:]+:\"[^"]+\"') FROM (SELECT TO_JSON_STRING(table) json))) unpivotedData)
        AS tags """

    # This is temp fix for colourtokens. refer CCM-5462 for more information
    if (jsonData["connectorId"] in ["QA096742272934"]) and (jsonData["accountId"] in ["MN8FCTn_Q9-DDHGIJWm-Xg"]):
        print_("Skipping ingesting tags")
        tags_query = "null AS tags "

    desirable_columns = ["resourceid", "usagestartdate", "productname", "productfamily", "servicecode", "servicename", "blendedrate", "blendedcost",
                         "unblendedrate", "unblendedcost", "region", "availabilityzone", "usageaccountid", "instancetype",
                         "usagetype", "lineitemtype", "effectivecost", "billingentity", "instancefamily", "marketoption", "usageamount"]
    if jsonData.get('accountId') in ACCOUNTS_ENABLED_WITH_ADDITIONAL_AWS_FIELDS_IN_UNIFIED_TABLE:
        desirable_columns += ["payeraccountid", "lineitemdescription"]
    available_columns = list(set(desirable_columns) & set(jsonData["available_columns"]))
    select_available_columns = prepare_select_query(jsonData, available_columns)  # passing updated available_columns
    available_columns = ", ".join(f"{w}" for w in available_columns)

    amortised_cost_query = prep_amortised_cost_query(jsonData, set(jsonData["available_columns"]))
    net_amortised_cost_query = prep_net_amortised_cost_query(jsonData, set(jsonData["available_columns"]))

    query = """
    DELETE FROM `%s` WHERE DATE(usagestartdate) >= '%s' AND DATE(usagestartdate) <= '%s' and usageaccountid IN (%s);
    INSERT INTO `%s` (%s, amortisedCost, netAmortisedCost, tags, fxRateSrcToDest, ccmPreferredCurrency, mspMarkupMultiplier) 
        SELECT %s, %s, %s, %s, %s as fxRateSrcToDest, %s as ccmPreferredCurrency, %s as mspMarkupMultiplier
        FROM `%s` table 
        WHERE DATE(usagestartdate) >= '%s' AND DATE(usagestartdate) <= '%s';
     """ % (tableName, date_start, date_end, jsonData["usageaccountid"],
            tableName, available_columns,
            select_available_columns, amortised_cost_query, net_amortised_cost_query, tags_query,
            ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
            (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData["ccmPreferredCurrency"] else "cast(null as string)"),
            ("mspMarkupMultiplier" if jsonData["markups"] else "cast(1 as float64)"),
            jsonData["tableId"],
            date_start, date_end)
    # Configure the query job.
    print_(query)
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
    try:
        query_job.result()
    except Exception as e:
        print_(query)
        raise e
    print_("Loaded into %s table..." % tableName)


def prepare_select_query(jsonData, columns_list):
    cost_columns = ["blendedcost", "unblendedcost", "effectivecost"]
    if jsonData["ccmPreferredCurrency"] and jsonData["markups"]:
        return ", ".join(f"( {w} {'* fxRateSrcToDest * mspMarkupMultiplier' if w in cost_columns else ''} ) as {w}" for w in columns_list)
    elif jsonData["ccmPreferredCurrency"]:
        return ", ".join(f"( {w} {'* fxRateSrcToDest' if w in cost_columns else ''} ) as {w}" for w in columns_list)
    elif jsonData["markups"]:
        return ", ".join(f"( {w} {'* mspMarkupMultiplier' if w in cost_columns else ''} ) as {w}" for w in columns_list)
    else:
        return ", ".join(f"{w}" for w in columns_list)


def prep_amortised_cost_query(jsonData, cols):
    # Prep amortised cost calculation query based on available cols
    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""
    msp_markup_multiplier_query = "*mspMarkupMultiplier" if jsonData["markups"] else ""
    query = """CASE  
                    WHEN (lineitemtype = 'SavingsPlanNegation') THEN 0
                    WHEN (lineitemtype = 'SavingsPlanUpfrontFee') THEN 0 
            """
    if "SavingsPlanEffectiveCost".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'SavingsPlanCoveredUsage') THEN (SavingsPlanEffectiveCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "TotalCommitmentToDate".lower() in cols and "UsedCommitment".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'SavingsPlanRecurringFee') THEN ((TotalCommitmentToDate - UsedCommitment){fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "EffectiveCost".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'DiscountedUsage') THEN (EffectiveCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "UnusedAmortizedUpfrontFeeForBillingPeriod".lower() in cols and "UnusedRecurringFee".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'RIFee') THEN ((UnusedAmortizedUpfrontFeeForBillingPeriod + UnusedRecurringFee){fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "ReservationARN".lower() in cols:
        query = query + f"WHEN ((lineitemtype = 'Fee') AND (ReservationARN <> '')) THEN 0 \n"
    query = query + f" ELSE (UnblendedCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) END amortisedCost \n"
    print_(query)
    return query

def prep_net_amortised_cost_query(jsonData, cols):
    # Prep net amortised cost calculation query based on available cols
    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""
    msp_markup_multiplier_query = "*mspMarkupMultiplier" if jsonData["markups"] else ""
    query = """CASE  
                    WHEN (lineitemtype = 'SavingsPlanNegation') THEN 0
                    WHEN (lineitemtype = 'SavingsPlanUpfrontFee') THEN 0 
            """
    if "NetSavingsPlanEffectiveCost".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'SavingsPlanCoveredUsage') THEN (NetSavingsPlanEffectiveCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "TotalCommitmentToDate".lower() in cols and "UsedCommitment".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'SavingsPlanRecurringFee') THEN ((TotalCommitmentToDate - UsedCommitment){fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "NetEffectiveCost".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'DiscountedUsage') THEN (NetEffectiveCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "NetUnusedAmortizedUpfrontFeeForBillingPeriod".lower() in cols and "NetUnusedRecurringFee".lower() in cols:
        query = query + f"WHEN (lineitemtype = 'RIFee') THEN ((NetUnusedAmortizedUpfrontFeeForBillingPeriod + NetUnusedRecurringFee){fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    if "ReservationARN".lower() in cols:
        query = query + f"WHEN ((lineitemtype = 'Fee') AND (ReservationARN <> '')) THEN 0 \n"
    if "NetUnblendedCost".lower() in cols:
        query = query + f" ELSE (NetUnblendedCost{fx_rate_multiplier_query}{msp_markup_multiplier_query}) \n"
    else:
        query = query + f" ELSE 0 \n"
    query = query + f"END netAmortisedCost \n"
    print_(query)
    return query

def get_unique_accountids(jsonData):
    # Support for account allowlist. When more usecases arises, we shall move this to a table in BQ
    account_allowlist = {
        'LI2hS5sbS_2gLSnDqpAbTg': ['087946768277', '102095771087', '753890487724', '912131591631', '551316786239',
                                   '314840214426', '211958814005', '950940341780', '533349434853']
    }

    # Get unique aws accountIds from main awsBilling table
    query = """ 
            SELECT DISTINCT(usageaccountid) FROM `%s`;
            """ % (jsonData["tableId"])
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        usageaccountid = []
        for row in results:
            usageaccountid.append(row.usageaccountid)
        print_("usageaccountid available are: %s" % usageaccountid)
        if len(account_allowlist.get(jsonData['accountId'], [])) > 0:
            print_("allow listed accounts are: %s" % account_allowlist[jsonData['accountId']])
            usageaccountid = list(set(usageaccountid) & set(account_allowlist[jsonData['accountId']]))
        jsonData["usageaccountid"] = ", ".join(f"'{w}'" for w in usageaccountid)
        jsonData["usageaccountidlist"] = usageaccountid
    except Exception as e:
        print_("Failed to retrieve distinct aws usageaccountid", "WARN")
        jsonData["usageaccountid"] = ""
        jsonData["usageaccountidlist"] = []
        raise e
    print_("usageaccountid we will use %s" % usageaccountid)


def ingest_data_to_preagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    tableName = "%s.%s" % (ds, "preAggregated")
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s preAggregated table..." % tableName)

    insert_columns = """startTime, awsBlendedRate, awsBlendedCost, awsUnblendedRate, awsUnblendedCost, cost,
                        awsServicecode, region, awsAvailabilityzone, awsUsageaccountid,
                        awsUsagetype, cloudProvider, fxRateSrcToDest, ccmPreferredCurrency, mspMarkupMultiplier"""

    select_columns = """TIMESTAMP_TRUNC(usagestartdate, DAY) as startTime, min(blendedrate) AS awsBlendedRate, sum(blendedcost) AS awsBlendedCost,
                    min(unblendedrate) AS awsUnblendedRate, sum(unblendedcost) AS awsUnblendedCost, sum(unblendedcost) AS cost,
                    servicename AS awsServicecode, region, availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid,
                    usagetype AS awsUsagetype, "AWS" AS cloudProvider, max(fxRateSrcToDest), max(ccmPreferredCurrency), max(mspMarkupMultiplier)"""

    group_by = """awsServicecode, region, awsAvailabilityzone, awsUsageaccountid, awsUsagetype, startTime"""
    # Amend query as per columns availability
    for additionalColumn in ["instancetype"]:
        if additionalColumn.lower() in jsonData["available_columns"]:
            insert_columns = insert_columns + ", aws%s" % additionalColumn
            select_columns = select_columns + ", %s as aws%s" % (additionalColumn, additionalColumn)
            # Modify groupbys as per columns availability and need
            if additionalColumn == "instancetype":
                group_by = group_by + ", aws%s" % additionalColumn

    query = """DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' AND cloudProvider = "AWS"
                AND awsUsageAccountId IN (%s);
               INSERT INTO `%s.preAggregated` (%s)
                   SELECT %s 
                   FROM `%s.awscur_%s` 
                   WHERE usageaccountid IN (%s) 
               GROUP BY %s;
    """ % (ds, date_start, date_end,
           jsonData["usageaccountid"],
           ds, insert_columns,
           select_columns, ds, jsonData["awsCurTableSuffix"],
           jsonData["usageaccountid"], group_by)
    print_(query)
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
    try:
        query_job.result()
    except Exception as e:
        print_(query)
        raise e
    print_("Loaded into %s table..." % tableName)


def ingest_data_to_unified(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    tableName = "%s.%s" % (ds, UNIFIED)
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s table..." % tableName)

    insert_columns = """product, startTime, fxRateSrcToDest, ccmPreferredCurrency, mspMarkupMultiplier, 
                    awsBlendedRate, awsBlendedCost,awsUnblendedRate, 
                    awsEffectiveCost, awsAmortisedCost, 
                    awsNetAmortisedCost, awsLineItemType,
                    awsUnblendedCost, cost, awsServicecode, region, 
                    awsAvailabilityzone, awsUsageaccountid, 
                    cloudProvider, awsBillingEntity, labels"""

    select_columns = """productname AS product, TIMESTAMP_TRUNC(usagestartdate, DAY) as startTime, fxRateSrcToDest, ccmPreferredCurrency, mspMarkupMultiplier,
                    blendedrate AS awsBlendedRate, blendedcost AS awsBlendedCost, unblendedrate AS awsUnblendedRate, 
                    effectivecost as awsEffectiveCost, amortisedCost as awsAmortisedCost, 
                    netAmortisedCost as awsNetAmortisedCost, lineitemtype as awsLineItemType,
                    unblendedcost AS awsUnblendedCost, unblendedcost AS cost, servicename AS awsServicecode, region, 
                    availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid, 
                    "AWS" AS cloudProvider, billingentity as awsBillingEntity, tags AS labels"""

    # Amend query as per columns availability
    for additionalColumn in ["instancetype", "usagetype"]:
        if additionalColumn.lower() in jsonData["available_columns"]:
            insert_columns = insert_columns + ", aws%s" % additionalColumn
            select_columns = select_columns + ", %s as aws%s" % (additionalColumn, additionalColumn)

    # supporting additional fields in unifiedTable for Elevance
    if jsonData.get('accountId') in ACCOUNTS_ENABLED_WITH_ADDITIONAL_AWS_FIELDS_IN_UNIFIED_TABLE:
        for additionalColumn in ["payeraccountid", "lineitemdescription", "resourceid",
                                 "instancefamily", "marketoption", "servicecode", "usageamount"]:
            if additionalColumn.lower() in jsonData["available_columns"]:
                insert_columns = insert_columns + ", aws%s%s" % (additionalColumn,
                                                                 "" if additionalColumn != "servicecode" else "_simplified")
                select_columns = select_columns + ", %s as aws%s%s" % (additionalColumn,
                                                                       additionalColumn,
                                                                       "" if additionalColumn != "servicecode" else "_simplified")

    query = """DELETE FROM `%s` WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s'  AND cloudProvider = "AWS"
                    AND awsUsageAccountId IN (%s);
               INSERT INTO `%s` (%s)
               SELECT %s 
               FROM `%s.awscur_%s` 
               WHERE usageaccountid IN (%s);
     """ % (tableName, date_start, date_end,
            jsonData["usageaccountid"],
            tableName, insert_columns,
            select_columns,
            ds, jsonData["awsCurTableSuffix"],
            jsonData["usageaccountid"])
    print_(query)

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
        run_bq_query_with_retries(client, query, max_retry_count=3, job_config=job_config)
        flatten_label_keys_in_table(client, jsonData.get("accountId"), PROJECTID, jsonData["datasetName"], UNIFIED,
                                    "labels", fetch_ingestion_filters(jsonData))
    except Exception as e:
        print_(query)
        raise e
    print_("Loaded into %s table..." % tableName)


def fetch_ingestion_filters(jsonData):
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])

    return """ DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' 
    AND cloudProvider = "AWS" AND awsUsageAccountId IN (%s) """ % (date_start, date_end, jsonData["usageaccountid"])


def ingest_data_to_costagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE(day) >= '%s' AND DATE(day) <= '%s'  AND cloudProvider = "AWS" AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId) 
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "AWS" AS cloudProvider, '%s' as accountId 
                FROM `%s`  
                WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' AND cloudProvider = "AWS" 
                GROUP BY day;
     """ % (table_name, date_start, date_end, jsonData.get("accountId"),
            table_name,
            jsonData.get("accountId"),
            source_table,
            date_start, date_end)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)


def alter_unified_table(jsonData):
    print_("Altering unifiedTable Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.unifiedTable` \
        ADD COLUMN IF NOT EXISTS awsBillingEntity STRING, \
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


def alter_awscur_table(jsonData):
    print_("Altering awscur Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.awscur_%s` \
        ADD COLUMN IF NOT EXISTS billingEntity STRING, \
        ADD COLUMN IF NOT EXISTS instanceFamily STRING, \
        ADD COLUMN IF NOT EXISTS marketOption STRING, \
        ADD COLUMN IF NOT EXISTS amortisedCost FLOAT64, \
        ADD COLUMN IF NOT EXISTS netAmortisedCost FLOAT64, \
        ADD COLUMN IF NOT EXISTS serviceName STRING;" % (ds, jsonData["awsCurTableSuffix"])

    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering awscur Table")
