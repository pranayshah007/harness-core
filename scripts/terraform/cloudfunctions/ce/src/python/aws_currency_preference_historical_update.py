# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import base64
import os
import re
import datetime
import util
import requests

from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CEINTERNALDATASET, CURRENCYCONVERSIONFACTORUSERINPUT, update_connector_data_sync_status, \
    add_currency_preferences_columns_to_schema, CURRENCY_LIST, BACKUP_CURRENCY_FX_RATES
from calendar import monthrange
from google.cloud import bigquery
from google.cloud import storage

"""
{
	"accountId": "nvsv7gjbtzya3cgsgxnocg",
	"cloudServiceProvider": "AWS",
	"months": ["2022-12-01", "2022-11-01"], # pass only months for which there won't be any concurrent access with regular CFs
	"userInputFxRates": {
        "INR": 82.48
    } 
    # if fxRate not found in this list, use default rates from CUR / API
	# For AWS:
	# will update all awscur_2022_12, unifeid, preagg, costagg(rerun query)
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)


def main(request):
    try:
        print(request)
        jsonData = request.get_json(force=True)
        print(jsonData)

        # Set accountid for GCP logging
        util.ACCOUNTID_LOG = jsonData.get("accountId")

        jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
        jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]

        # fetch fxRates from billing_export & API/Default_table if userInput of CF doesn't have fxRate for ccy pair
        get_preferred_currency(jsonData)
        if not jsonData["ccmPreferredCurrency"]:
            return
        initialize_fx_rates_dict(jsonData)
        fetch_default_conversion_factors_from_API(jsonData)
        fetch_custom_conversion_factors(jsonData)
        verify_existence_of_required_conversion_factors(jsonData)

        for billing_month in jsonData["months"]:
            month = billing_month.split('-')[1]
            year = billing_month.split('-')[0]
            awscur_tableid = "%s.%s.awscur_%s_%s" % (PROJECTID, jsonData["datasetName"], year, month)
            add_currency_preferences_columns_to_schema(client, [awscur_tableid])

            create_fx_rate_query(jsonData, "usageaccountid", "usagestartdate")
            update_costs_in_awscur_table(awscur_tableid, jsonData)

        ds = f"{PROJECTID}.{jsonData['datasetName']}"
        table_ids = ["%s.%s" % (ds, "unifiedTable"),
                     "%s.%s" % (ds, "preAggregated")]
        add_currency_preferences_columns_to_schema(client, table_ids)

        create_fx_rate_query(jsonData, "awsUsageaccountid", "startTime")
        update_costs_in_unified_table("%s.%s" % (ds, "unifiedTable"), jsonData)

        create_fx_rate_query(jsonData, "awsUsageaccountid", "startTime")
        update_costs_in_preagg_table("%s.%s" % (ds, "preAggregated"), jsonData)

        # ingest_data_to_costagg(jsonData)

        unset_historical_update_flag_in_bq(jsonData)

        return "Historical cost successfully converted in preferred currency."

    except Exception as e:
        print_(e)
        return "Failed to convert historical cost data in preferred currency."


def unset_historical_update_flag_in_bq(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = """UPDATE `%s.%s` 
                SET isHistoricalUpdateRequired = FALSE
                WHERE cloudServiceProvider = "AWS" 
                AND month in (%s)
                AND accountId = '%s';
                """ % (ds, CURRENCYCONVERSIONFACTORUSERINPUT,
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]),
                       jsonData.get("accountId"))
    print_(query)
    try:
        query_job = client.query(query)
        query_job.result()  # wait for job to complete
    except Exception as e:
        print_(e)
        print_("Failed to unset isHistoricalUpdateRequired flags in CURRENCYCONVERSIONFACTORUSERINPUT table", "WARN")


def ingest_data_to_costagg(jsonData):
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE_TRUNC( DATE(day), month ) in (%s) AND cloudProvider = "AWS" AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId) 
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, "AWS" AS cloudProvider, '%s' as accountId 
                FROM `%s`  
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AWS" 
                GROUP BY day;
     """ % (table_name, ", ".join(f"DATE('{month}')" for month in jsonData["months"]), jsonData.get("accountId"),
            table_name,
            jsonData.get("accountId"),
            source_table,
            ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    print_(query)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)


