# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

"""
K8s scheduled job for Azure data pipeline
"""
import os
import re
from google.cloud import bigquery
import datetime
from util import print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    CEINTERNALDATASET, update_connector_data_sync_status, \
    flatten_label_keys_in_table, run_bq_query_with_retries
from calendar import monthrange
from azure.storage.blob import BlobServiceClient
import clickhouse_connect

# Clickhouse related configuration
CLICKHOUSE_URL = os.environ.get('CLICKHOUSE_URL', 'localhost')
CLICKHOUSE_PORT = os.environ.get('CLICKHOUSE_PORT', 8123)
CLICKHOUSE_USERNAME = os.environ.get('CLICKHOUSE_USERNAME', 'default')
CLICKHOUSE_PASSWORD = os.environ.get('CLICKHOUSE_PASSWORD', '')
clickhouse_client = clickhouse_connect.get_client(host=CLICKHOUSE_URL, port=CLICKHOUSE_PORT, username=CLICKHOUSE_USERNAME, password=CLICKHOUSE_PASSWORD)

# Azure related configuration
AZURE_STORAGE_ACCOUNT = os.environ.get('AZURE_STORAGE_ACCOUNT', 'ccmcustomerbillingdata')
AZURE_CONTAINER = os.environ.get('AZURE_CONTAINER', 'billingdatacontainer')
AZURE_SAS_TOKEN = os.enviorn.get('AZURE_SAS_TOKEN', 'sasToken')
TIME_DELTA = os.enviorn.get('TIME_DELTA', 1)
CONNECT_STR = 'BlobEndpoint=https://' + AZURE_STORAGE_ACCOUNT + '.blob.core.windows.net;SharedAccessSignature=' + AZURE_SAS_TOKEN

def main():
    """
    Triggered from a K8s scheduled job.
    """
    jsonData = {}
    create_db_and_tables()

    get_blob_path_to_be_ingested(jsonData)

    # paths will be in this format: <accountId>/<connectorId>/<tenantId>/<reportName>/<monthFolder>/<randomAzureId>.csv
    # or for partitioned report it will be in this format: <accountId>/<connectorId>/<tenantId>/<reportName>/<monthFolder>/<dateFolder>/<randomAzureId>/*.csv
    if len(jsonData["paths"]) == 0:
        print("No reports to ingest...")

    for absolutePath in jsonData["paths"]:
        pathSplit = absolutePath.split("/")
        jsonData["tenant_id"] = pathSplit[2]
        if len(pathSplit) == 6:
            monthfolder = pathSplit[-2]
        else:
            monthfolder = pathSplit[-4]

        jsonData["reportYear"] = monthfolder.split("-")[0][:4]
        jsonData["reportMonth"] = monthfolder.split("-")[0][4:6]
        
        jsonData["accountId"] = pathSplit[0]
        jsonData["connectorId"] = pathSplit[1]

        accountIdBQ = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())

        jsonData["tableSuffix"] = "%s_%s_%s" % (jsonData["reportYear"], jsonData["reportMonth"], jsonData["connectorId"])
        jsonData["tableName"] = f"azureBilling_{jsonData['tableSuffix']}"

        jsonData["columns"], jsonData["tableSchema"] = get_table_schema(absolutePath)
        azure_column_mapping = set_available_columns(jsonData)

        create_billing_table(jsonData, azure_column_mapping)

        create_azure_cost_table(jsonData)

        get_unique_subs_id(jsonData, azure_column_mapping)
        ingest_data_into_preagg(jsonData, azure_column_mapping)
        ingest_data_into_unified(jsonData, azure_column_mapping)
        update_connector_data_sync_status(jsonData, PROJECTID, client)
        # ingest_data_to_costagg(jsonData)

    print("K8S job executed successfully...")


