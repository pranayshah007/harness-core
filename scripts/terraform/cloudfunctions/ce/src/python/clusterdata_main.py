# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import datetime
import os
import util
import re
import time

from google.cloud import bigquery
from google.cloud import storage

from util import create_dataset, if_tbl_exists, createTable, print_, run_batch_query, COSTAGGREGATED, UNIFIED, \
    CEINTERNALDATASET, flatten_label_keys_in_table, LABELKEYSTOCOLUMNMAPPING, run_bq_query_with_retries

"""
Step1) Ingest data from avro to Bigquery's clusterData table.
Step2) Run the SQL to load into unifiedTable in the same dataset.

event:
{
     'bucket': 'clusterdata-dev',
     'contentType': 'application/octet-stream',
     'crc32c': '+4CFsw==',
     'etag': 'CJX7js2X9uwCEAE=',
     'generation': '1604949714910613',
     'id': 'clusterdata-dev/nvsv7gjbtzya3cgsgxnocg/WhejVM7NTJe2fZ99Pdo2YA_billing_data_2020_NOVEMBER_1.avro/1604949714910613',
     'kind': 'storage#object',
     'md5Hash': 'SXp0LaXb6gusNS5+1F+MAA==',
     'mediaLink': 'https://www.googleapis.com/download/storage/v1/b/clusterdata-dev/o/nvsv7gjbtzya3cgsgxnocg%2FWhejVM7NTJe2fZ99Pdo2YA_billing_data_2020_NOVEMBER_1.avro?generation=1604949714910613&alt=media',
     'metageneration': '1',
     'name': 'nvsv7gjbtzya3cgsgxnocg/WhejVM7NTJe2fZ99Pdo2YA_billing_data_2020_NOVEMBER_1.avro',
     'selfLink': 'https://www.googleapis.com/storage/v1/b/clusterdata-dev/o/nvsv7gjbtzya3cgsgxnocg%2FWhejVM7NTJe2fZ99Pdo2YA_billing_data_2020_NOVEMBER_1.avro',
     'size': '1121493',
     'storageClass': 'STANDARD',
     'timeCreated': '2020-11-09T19:21:54.910Z',
     'timeStorageClassUpdated': '2020-11-09T19:21:54.910Z',
     'updated': '2020-11-09T19:21:54.910Z'
}
     
"""

PROJECTID = os.environ.get('GCP_PROJECT', 'ccm-play')
client = bigquery.Client(PROJECTID)
storage_client = storage.Client(PROJECTID)


