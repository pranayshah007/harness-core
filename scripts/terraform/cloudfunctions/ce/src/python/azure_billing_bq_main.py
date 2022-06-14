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
import re
from google.cloud import bigquery
from google.cloud import storage
import datetime
import util
from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    PREAGGREGATED, CEINTERNALDATASET, update_connector_data_sync_status
from calendar import monthrange

"""
Event format:
{
	"bucket": "azurecustomerbillingdata-qa",
	"path": "kmpySmUISimoRrJL6NL73w/JUKVZIGKQzCVKXYbDhmM_g/20210101-20210131/cereportnikunj_0e8fa366-608d-4aa8-8fb7-dc8fef9fe717.csv",
	"accountId": "kmpysmuisimorrjl6nl73w",
}
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)
STATIC_MARKUP_LIST = {
    "UVxMDMhNQxOCvroqqImWdQ": 5.04,  # AXA XL %age markup to costs
}


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

    if not if_tbl_exists(client, unifiedTableRef):
        print_("%s table does not exists, creating table..." % unifiedTableRef)
        createTable(client, unifiedTableRef)
    else:
        # Disable this call when the pipeline has executed once for each customer
        alter_unified_table(jsonData)
        print_("%s table exists" % unifiedTableTableName)

    if not if_tbl_exists(client, preAggragatedTableRef):
        print_("%s table does not exists, creating table..." % preAggragatedTableRef)
        createTable(client, preAggragatedTableRef)
    else:
        alter_preagg_table(jsonData)
        print_("%s table exists" % preAggregatedTableTableName)

    # start streaming the data from the gcs
    print_("%s table exists. Starting to write data from gcs into it..." % jsonData["tableName"])
    try:
        ingest_data_from_csv(jsonData)
    except Exception as e:
        print_(e)
        raise
    azure_column_mapping = setAvailableColumns(jsonData)
    get_unique_subs_id(jsonData, azure_column_mapping)
    ingest_data_into_preagg(jsonData, azure_column_mapping)
    ingest_data_into_unified(jsonData, azure_column_mapping)
    update_connector_data_sync_status(jsonData, PROJECTID, client)
    ingest_data_to_costagg(jsonData)
    print_("Completed")

def is_valid_month_folder(folderstr):
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
    # We can have both partitioned and non partitioned in the same folder
    if blob_maxsize > subfolder_maxsize:
        csvtoingest = single_csvtoingest
    else:
        csvtoingest = partitioned_csvtoingest

    if not csvtoingest:
        print_("No CSV to insert. GCS bucket might be empty", "WARN")
        return
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
    uri = "gs://" + jsonData["bucket"] + "/" + csvtoingest
    print_("Ingesting CSV from %s" % uri)
    print_("Loading into %s table..." % jsonData["tableId"])
    load_job = client.load_table_from_uri(
        [uri],
        jsonData["tableId"],
        job_config=job_config
    )

    load_job.result()  # Wait for the job to complete.
    table = client.get_table(jsonData["tableId"])
    print_("Total {} rows in table {}".format(table.num_rows, jsonData["tableId"]))
    jsonData["uri"] = uri

    # cleanup the processed blobs
    blobs = storage_client.list_blobs(
        jsonData["bucket"], prefix=jsonData["path"]
    )
    for blob in blobs:
        blob.delete()
        print_("Cleaned up {}.".format(blob.name))


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
        jsonData["subsId"] = ", ".join(f"'{w}'" for w in subsids)
    except Exception as e:
        print_("Failed to retrieve distinct subsids", "WARN")
        jsonData["subsId"] = ""
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
    query = """DELETE FROM `%s.preAggregated` WHERE DATE(startTime) >= '%s' AND DATE(startTime) <= '%s' 
                AND cloudProvider = "AZURE" AND azureSubscriptionGuid IN (%s);
           INSERT INTO `%s.preAggregated` (startTime, azureResourceRate, cost,
                                           azureServiceName, region, azureSubscriptionGuid,
                                            cloudProvider, azureTenantId)
           SELECT TIMESTAMP(%s) as startTime, min(%s) AS azureResourceRate, sum(%s) AS cost,
                MeterCategory AS azureServiceName, ResourceLocation as region, %s as azureSubscriptionGuid,
                "AZURE" AS cloudProvider, '%s' as azureTenantId
           FROM `%s`
           WHERE %s IN (%s)
           GROUP BY azureServiceName, region, azureSubscriptionGuid, startTime;
    """ % (ds, date_start, date_end, jsonData["subsId"], ds, azure_column_mapping["startTime"],
           azure_column_mapping["azureResourceRate"],
           azure_column_mapping["cost"], azure_column_mapping["azureSubscriptionGuid"], jsonData["tenant_id"],
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
    # Prepare default columns
    insert_columns = """product, startTime, cost,
                        azureMeterCategory, azureMeterSubcategory, azureMeterId,
                        azureMeterName,
                        azureInstanceId, region, azureResourceGroup,
                        azureSubscriptionGuid, azureServiceName,
                        cloudProvider, labels, azureResource, azureVMProviderId, azureTenantId,
                        azureResourceRate
                    """
    select_columns = """MeterCategory AS product, TIMESTAMP(%s) as startTime, %s*%s AS cost,
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
                        %s AS azureResourceRate
                     """ % (azure_column_mapping["startTime"],
                            azure_column_mapping["cost"], get_cost_markup_factor(jsonData),
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureResourceGroup"],
                            azure_column_mapping["azureSubscriptionGuid"], PROJECTID, CEINTERNALDATASET,
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"], azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"],
                            azure_column_mapping["azureInstanceId"], azure_column_mapping["azureInstanceId"],
                            jsonData["tenant_id"], azure_column_mapping["azureResourceRate"])

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
    query_job = client.query(query, job_config=job_config)
    query_job.result()
    print_("Loaded into %s table..." % tableName)


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
        ADD COLUMN IF NOT EXISTS azureBillingCurrency STRING;" % ds

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
                WHERE DATE(startTime) >= '%s' and cloudProvider = "AZURE" 
                GROUP BY day;
     """ % (table_name, date_start, date_end, jsonData.get("accountId"), table_name, jsonData.get("accountId"), source_table, date_start)

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=120)