def create_db_and_tables():
    try:
        clickhouse_client.command('create database if not exists ccm')
        print("Default DB ccm is created if it does not exist")
        clickhouse_client.command('CREATE TABLE IF NOT EXISTS ccm.unifiedTable ( `startTime` DateTime('UTC') NOT NULL, `cost` Float NULL, `gcpProduct` String NULL, `gcpSkuId` String NULL, `gcpSkuDescription` String NULL, `gcpProjectId` String NULL, `gcpInvoiceMonth` String NULL, `gcpCostType` String NULL, `region` String NULL, `zone` String NULL, `gcpBillingAccountId` String NULL, `cloudProvider` String NULL, `awsBlendedRate` String NULL, `awsBlendedCost` Float NULL, `awsUnblendedRate` String NULL, `awsUnblendedCost` Float NULL, `awsEffectiveCost` Float NULL, `awsAmortisedCost` Float NULL, `awsNetAmortisedCost` Float NULL, `awsLineItemType` String NULL, `awsServicecode` String NULL, `awsAvailabilityzone` String NULL, `awsUsageaccountid` String NULL, `awsInstancetype` String NULL, `awsUsagetype` String NULL, `awsBillingEntity` String NULL, `discount` Float NULL, `endtime` DateTime('UTC') NULL, `accountid` String NULL, `instancetype` String NULL, `clusterid` String NULL, `clustername` String NULL, `appid` String NULL, `serviceid` String NULL, `envid` String NULL, `cloudproviderid` String NULL, `launchtype` String NULL, `clustertype` String NULL, `workloadname` String NULL, `workloadtype` String NULL, `namespace` String NULL, `cloudservicename` String NULL, `taskid` String NULL, `clustercloudprovider` String NULL, `billingamount` Float NULL, `cpubillingamount` Float NULL, `memorybillingamount` Float NULL, `idlecost` Float NULL, `maxcpuutilization` Float NULL, `avgcpuutilization` Float NULL, `systemcost` Float NULL, `actualidlecost` Float NULL, `unallocatedcost` Float NULL, `networkcost` Float NULL, `product` String NULL, `azureMeterCategory` String NULL, `azureMeterSubcategory` String NULL, `azureMeterId` String NULL, `azureMeterName` String NULL, `azureResourceType` String NULL, `azureServiceTier` String NULL, `azureInstanceId` String NULL, `azureResourceGroup` String NULL, `azureSubscriptionGuid` String NULL, `azureAccountName` String NULL, `azureFrequency` String NULL, `azurePublisherType` String NULL, `azurePublisherName` String NULL, `azureServiceName` String NULL, `azureSubscriptionName` String NULL, `azureReservationId` String NULL, `azureReservationName` String NULL, `azureResource` String NULL, `azureVMProviderId` String NULL, `azureTenantId` String NULL, `azureBillingCurrency` String NULL, `azureCustomerName` String NULL, `azureResourceRate` Float NULL, `orgIdentifier` String NULL, `projectIdentifier` String NULL, `labels` Map(String, String) ) ENGINE = MergeTree ORDER BY tuple(startTime) SETTINGS allow_nullable_key = 1')
        print("Table ccm.unifiedTable is created if it does not exist")
        clickhouse_client.command('CREATE TABLE IF NOT EXISTS ccm.preAggregated ( `cost` Float NULL, `gcpProduct` String NULL, `gcpSkuId` String NULL, `gcpSkuDescription` String NULL, `startTime` DateTime('UTC') NULL, `gcpProjectId` String NULL, `region` String NULL, `zone` String NULL, `gcpBillingAccountId` String NULL, `cloudProvider` String NULL, `awsBlendedRate` String NULL, `awsBlendedCost` Float NULL, `awsUnblendedRate` String NULL, `awsUnblendedCost` Float NULL, `awsServicecode` String NULL, `awsAvailabilityzone` String NULL, `awsUsageaccountid` String NULL, `awsInstancetype` String NULL, `awsUsagetype` String NULL, `discount` Float NULL, `azureServiceName` String NULL, `azureResourceRate` Float NULL, `azureSubscriptionGuid` String NULL, `azureTenantId` String NULL ) ENGINE = MergeTree ORDER BY tuple(startTime) SETTINGS allow_nullable_key = 1')
        print("Table ccm.preAggregated is created if it does not exist")
        clickhouse_client.command('CREATE TABLE IF NOT EXISTS ccm.costAggregated ( `accountId` String NULL, `cloudProvider` String NOT NULL, `cost` Float NOT NULL, `day` DateTime('UTC') NOT NULL ) ENGINE = MergeTree ORDER BY tuple(day) SETTINGS allow_nullable_key = 1;')
        print("Table ccm.costAggregated is created if it does not exist")
        clickhouse_client.command('CREATE TABLE IF NOT EXISTS ccm.connectorDataSyncStatus ( `accountId` String NULL, `connectorId` String NOT NULL, `jobType` String NULL, `cloudProviderId` String NULL, `lastSuccessfullExecutionAt` DateTime('UTC') NOT NULL ) ENGINE = MergeTree ORDER BY tuple(lastSuccessfullExecutionAt) SETTINGS allow_nullable_key = 1;')
        print("Table ccm.connectorDataSyncStatus is created if it does not exist")
    except Exception as e:
        raise e
    print("Default DB and Tables required for Azure Data Ingestion created if it does not exist")


