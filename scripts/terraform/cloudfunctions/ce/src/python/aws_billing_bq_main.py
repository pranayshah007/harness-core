# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import json
import base64
import os
import re
import datetime
import util

from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CEINTERNALDATASET, update_connector_data_sync_status
from calendar import monthrange
from google.cloud import bigquery
from google.cloud import storage

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
    "keepFiles": false
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)


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

    # Set accountid for GCP logging
    util.ACCOUNTID_LOG = jsonData.get("accountId")
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
        return
    ingest_data_from_csv(jsonData)
    set_available_columns(jsonData)
    get_unique_accountids(jsonData)
    ingest_data_to_awscur(jsonData)
    ingest_data_to_preagg(jsonData)
    ingest_data_to_unified(jsonData)
    update_connector_data_sync_status(jsonData, PROJECTID, client)
    ingest_data_to_costagg(jsonData)
    print_("Completed")


def create_dataset_and_tables(jsonData):
    create_dataset(client, jsonData["datasetName"], jsonData.get("accountId"))
    dataset = client.dataset(jsonData["datasetName"])
    if not create_table_from_manifest(jsonData):
        return False

    aws_cur_table_ref = dataset.table("awscur_%s" % (jsonData["awsCurTableSuffix"]))
    pre_aggragated_table_ref = dataset.table(PREAGGREGATED)
    unified_table_ref = dataset.table(UNIFIED)

    for table_ref in [aws_cur_table_ref, pre_aggragated_table_ref, unified_table_ref]:
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
                      "usagetype", "lineitemtype", "effectivecost", "billingentity", "instanceFamily", "marketOption"]
    available_columns = list(set(desirable_columns) & set(jsonData["available_columns"]))
    available_columns = ", ".join(f"{w}" for w in available_columns)

    amortised_cost_query = prep_amortised_cost_query(set(jsonData["available_columns"]))
    net_amortised_cost_query = prep_net_amortised_cost_query(set(jsonData["available_columns"]))

    query = """
    DELETE FROM `%s` WHERE DATE(usagestartdate) >= '%s' AND DATE(usagestartdate) <= '%s' and usageaccountid IN (%s);
    INSERT INTO `%s` (%s, amortisedCost, netAmortisedCost, tags) 
        SELECT %s, %s, %s, %s
        FROM `%s` table 
        WHERE DATE(usagestartdate) >= '%s' AND DATE(usagestartdate) <= '%s';
     """ % (tableName, date_start, date_end, jsonData["usageaccountid"],
            tableName, available_columns,
            available_columns, amortised_cost_query, net_amortised_cost_query, tags_query,
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

def prep_amortised_cost_query(cols):
    # Prep amortised cost calculation query based on available cols
    query = """CASE  
                    WHEN (lineitemtype = 'SavingsPlanNegation') THEN 0
                    WHEN (lineitemtype = 'SavingsPlanUpfrontFee') THEN 0 
            """
    if "SavingsPlanEffectiveCost".lower() in cols:
        query = query + "WHEN (lineitemtype = 'SavingsPlanCoveredUsage') THEN SavingsPlanEffectiveCost \n"
    if "TotalCommitmentToDate".lower() in cols and "UsedCommitment".lower() in cols:
        query = query + "WHEN (lineitemtype = 'SavingsPlanRecurringFee') THEN (TotalCommitmentToDate - UsedCommitment) \n"
    if "EffectiveCost".lower() in cols:
        query = query + "WHEN (lineitemtype = 'DiscountedUsage') THEN EffectiveCost \n"
    if "UnusedAmortizedUpfrontFeeForBillingPeriod".lower() in cols and "UnusedRecurringFee".lower() in cols:
        query = query + "WHEN (lineitemtype = 'RIFee') THEN (UnusedAmortizedUpfrontFeeForBillingPeriod + UnusedRecurringFee) \n"
    if "ReservationARN".lower() in cols:
        query = query + "WHEN ((lineitemtype = 'Fee') AND (ReservationARN <> '')) THEN 0 \n"
    query = query + " ELSE UnblendedCost END amortisedCost \n"
    print_(query)
    return query

def prep_net_amortised_cost_query(cols):
    # Prep net amortised cost calculation query based on available cols
    query = """CASE  
                    WHEN (lineitemtype = 'SavingsPlanNegation') THEN 0
                    WHEN (lineitemtype = 'SavingsPlanUpfrontFee') THEN 0 
            """
    if "NetSavingsPlanEffectiveCost".lower() in cols:
        query = query + "WHEN (lineitemtype = 'SavingsPlanCoveredUsage') THEN NetSavingsPlanEffectiveCost \n"
    if "TotalCommitmentToDate".lower() in cols and "UsedCommitment".lower() in cols:
        query = query + "WHEN (lineitemtype = 'SavingsPlanRecurringFee') THEN (TotalCommitmentToDate - UsedCommitment) \n"
    if "NetEffectiveCost".lower() in cols:
        query = query + "WHEN (lineitemtype = 'DiscountedUsage') THEN NetEffectiveCost \n"
    if "NetUnusedAmortizedUpfrontFeeForBillingPeriod".lower() in cols and "NetUnusedRecurringFee".lower() in cols:
        query = query + "WHEN (lineitemtype = 'RIFee') THEN (NetUnusedAmortizedUpfrontFeeForBillingPeriod + NetUnusedRecurringFee) \n"
    if "ReservationARN".lower() in cols:
        query = query + "WHEN ((lineitemtype = 'Fee') AND (ReservationARN <> '')) THEN 0 \n"
    if "NetUnblendedCost".lower() in cols:
        query = query + " ELSE NetUnblendedCost \n"
    else:
        query = query + " ELSE 0 \n"
    query = query + "END netAmortisedCost \n"
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
    except Exception as e:
        print_("Failed to retrieve distinct aws usageaccountid", "WARN")
        jsonData["usageaccountid"] = ""
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
                        awsUsagetype, cloudProvider"""

    select_columns = """TIMESTAMP_TRUNC(usagestartdate, DAY) as startTime, min(blendedrate) AS awsBlendedRate, sum(blendedcost) AS awsBlendedCost,
                    min(unblendedrate) AS awsUnblendedRate, sum(unblendedcost) AS awsUnblendedCost, sum(unblendedcost) AS cost,
                    servicename AS awsServicecode, region, availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid,
                    usagetype AS awsUsagetype, "AWS" AS cloudProvider"""

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

    insert_columns = """product, startTime, 
                    awsBlendedRate, awsBlendedCost,awsUnblendedRate, 
                    awsUnblendedCost, cost, awsServicecode, region, 
                    awsAvailabilityzone, awsUsageaccountid, 
                    cloudProvider, awsBillingEntity, labels"""

    select_columns = """productname AS product, TIMESTAMP_TRUNC(usagestartdate, DAY) as startTime, 
                    blendedrate AS awsBlendedRate, blendedcost AS awsBlendedCost, unblendedrate AS awsUnblendedRate, 
                    unblendedcost AS awsUnblendedCost, unblendedcost AS cost, servicename AS awsServicecode, region, 
                    availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid, 
                    "AWS" AS cloudProvider, billingentity as awsBillingEntity, tags AS labels"""

    # Amend query as per columns availability
    for additionalColumn in ["instancetype", "usagetype"]:
        if additionalColumn.lower() in jsonData["available_columns"]:
            insert_columns = insert_columns + ", aws%s" % additionalColumn
            select_columns = select_columns + ", %s as aws%s" % (additionalColumn, additionalColumn)

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
    query_job = client.query(query, job_config=job_config)
    try:
        query_job.result()
    except Exception as e:
        print_(query)
        raise e
    print_("Loaded into %s table..." % tableName)


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
        ADD COLUMN IF NOT EXISTS awsBillingEntity STRING;" % ds

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