def main(jsonData, context):
    """Triggered from a message on a Cloud Pub/Sub topic.
    Args:
         event (dict): Event payload.
         context (google.cloud.functions.Context): Metadata for the event.
    """
    print(jsonData)
    if os.environ.get('disabled', 'false').lower() == 'true':
        print("Function is disabled...")
        return
    filePath = jsonData['name']
    print(f"Processing file: {filePath}.")
    if not filePath.endswith(".avro"):
        print("Nothing to ingest")
        return

    # clusterdata-prod/WhejVM7NTJe2fZ99Pdo2YA/*.avro
    jsonData["fileName"] = filePath.split("/")[-1]
    jsonData["accountId"] = filePath.split("/")[-2]
    jsonData["cloudProvider"] = "CLUSTER"
    if jsonData["accountId"] in os.environ.get("disable_for_accounts", "").split(","):
        print_("Execution disabled for this account :%s" % jsonData["accountId"])
        return
    util.ACCOUNTID_LOG = jsonData["accountId"]

    # cluster table name
    jsonData["isHourly"] = False
    if jsonData["fileName"].startswith("billing_data_hourly"):
        jsonData["tableName"] = "clusterDataHourly"
        jsonData["tableNameAggregated"] = "clusterDataHourlyAggregated"
        jsonData["isHourly"] = True
    elif jsonData["fileName"].startswith("billing_data"):
        jsonData["tableName"] = "clusterData"
        jsonData["tableNameAggregated"] = "clusterDataAggregated"

    # Verify file format
    try:
        if jsonData["isHourly"]:
            jsonData["fileDate"] = re.search("billing_data_hourly_(.+?).avro", jsonData["fileName"]).group(1).split(
                "_")  # ['2020', 'NOVEMBER', '1', '1']
            jsonData["fileYear"] = int(jsonData["fileDate"][0])
            jsonData["fileMonth"] = MONTHMAP[jsonData["fileDate"][1]]
            jsonData["fileDay"] = int(jsonData["fileDate"][2])
            jsonData["fileHour"] = jsonData["fileDate"][3]
        else:
            jsonData["fileDate"] = re.search("billing_data_(.+?).avro", jsonData["fileName"]).group(1).split(
                "_")  # ['2020', 'NOVEMBER', '1']
            jsonData["fileYear"] = int(jsonData["fileDate"][0])
            jsonData["fileMonth"] = MONTHMAP[jsonData["fileDate"][1]]
            jsonData["fileDay"] = int(jsonData["fileDate"][2])
            jsonData["fileHour"] = "00"
    except Exception as e:
        # invalid file format
        print_(e)
        print_("Filename is invalid {}".format(jsonData["fileName"]))
        jsonData["fileDate"] = None

    jsonData["accountIdBQ"] = re.sub('[^0-9a-z]', '_', jsonData.get("accountId").lower())
    jsonData["datasetName"] = "BillingReport_%s" % jsonData["accountIdBQ"]
    jsonData["tableId"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableName"])
    jsonData["tableIdAggregated"] = "%s.%s.%s" % (PROJECTID, jsonData["datasetName"], jsonData["tableNameAggregated"])

    create_dataset_and_tables(jsonData)
    if not ingest_data_from_avro(jsonData):
        print_("Completed")
        return
    ingest_data_in_unified(jsonData)
    ingest_aggregated_data(jsonData)
    # ingest_data_to_costagg(jsonData)
    print_("Completed")


def create_dataset_and_tables(jsonData):
    create_dataset(client, jsonData["datasetName"], jsonData.get("accountId"))
    dataset = client.dataset(jsonData["datasetName"])

    cluster_data_table_ref = dataset.table(jsonData["tableName"])
    cluster_data_aggregated_table_ref = dataset.table(jsonData["tableNameAggregated"])
    unified_table_ref = dataset.table("unifiedTable")
    label_keys_to_column_mapping_table_ref = dataset.table(LABELKEYSTOCOLUMNMAPPING)
    cost_aggregated_table_ref = client.dataset(CEINTERNALDATASET).table(COSTAGGREGATED)

    for table_ref in [cluster_data_table_ref, unified_table_ref, cluster_data_aggregated_table_ref,
                      cost_aggregated_table_ref, label_keys_to_column_mapping_table_ref]:
        if not if_tbl_exists(client, table_ref):
            print_("%s table does not exists, creating table..." % table_ref)
            createTable(client, table_ref)
        else:
            print_("%s table exists" % table_ref)
            # Enable these only when needed.
            # if table_ref == cluster_data_aggregated_table_ref:
            #     alterTableAggregated(jsonData)
            # elif table_ref == cluster_data_table_ref:
            #     alterTable(jsonData)
            # elif table_ref == unified_table_ref:
            #     alter_unified_table(jsonData)


def ingest_data_from_avro(jsonData):
    """
    Loads data from Avro to clusterData/clusterDataHourly table
    """
    uri = "gs://" + jsonData["bucket"] + "/" + jsonData["accountId"] + "/" + jsonData["fileName"]  # or "*.avro"
    print_(uri)
    blob_to_delete = check_file_exists_in_gcs(jsonData)
    if blob_to_delete is None:
        print_("Nothing to ingest. No blob exists")
        return False
    delete_existing_data(jsonData)
    job_config = bigquery.LoadJobConfig(
        source_format=bigquery.SourceFormat.AVRO,
        use_avro_logical_types=True,
        max_bad_records=0,
        write_disposition="WRITE_APPEND"  # default value
    )

    print_("Loading into %s table..." % jsonData["tableId"])
    load_job = client.load_table_from_uri(
        uri,
        jsonData["tableId"],
        job_config=job_config
    )  # Make an API request.
    try:
        load_job.result()  # Wait for the job to complete.
        table = client.get_table(jsonData["tableId"])
        print_("Total: {} rows in table {}".format(table.num_rows, jsonData["tableId"]))
        blob_to_delete.delete()
        print_("Deleted blob : %s" % blob_to_delete.name)
        # flatten_label_keys_in_table(client, jsonData.get("accountId"), PROJECTID, jsonData["datasetName"],
        #                             jsonData["tableName"], "labels", fetch_cluster_table_ingestion_filters(jsonData))
    except Exception as e:
        print_(e, "WARN")
        return False
        # Probably the file was deleted in earlier runs
    return True

def check_file_exists_in_gcs(jsonData):
    """
    Deletes the blob from GCS
    :param jsonData:
    :return:
    """
    bucket_name = jsonData["bucket"]
    directory_name = jsonData["accountId"]
    bucket = storage_client.get_bucket(bucket_name)
    # list all objects in the directory
    blobs = bucket.list_blobs(prefix=directory_name)
    for blob in blobs:
        # kmpySmUISimoRrJL6NL73w/billing_data_2020_DECEMBER_10.avro
        if blob.name.endswith(jsonData["fileName"]):  # or .endswith(".avro"):
            print_("Will be deleting this blob after ingestion... : %s" % blob.name)
            return blob
    return None


def delete_existing_data(jsonData):
    """
    Deletes data from clusterData/clusterDataHourly tables before insertion
    :param jsonData:
    :return:
    """
    if jsonData["fileDate"] == None:
        # Couldnt determine date from filename. return.
        return
    if jsonData["isHourly"]:
        print_("Deleting 1 hour data from %s..." % jsonData["tableId"])
        query = """DELETE FROM `%s` WHERE starttime = UNIX_MILLIS(TIMESTAMP(DATETIME(%s, %s, %s, %s, 00, 00)));
                """ % (jsonData["tableId"], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"],
                       jsonData["fileHour"])
    else:
        print_("Deleting 1 day data from %s..." % jsonData["tableId"])
        query = """DELETE FROM `%s` WHERE DATE(TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(starttime), DAY))
                  = DATE(%s, %s, %s);
                """ % (jsonData["tableId"], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"])

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
    # run_batch_query(client, query, job_config, timeout=240)
    run_bq_query_with_retries(client, query, max_retry_count=3, job_config=job_config)
    print_("Deleted data from %s..." % jsonData["tableId"])


def ingest_data_in_unified(jsonData):
    if jsonData["tableName"] == "clusterDataHourly":
        return
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    print_("Loading into %s.unifiedTable..." % ds)
    query = """DELETE FROM `%s.unifiedTable` WHERE DATE(startTime) = DATE(%s, %s, %s)
                AND cloudProvider = "CLUSTER";
            INSERT INTO `%s.unifiedTable` (CLOUDPROVIDER, PRODUCT, STARTTIME, ENDTIME, COST, CPUBILLINGAMOUNT,
                MEMORYBILLINGAMOUNT, ACTUALIDLECOST, SYSTEMCOST, UNALLOCATEDCOST, NETWORKCOST, CLUSTERCLOUDPROVIDER,
                ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, NAMESPACE, WORKLOADNAME, WORKLOADTYPE,
                INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, LAUNCHTYPE, CLOUDSERVICENAME, 
                ORGIDENTIFIER, PROJECTIDENTIFIER, LABELS)
            SELECT "CLUSTER" AS CLOUDPROVIDER,
                (CASE
                    WHEN CLUSTERTYPE = "K8S" THEN "Kubernetes Cluster"
                    ELSE "ECS Cluster"
                END) AS PRODUCT,
                TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(starttime), DAY) as STARTTIME, TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(endtime), DAY) as ENDTIME,
                SUM(billingamount) AS COST, SUM(cpubillingamount) AS CPUBILLINGAMOUNT, SUM(memorybillingamount) AS MEMORYBILLINGAMOUNT,
                SUM(actualidlecost) AS ACTUALIDLECOST,  SUM(systemcost) AS SYSTEMCOST,  SUM(unallocatedcost) AS UNALLOCATEDCOST,
                SUM(networkcost) AS NETWORKCOST, cloudprovider AS CLUSTERCLOUDPROVIDER, accountid AS ACCOUNTID, clusterid AS CLUSTERID,
                clustername AS CLUSTERNAME, clustertype AS CLUSTERTYPE, region AS REGION, namespace AS NAMESPACE, workloadname AS WORKLOADNAME,
                workloadtype AS WORKLOADTYPE, instancetype AS INSTANCETYPE, appname AS APPID, servicename AS SERVICEID, envname AS ENVID,
                cloudproviderid AS CLOUDPROVIDERID, launchtype AS LAUNCHTYPE, cloudservicename AS CLOUDSERVICENAME, 
                ORGIDENTIFIER, PROJECTIDENTIFIER, ANY_VALUE(labels) as LABELS 
            FROM `%s` 
            WHERE DATE(TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(starttime), DAY)) = DATE(%s, %s, %s) AND instancetype != "CLUSTER_UNALLOCATED"
            GROUP BY ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, NAMESPACE,
                WORKLOADNAME,WORKLOADTYPE, INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, LAUNCHTYPE, 
                CLOUDSERVICENAME, STARTTIME, ENDTIME, CLUSTERCLOUDPROVIDER, ORGIDENTIFIER, PROJECTIDENTIFIER;
    """ % (ds,  jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"], ds,
           jsonData['tableId'], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"])
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
        #                             "labels", fetch_unifiedTable_ingestion_filters(jsonData))
    except Exception as e:
        print_(query)
        raise e
    print_("Loaded into %s.unifiedTable..." % ds)


def fetch_unifiedTable_ingestion_filters(jsonData):
    return """ DATE(startTime) = DATE(%s, %s, %s) 
    AND cloudProvider = "CLUSTER" """ % (jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"])


def fetch_cluster_table_ingestion_filters(jsonData):
    return """ DATE(TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(starttime), DAY)) = DATE(%s, %s, %s) """ \
           % (jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"])


def fetch_cluster_table_aggregated_ingestion_filters(jsonData):
    return """ starttime = UNIX_MILLIS(TIMESTAMP(DATETIME(%s, %s, %s, %s, 00, 00))) """ \
           % (jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"], jsonData["fileHour"])


def ingest_aggregated_data(jsonData):
    # cleanup older data
    print_("Ingesting aggregated data")
    print_("Cleaning up older aggregated data")
    DELETE_EXISTING_PREAGG = """DELETE FROM %s 
                                WHERE starttime = UNIX_MILLIS(TIMESTAMP(DATETIME(%s, %s, %s, %s, 00, 00))) ;
                            """ % (jsonData["tableIdAggregated"], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"],
                                   jsonData["fileHour"])
    try:
        run_bq_query_with_retries(client, DELETE_EXISTING_PREAGG, max_retry_count=3)
    except Exception as e:
        print_(DELETE_EXISTING_PREAGG, "ERROR")
        print_(e)

    # Gen preagg billing data
    PREAGG_QUERY = """INSERT INTO %s (MEMORYACTUALIDLECOST, CPUACTUALIDLECOST, STARTTIME, ENDTIME, BILLINGAMOUNT,
                        ACTUALIDLECOST, UNALLOCATEDCOST, SYSTEMCOST, STORAGEACTUALIDLECOST, STORAGEUNALLOCATEDCOST, 
                        STORAGEUTILIZATIONVALUE, STORAGEREQUEST, STORAGECOST, MEMORYUNALLOCATEDCOST, CPUUNALLOCATEDCOST, 
                        CPUBILLINGAMOUNT, MEMORYBILLINGAMOUNT, ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, 
                        REGION, NAMESPACE, WORKLOADNAME, WORKLOADTYPE,  INSTANCETYPE, APPID, SERVICEID, ENVID, 
                        CLOUDPROVIDERID, LAUNCHTYPE, CLOUDSERVICENAME, INSTANCENAME, CLOUDPROVIDER, NETWORKCOST, APPNAME,
                        SERVICENAME, ENVNAME, ORGIDENTIFIER, PROJECTIDENTIFIER, LABELS)  
                     SELECT SUM(MEMORYACTUALIDLECOST) as MEMORYACTUALIDLECOST, SUM(CPUACTUALIDLECOST) as CPUACTUALIDLECOST, 
                        max(STARTTIME) as STARTTIME, max(ENDTIME) as ENDTIME, sum(BILLINGAMOUNT) as BILLINGAMOUNT, 
                        sum(ACTUALIDLECOST) as ACTUALIDLECOST, sum(UNALLOCATEDCOST) as UNALLOCATEDCOST, 
                        sum(SYSTEMCOST) as SYSTEMCOST, SUM(STORAGEACTUALIDLECOST) as STORAGEACTUALIDLECOST, 
                        SUM(STORAGEUNALLOCATEDCOST) as STORAGEUNALLOCATEDCOST, MAX(STORAGEUTILIZATIONVALUE) as STORAGEUTILIZATIONVALUE, 
                        MAX(STORAGEREQUEST) as STORAGEREQUEST, SUM(STORAGECOST) as STORAGECOST, 
                        SUM(MEMORYUNALLOCATEDCOST) as MEMORYUNALLOCATEDCOST, SUM(CPUUNALLOCATEDCOST) as CPUUNALLOCATEDCOST, 
                        SUM(CPUBILLINGAMOUNT) as CPUBILLINGAMOUNT, SUM(MEMORYBILLINGAMOUNT) as MEMORYBILLINGAMOUNT, 
                        ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, NAMESPACE, WORKLOADNAME, WORKLOADTYPE,  
                        INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, LAUNCHTYPE, CLOUDSERVICENAME, INSTANCENAME,
                        CLOUDPROVIDER, SUM(networkcost) AS NETWORKCOST, APPNAME, SERVICENAME, ENVNAME, ORGIDENTIFIER, 
                        PROJECTIDENTIFIER, ANY_VALUE(labels) as LABELS 
                    FROM %s 
                    WHERE STARTTIME = UNIX_MILLIS(TIMESTAMP(DATETIME(%s, %s, %s, %s, 00, 00))) 
                        AND INSTANCETYPE IN ("K8S_POD", 
                        "ECS_CONTAINER_INSTANCE", "ECS_TASK_EC2", "ECS_TASK_FARGATE", "K8S_POD_FARGATE") 
                    GROUP BY ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, NAMESPACE, WORKLOADNAME, 
                        WORKLOADTYPE, INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, LAUNCHTYPE, CLOUDSERVICENAME, 
                        INSTANCENAME, CLOUDPROVIDER, APPNAME, SERVICENAME, ENVNAME, ORGIDENTIFIER, PROJECTIDENTIFIER ;
                    """ % (jsonData["tableIdAggregated"], jsonData["tableId"], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"],
                           jsonData["fileHour"])

    # Call query
    print_("Ingesting aggregated data 1st query")
    try:
        run_bq_query_with_retries(client, PREAGG_QUERY, max_retry_count=3)
    except Exception as e:
        print_(PREAGG_QUERY, "ERROR")
        print_(e)

    PREAGG_QUERY_ID = """INSERT INTO %s (MEMORYACTUALIDLECOST, CPUACTUALIDLECOST, STARTTIME, ENDTIME, 
                            BILLINGAMOUNT, ACTUALIDLECOST, UNALLOCATEDCOST, SYSTEMCOST, STORAGEACTUALIDLECOST, STORAGEUNALLOCATEDCOST, 
                            STORAGEUTILIZATIONVALUE, STORAGEREQUEST, STORAGECOST, MEMORYUNALLOCATEDCOST, CPUUNALLOCATEDCOST, 
                            CPUBILLINGAMOUNT, MEMORYBILLINGAMOUNT, ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, 
                            NAMESPACE, WORKLOADNAME, WORKLOADTYPE,  INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, 
                            LAUNCHTYPE, CLOUDSERVICENAME, INSTANCEID, INSTANCENAME, CLOUDPROVIDER, NETWORKCOST, APPNAME,
                            SERVICENAME, ENVNAME, ORGIDENTIFIER, PROJECTIDENTIFIER, LABELS) 
                        SELECT SUM(MEMORYACTUALIDLECOST) as MEMORYACTUALIDLECOST, SUM(CPUACTUALIDLECOST) as CPUACTUALIDLECOST, 
                            max(STARTTIME) as STARTTIME, max(ENDTIME) as ENDTIME, sum(BILLINGAMOUNT) as BILLINGAMOUNT, 
                            sum(ACTUALIDLECOST) as ACTUALIDLECOST, sum(UNALLOCATEDCOST) as UNALLOCATEDCOST, sum(SYSTEMCOST) as SYSTEMCOST, 
                            SUM(STORAGEACTUALIDLECOST) as STORAGEACTUALIDLECOST, SUM(STORAGEUNALLOCATEDCOST) as STORAGEUNALLOCATEDCOST, 
                            MAX(STORAGEUTILIZATIONVALUE) as STORAGEUTILIZATIONVALUE, MAX(STORAGEREQUEST) as STORAGEREQUEST, 
                            SUM(STORAGECOST) as STORAGECOST, SUM(MEMORYUNALLOCATEDCOST) as MEMORYUNALLOCATEDCOST, 
                            SUM(CPUUNALLOCATEDCOST) as CPUUNALLOCATEDCOST, SUM(CPUBILLINGAMOUNT) as CPUBILLINGAMOUNT, 
                            SUM(MEMORYBILLINGAMOUNT) as MEMORYBILLINGAMOUNT, ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, 
                            REGION, NAMESPACE, WORKLOADNAME, WORKLOADTYPE,  INSTANCETYPE, APPID, SERVICEID, ENVID, 
                            CLOUDPROVIDERID, LAUNCHTYPE, CLOUDSERVICENAME, INSTANCEID, INSTANCENAME, CLOUDPROVIDER,
                            SUM(networkcost) AS NETWORKCOST, APPNAME, SERVICENAME, ENVNAME, ORGIDENTIFIER, 
                            PROJECTIDENTIFIER, ANY_VALUE(labels) as LABELS 
                        FROM %s 
                        WHERE STARTTIME = UNIX_MILLIS(TIMESTAMP(DATETIME(%s, %s, %s, %s, 00, 00))) and INSTANCETYPE IN ("K8S_NODE", "K8S_PV") 
                        GROUP BY ACCOUNTID, CLUSTERID, CLUSTERNAME, CLUSTERTYPE, REGION, NAMESPACE, WORKLOADNAME, 
                            WORKLOADTYPE, INSTANCETYPE, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, LAUNCHTYPE, 
                            CLOUDSERVICENAME, INSTANCEID, INSTANCENAME, CLOUDPROVIDER, APPNAME, SERVICENAME, ENVNAME, ORGIDENTIFIER, PROJECTIDENTIFIER ;
                        """ % (jsonData["tableIdAggregated"], jsonData["tableId"], jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"],
                               jsonData["fileHour"])

    print_("Ingesting aggregated data 2nd query")
    try:
        run_bq_query_with_retries(client, PREAGG_QUERY_ID, max_retry_count=3)
    except Exception as e:
        print_(PREAGG_QUERY_ID, "ERROR")
        print_(e)

    # try:
    #     flatten_label_keys_in_table(client, jsonData.get("accountId"), PROJECTID, jsonData["datasetName"],
    #                                 jsonData["tableNameAggregated"], "labels",
    #                                 fetch_cluster_table_aggregated_ingestion_filters(jsonData))
    # except Exception as e:
    #     print_(e, "ERROR")
    #     raise e


def alterTableAggregated(jsonData):
    print_("Altering %s Table" % jsonData["tableIdAggregated"])
    query = "ALTER TABLE `%s` \
            ADD COLUMN IF NOT EXISTS networkcost FLOAT64, \
            ADD COLUMN IF NOT EXISTS appname STRING, \
            ADD COLUMN IF NOT EXISTS servicename STRING, \
            ADD COLUMN IF NOT EXISTS envname STRING, \
            ADD COLUMN IF NOT EXISTS cloudprovider STRING, \
            ADD COLUMN IF NOT EXISTS labels ARRAY<STRUCT<key STRING, value STRING>>, \
            ADD COLUMN IF NOT EXISTS storageactualidlecost FLOAT64, \
            ADD COLUMN IF NOT EXISTS maxstorageutilizationvalue FLOAT64, \
            ADD COLUMN IF NOT EXISTS maxstoragerequest FLOAT64, \
            ADD COLUMN IF NOT EXISTS orgIdentifier STRING, \
            ADD COLUMN IF NOT EXISTS projectIdentifier STRING;" % (jsonData["tableIdAggregated"])
    try:
        query_job = client.query(query)
        print_(query_job.job_id)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e)
    else:
        print_("Finished altering %s table" % jsonData["tableIdAggregated"])

def alterTable(jsonData):
    print_("Altering %s Table" % jsonData["tableId"])
    query = "ALTER TABLE `%s` \
            ADD COLUMN IF NOT EXISTS storageactualidlecost FLOAT64, \
            ADD COLUMN IF NOT EXISTS maxstorageutilizationvalue FLOAT64, \
            ADD COLUMN IF NOT EXISTS maxstoragerequest FLOAT64, \
            ADD COLUMN IF NOT EXISTS orgIdentifier STRING, \
            ADD COLUMN IF NOT EXISTS projectIdentifier STRING;" % (jsonData["tableId"])
    try:
        query_job = client.query(query)
        print_(query_job.job_id)
        query_job.result()
    except Exception as e:
        print_(query)
        print_(e)
    else:
        print_("Finished altering %s table" % jsonData["tableId"])


def alter_unified_table(jsonData):
    print_("Altering unifiedTable Table")
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    query = "ALTER TABLE `%s.unifiedTable` \
        ADD COLUMN IF NOT EXISTS orgIdentifier STRING, \
        ADD COLUMN IF NOT EXISTS projectIdentifier STRING;" % ds

    try:
        print_(query)
        query_job = client.query(query)
        query_job.result()
    except Exception as e:
        # Error Running Alter Query
        print_(e)
    else:
        print_("Finished Altering unifiedTable Table")


def ingest_data_to_costagg(jsonData):
    if jsonData["isHourly"]:
        return
    ds = "%s.%s" % (PROJECTID, jsonData["datasetName"])
    table_name = "%s.%s.%s" % (PROJECTID, CEINTERNALDATASET, COSTAGGREGATED)
    source_table = "%s.%s" % (ds, UNIFIED)
    print_("Loading into %s table..." % table_name)
    query = """DELETE FROM `%s` WHERE DATE(day) = DATE(%s, %s, %s)  
                AND cloudProvider like "K8S_%%" AND accountId = '%s';
               INSERT INTO `%s` (day, cost, cloudProvider, accountId)
                SELECT TIMESTAMP_TRUNC(startTime, DAY) AS day, SUM(cost) AS cost, CONCAT(clusterType, '_', clusterCloudProvider) AS cloudProvider, '%s' as accountId
                FROM `%s`  
                WHERE DATE(startTime) = DATE(%s, %s, %s)  and cloudProvider = "CLUSTER" AND clusterType = "K8S"
                GROUP BY day, cloudProvider;
     """ % (table_name, jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"], jsonData.get("accountId"),
            table_name,
            jsonData.get("accountId"),
            source_table,
            jsonData["fileYear"], jsonData["fileMonth"], jsonData["fileDay"])

    job_config = bigquery.QueryJobConfig(
        priority=bigquery.QueryPriority.BATCH
    )
    run_batch_query(client, query, job_config, timeout=180)


MONTHMAP = {
    "JANUARY": 1,
    "FEBRUARY": 2,
    "MARCH": 3,
    "APRIL": 4,
    "MAY": 5,
    "JUNE": 6,
    "JULY": 7,
    "AUGUST": 8,
    "SEPTEMBER": 9,
    "OCTOBER": 10,
    "NOVEMBER": 11,
    "DECEMBER": 12
}