def get_blob_path_to_be_ingested(jsonData):
    blob_service_client = BlobServiceClient.from_connection_string(CONNECT_STR)
    container_client = blob_service_client.get_container_client(AZURE_CONTAINER)

    blobs_after_this_time = datetime.utcnow().replace(tzinfo=timezone.utc) - timedelta(days=TIME_DELTA)

    blobs = container_client.list_blobs()
    latest_blobs = []
    for blob in blobs:
        if blob.last_modified >= blobs_after_this_time:
            latest_blobs.append(blob)

    sorted_by_size_blobs = sorted(latest_blobs, key=lambda x: x.size, reverse=True)
    reports_to_ingest = []
    for blob in sorted_by_size_blobs:
        blob_name_splitted = (blob.name).split("/")
        blob_name_len = len(blob_name_splitted)
        if blob_name_len == 6:
            concatenated_path = '/'.join(blob_name_splitted[:-1])
            exists = any(e.startswith(concatenated_path) for e in reports_to_ingest)
            if exists == False:
                reports_to_ingest.append(blob.name)
    print("Following absolute path reports will be ingested:", reports_to_ingest)

    sorted_by_name_blobs = sorted(latest_blobs, key=lambda x: x.name, reverse=True)
    partitioned_report_to_ingest = []
    for blob in sorted_by_name_blobs:
        blob_name_splitted = (blob.name).split("/")
        blob_name_len = len(blob_name_splitted)
        if blob_name_len == 8:
            month_folder = blob_name_splitted[-4]
            year_month = month_folder.split("-")[0][:4] + month_folder.split("-")[0][4:6]
            if (not blob_name_splitted[-3].startswith(year_month)):
                continue
            concatenated_path = '/'.join(blob_name_splitted[:-3])
            exists = any(e.startswith(concatenated_path) for e in partitioned_report_to_ingest)
            if exists == False:
                partitioned_report_to_ingest.append(concatenated_path)
                reports_to_ingest.append('/'.join(blob_name_splitted[:-1]) + "/*.csv")
    print("Following partitioned reports will be ingested:", partitioned_report_to_ingest)

    jsonData["paths"] = reports_to_ingest


