# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

"""
CF2 for Azure data pipeline
"""
import json
import base64
import os
import sys
import re
from google.cloud import bigquery
from google.cloud import storage
from google.cloud import pubsub_v1
from google.cloud import functions_v2
import datetime
import util
import requests
from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CURRENCYCONVERSIONFACTORUSERINPUT, CEINTERNALDATASET, update_connector_data_sync_status, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES, send_event, \
    flatten_label_keys_in_table, LABELKEYSTOCOLUMNMAPPING, run_bq_query_with_retries
from calendar import monthrange

"""
Event format:
{
	"bucket": "azurecustomerbillingdata-qa",
	"path": "kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv",
	"accountId": "kmpysmuisimorrjl6nl73w",
	"triggerHistoricalCostUpdateInPreferredCurrency": True,
    "disableHistoricalUpdateForMonths": ['2022-12-01', '2022-11-01']
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)
publisher = pubsub_v1.PublisherClient()
STATIC_MARKUP_LIST = {
    "UVxMDMhNQxOCvroqqImWdQ": 5.04,  # AXA XL %age markup to costs
}
AZURESCHEMATOPIC = os.environ.get('AZURESCHEMATOPIC', f'projects/{PROJECTID}/topics/nikunjtesttopic')
AZURESCHEMA_TOPIC_PATH = publisher.topic_path(PROJECTID, AZURESCHEMATOPIC)
AZURE_COST_CF_TOPIC_NAME = os.environ.get('AZURECOSTCFTOPIC', 'nikunjtesttopic')
AZURE_COST_CF_TOPIC_PATH = publisher.topic_path(PROJECTID, AZURE_COST_CF_TOPIC_NAME)
COSTCATEGORIESUPDATETOPIC = os.environ.get('COSTCATEGORIESUPDATETOPIC', 'ccm-bigquery-batch-update')

def main(event, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    print(event)
    data = base64.b64decode(event['data']).decode('utf-8')
    event_json = json.loads(data)
    jsonData = event_json.get("data", {}).get("message", {})
    print(jsonData)

    # Set the accountId for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
    jsonData["cloudProvider"] = "AZURE"
    # path is folder name in this format vZYBQdFRSlesqo3CMB90Ag/myqO-niJS46aVm3b646SKA/cereportnikunj/20210201-20210228/
    # or in  vZYBQdFRSlesqo3CMB90Ag/myqO-niJS46aVm3b646SKA/<tenantid>/cereportnikunj/20210201-20210228/
    # or in 0Z0vv0uwRoax_oZ62jBFfg/tKFNTih2SPyIdWt_yJMZvg/8445d4f3-c4d8-4c5e-a6f1-743cee2c9e84/HarnessExport/20220501-20220531/.....partitioned csv format
    ps = jsonData["path"].split("/")
    path_length = len(ps)
    if path_length not in [3, 4, 5, 7]:
        raise Exception("Invalid path format %s" % jsonData["path"])
    else:
        if path_length in [3, 4]:
            # old flow
            jsonData["tenant_id"] = ""
            monthfolder = ps[-1]  # last folder in path
            jsonData["is_partitioned_csv"] = False
        elif path_length == 5:
            jsonData["tenant_id"] = ps[2]
            monthfolder = ps[-1]  # last folder in path
            jsonData["is_partitioned_csv"] = False


    jsonData["reportYear"] = monthfolder.split("-")[0][:4]
    jsonData["reportMonth"] = monthfolder.split("-")[0][4:6]

    jsonData["connectorId"] = ps[1]  # second from beginning is connector id in mongo

    accountIdBQ = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % (accountIdBQ)

    jsonData["tableSuffix"] = "%s_%s_%s" % (jsonData["reportYear"], jsonData["reportMonth"], jsonData["connectorId"])
    jsonData["tableName"] = f"azureBilling_{jsonData['tableSuffix']}"
    jsonData["tableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])

    create_dataset(client, jsonData["datasetName"], jsonData.get("accountId"))
    dataset = client.dataset(jsonData["datasetName"])

    preAggragatedTableRef = dataset.table("preAggregated")
    preAggregatedTableTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], PREAGGREGATED)
    unifiedTableRef = dataset.table("unifiedTable")
    unifiedTableTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], UNIFIED)
    currencyConversionFactorUserInputTableRef = dataset.table(CURRENCYCONVERSIONFACTORUSERINPUT)
    currencyConversionFactorUserInputTableName = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], CURRENCYCONVERSIONFACTORUSERINPUT)
    label_keys_to_column_mapping_table_ref = dataset.table(LABELKEYSTOCOLUMNMAPPING)

    if not if_tbl_exists(client, unifiedTableRef):
        print_("%s table does not exists, creating table..." % unifiedTableRef)
        createTable(client, unifiedTableRef)
    else:
        # Enable these only when needed.
        # alter_unified_table(jsonData)
        print_("%s table exists" % unifiedTableTableName)

    if not if_tbl_exists(client, preAggragatedTableRef):
        print_("%s table does not exists, creating table..." % preAggragatedTableRef)
        createTable(client, preAggragatedTableRef)
    else:
        # Enable these only when needed.
        # alter_preagg_table(jsonData)
        print_("%s table exists" % preAggregatedTableTableName)

    if not if_tbl_exists(client, currencyConversionFactorUserInputTableRef):
        print_("%s table does not exists, creating table..." % currencyConversionFactorUserInputTableName)
        createTable(client, currencyConversionFactorUserInputTableRef)
    else:
        print_("%s table exists" % currencyConversionFactorUserInputTableName)

    if not if_tbl_exists(client, label_keys_to_column_mapping_table_ref):
        print_("%s table does not exist, creating table..." % LABELKEYSTOCOLUMNMAPPING)
        createTable(client, label_keys_to_column_mapping_table_ref)
    else:
        print_("%s table exists" % LABELKEYSTOCOLUMNMAPPING)

    ds = f"{PROJECTID}.{jsonData['datasetName']}"
    table_ids = ["%s.%s" % (ds, "unifiedTable"),
                 "%s.%s" % (ds, "preAggregated")]
    # azurecost tables will have the new currency column(s) since we delete and create table again
    add_currency_preferences_columns_to_schema(client, table_ids)

    # start streaming the data from the gcs
    print_("%s table exists. Starting to write data from gcs into it..." % jsonData["tableName"])

    if not ingest_data_from_csv(jsonData):
        return
    azure_column_mapping = setAvailableColumns(jsonData)

    get_preferred_currency(jsonData)
    insert_currencies_with_unit_conversion_factors_in_bq(jsonData, azure_column_mapping)
    initialize_fx_rates_dict(jsonData, azure_column_mapping)
    fetch_default_conversion_factors_from_API(jsonData)
    fetch_default_conversion_factors_from_billing_export(jsonData)
    fetch_custom_conversion_factors(jsonData)
    verify_existence_of_required_conversion_factors(jsonData)
    update_fx_rate_column_in_raw_table(jsonData, azure_column_mapping)

    # sending event to pubsub to create azurecost_* tables
    send_event(AZURE_COST_CF_TOPIC_PATH, {
        "tableId": jsonData["tableId"],
        "azure_column_mapping": azure_column_mapping,
        "ccmPreferredCurrency": jsonData["ccmPreferredCurrency"]
    })

    get_unique_subs_id(jsonData, azure_column_mapping)
    ingest_data_into_preagg(jsonData, azure_column_mapping)
    ingest_data_into_unified(jsonData, azure_column_mapping)
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
            "cloudProvider": "AZURE",
            "cloudProviderAccountIds": jsonData["subsIdsList"]
        }
    })
    print_("Completed")


def trigger_historical_cost_update_in_preferred_currency(jsonData):
    if not jsonData["disableHistoricalUpdateForMonths"]:
        jsonData["disableHistoricalUpdateForMonths"] = [f"{jsonData['reportYear']}-{jsonData['reportMonth']}-01"]

    # get months for which historical update needs to be triggered, and custom conversion factors
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """SELECT month, conversionType, sourceCurrency, destinationCurrency, conversionFactor
                FROM `%s.%s`
                WHERE accountId="%s" AND destinationCurrency is NOT NULL AND isHistoricalUpdateRequired = TRUE
                AND cloudServiceProvider = "AZURE"
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
                WHERE cloudServiceProvider = "AZURE" 
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
            "cloudServiceProvider": "AZURE",
            "months": list(historical_update_months),
            "userInputFxRates": custom_factors_dict
        }
        url = get_cf_v2_uri(f"projects/{PROJECTID}/locations/us-central1/functions/ce-azure-historical-currency-update-bq-terraform")
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
            print_("Encountered an exception during triggering historical update for Azure (Ignore if timeout exception).")


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
                FROM %s.%s
                WHERE accountId="%s" and destinationCurrency is NOT NULL LIMIT 1;
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT, jsonData.get("accountId"))
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

    if jsonData["ccmPreferredCurrency"] is None:
        print_("No preferred-currency found for account %s" % jsonData.get("accountId"))


