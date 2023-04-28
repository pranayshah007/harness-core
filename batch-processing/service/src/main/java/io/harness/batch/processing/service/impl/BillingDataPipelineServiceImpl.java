/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getImpersonatedCredentials;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.BillingDataPipelineRecordDao;
import io.harness.batch.processing.service.BillingDataPipelineUtils;
import io.harness.batch.processing.service.intfc.BillingDataPipelineService;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;

import software.wings.beans.Account;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TimePartitioning;
import com.google.cloud.bigquery.datatransfer.v1.CreateTransferConfigRequest;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceClient.ListTransferRunsPagedResponse;
import com.google.cloud.bigquery.datatransfer.v1.DataTransferServiceSettings;
import com.google.cloud.bigquery.datatransfer.v1.ScheduleOptions;
import com.google.cloud.bigquery.datatransfer.v1.StartManualTransferRunsRequest;
import com.google.cloud.bigquery.datatransfer.v1.TransferConfig;
import com.google.cloud.bigquery.datatransfer.v1.TransferRun;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BillingDataPipelineServiceImpl implements BillingDataPipelineService {
  private static final String USER_AGENT_HEADER = "user-agent";
  private static final String USER_AGENT_HEADER_ENVIRONMENT_VARIABLE = "USER_AGENT_HEADER";
  private static final String DEFAULT_USER_AGENT = "default-user-agent";
  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";

  private static final String DATA_SET_DESCRIPTION_TEMPLATE = "Data set for [ AccountId: %s ], [ AccountName: %s ]";
  private static final String FILE_FORMAT_CONST = "file_format";
  private static final String DATA_PATH_TEMPLATE_CONST = "data_path_template";
  private static final String IGNORE_UNKNOWN_VALUES_CONST = "ignore_unknown_values";
  private static final String FIELD_DELIMITER_CONST = "field_delimiter";
  private static final String SKIP_LEADING_ROWS_CONST = "skip_leading_rows";
  private static final String ALLOWS_QUOTED_NEWLINES_CONST = "allow_quoted_newlines";
  private static final String ALLOWS_JAGGED_ROWS_CONST = "allow_jagged_rows";
  private static final String DELETE_SOURCE_FILES_CONST = "delete_source_files";
  private static final String DEST_TABLE_NAME_CONST = "destination_table_name_template";
  private static final String QUERY_CONST = "query";
  private static final String WRITE_DISPOSITION_CONST = "write_disposition";
  private static final String PARTITIONING_CONST = "partitioning_field";
  private static final String PARTITIONING_VALUE = "usage_start_time";
  private static final String TEMP_DEST_TABLE_NAME_VALUE = "awsCurTable_{run_time|\"%Y_%m\"}";
  private static final String PREV_MONTH_TEMP_DEST_TABLE_NAME_VALUE = "awsCurTable_{run_time-480h|\"%Y_%m\"}";
  private static final String DEST_TABLE_NAME_VALUE = "awscur_{run_time|\"%Y_%m\"}";
  private static final String PREV_MONTH_DEST_TABLE_NAME_VALUE = "awscur_{run_time-480h|\"%Y_%m\"}";
  private static final String GCP_DEST_TABLE_NAME_VALUE = "gcp_billing_export";
  private static final String PRE_AGG_TABLE_NAME_VALUE = "preAggregated";
  private static final String LOCATION = "us";
  private static final String GCS_DATA_SOURCE_ID = "google_cloud_storage";
  private static final String SCHEDULED_QUERY_DATA_SOURCE_ID = "scheduled_query";
  private static final String WRITE_TRUNCATE_VALUE = "WRITE_TRUNCATE";
  protected static final String PARENT_TEMPLATE = "projects/%s";
  protected static final String PARENT_TEMPLATE_WITH_LOCATION = "projects/%s/locations/%s";
  private static final String DATA_PATH_TEMPLATE = "%s/*%s/%s/%s/*/*.csv.gz";
  private static final String PREV_MONTH_DATA_PATH_TEMPLATE =
      "%s/*%s/%s/%s/{run_time-480h|\"%%Y%%m01\"}-{run_time|\"%%Y%%m01\"}/*.csv.gz";
  private static final String TRANSFER_JOB_NAME_TEMPLATE = "gcsToBigQueryTransferJob_%s";
  private static final String PREV_MONTH_TRANSFER_JOB_NAME_TEMPLATE = "prevMonthGcsToBigQueryTransferJob_%s";
  private static final String SCHEDULED_QUERY_TEMPLATE = "scheduledQuery_%s";
  private static final String PREV_MONTH_SCHEDULED_QUERY_TEMPLATE = "prevMonthScheduledQuery_%s";
  private static final String AWS_PRE_AGG_QUERY_TEMPLATE = "awsPreAggQuery_%s";

  public static final String scheduledQueryKey = "scheduledQuery";
  public static final String prevMonthScheduledQueryKey = "prevMonthScheduledQuery";
  public static final String preAggQueryKey = "preAggQueryKey";

  public static final String DATA_SET_NAME_TEMPLATE = "BillingReport_%s";
  private static final String PREV_MONTH_TEMP_TABLE_SCHEDULED_QUERY_TEMPLATE =
      "SELECT resourceid, usagestartdate, productname, productfamily, servicecode, blendedrate, blendedcost, unblendedrate, unblendedcost,"
      + " region, availabilityzone, usageaccountid,  instancetype, usagetype, lineitemtype, effectivecost, (%n"
      + " SELECT %n"
      + "   ARRAY_AGG(STRUCT(%n"
      + "     regexp_replace(REGEXP_EXTRACT(unpivotedData, '[^\"]*'), 'TAG_' , '') AS key%n"
      + "   , regexp_replace(REGEXP_EXTRACT(unpivotedData, r':\\\"[^\"]*'), ':\"', '') AS value%n"
      + "   ))%n"
      + "  FROM UNNEST(( %n"
      + "    SELECT REGEXP_EXTRACT_ALL(json, 'TAG_' || r'[^:]+:\\\"[^\"]+\\\"')%n"
      + "    FROM (SELECT TO_JSON_STRING(table) json))) unpivotedData) AS tags FROM `%s.%s.awsCurTable_*` table WHERE _TABLE_SUFFIX = %n"
      + "      CONCAT(CAST(EXTRACT(YEAR from TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date), INTERVAL 10 DAY), DAY)) as string),'_' , %n"
      + "  LPAD(CAST(EXTRACT(MONTH from TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date ), INTERVAL 10 DAY), DAY)) as string),2,'0'));%n"
      + "    ";
  private static final String TEMP_TABLE_SCHEDULED_QUERY_TEMPLATE =
      "SELECT resourceid, usagestartdate, productname, productfamily, servicecode, blendedrate, blendedcost, unblendedrate, unblendedcost,"
      + " region, availabilityzone, usageaccountid,  instancetype, usagetype, lineitemtype, effectivecost, (%n"
      + " SELECT %n"
      + "   ARRAY_AGG(STRUCT(%n"
      + "     regexp_replace(REGEXP_EXTRACT(unpivotedData, '[^\"]*'), 'TAG_' , '') AS key%n"
      + "   , regexp_replace(REGEXP_EXTRACT(unpivotedData, r':\\\"[^\"]*'), ':\"', '') AS value%n"
      + "   ))%n"
      + "  FROM UNNEST(( %n"
      + "    SELECT REGEXP_EXTRACT_ALL(json, 'TAG_' || r'[^:]+:\\\"[^\"]+\\\"')%n"
      + "    FROM (SELECT TO_JSON_STRING(table) json))) unpivotedData) AS tags FROM `%s.%s.awsCurTable_*` table WHERE "
      + "_TABLE_SUFFIX = CONCAT(CAST(EXTRACT(YEAR from TIMESTAMP_TRUNC(TIMESTAMP (@run_date), DAY)) "
      + "as string),'_' , LPAD(CAST(EXTRACT(MONTH from TIMESTAMP_TRUNC(TIMESTAMP (@run_date), DAY)) "
      + "as string),2,'0'));";

  private static final String AWS_PRE_AGG_TABLE_SCHEDULED_QUERY_TEMPLATE =
      "DELETE FROM `%s.preAggregated` WHERE DATE(startTime) "
      + ">= DATE_SUB(@run_date , INTERVAL 3 DAY) AND cloudProvider = \"AWS\";%n"
      + "%n"
      + "INSERT INTO `%s.preAggregated` (startTime, awsBlendedRate,awsBlendedCost,"
      + "awsUnblendedRate, awsUnblendedCost, cost, awsServicecode, region,awsAvailabilityzone,awsUsageaccountid,awsInstancetype,"
      + "awsUsagetype,cloudProvider)%n"
      + "SELECT TIMESTAMP_TRUNC(usagestartdate, DAY) as startTime, min(blendedrate) AS awsBlendedRate, sum(blendedcost) AS "
      + "awsBlendedCost, min(unblendedrate) AS awsUnblendedRate, sum(unblendedcost) AS awsUnblendedCost, sum(unblendedcost) AS "
      + "cost, productname AS awsServicecode, region, availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid, "
      + "instancetype AS awsInstancetype, usagetype AS awsUsagetype, \"AWS\" AS cloudProvider FROM "
      + "`%s.awscur_*` WHERE lineitemtype != 'Tax' AND %n"
      + "%n"
      + "_TABLE_SUFFIX %n"
      + "BETWEEN%n"
      + "  CONCAT(CAST(EXTRACT(YEAR from TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date), INTERVAL 10 DAY), DAY)) as string),'_' , %n"
      + "  LPAD(CAST(EXTRACT(MONTH from TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date ), INTERVAL 10 DAY), DAY)) as string),2,'0'))%n"
      + "AND%n"
      + "  CONCAT(CAST(EXTRACT(YEAR from TIMESTAMP_TRUNC(TIMESTAMP(@run_date), DAY)) as string),'_' , %n"
      + "  LPAD(CAST(EXTRACT(MONTH from TIMESTAMP_TRUNC(TIMESTAMP(@run_date), DAY)) as string),2,'0')) %n"
      + "AND %n"
      + "TIMESTAMP_TRUNC(usagestartdate, DAY) <= TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date), INTERVAL 1 DAY), DAY) AND "
      + "TIMESTAMP_TRUNC(usagestartdate, DAY) >=       TIMESTAMP_TRUNC(TIMESTAMP_SUB(TIMESTAMP (@run_date), INTERVAL 3 DAY), DAY) "
      + "GROUP BY awsServicecode, region, awsAvailabilityzone, awsUsageaccountid, awsInstancetype, awsUsagetype, startTime;";

  private static final String GCP_PRE_AGG_TABLE_SCHEDULED_QUERY_TEMPLATE = "DELETE FROM `%s.preAggregated` "
      + "WHERE DATE(startTime) >= DATE_SUB(@run_date , INTERVAL 3 DAY) AND cloudProvider = \"GCP\"; "
      + "INSERT INTO `%s.preAggregated` (cost, gcpProduct,gcpSkuId,gcpSkuDescription, startTime,gcpProjectId,region,zone,gcpBillingAccountId,cloudProvider, discount)"
      + "SELECT SUM(cost) AS cost, service.description AS gcpProduct, sku.id AS gcpSkuId, sku.description AS gcpSkuDescription,"
      + " TIMESTAMP_TRUNC(usage_start_time, DAY) as startTime, project.id AS gcpProjectId, location.region AS region, location.zone AS zone,"
      + " billing_account_id AS gcpBillingAccountId, \"GCP\" AS cloudProvider, SUM(credits.amount) as discount FROM `%s.gcp_billing_export*` "
      + "LEFT JOIN UNNEST(credits) as credits WHERE DATE(usage_start_time) <= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), "
      + "INTERVAL 1 DAY) AND DATE(usage_start_time) >= DATE_SUB(CAST(FORMAT_DATE('%%Y-%%m-%%d', @run_date) AS DATE), INTERVAL 3 DAY) "
      + "GROUP BY service.description, sku.id, sku.description, startTime, project.id, location.region, location.zone, billing_account_id;";

  private static final String RUN_ONCE_GCP_SCHEDULED_QUERY_TEMPLATE = "SELECT * FROM `%s.gcp_billing_export_*`;";

  private static final String TRANSFER_GCP_SCHEDULED_QUERY_TEMPLATE =
      "DELETE FROM `%s.gcp_billing_export` WHERE DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY);%n"
      + "INSERT INTO `%s.gcp_billing_export` SELECT * FROM `%s.gcp_billing_export_*` WHERE DATE(usage_start_time) >= DATE_SUB(@run_date , INTERVAL 3 DAY);";

  private BatchMainConfig mainConfig;
  private BillingDataPipelineRecordDao billingDataPipelineRecordDao;

  @Autowired
  public BillingDataPipelineServiceImpl(
      BatchMainConfig mainConfig, BillingDataPipelineRecordDao billingDataPipelineRecordDao) {
    this.mainConfig = mainConfig;
    this.billingDataPipelineRecordDao = billingDataPipelineRecordDao;
  }

  public static String getDataSetDescription(String harnessAccountId, String accountName) {
    return String.format(DATA_SET_DESCRIPTION_TEMPLATE, harnessAccountId, accountName);
  }

  public String createDataSet(Account account) {
    String accountId = account.getUuid();
    BillingDataPipelineRecord billingDataPipelineRecord = billingDataPipelineRecordDao.getByAccountId(accountId);
    if (billingDataPipelineRecord != null) {
      return billingDataPipelineRecord.getDataSetId();
    }
    String accountName = account.getAccountName();
    String dataSetName =
        String.format(DATA_SET_NAME_TEMPLATE, BillingDataPipelineUtils.modifyStringToComplyRegex(accountId));
    String description = getDataSetDescription(accountId, accountName);
    Map<String, String> labelMap =
        BillingDataPipelineUtils.getLabelMap(accountName, BillingDataPipelineUtils.getAccountType(account));

    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    BigQuery bigquery = BigQueryOptions.newBuilder()
                            .setCredentials(credentials)
                            .setHeaderProvider(getHeaderProvider())
                            .build()
                            .getService();
    try {
      DatasetInfo datasetInfo =
          DatasetInfo.newBuilder(dataSetName).setDescription(description).setLabels(labelMap).build();

      Dataset createdDataSet = bigquery.create(datasetInfo);
      bigquery.create(getPreAggregateTableInfo(dataSetName));
      return createdDataSet.getDatasetId().getDataset();
    } catch (BigQueryException bigQueryEx) {
      // data set/PreAggregate Table already exists.
      if (bigQueryEx.getCode() == 409) {
        if (bigquery.getTable(TableId.of(dataSetName, PRE_AGG_TABLE_NAME_VALUE)) == null) {
          bigquery.create(getPreAggregateTableInfo(dataSetName));
        }
        return dataSetName;
      }
      log.error("BQ Data Set was not created {} " + bigQueryEx);
    }
    return null;
  }

  private HeaderProvider getHeaderProvider() {
    String userAgent = System.getenv(USER_AGENT_HEADER_ENVIRONMENT_VARIABLE);
    return FixedHeaderProvider.create(
        ImmutableMap.of(USER_AGENT_HEADER, Objects.nonNull(userAgent) ? userAgent : DEFAULT_USER_AGENT));
  }

  @Override
  public String createTransferScheduledQueriesForGCP(String scheduledQueryName, String dstDataSetId,
      String impersonatedServiceAccount, String srcTablePrefix) throws IOException {
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String tablePrefix = gcpProjectId + "." + dstDataSetId;
    Map<String, Value> params = new HashMap<>();
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, impersonatedServiceAccount);
    DataTransferServiceClient dataTransferServiceClient = getDataTransferClient(credentials);

    String query = String.format(TRANSFER_GCP_SCHEDULED_QUERY_TEMPLATE, tablePrefix, tablePrefix, srcTablePrefix);
    params.put("query", Value.newBuilder().setStringValue(query).build());
    String parent = String.format(PARENT_TEMPLATE_WITH_LOCATION, gcpProjectId, LOCATION);
    log.info("Creating transfer scheduled queries for gcp {}", scheduledQueryName);
    TransferConfig transferConfig =
        TransferConfig.newBuilder()
            .setDisplayName(scheduledQueryName)
            .setParams(Struct.newBuilder().putAllFields(params).build())
            .setScheduleOptions(ScheduleOptions.newBuilder().setStartTime(getJobStartTimeStamp(1, 6, 0)).build())
            .setDataSourceId(SCHEDULED_QUERY_DATA_SOURCE_ID)
            .setNotificationPubsubTopic(mainConfig.getBillingDataPipelineConfig().getGcpPipelinePubSubTopic())
            .build();

    CreateTransferConfigRequest scheduledTransferConfigRequest =
        CreateTransferConfigRequest.newBuilder().setTransferConfig(transferConfig).setParent(parent).build();
    TransferConfig createdTransferConfig =
        executeDataTransferJobCreate(scheduledTransferConfigRequest, dataTransferServiceClient);
    log.info("Created transfer scheduled queries for gcp {}", createdTransferConfig.getName());
    return createdTransferConfig.getName();
  }

  @Override
  public String createRunOnceScheduledQueryGCP(String runOnceScheduledQueryName, String gcpBqProjectId,
      String gcpBqDatasetId, String dstDataSetId, String serviceAccountEmail) throws IOException {
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, serviceAccountEmail);
    DataTransferServiceClient client = getDataTransferClient(credentials);

    String query = String.format(RUN_ONCE_GCP_SCHEDULED_QUERY_TEMPLATE, gcpBqProjectId + "." + gcpBqDatasetId);
    log.info("Creating run once scheduled query for gcp {}", runOnceScheduledQueryName);
    String parent = String.format(PARENT_TEMPLATE, gcpProjectId);
    TransferConfig transferConfig =
        TransferConfig.newBuilder()
            .setDisplayName(runOnceScheduledQueryName)
            .setParams(
                Struct.newBuilder()
                    .putFields(QUERY_CONST, Value.newBuilder().setStringValue(query).build())
                    .putFields(WRITE_DISPOSITION_CONST, Value.newBuilder().setStringValue(WRITE_TRUNCATE_VALUE).build())
                    .putFields(PARTITIONING_CONST, Value.newBuilder().setStringValue(PARTITIONING_VALUE).build())
                    .putFields(
                        DEST_TABLE_NAME_CONST, Value.newBuilder().setStringValue(GCP_DEST_TABLE_NAME_VALUE).build())
                    .build())
            .setDestinationDatasetId(dstDataSetId)
            .setScheduleOptions(ScheduleOptions.newBuilder().setDisableAutoScheduling(true).build())
            .setNotificationPubsubTopic(mainConfig.getBillingDataPipelineConfig().getGcpPipelinePubSubTopic())
            .setDataSourceId(SCHEDULED_QUERY_DATA_SOURCE_ID)
            .build();
    CreateTransferConfigRequest transferConfigRequest =
        CreateTransferConfigRequest.newBuilder().setTransferConfig(transferConfig).setParent(parent).build();

    TransferConfig createdTransferConfig = executeDataTransferJobCreate(transferConfigRequest, client);
    log.info("Created run once scheduled query for gcp {}", createdTransferConfig.getName());
    return createdTransferConfig.getName();
  }

  @Override
  public String createScheduledQueriesForGCP(String scheduledQueryName, String dstDataSetId) throws IOException {
    if (mainConfig.getBillingDataPipelineConfig().isGcpUseNewPipeline()) {
      log.info("Using new pipeline. Not creating preaggregated scheduled query for GCP");
      return "";
    }
    log.info("Using old pipeline. creating preaggregated scheduled query for GCP");
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    DataTransferServiceClient dataTransferServiceClient = getDataTransferClient();
    String tablePrefix = gcpProjectId + "." + dstDataSetId;
    String query = String.format(GCP_PRE_AGG_TABLE_SCHEDULED_QUERY_TEMPLATE, tablePrefix, tablePrefix, tablePrefix);
    CreateTransferConfigRequest scheduledTransferConfigRequest =
        getTransferConfigRequest(dstDataSetId, scheduledQueryName, true, query, false);
    TransferConfig createdTransferConfig =
        executeDataTransferJobCreate(scheduledTransferConfigRequest, dataTransferServiceClient);
    return createdTransferConfig.getName();
  }

  @Override
  public HashMap<String, String> createScheduledQueriesForAWS(String dstDataSetId, String accountId, String accountName)
      throws IOException {
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String uniqueSuffixFromAccountId = getUniqueSuffixFromAccountId(accountId, accountName);

    HashMap<String, String> scheduledQueriesMap = new HashMap<>();
    DataTransferServiceClient dataTransferServiceClient = getDataTransferClient();

    // Create Scheduled query for the Fallback Temp Table
    String scheduledQueryName1 = String.format(SCHEDULED_QUERY_TEMPLATE, uniqueSuffixFromAccountId);
    String query1 = String.format(TEMP_TABLE_SCHEDULED_QUERY_TEMPLATE, gcpProjectId, dstDataSetId);
    CreateTransferConfigRequest scheduledTransferConfigRequest =
        getTransferConfigRequest(dstDataSetId, scheduledQueryName1, false, query1, false);
    executeDataTransferJobCreate(scheduledTransferConfigRequest, dataTransferServiceClient);
    scheduledQueriesMap.put(scheduledQueryKey, scheduledQueryName1);

    // Create Scheduled query for the Pre-Aggregated Table
    String scheduledQueryName2 = String.format(AWS_PRE_AGG_QUERY_TEMPLATE, uniqueSuffixFromAccountId);
    String tablePrefix = gcpProjectId + "." + dstDataSetId;
    String query2 = String.format(AWS_PRE_AGG_TABLE_SCHEDULED_QUERY_TEMPLATE, tablePrefix, tablePrefix, tablePrefix);
    CreateTransferConfigRequest preAggTransferConfigRequest =
        getTransferConfigRequest(dstDataSetId, scheduledQueryName2, true, query2, false);
    executeDataTransferJobCreate(preAggTransferConfigRequest, dataTransferServiceClient);
    scheduledQueriesMap.put(preAggQueryKey, scheduledQueryName2);

    // Create Scheduled query for prevMonth Fallback Temp Table
    String scheduledQueryName3 = String.format(PREV_MONTH_SCHEDULED_QUERY_TEMPLATE, uniqueSuffixFromAccountId);
    String query3 = String.format(PREV_MONTH_TEMP_TABLE_SCHEDULED_QUERY_TEMPLATE, gcpProjectId, dstDataSetId);
    CreateTransferConfigRequest prevMonthScheduledTransferConfigRequest =
        getTransferConfigRequest(dstDataSetId, scheduledQueryName3, false, query3, true);
    executeDataTransferJobCreate(prevMonthScheduledTransferConfigRequest, dataTransferServiceClient);
    scheduledQueriesMap.put(prevMonthScheduledQueryKey, scheduledQueryName3);
    return scheduledQueriesMap;
  }

  private CreateTransferConfigRequest getTransferConfigRequest(String dstDataSetId, String scheduledQueryName,
      boolean isPreAggregateScheduledQuery, String query, boolean isPrevMonthQuery) {
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String parent;
    int numberOfHours;
    int numberOfMinutes;
    TransferConfig transferConfig;

    if (isPreAggregateScheduledQuery) {
      parent = String.format(PARENT_TEMPLATE_WITH_LOCATION, gcpProjectId, LOCATION);
      numberOfHours = 6;
      numberOfMinutes = 30;

      transferConfig =
          TransferConfig.newBuilder()
              .setDisplayName(scheduledQueryName)
              .setParams(
                  Struct.newBuilder().putFields(QUERY_CONST, Value.newBuilder().setStringValue(query).build()).build())
              .setScheduleOptions(ScheduleOptions.newBuilder()
                                      .setStartTime(getJobStartTimeStamp(1, numberOfHours, numberOfMinutes))
                                      .build())
              .setDataSourceId(SCHEDULED_QUERY_DATA_SOURCE_ID)
              .build();
    } else {
      parent = String.format(PARENT_TEMPLATE, gcpProjectId);
      numberOfHours = 6;
      numberOfMinutes = 15;
      TransferConfig.Builder transferConfigBuilder =
          TransferConfig.newBuilder()
              .setDisplayName(scheduledQueryName)
              .setParams(Struct.newBuilder()
                             .putFields(QUERY_CONST, Value.newBuilder().setStringValue(query).build())
                             .putFields(WRITE_DISPOSITION_CONST,
                                 Value.newBuilder().setStringValue(WRITE_TRUNCATE_VALUE).build())
                             .putFields(DEST_TABLE_NAME_CONST,
                                 Value.newBuilder()
                                     .setStringValue(
                                         isPrevMonthQuery ? PREV_MONTH_DEST_TABLE_NAME_VALUE : DEST_TABLE_NAME_VALUE)
                                     .build())
                             .build())
              .setDestinationDatasetId(dstDataSetId)
              .setDataSourceId(SCHEDULED_QUERY_DATA_SOURCE_ID);

      if (!isPrevMonthQuery) {
        transferConfigBuilder.setScheduleOptions(
            ScheduleOptions.newBuilder().setStartTime(getJobStartTimeStamp(1, numberOfHours, numberOfMinutes)).build());
      }

      if (isPrevMonthQuery) {
        transferConfigBuilder.setSchedule("1,2,3,4,5,6,7,8,9,10 of month 06:15");
      }
      transferConfig = transferConfigBuilder.build();
    }

    return CreateTransferConfigRequest.newBuilder().setTransferConfig(transferConfig).setParent(parent).build();
  }
  @Override
  public void triggerTransferJobRun(String transferResourceName, String impersonatedServiceAccount) throws IOException {
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, impersonatedServiceAccount);
    DataTransferServiceClient client = getDataTransferClient(credentials);
    StartManualTransferRunsRequest request =
        StartManualTransferRunsRequest.newBuilder()
            .setParent(transferResourceName)
            .setRequestedRunTime(Timestamp.newBuilder()
                                     .setSeconds(TimeUnit.MILLISECONDS.toSeconds(Instant.now().toEpochMilli()))
                                     .build())
            .build();
    client.startManualTransferRuns(request);
  }

  @Override
  public List<TransferRun> listTransferRuns(String transferResourceName, String impersonatedServiceAccount)
      throws IOException {
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, impersonatedServiceAccount);
    DataTransferServiceClient client = getDataTransferClient(credentials);

    ListTransferRunsPagedResponse response = client.listTransferRuns(transferResourceName);
    return StreamSupport.stream(response.iterateAll().spliterator(), true).collect(Collectors.toList());
  }

  @Override
  public TransferRun getTransferRuns(String transferRunResourceName, String impersonatedServiceAccount)
      throws IOException {
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, impersonatedServiceAccount);
    DataTransferServiceClient client = getDataTransferClient(credentials);

    return client.getTransferRun(transferRunResourceName);
  }

  @Override
  public String createDataTransferJobFromBQ(String jobName, String srcProjectId, String srcDatasetId,
      String dstProjectId, String dstDatasetId, String impersonatedServiceAccount) throws IOException {
    ServiceAccountCredentials sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, impersonatedServiceAccount);
    DataTransferServiceClient client = getDataTransferClient(credentials);
    log.info("Creating data transfer job {}", jobName);
    String parent = String.format(PARENT_TEMPLATE, dstProjectId);
    TransferConfig transferConfig =
        TransferConfig.newBuilder()
            .setDisplayName(jobName)
            .setDataSourceId("cross_region_copy")
            .setDestinationDatasetId(dstDatasetId)
            .setParams(Struct.newBuilder()
                           .putFields("source_project_id", Value.newBuilder().setStringValue(srcProjectId).build())
                           .putFields("source_dataset_id", Value.newBuilder().setStringValue(srcDatasetId).build())
                           .putFields("overwrite_destination_table", Value.newBuilder().setBoolValue(true).build())
                           .build())
            .setScheduleOptions(ScheduleOptions.newBuilder().setStartTime(getJobStartTimeStamp(1, 6, 0)).build())
            .setNotificationPubsubTopic(mainConfig.getBillingDataPipelineConfig().getGcpPipelinePubSubTopic())
            .build();
    CreateTransferConfigRequest request =
        CreateTransferConfigRequest.newBuilder().setTransferConfig(transferConfig).setParent(parent).build();
    TransferConfig createdTransferConfig = executeDataTransferJobCreate(request, client);
    log.info("Created data transfer job {}", createdTransferConfig.getName());
    return createdTransferConfig.getName();
  }

  @Override
  public String createDataTransferJobFromGCS(String destinationDataSetId, String settingId, String accountId,
      String accountName, String curReportName, boolean isPrevMonthTransferJob) throws IOException {
    DataTransferServiceClient dataTransferServiceClient = getDataTransferClient();
    String gcpProjectId = mainConfig.getBillingDataPipelineConfig().getGcpProjectId();
    String parent = String.format(PARENT_TEMPLATE, gcpProjectId);
    String uniqueSuffixFromAccountId = getUniqueSuffixFromAccountId(accountId, accountName);
    String transferJobName = String.format(TRANSFER_JOB_NAME_TEMPLATE, uniqueSuffixFromAccountId);

    TransferConfig.Builder transferConfigBuilder =
        TransferConfig.newBuilder()
            .setDestinationDatasetId(destinationDataSetId)
            .setDataSourceId(GCS_DATA_SOURCE_ID)
            .setScheduleOptions(ScheduleOptions.newBuilder().setStartTime(getJobStartTimeStamp(1, 6, 0)).build());

    Struct.Builder paramsBuilder =
        Struct.newBuilder()
            .putFields(FILE_FORMAT_CONST, Value.newBuilder().setStringValue("CSV").build())
            .putFields(IGNORE_UNKNOWN_VALUES_CONST, Value.newBuilder().setBoolValue(true).build())
            .putFields(FIELD_DELIMITER_CONST, Value.newBuilder().setStringValue(",").build())
            .putFields(SKIP_LEADING_ROWS_CONST, Value.newBuilder().setStringValue("1").build())
            .putFields(ALLOWS_QUOTED_NEWLINES_CONST, Value.newBuilder().setBoolValue(true).build())
            .putFields(ALLOWS_JAGGED_ROWS_CONST, Value.newBuilder().setBoolValue(true).build())
            .putFields(DELETE_SOURCE_FILES_CONST, Value.newBuilder().setBoolValue(true).build());

    if (isPrevMonthTransferJob) {
      paramsBuilder
          .putFields(DATA_PATH_TEMPLATE_CONST,
              Value.newBuilder()
                  .setStringValue(String.format(PREV_MONTH_DATA_PATH_TEMPLATE,
                      mainConfig.getBillingDataPipelineConfig().getGcsBasePath(), accountId, settingId, curReportName))
                  .build())
          .putFields(
              DEST_TABLE_NAME_CONST, Value.newBuilder().setStringValue(PREV_MONTH_TEMP_DEST_TABLE_NAME_VALUE).build());
      transferJobName = String.format(PREV_MONTH_TRANSFER_JOB_NAME_TEMPLATE, uniqueSuffixFromAccountId);

      transferConfigBuilder.setDisplayName(transferJobName).setSchedule("1,2,3,4,5,6,7,8,9,10 of month 05:45");
    } else {
      paramsBuilder
          .putFields(DATA_PATH_TEMPLATE_CONST,
              Value.newBuilder()
                  .setStringValue(String.format(DATA_PATH_TEMPLATE,
                      mainConfig.getBillingDataPipelineConfig().getGcsBasePath(), accountId, settingId, curReportName))
                  .build())

          .putFields(DEST_TABLE_NAME_CONST, Value.newBuilder().setStringValue(TEMP_DEST_TABLE_NAME_VALUE).build());
    }

    TransferConfig transferConfig =
        transferConfigBuilder.setDisplayName(transferJobName).setParams(paramsBuilder.build()).build();

    CreateTransferConfigRequest createTransferConfigRequest =
        CreateTransferConfigRequest.newBuilder().setTransferConfig(transferConfig).setParent(parent).build();
    executeDataTransferJobCreate(createTransferConfigRequest, dataTransferServiceClient);
    return transferJobName;
  }

  private Timestamp getJobStartTimeStamp(int numberOfDays, int numberOfHours, int numberOfMinutes) {
    long timeStampInSeconds = Instant.now()
                                  .truncatedTo(ChronoUnit.DAYS)
                                  .plus(numberOfDays, ChronoUnit.DAYS)
                                  .plus(numberOfHours, ChronoUnit.HOURS)
                                  .plus(numberOfMinutes, ChronoUnit.MINUTES)
                                  .getEpochSecond();
    return Timestamp.newBuilder().setSeconds(timeStampInSeconds).build();
  }

  private String getUniqueSuffixFromAccountId(String harnessAccountId, String accountName) {
    return BillingDataPipelineUtils.modifyStringToComplyRegex(accountName) + "_"
        + BillingDataPipelineUtils.modifyStringToComplyRegex(harnessAccountId);
  }

  @VisibleForTesting
  TransferConfig executeDataTransferJobCreate(
      CreateTransferConfigRequest request, DataTransferServiceClient dataTransferServiceClient) {
    return dataTransferServiceClient.createTransferConfig(request);
  }

  public DataTransferServiceClient getDataTransferClient() throws IOException {
    GoogleCredentials sourceCredentials;
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv("USE_WORKLOAD_IDENTITY"));
    if (!usingWorkloadIdentity) {
      log.info("WI: Initializing DataTransferServiceClient with JSON Key file");
      sourceCredentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    } else {
      log.info("WI: DataTransferServiceClient using Google ADC");
      sourceCredentials = GoogleCredentials.getApplicationDefault();
    }
    Credentials credentials = getImpersonatedCredentials(sourceCredentials, null);
    return getDataTransferClient(credentials);
  }

  @VisibleForTesting
  DataTransferServiceClient getDataTransferClient(Credentials credentials) throws IOException {
    DataTransferServiceSettings dataTransferServiceSettings =
        DataTransferServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();
    return DataTransferServiceClient.create(dataTransferServiceSettings);
  }

  protected static TableInfo getPreAggregateTableInfo(String dataSetName) {
    TableId tableId = TableId.of(dataSetName, PRE_AGG_TABLE_NAME_VALUE);
    TimePartitioning partitioning =
        TimePartitioning.newBuilder(TimePartitioning.Type.DAY).setField("startTime").build();

    Schema schema = Schema.of(Field.of("cost", StandardSQLTypeName.FLOAT64),
        Field.of("discount", StandardSQLTypeName.FLOAT64), Field.of("gcpProduct", StandardSQLTypeName.STRING),
        Field.of("gcpSkuId", StandardSQLTypeName.STRING), Field.of("gcpSkuDescription", StandardSQLTypeName.STRING),
        Field.of("startTime", StandardSQLTypeName.TIMESTAMP), Field.of("gcpProjectId", StandardSQLTypeName.STRING),
        Field.of("region", StandardSQLTypeName.STRING), Field.of("zone", StandardSQLTypeName.STRING),
        Field.of("gcpBillingAccountId", StandardSQLTypeName.STRING),
        Field.of("cloudProvider", StandardSQLTypeName.STRING), Field.of("awsBlendedRate", StandardSQLTypeName.STRING),
        Field.of("awsBlendedCost", StandardSQLTypeName.FLOAT64),
        Field.of("awsUnblendedRate", StandardSQLTypeName.STRING),
        Field.of("awsUnblendedCost", StandardSQLTypeName.FLOAT64),
        Field.of("awsServicecode", StandardSQLTypeName.STRING),
        Field.of("awsAvailabilityzone", StandardSQLTypeName.STRING),
        Field.of("awsUsageaccountid", StandardSQLTypeName.STRING),
        Field.of("awsInstancetype", StandardSQLTypeName.STRING), Field.of("awsUsagetype", StandardSQLTypeName.STRING));

    StandardTableDefinition tableDefinition =
        StandardTableDefinition.newBuilder().setSchema(schema).setTimePartitioning(partitioning).build();
    return TableInfo.newBuilder(tableId, tableDefinition).build();
  }
}