def update_costs_in_preagg_table(table_id, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                awsBlendedCost = (awsBlendedCost * (%s)), 
                awsUnblendedCost = (awsUnblendedCost * (%s)), 
                cost = (cost * (%s))
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AWS"
                AND ccmPreferredCurrency is NULL;
                """ % (table_id, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in pre-aggregated table %s" % table_id, "WARN")


def update_costs_in_unified_table(table_id, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                awsBlendedCost = (awsBlendedCost * (%s)), 
                awsUnblendedCost = (awsUnblendedCost * (%s)), 
                cost = (cost * (%s))
                WHERE DATE_TRUNC( DATE(startTime), month ) in (%s) AND cloudProvider = "AWS" 
                AND ccmPreferredCurrency is NULL;
                """ % (table_id, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in unified table %s" % table_id, "WARN")


def update_costs_in_awscur_table(awscur_tableid, jsonData):
    query = """UPDATE `%s` 
                SET fxRateSrcToDest = (%s), 
                ccmPreferredCurrency = '%s',
                blendedcost = (blendedcost * (%s)), 
                unblendedcost = (unblendedcost * (%s)), 
                effectivecost = (effectivecost * (%s)), 
                amortisedCost = (amortisedCost * (%s)), 
                netAmortisedCost = (netAmortisedCost * (%s))
                WHERE ccmPreferredCurrency is NULL;
                """ % (awscur_tableid, jsonData["fx_rate_query"], jsonData["ccmPreferredCurrency"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"], jsonData["fx_rate_query"],
                       jsonData["fx_rate_query"], jsonData["fx_rate_query"])
    try:
        job_config = bigquery.QueryJobConfig(
            priority=bigquery.QueryPriority.BATCH
        )
        run_batch_query(client, query, job_config, timeout=1800)
    except Exception as e:
        print_(e)
        print_("Failed to update cost columns in awscur table %s" % awscur_tableid, "WARN")


def create_fx_rate_query(jsonData, usageaccountid_column_name, usagestartdate_column_name):
    jsonData["fx_rate_query"] = "CASE "
    for usageaccount_usagemonth in jsonData["source_currency_mapping"]:
        billing_month = usageaccount_usagemonth.split('_')[1]
        jsonData["fx_rate_query"] += "WHEN CONCAT( %s, '_', DATE_TRUNC(DATE(%s), month) ) = '%s' " \
                                     "THEN CAST(%s AS FLOAT64) " \
                                     % (usageaccountid_column_name, usagestartdate_column_name, usageaccount_usagemonth,
                                        jsonData['fx_rates_srcCcy_to_destCcy'][billing_month][jsonData["source_currency_mapping"][usageaccount_usagemonth]])
    jsonData["fx_rate_query"] += " WHEN 1=0 THEN CAST(1.0 AS FLOAT64) "
    jsonData["fx_rate_query"] += " ELSE CAST(1.0 AS FLOAT64) END"


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


def initialize_fx_rates_dict(jsonData):
    if not jsonData["ccmPreferredCurrency"]:
        return
    jsonData["fx_rates_srcCcy_to_destCcy"] = {}
    jsonData["source_currency_mapping"] = {}
    # jsonData["months"] = set()

    query = """SELECT usageaccountid, DATE_TRUNC(DATE(usagestartdate), month) as usagemonth, max(currencycode) as currencycode
                FROM `%s.%s.awsBilling_*`
                WHERE DATE_TRUNC(DATE(usagestartdate), month) in (%s)
                GROUP BY usageaccountid, usagemonth;
                """ % (PROJECTID, jsonData["datasetName"],
                       ", ".join(f"DATE('{month}')" for month in jsonData["months"]))
    print_(query)
    try:
        query_job = client.query(query)
        results = query_job.result()  # wait for job to complete
        for row in results:
            jsonData["source_currency_mapping"][str(row.usageaccountid) + "_" + str(row.usagemonth)] = str(row.currencycode)
            # jsonData["months"].add(str(row.usagemonth))
            if str(row.usagemonth) not in jsonData["fx_rates_srcCcy_to_destCcy"]:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.usagemonth)] = {row.currencycode.upper(): None}
            else:
                jsonData["fx_rates_srcCcy_to_destCcy"][str(row.usagemonth)][row.currencycode.upper()] = None
    except Exception as e:
        print_("Failed to list distinct AWS source-currencies for account", "WARN")


def fetch_default_conversion_factors_from_API(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if jsonData["ccmPreferredCurrency"] is None:
        return

    for billing_month in jsonData["months"]:
        # fetch conversion factors from the API for all sourceCurrencies vs USD and destCcy vs USD for that DATE.
        # cdn.jsdelivr.net api returns currency-codes in lowercase
        try:
            response = requests.get(f"https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/{billing_month}/currencies/usd.json")
            fx_rates_from_api = response.json()
        except Exception as e:
            print_(e)
            fx_rates_from_api = BACKUP_CURRENCY_FX_RATES[billing_month]

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
                        fx_rates_from_api["usd"][jsonData["ccmPreferredCurrency"].lower()] / fx_rates_from_api["usd"][srcCurrency]
            except Exception as e:
                print_(e, "WARN")
                print_(f"fxRate for {srcCurrency} to {jsonData['ccmPreferredCurrency']} was not found in API response.")


def fetch_custom_conversion_factors(jsonData):
    if jsonData["ccmPreferredCurrency"] is None:
        return
    for billing_month in jsonData["fx_rates_srcCcy_to_destCcy"]:
        for source_currency in jsonData["userInputFxRates"]:
            if source_currency in jsonData["fx_rates_srcCcy_to_destCcy"][billing_month]:
                jsonData["fx_rates_srcCcy_to_destCcy"][billing_month][source_currency] = float(jsonData["userInputFxRates"][source_currency])


def verify_existence_of_required_conversion_factors(jsonData):
    # preferred currency should've been obtained at this point if it was set by customer
    if not jsonData["ccmPreferredCurrency"]:
        return

    for billing_month_start in jsonData["fx_rates_srcCcy_to_destCcy"]:
        if not all(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start].values()):
            print_(jsonData["fx_rates_srcCcy_to_destCcy"][billing_month_start])
            print_("Required fx rate not found for at least one currency pair", "ERROR")
            # throw error here. CF execution can't proceed from here