def get_table_schema(absolutePath):
    get_table_schema_query = """DESCRIBE TABLE
                                (select * from azureBlobStorage('%s','%s','%s'))
                                """ % (CONNECT_STR, AZURE_CONTAINER, absolutePath)
    print("get_table_schema_query: %s" % get_table_schema_query)

    try:
        query_job = client.query(get_table_schema_query)
        results = query_job.result_rows
        schema = []
        pattern = r'\((.*?)\)'
        columns = set()
        for row in results:
            match = re.search(pattern, row[1].lower())
            if match:
                dataType = match.group(1)
            else:
                dataType = 'string'
            schema.append(f"{row[0].lower()} {dataType.lower()}")
            columns.add(row[0].lower())
        table_schema = ', '.join(schema)
        print("Retrieved table_schema: %s" % table_schema)
        return columns, table_schema
    except Exception as e:
        print("Failed to retrieve table_schema", "WARN")
        raise e


def set_available_columns(jsonData):
    # Ref: https://docs.microsoft.com/en-us/azure/cost-management-billing/understand/mca-understand-your-usage
    # Clickhouse column names are case sensitive.
    azure_column_mapping = {
        "startTime": "",
        "azureResourceRate": "",
        "cost": "",
        "region": "",
        "azureSubscriptionGuid": "",
        "azureInstanceId": "",
        "currency": "",
    }

    columns = jsonData["columns"]

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
    elif "instancename" in columns:
        azure_column_mapping["azureInstanceId"] = "instancename"
    else:
        raise Exception("No mapping found for azureInstanceId column")

    # azureResourceGroup
    if "resourcegroup" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroup"
    elif "resourcegroupname" in columns:
        azure_column_mapping["azureResourceGroup"] = "resourcegroupname"
    else:
        raise Exception("No mapping found for azureResourceGroup column")

    print("azure_column_mapping: %s" % azure_column_mapping)
    return azure_column_mapping


def create_billing_table(jsonData, azure_column_mapping):
    create_billing_table_query = """create table if not exists ccm.%s
                                    (%s)
                                    ENGINE = MergeTree ORDER BY tuple(%s) SETTINGS allow_nullable_key = 1;
                                    """ % (jsonData["tableName"], jsonData["tableSchema"], azure_column_mapping["startTime"])
    print("create_billing_table_query: %s" % create_billing_table_query)

    try:
        clickhouse_client.command(f"DROP TABLE IF EXISTS ccm.{jsonData["tableName"]}")
        print("Dropped table:", jsonData["tableName"])
        clickhouse_client.command(create_billing_table_query)
        print("Created Empty table:", jsonData["tableName"])
        clickhouse_client.command("SET input_format_csv_skip_first_lines=1")
        clickhouse_client.command("SET max_memory_usage=1000000000000")
        clickhouse_client.command("insert into ccm." + jsonData["tableName"] + " select * from azureBlobStorage('" + CONNECT_STR + "', '" + AZURE_CONTAINER + "', '" + absolutePath + "')")
        print("Inserted raw data into table:", jsonData["tableName"])
    except Exception as e:
        raise e


def create_azure_cost_table(jsonData):
    columns = jsonData["columns"]
    azure_cost_table_name = jsonData["tableName"].replace("azureBilling_", "azureCost_")


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

    # Amend query for Elevance
    if jsonData.get("accountId") == "pC_7h33wQTeZ_j-libvF4A":
        print_("Adding more fields for Elevance")
        for additionalColumn in ["BillingAccountId", "BillingAccountName", "BillingPeriodStartDate", "BillingPeriodEndDate",
                                 "ProductName", "Quantity", "UnitPrice", "ResourceName", "CostCenter",
                                 "UnitOfMeasure", "ChargeType"]:
            if additionalColumn.lower() in jsonData["columns"]:
                insert_columns = insert_columns + ", azure%s" % additionalColumn
                if additionalColumn.lower() in ["costcenter"]:
                    # BQ schema autodetect failure handling
                    select_columns = select_columns + ", CAST(%s AS INT64) as azure%s" % (additionalColumn, additionalColumn)
                else:
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
        flatten_label_keys_in_table(client, jsonData.get("accountId"), PROJECTID, jsonData["datasetName"], UNIFIED,
                                    "labels", fetch_ingestion_filters(jsonData))
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