def update_fx_rate_column_in_raw_table(jsonData, azure_column_mapping):
    if not jsonData["ccmPreferredCurrency"]:
        return

    # add fxRateSrcToDest column if not exists
    print_("Altering raw azureBilling Table - adding fxRateSrcToDest column")
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
        print_("Finished Altering azureBilling Table")

    # update value of fxRateSrcToDest column using dict
    fx_rate_case_when_query = "CASE %s " % (azure_column_mapping['currency'])
    for sourceCurrency in jsonData["fx_rates_srcCcy_to_destCcy"]:
        fx_rate_case_when_query += f" WHEN '{sourceCurrency}' THEN CAST({jsonData['fx_rates_srcCcy_to_destCcy'][sourceCurrency]} AS FLOAT64) "
    fx_rate_case_when_query += f" WHEN 'NONE' THEN CAST(1.0 AS FLOAT64) ELSE CAST(1.0 AS FLOAT64) END"

    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s) WHERE %s IS NOT NULL;
                """ % (jsonData["tableId"], fx_rate_case_when_query, azure_column_mapping['currency'])
    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to update fxRateSrcToDest column in raw table %s" % (jsonData["tableId"]), "WARN")


def insert_currencies_with_unit_conversion_factors_in_bq(jsonData, azure_column_mapping):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year

    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])

    # update 1.0 rows (BILLING_EXPORT_SRC_CCY) in currencyConversionFactorDefault table only for current month
    if str(year) != str(currentYear) or str(month) != str(currentMonth):
        return

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId = '%s' AND cloudServiceProvider = "AZURE" AND sourceCurrency = destinationCurrency 
                AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month < DATE('%s');
               INSERT INTO `%s.CE_INTERNAL.currencyConversionFactorDefault` 
               (accountId,cloudServiceProvider,sourceCurrency,destinationCurrency,
               conversionFactor,month,conversionSource,createdAt,updatedAt)
                   SELECT distinct 
                   '%s' as accountId,
                   "AZURE" as cloudServiceProvider,
                   %s as sourceCurrency,
                   %s as destinationCurrency,
                   1.0 as conversionFactor,
                   DATE('%s') as month,
                   "BILLING_EXPORT_SRC_CCY" as conversionSource,
                   TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
                   FROM `%s` 
                   where %s not in (select distinct sourceCurrency FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                   WHERE accountId = '%s' AND cloudServiceProvider = "AZURE" AND sourceCurrency = destinationCurrency 
                   AND conversionSource = "BILLING_EXPORT_SRC_CCY" AND month = DATE('%s'));
    """ % (PROJECTID,
           jsonData.get("accountId"),
           date_start,
           PROJECTID,
           jsonData.get("accountId"),
           azure_column_mapping["currency"],
           azure_column_mapping["currency"],
           date_start,
           current_timestamp, current_timestamp,
           jsonData["tableId"], azure_column_mapping["currency"], PROJECTID,
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


def initialize_fx_rates_dict(jsonData, azure_column_mapping):
    if jsonData["ccmPreferredCurrency"] is None:
        return
    jsonData["fx_rates_srcCcy_to_destCcy"] = {}

    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    query = """SELECT distinct %s
                FROM `%s`;
                """ % (azure_column_mapping["currency"], jsonData["tableId"])
    try:
        print_(query)
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["fx_rates_srcCcy_to_destCcy"][row[f'{azure_column_mapping["currency"]}'].upper()] = None
    except Exception as e:
        print_(e)
        print_("Failed to list distinct AZURE source-currencies for account", "WARN")


def fetch_default_conversion_factors_from_API(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if jsonData["ccmPreferredCurrency"] is None:
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
            print_(f"fxRate for {srcCurrency.upper()} to {jsonData['ccmPreferredCurrency']} was not found in API response.")

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
    if jsonData["ccmPreferredCurrency"] is None or "exchangeratepricingtobilling" not in jsonData["columns"]:
        return

    fx_rates_from_billing_export = []
    query = """SELECT distinct pricingCurrency, billingCurrency, exchangeRatePricingToBilling
                from `%s`;
                """ % (jsonData["tableId"])
    try:
        print_(query)
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            fx_rates_from_billing_export.append({
                "sourceCurrency": row.pricingCurrency.upper(),
                "destinationCurrency": row.billingCurrency.upper(),
                "fxRate": float(row.exchangeRatePricingToBilling)
            })
            if row.pricingCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"] and jsonData["ccmPreferredCurrency"] == row.billingCurrency.upper():
                jsonData["fx_rates_srcCcy_to_destCcy"][row.pricingCurrency.upper()] = float(row.exchangeRatePricingToBilling)
    except Exception as e:
        print_(e)
        print_("Failed to fetch conversion-factors from the BILLING_EXPORT", "WARN")

    # update currencyConversionFactorDefault table if reportMonth is current month
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    current_timestamp = datetime.datetime.utcnow()
    currentMonth = f"{current_timestamp.month:02d}"
    currentYear = current_timestamp.year
    if str(year) != str(currentYear) or str(month) != str(currentMonth):
        return

    currency_pairs_from_billing_export = ", ".join([f"'{row['sourceCurrency']}_{row['destinationCurrency']}'" for row in fx_rates_from_billing_export])
    select_query = ""
    for row in fx_rates_from_billing_export:
        if select_query:
            select_query += " UNION ALL "
        select_query += """
        SELECT '%s' as accountId, 'AZURE' as cloudServiceProvider,
        '%s' as sourceCurrency, '%s' as destinationCurrency, %s as conversionFactor, DATE('%s') as month,
        "BILLING_EXPORT" as conversionSource,
        TIMESTAMP('%s') as createdAt, TIMESTAMP('%s') as updatedAt 
        """ % (jsonData.get("accountId"), row['sourceCurrency'], row['destinationCurrency'],
               row['fxRate'], date_start, current_timestamp, current_timestamp)

    query = """DELETE FROM `%s.CE_INTERNAL.currencyConversionFactorDefault` 
                WHERE accountId = '%s' AND conversionSource = "BILLING_EXPORT" 
                AND cloudServiceProvider = 'AZURE' 
                AND CONCAT(sourceCurrency,'_',destinationCurrency) in (%s);
                    
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
    if jsonData["ccmPreferredCurrency"] is None:
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
                and cloudServiceProvider="AZURE" 
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
                and customFxRates.cloudServiceProvider="AZURE" 
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
    try:
        print_(query)
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            # update if latest user input had entered a custom fxrate
            if row.sourceCurrency.upper() in jsonData["fx_rates_srcCcy_to_destCcy"] and row.conversion_type == "CUSTOM":
                jsonData["fx_rates_srcCcy_to_destCcy"][row.sourceCurrency.upper()] = float(row.fx_rate)
    except Exception as e:
        print_(e)
        print_("Failed to fetch custom conversion-factors for account", "WARN")


def verify_existence_of_required_conversion_factors(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if not jsonData["ccmPreferredCurrency"]:
        return
    if not all(jsonData["fx_rates_srcCcy_to_destCcy"].values()):
        print_(jsonData["fx_rates_srcCcy_to_destCcy"])
        print_("Required fx rate not found for at least one currency pair", "ERROR")
    # throw error here. CF execution can't proceed from here


def is_valid_month_folder(folderstr):
    folderstr = folderstr.split('/')[-1]
    print_(folderstr)
    try:
        report_month = folderstr.split("-")
        startstr = report_month[0]
        endstr = report_month[1]
        if (len(startstr) != 8) or (len(endstr) != 8):
            raise
        if not int(endstr) > int(startstr):
            raise
        # Check for valid dates
        datetime.datetime.strptime(startstr, '%Y%m%d')
        datetime.datetime.strptime(endstr, '%Y%m%d')
    except Exception as e:
        # Any error we should not take this path for processing
        return False
    return True

def ingest_data_from_csv(jsonData):
    csvtoingest = None
    # Determine either 'sub folder' of highest size in this month folder or max size csv
    blobs = storage_client.list_blobs(
        jsonData["bucket"], prefix=jsonData["path"]
    )
    subfolder_maxsize = 0
    blob_maxsize = 0
    single_csvtoingest = None
    partitioned_csvtoingest = None
    unique_subfolder_size_map = {}
    for blob in blobs:
        if blob.name.endswith(".csv") or blob.name.endswith(".csv.gz"):
            folder = "/".join(blob.name.split('/')[:-1])
            if is_valid_month_folder(folder):
                # Unpartitioned CSV way
                if blob.size > blob_maxsize:
                    blob_maxsize = blob.size
                    single_csvtoingest = blob.name
            else:
                # Partitioned CSV way
                try:
                    unique_subfolder_size_map[folder] += blob.size
                except:
                    unique_subfolder_size_map[folder] = blob.size
                if unique_subfolder_size_map[folder] > subfolder_maxsize:
                    subfolder_maxsize = unique_subfolder_size_map[folder]
                    partitioned_csvtoingest = folder + "/*.csv"
        print_("blob_maxsize: %s, single_csvtoingest: %s, subfolder_maxsize: %s, partitioned_csvtoingest: %s" %
               (blob_maxsize, single_csvtoingest, subfolder_maxsize, partitioned_csvtoingest))
    # We can have both partitioned and non partitioned in the same folder
    if blob_maxsize > subfolder_maxsize:
        csvtoingest = single_csvtoingest
    else:
        csvtoingest = partitioned_csvtoingest

    if not csvtoingest:
        print_("No CSV to insert. GCS bucket might be empty", "WARN")
        return True
    print_("csvtoingest: %s" % csvtoingest)

    job_config = bigquery.LoadJobConfig(
        max_bad_records=10,  # TODO: Temporary fix until https://issuetracker.google.com/issues/74021820 is available
        autodetect=True,
        skip_leading_rows=1,
        field_delimiter=",",
        ignore_unknown_values=True,
        source_format="CSV",
        allow_quoted_newlines=True,
        allow_jagged_rows=True,
        write_disposition='WRITE_TRUNCATE'  # If the table already exists, BigQuery overwrites the table data
    )
    jsonData["uri"] = "gs://" + jsonData["bucket"] + "/" + csvtoingest
    jsonData["csvtoingest"] = csvtoingest
    print_("Ingesting CSV from %s" % jsonData["uri"])
    print_("Loading into %s table..." % jsonData["tableId"])
    load_job = client.load_table_from_uri(
        [jsonData["uri"]],
        jsonData["tableId"],
        job_config=job_config
    )
    print_(load_job.job_id)
    try:
        load_job.result()  # Wait for the job to complete.
    except Exception as e:
        print_(e, "WARN")
        print_("Ingesting in existing table if exists")
        job_config.autodetect = False
        load_job = client.load_table_from_uri(
            [jsonData["uri"]],
            jsonData["tableId"],
            job_config=job_config
        )
        print_(load_job.job_id)
        try:
            load_job.result()  # Wait for the job to complete.
        except Exception as e:
            print_(e, "WARN")
            print_("Ingesting in existing table failed. Sending event to generate dynamic schema.\n"
                   "Ingestion with new schema will be automatically retried in next 1 hr", "WARN")
            send_event(AZURESCHEMA_TOPIC_PATH, jsonData)
            # Cleanly exit from CF at this point
            print_("Exiting..")
            return False

    table = client.get_table(jsonData["tableId"])
    print_("Total {} rows in table {}".format(table.num_rows, jsonData["tableId"]))

    # cleanup the processed blobs
    blobs = storage_client.list_blobs(
        jsonData["bucket"], prefix=jsonData["path"]
    )
    for blob in blobs:
        blob.delete()
        print_("Cleaned up {}.".format(blob.name))
    return True

def send_event(topic_path, jsonData):
    message_json = json.dumps(jsonData)
    message_bytes = message_json.encode('utf-8')
    try:
        publish_future = publisher.publish(topic_path, data=message_bytes)
        publish_future.result()  # Verify the publish succeeded
        print('Message published: %s.' % jsonData)
    except Exception as e:
        print(e)

def get_unique_subs_id(jsonData, azure_column_mapping):
    # Get unique subsids from main azureBilling table
    query = """ 
            SELECT DISTINCT(%s) as subscriptionid FROM `%s`;
            """ % (azure_column_mapping["azureSubscriptionGuid"], jsonData["tableId"])
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        subsids = []
        for row in results:
            subsids.append(row.subscriptionid)
        jsonData["subsIdsList"] = subsids
        jsonData["subsId"] = ", ".join(f"'{w}'" for w in subsids)
    except Exception as e:
        print_("Failed to retrieve distinct subsids", "WARN")
        jsonData["subsId"] = ""
        jsonData["subsIdsList"] = []
        raise e
    print_("Found unique subsids %s" % subsids)


def setAvailableColumns(jsonData):
    # Ref: https://docs.microsoft.com/en-us/azure/cost-management-billing/understand/mca-understand-your-usage
    # BQ column names are case insensitive.
    azure_column_mapping = {
        "startTime": "",
        "azureResourceRate": "",
        "cost": "",
        "region": "",
        "azureSubscriptionGuid": "",
        "azureInstanceId": "",
        "currency": "",
    }

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
        jsonData["columns"] = columns
        print_("Retrieved available columns: %s" % columns)
    except Exception as e:
        print_("Failed to retrieve available columns", "WARN")
        jsonData["columns"] = None
        raise e

    # startTime
    if "date" in columns:  # This is O(1) for python sets
        azure_column_mapping["startTime"] = "date"
    elif "usagedatetime" in columns:
        azure_column_mapping["startTime"] = "usagedatetime"
    else:
        raise Exception("No mapping found for startTime column")

    # azureResourceRate
    if "effectiveprice" in columns:
        azure_column_mapping["azureResourceRate"] = "effectiveprice"
    elif "resourcerate" in columns:
        azure_column_mapping["azureResourceRate"] = "resourcerate"
    else:
        raise Exception("No mapping found for azureResourceRate column")

    # currency
    if "billingcurrency" in columns:
        azure_column_mapping["currency"] = "billingcurrency"
    elif "currency" in columns:
        azure_column_mapping["currency"] = "currency"
    elif "billingcurrencycode" in columns:
        azure_column_mapping["currency"] = "billingcurrencycode"
    else:
        raise Exception("No mapping found for currency column")

    # cost
    if "costinbillingcurrency" in columns:
        azure_column_mapping["cost"] = "costinbillingcurrency"
    elif "pretaxcost" in columns:
        azure_column_mapping["cost"] = "pretaxcost"
    elif "cost" in columns:
        azure_column_mapping["cost"] = "cost"
    else:
        raise Exception("No mapping found for cost column")

    # azureSubscriptionGuid
    if "subscriptionid" in columns:
        azure_column_mapping["azureSubscriptionGuid"] = "subscriptionid"
    elif "subscriptionguid" in columns:
        azure_column_mapping["azureSubscriptionGuid"] = "subscriptionguid"
    else:
        raise Exception("No mapping found for azureSubscriptionGuid column")

    # azureInstanceId
    if "resourceid" in columns:
        azure_column_mapping["azureInstanceId"] = "resourceid"
    elif "instanceid" in columns:
        azure_column_mapping["azureInstanceId"] = "instanceid"
    else:
        raise Exception("No mapping found for azureInstanceId column")

    # azureResourceGroup
    if "resourcegroup" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroup"
    elif "resourcegroupname" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroupname"
    else:
        raise Exception("No mapping found for azureResourceGroup column")

    print_("azure_column_mapping: %s" % azure_column_mapping)
    return azure_column_mapping


def ingest_data_into_preagg(jsonData, azure_column_mapping):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    tableName = "%s.%s" % (ds, "preAggregated")
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s preAggregated table..." % tableName)

    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""

    query = """DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' 
                AND cloudProvider = "AZURE" AND azureSubscriptionGuid IN (%s);
           INSERT INTO `%s.preAggregated` (startTime, azureResourceRate, cost,
                                           azureServiceName, region, azureSubscriptionGuid,
                                            cloudProvider, azureTenantId, fxRateSrcToDest, ccmPreferredCurrency)
           SELECT IF(REGEXP_CONTAINS(CAST(%s AS STRING), r'^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\d{4}$'), 
                     PARSE_TIMESTAMP("%%m/%%d/%%Y", CAST(%s AS STRING)), 
                     TIMESTAMP(%s)) as startTime, 
                min(%s %s) AS azureResourceRate, sum(%s %s) AS cost,
                MeterCategory AS azureServiceName, ResourceLocation as region, %s as azureSubscriptionGuid,
                "AZURE" AS cloudProvider, '%s' as azureTenantId, 
                %s as fxRateSrcToDest, %s as ccmPreferredCurrency 
           FROM `%s`
           WHERE %s IN (%s)
           GROUP BY azureServiceName, region, azureSubscriptionGuid, startTime;
    """ % (ds, date_start, date_end,
           jsonData["subsId"],
           ds,
           azure_column_mapping["startTime"], azure_column_mapping["startTime"], azure_column_mapping["startTime"],
           azure_column_mapping["azureResourceRate"], fx_rate_multiplier_query,
           azure_column_mapping["cost"], fx_rate_multiplier_query, azure_column_mapping["azureSubscriptionGuid"], jsonData["tenant_id"],
           ("max(fxRateSrcToDest)" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
           (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData["ccmPreferredCurrency"] else "cast(null as string)"),
           jsonData["tableId"],
           azure_column_mapping["azureSubscriptionGuid"], jsonData["subsId"])

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
    query_job.result()
    print_("Loaded into %s table..." % tableName)


def ingest_data_into_unified(jsonData, azure_column_mapping):
    # create_bq_udf() # Enable this only when needed.
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    tableName = "%s.%s" % (ds, "unifiedTable")
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s table..." % tableName)

    fx_rate_multiplier_query = "*fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else ""

    # Prepare default columns
    insert_columns = """product, startTime, cost,
                        azureMeterCategory, azureMeterSubcategory, azureMeterId,
                        azureMeterName,
                        azureInstanceId, region, azureResourceGroup,
                        azureSubscriptionGuid, azureServiceName,
                        cloudProvider, labels, azureResource, azureVMProviderId, azureTenantId,
                        azureResourceRate, fxRateSrcToDest, ccmPreferredCurrency
                    """
    select_columns = """MeterCategory AS product, 
                        IF(REGEXP_CONTAINS(CAST(%s AS STRING), r'^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\d{4}$'), 
                            PARSE_TIMESTAMP("%%m/%%d/%%Y", CAST(%s AS STRING)), 
                            TIMESTAMP(%s)) as startTime,
                        (%s*%s %s) AS cost,
                        MeterCategory as azureMeterCategory,MeterSubcategory as azureMeterSubcategory,MeterId as azureMeterId,
                        MeterName as azureMeterName,
                        %s as azureInstanceId, ResourceLocation as region,  %s as azureResourceGroup,
                        %s as azureSubscriptionGuid, MeterCategory as azureServiceName,
                        "AZURE" AS cloudProvider, `%s.%s.jsonStringToLabelsStruct`(Tags) as labels,
                        ARRAY_REVERSE(SPLIT(%s,REGEXP_EXTRACT(%s, r'(?i)providers/')))[OFFSET(0)] as azureResource,
                        IF(REGEXP_CONTAINS(%s, r'virtualMachineScaleSets'),
                            LOWER(CONCAT('azure://', %s, '/virtualMachines/',
                                REGEXP_EXTRACT(JSON_VALUE(AdditionalInfo, '$.VMName'), r'_([0-9]+)$') )),
                            IF(REGEXP_CONTAINS(%s, r'virtualMachines'),
                                LOWER(CONCAT('azure://', %s)),
                                null)),
                        '%s' as azureTenantId,
                        (%s %s) AS azureResourceRate,
                         %s as fxRateSrcToDest, %s as ccmPreferredCurrency 
                     """ % (azure_column_mapping["startTime"], azure_column_mapping["startTime"], azure_column_mapping["startTime"],
                            azure_column_mapping["cost"], get_cost_markup_factor(jsonData), fx_rate_multiplier_query,
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureResourceGroup"],
                            azure_column_mapping["azureSubscriptionGuid"], PROJECTID, CEINTERNALDATASET,
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"], azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"], azure_column_mapping["azureInstanceId"],
                            jsonData["tenant_id"], azure_column_mapping["azureResourceRate"], fx_rate_multiplier_query,
                            ("fxRateSrcToDest" if jsonData["ccmPreferredCurrency"] else "cast(null as float64)"),
                            (f"'{jsonData['ccmPreferredCurrency']}'" if jsonData["ccmPreferredCurrency"] else "cast(null as string)"))

    # Amend query as per columns availability
    for additionalColumn in ["AccountName", "Frequency", "PublisherType", "ServiceTier", "ResourceType",
                             "SubscriptionName", "ReservationId", "ReservationName", "PublisherName",
                             "CustomerName", "BillingCurrency"]:
        if additionalColumn.lower() in jsonData["columns"]:
            insert_columns = insert_columns + ", azure%s" % additionalColumn
            select_columns = select_columns + ", %s as azure%s" % (additionalColumn, additionalColumn)

    query = """DELETE FROM `%s.unifiedTable` WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s'  
                AND cloudProvider = "AZURE" AND azureSubscriptionGuid IN (%s);
               INSERT INTO `%s.unifiedTable` (%s)
               SELECT %s
               FROM `%s` 
               WHERE %s IN (%s);
            """ % (
        ds, date_start, date_end, jsonData["subsId"], ds, insert_columns, select_columns, jsonData["tableId"],
        azure_column_mapping["azureSubscriptionGuid"], jsonData["subsId"])

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
        # flatten_label_keys_in_table(client, jsonData.get("accountId"), PROJECTID, jsonData["datasetName"], UNIFIED,
        #                             "labels", fetch_ingestion_filters(jsonData))
    except Exception as e:
        print_(e, "ERROR")
        raise e
    print_("Loaded into %s table..." % tableName)


def fetch_ingestion_filters(jsonData):
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])

    return """ DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' 
    AND cloudProvider = "AZURE" AND azureSubscriptionGuid IN (%s) """ % (date_start, date_end, jsonData["subsId"])


def create_bq_udf():
    create_dataset(client, CEINTERNALDATASET)
    query = """CREATE FUNCTION IF NOT EXISTS `%s.%s.jsonStringToLabelsStruct`(input STRING)
                RETURNS Array<STRUCT<key String, value String>>
                LANGUAGE js AS \"""
                var output = []
                if(!input || input.length === 0) {
                    return output;
                }
                try {
                    var data = JSON.parse(input);
                } catch (SyntaxError) {
                    input="{".concat(input, "}")
                    try {
                        var data = JSON.parse(input);
                    } catch (SyntaxError) {
                        return output;
                    }
                }
                for (const [key, value] of Object.entries(data)) {
                    newobj = {};
                    newobj.key = key;
                    newobj.value = value;
                    output.push(newobj);
                };
                return output;
                \""";
    """ % (PROJECTID, CEINTERNALDATASET)
    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        print_(e)


def alter_preagg_table(jsonData):
    print_("Altering Preaggregated Data Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.preAggregated` \
            ADD COLUMN IF NOT EXISTS azureSubscriptionGuid STRING, \
            ADD COLUMN IF NOT EXISTS azureResourceRate FLOAT64, \
            ADD COLUMN IF NOT EXISTS azureServiceName STRING, \
            ADD COLUMN IF NOT EXISTS azureTenantId STRING;" % ds

    try:
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering preAggregated Table")


def alter_unified_table(jsonData):
    print_("Altering unifiedTable Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.unifiedTable` \
        ADD COLUMN IF NOT EXISTS azureMeterCategory STRING, \
        ADD COLUMN IF NOT EXISTS azureResourceRate FLOAT64, \
        ADD COLUMN IF NOT EXISTS azureMeterSubcategory STRING, \
        ADD COLUMN IF NOT EXISTS azureMeterId STRING, \
        ADD COLUMN IF NOT EXISTS azureMeterName STRING, \
        ADD COLUMN IF NOT EXISTS azureResourceType STRING, \
        ADD COLUMN IF NOT EXISTS azureServiceTier STRING, \
        ADD COLUMN IF NOT EXISTS azureInstanceId STRING, \
        ADD COLUMN IF NOT EXISTS azureResourceGroup STRING, \
        ADD COLUMN IF NOT EXISTS azureSubscriptionGuid STRING, \
        ADD COLUMN IF NOT EXISTS azureAccountName STRING, \
        ADD COLUMN IF NOT EXISTS azureFrequency STRING, \
        ADD COLUMN IF NOT EXISTS azurePublisherType STRING, \
        ADD COLUMN IF NOT EXISTS azureSubscriptionName STRING, \
        ADD COLUMN IF NOT EXISTS azureReservationId STRING, \
        ADD COLUMN IF NOT EXISTS azureReservationName STRING, \
        ADD COLUMN IF NOT EXISTS azurePublisherName STRING, \
        ADD COLUMN IF NOT EXISTS azureServiceName STRING, \
        ADD COLUMN IF NOT EXISTS azureVMProviderId STRING, \
        ADD COLUMN IF NOT EXISTS azureResource STRING, \
        ADD COLUMN IF NOT EXISTS azureTenantId STRING, \
        ADD COLUMN IF NOT EXISTS azureCustomerName STRING, \
        ADD COLUMN IF NOT EXISTS azureBillingCurrency STRING, \
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


def get_cost_markup_factor(jsonData):
    # Try to get custom markup from event data. if not fallback to static list
    markuppercent = jsonData.get("costMarkUp", 0) or STATIC_MARKUP_LIST.get(jsonData["accountId"], 0)
    if markuppercent != 0:
        return 1 + markuppercent / 100
    else:
        return 1

def ingest_data_to_costagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    year, month = jsonData["reportYear"], jsonData["reportMonth"]
    date_start = "%s-%s-01" % (year, month)
    date_end = "%s-%s-%s" % (year, month, monthrange(int(year), int(month))[1])
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE(day) >= '%s' AND DATE(day) <= '%s'  AND cloudProvider = "AZURE" AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId)
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "AZURE" AS cloudProvider, '%s' as accountId
                FROM `%s`  
                WHERE DATE(startTime) >= '%s' and DATE(startTime) <= '%s' and cloudProvider = "AZURE" 
                GROUP BY day;
     """ % (table_name, date_start, date_end, jsonData.get("accountId"), table_name, jsonData.get("accountId"),
            source_table, date_start, date_end)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)
