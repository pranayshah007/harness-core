/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.aws.AwsClientImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.clickHouse.ClickHouseServiceImpl;
import io.harness.ccm.commons.beans.JobConstants;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class S3ToClickHouseSyncTasklet implements Tasklet {
  @Autowired AwsClientImpl awsClient;
  @Autowired ClickHouseServiceImpl clickHouseService;
  @Autowired BatchMainConfig configuration;

  private static final int timeIntervalHours = 24;
  private static final Properties properties = new Properties();

  // util file similar to CFs' bq_schema
  private static final String createCCMDBQuery = "create database if not exists ccm;";
  private static final String createAwsCurTableQuery =
      "CREATE TABLE IF NOT EXISTS ccm.awscur ( `resourceid` String NULL, `usagestartdate` DateTime('UTC') NULL, `productname` String NULL, `productfamily` String NULL, `servicecode` String NULL, `servicename` String NULL, `blendedrate` String NULL, `blendedcost` Float NULL, `unblendedrate` String NULL, `unblendedcost` Float NULL, `region` String NULL, `availabilityzone` String NULL, `usageaccountid` String NULL, `instancetype` String NULL, `usagetype` String NULL, `lineitemtype` String NULL, `effectivecost` Float NULL, `usageamount` Float NULL, `billingentity` String NULL, `instanceFamily` String NULL, `marketOption` String NULL, `amortisedCost` Float NULL, `netAmortisedCost` Float NULL, `tags` Map(String, String) ) ENGINE = MergeTree ORDER BY tuple(usagestartdate) SETTINGS allow_nullable_key = 1;";
  private static final String createUnifiedTableTableQuery =
      "CREATE TABLE IF NOT EXISTS ccm.unifiedTable ( `startTime` DateTime('UTC') NOT NULL, `cost` Float NULL, `gcpProduct` String NULL, `gcpSkuId` String NULL, `gcpSkuDescription` String NULL, `gcpProjectId` String NULL, `region` String NULL, `zone` String NULL, `gcpBillingAccountId` String NULL, `cloudProvider` String NULL, `awsBlendedRate` String NULL, `awsBlendedCost` Float NULL, `awsUnblendedRate` String NULL, `awsUnblendedCost` Float NULL, `awsServicecode` String NULL, `awsAvailabilityzone` String NULL, `awsUsageaccountid` String NULL, `awsInstancetype` String NULL, `awsUsagetype` String NULL, `awsBillingEntity` String NULL, `discount` Float NULL, `endtime` DateTime('UTC') NULL, `accountid` String NULL, `instancetype` String NULL, `clusterid` String NULL, `clustername` String NULL, `appid` String NULL, `serviceid` String NULL, `envid` String NULL, `cloudproviderid` String NULL, `launchtype` String NULL, `clustertype` String NULL, `workloadname` String NULL, `workloadtype` String NULL, `namespace` String NULL, `cloudservicename` String NULL, `taskid` String NULL, `clustercloudprovider` String NULL, `billingamount` Float NULL, `cpubillingamount` Float NULL, `memorybillingamount` Float NULL, `idlecost` Float NULL, `maxcpuutilization` Float NULL, `avgcpuutilization` Float NULL, `systemcost` Float NULL, `actualidlecost` Float NULL, `unallocatedcost` Float NULL, `networkcost` Float NULL, `product` String NULL, `azureMeterCategory` String NULL, `azureMeterSubcategory` String NULL, `azureMeterId` String NULL, `azureMeterName` String NULL, `azureResourceType` String NULL, `azureServiceTier` String NULL, `azureInstanceId` String NULL, `azureResourceGroup` String NULL, `azureSubscriptionGuid` String NULL, `azureAccountName` String NULL, `azureFrequency` String NULL, `azurePublisherType` String NULL, `azurePublisherName` String NULL, `azureServiceName` String NULL, `azureSubscriptionName` String NULL, `azureReservationId` String NULL, `azureReservationName` String NULL, `azureResource` String NULL, `azureVMProviderId` String NULL, `azureTenantId` String NULL, `azureBillingCurrency` String NULL, `azureCustomerName` String NULL, `azureResourceRate` Float NULL, `orgIdentifier` String NULL, `projectIdentifier` String NULL, `labels` Map(String, String) ) ENGINE = MergeTree ORDER BY tuple(startTime) SETTINGS allow_nullable_key = 1;";
  private static final String createPreAggregatedTableQuery =
      "CREATE TABLE IF NOT EXISTS ccm.preAggregated ( `cost` Float NULL, `gcpProduct` String NULL, `gcpSkuId` String NULL, `gcpSkuDescription` String NULL, `startTime` DateTime('UTC') NULL, `gcpProjectId` String NULL, `region` String NULL, `zone` String NULL, `gcpBillingAccountId` String NULL, `cloudProvider` String NULL, `awsBlendedRate` String NULL, `awsBlendedCost` Float NULL, `awsUnblendedRate` String NULL, `awsUnblendedCost` Float NULL, `awsServicecode` String NULL, `awsAvailabilityzone` String NULL, `awsUsageaccountid` String NULL, `awsInstancetype` String NULL, `awsUsagetype` String NULL, `discount` Float NULL, `azureServiceName` String NULL, `azureResourceRate` Float NULL, `azureSubscriptionGuid` String NULL, `azureTenantId` String NULL ) ENGINE = MergeTree ORDER BY tuple(startTime) SETTINGS allow_nullable_key = 1;";
  private static final String createCostAggregatedTableQuery =
      "CREATE TABLE IF NOT EXISTS ccm.costAggregated ( `accountId` String NULL, `cloudProvider` String NOT NULL, `cost` Float NOT NULL, `day` DateTime('UTC') NOT NULL ) ENGINE = MergeTree ORDER BY tuple(day) SETTINGS allow_nullable_key = 1;";
  private static final String createConnectorDataSyncStatusTableQuery =
      "CREATE TABLE IF NOT EXISTS ccm.connectorDataSyncStatus ( `accountId` String NULL, `connectorId` String NOT NULL, `jobType` String NULL, `cloudProviderId` String NULL, `lastSuccessfullExecutionAt` DateTime('UTC') NOT NULL ) ENGINE = MergeTree ORDER BY tuple(lastSuccessfullExecutionAt) SETTINGS allow_nullable_key = 1;";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    log.info("isDeploymentOnPrem: " + configuration.getIsDeploymentOnPrem());
    if (!configuration.getIsDeploymentOnPrem()) {
      return null;
    }
    log.info("Running the S3ToClickHouseSync job");
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    log.info("Running s3ToCH for account: " + jobConstants.getAccountId());
    //    String accountId = jobConstants.getAccountId();
    //    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    //    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());
    Instant endTime = Instant.now();
    Instant startTime = endTime.minus(15, ChronoUnit.HOURS);

    // 0. create database/dataset from accountid "if not exists" if possible using query. else use ccm db.
    //     - and then use this DB. or use ccm db.
    //     - create awscur, unified, preagg. tables in the DB/ccm
    createDBAndTables();

    // 1. obtain all files updated in [..,..] which have manifest or csv.
    //     - store all manifest obj keys/path in a list
    //     - store all CSVs path in another map folders_to_ingest:
    //       {folder_to_ingest: [list of CSVs]} - it will have CSVs across months and across different
    //       connectors/awsaccounts
    // List<String> manifestJsons = new ArrayList<>();
    Map<String, List<String>> uniqueMonthFolders = new HashMap<>();
    Map<String, Long> csvFolderSizeMap = new HashMap<>();

    log.info((configuration.getAwsS3SyncConfig() != null) + "_isConfigObjectNotNull");
    log.info((configuration.getAwsS3SyncConfig().getAwsSecretKey() != null) + "_isSecretKeyNotNull");

    AWSCredentialsProvider credentials = awsClient.constructStaticBasicAwsCredentials(
        configuration.getAwsS3SyncConfig().getAwsAccessKey(), configuration.getAwsS3SyncConfig().getAwsSecretKey());
    S3Objects s3Objects = awsClient.getIterableS3ObjectSummaries(
        credentials, configuration.getAwsS3SyncConfig().getAwsS3BucketName(), "");
    for (S3ObjectSummary objectSummary : s3Objects) {
      List<String> path = Arrays.asList(objectSummary.getKey().split("/"));
      if (path.size() != 5 && path.size() != 6)
        continue;

      // gcs DT job's work will be performed by this 'if' condition
      if (objectSummary.getLastModified().compareTo(java.util.Date.from(startTime)) >= 0
          && objectSummary.getLastModified().compareTo(java.util.Date.from(endTime)) <= 0) {
        if (objectSummary.getKey().endsWith(".csv.gz") || objectSummary.getKey().endsWith(".csv.zip")
            || objectSummary.getKey().endsWith(".csv")) {
          // String folderName = String.join("/", path.subList(0, path.size()-1));
          String csvFolderName = String.join("/", path.subList(0, path.size() - 1));
          if (!isValidMonthFolder(path.get(path.size() - 2))) {
            // versioned folder case
            // update uniqueMonthFolders
            String monthFolderPath = String.join("/", path.subList(0, path.size() - 2));
            if (!uniqueMonthFolders.containsKey(monthFolderPath)) {
              uniqueMonthFolders.put(monthFolderPath, new ArrayList<>());
            }
            List<String> subFoldersList = uniqueMonthFolders.get(monthFolderPath);
            subFoldersList.add(csvFolderName);
            uniqueMonthFolders.put(monthFolderPath, subFoldersList);

            // update csvFolderSizeMap
            if (!csvFolderSizeMap.containsKey(csvFolderName)) {
              csvFolderSizeMap.put(csvFolderName, objectSummary.getSize());
            } else {
              csvFolderSizeMap.put(csvFolderName, csvFolderSizeMap.get(csvFolderName) + objectSummary.getSize());
            }
          } else {
            // update uniqueMonthFolders
            uniqueMonthFolders.put(csvFolderName, List.of(csvFolderName));

            // update csvFolderSizeMap
            if (!csvFolderSizeMap.containsKey(csvFolderName)) {
              csvFolderSizeMap.put(csvFolderName, objectSummary.getSize());
            } else {
              csvFolderSizeMap.put(csvFolderName, csvFolderSizeMap.get(csvFolderName) + objectSummary.getSize());
            }
          }
        }
        // else if (objectSummary.getKey().endsWith("Manifest.json")) {
        //     manifestJsons.add(objectSummary.getKey());
        // }
      }
    }

    Set<String> foldersToIngestSet = new HashSet<String>();

    for (String monthFolder : uniqueMonthFolders.keySet()) {
      // System.out.println(monthFolder + "\n");
      long maxSizeAmongVersionedFolders = 0;
      String maxSizedVersionedFolderName = null;
      for (String versionedFolder : uniqueMonthFolders.get(monthFolder)) {
        if (csvFolderSizeMap.get(versionedFolder) > maxSizeAmongVersionedFolders) {
          maxSizeAmongVersionedFolders = csvFolderSizeMap.get(versionedFolder);
          maxSizedVersionedFolderName = versionedFolder;
        }
      }
      foldersToIngestSet.add(maxSizedVersionedFolderName);
    }
    List<String> foldersToIngest = new ArrayList<String>(foldersToIngestSet);

    log.info("\nFollowing folders will be ingested:\n" + String.join(", ", foldersToIngest));

    for (String folderPath : foldersToIngest) {
      String jsonString = fetchSchemaFromManifestFileInFolder(folderPath);
      JSONObject obj = new JSONObject(jsonString);

      JSONArray column_list = obj.getJSONArray("columns");
      Map<String, Integer> map_tags = new HashMap<>();
      String schema = "";
      List<String> availableColumns = new ArrayList<>();

      for (int i = 0; i < column_list.length(); i++) {
        if (!schema.isEmpty())
          schema += ", ";

        JSONObject column = column_list.getJSONObject(i);
        String name = column.getString("name").toLowerCase().replaceAll("\\s+", "");
        // uncomment following line to test for tag column
        // name = "aws:autoscaling:groupName";
        String nameConverted = name;
        if (name.replaceAll("[^a-zA-Z0-9_]", "_") != name) {
          nameConverted = name.replaceAll("[^a-zA-Z0-9_]", "_");
          name = "TAG_" + nameConverted;
        }
        // System.out.println(nameConverted);
        String name_for_map = nameConverted;
        if (map_tags.containsKey(name_for_map)) {
          name = name + "_" + map_tags.get(name_for_map);
          map_tags.put(name_for_map, map_tags.get(name_for_map) + 1);
        } else {
          map_tags.put(name_for_map, 1);
        }
        String dataType = getMappedDataColumn(column.getString("type"));
        schema += ("`" + name + "` " + dataType + " NULL");
        availableColumns.add(name);
      }

      // System.out.println(schema);

      List<String> ps = Arrays.asList(folderPath.split("/"));
      String monthFolder = "";
      if (ps.size() == 4) {
        monthFolder = ps.get(ps.size() - 1);
      } else if (ps.size() == 5) {
        monthFolder = ps.get(ps.size() - 2);
      }
      String reportYear = Arrays.asList(monthFolder.split("-")).get(0).substring(0, 4);
      String reportMonth = Arrays.asList(monthFolder.split("-")).get(0).substring(4, 6);
      String connectorId = ps.get(1);
      String awsBillingTableId = "ccm.awsBilling_" + connectorId + "_" + reportYear + "_" + reportMonth;

      // System.out.println(awsBillingTableId + " <-- " + folderPath);

      // DROP existing table if found
      clickHouseService.executeClickHouseQuery(
          configuration.getClickHouseConfig(), "DROP TABLE IF EXISTS " + awsBillingTableId, Boolean.FALSE);

      String createAwsBillingTableQuery = "CREATE TABLE IF NOT EXISTS " + awsBillingTableId + " (" + schema
          + " ) ENGINE = MergeTree ORDER BY tuple(usagestartdate) SETTINGS allow_nullable_key = 1;";
      // System.out.println(createAwsBillingTableQuery);
      log.info(createAwsBillingTableQuery);
      clickHouseService.executeClickHouseQuery(
          configuration.getClickHouseConfig(), createAwsBillingTableQuery, Boolean.FALSE);

      insertIntoAwsBillingTableFromS3Bucket(awsBillingTableId, folderPath);

      List<String> usageAccountIds = getUniqueAccountIds(awsBillingTableId);
      log.info(String.join(", ", usageAccountIds));

      ingestDataIntoAwsCur(awsBillingTableId, usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");
      ingestDataIntoUnified(usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");
      ingestDataIntoPreAgg(usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");

      updateConnectorDataSyncStatus(connectorId);
      ingestDataIntoCostAgg(usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");
    }

    return null;
  }

  public void updateConnectorDataSyncStatus(String connectorId) throws Exception {
    final String PATTERN_FORMAT = "yyyy-MM-dd HH:mm:ss";
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT).withZone(ZoneOffset.UTC);
    String currentInstant = formatter.format(Instant.now());
    String insertQuery =
        "INSERT INTO ccm.connectorDataSyncStatus (connectorId, lastSuccessfullExecutionAt, jobType, cloudProviderId)  "
        + "  VALUES ('" + connectorId + "', '" + currentInstant + "', 'cloudfunction', 'AWS');";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public void ingestDataIntoCostAgg(List<String> usageAccountIds, String month) throws Exception {
    // not added accountId condition since on-prem customer will have a single accountId anyway
    // todo: should we add accountId field in costAgg for on-prem?
    String deleteQuery =
        "DELETE from ccm.costAggregated WHERE DATE_TRUNC('month', day) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND cloudProvider = 'AWS';";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), deleteQuery, Boolean.FALSE);

    String insertQuery = "INSERT INTO ccm.costAggregated (day, cost, cloudProvider)  "
        + "  SELECT date_trunc('day', startTime) AS day, SUM(cost) AS cost, 'AWS' AS cloudProvider  "
        + "  FROM ccm.unifiedTable "
        + "  WHERE DATE_TRUNC('month', day) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND cloudProvider = 'AWS'"
        + "  GROUP BY day;";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public void ingestDataIntoPreAgg(List<String> usageAccountIds, String month) throws Exception {
    String deleteQuery =
        "DELETE from ccm.preAggregated WHERE DATE_TRUNC('month', startTime) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND cloudProvider = 'AWS' AND awsUsageaccountid IN ("
        + String.join(", ",
            usageAccountIds.stream().map(usageAccountId -> "'" + usageAccountId + "'").collect(Collectors.toList()))
        + ");";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), deleteQuery, Boolean.FALSE);

    String insertQuery =
        "INSERT INTO ccm.preAggregated (startTime, awsBlendedRate, awsBlendedCost, awsUnblendedRate, awsUnblendedCost, cost, "
        + "        awsServicecode, region, awsAvailabilityzone, awsUsageaccountid, "
        + "        awsUsagetype, cloudProvider, awsInstancetype) "
        + "  SELECT date_trunc('day', usagestartdate) as startTime, min(blendedrate) AS awsBlendedRate, sum(blendedcost) AS awsBlendedCost, "
        + "  min(unblendedrate) AS awsUnblendedRate, sum(unblendedcost) AS awsUnblendedCost, sum(unblendedcost) AS cost, "
        + "  servicename AS awsServicecode, region, availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid, "
        + "  usagetype AS awsUsagetype, 'AWS' AS cloudProvider, instancetype as awsInstancetype  "
        + "  FROM ccm.awscur "
        + "  WHERE DATE_TRUNC('month', usagestartdate) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND usageaccountid IN ("
        + String.join(", ",
            usageAccountIds.stream().map(usageAccountId -> "'" + usageAccountId + "'").collect(Collectors.toList()))
        + ")"
        + "  GROUP BY awsServicecode, region, awsAvailabilityzone, awsUsageaccountid, awsUsagetype, startTime, awsInstancetype;";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public void ingestDataIntoUnified(List<String> usageAccountIds, String month) throws Exception {
    String deleteQuery =
        "DELETE from ccm.unifiedTable WHERE DATE_TRUNC('month', startTime) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND cloudProvider = 'AWS' AND awsUsageaccountid IN ("
        + String.join(", ",
            usageAccountIds.stream().map(usageAccountId -> "'" + usageAccountId + "'").collect(Collectors.toList()))
        + ");";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), deleteQuery, Boolean.FALSE);

    String insertQuery = "INSERT INTO ccm.unifiedTable (product, startTime,  "
        + "      awsBlendedRate, awsBlendedCost,awsUnblendedRate,  "
        + "      awsUnblendedCost, cost, awsServicecode, region,  "
        + "      awsAvailabilityzone, awsUsageaccountid,  "
        + "      cloudProvider, awsBillingEntity, awsInstancetype, awsUsagetype) "
        + "  SELECT productname AS product, date_trunc('day', usagestartdate) as startTime,  "
        + "      blendedrate AS awsBlendedRate, blendedcost AS awsBlendedCost, unblendedrate AS awsUnblendedRate,  "
        + "      unblendedcost AS awsUnblendedCost, unblendedcost AS cost, servicename AS awsServicecode, region,  "
        + "      availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid,  "
        + "      'AWS' AS cloudProvider, billingentity as awsBillingEntity, instancetype as awsInstancetype, usagetype as awsUsagetype  "
        + "  FROM ccm.awscur  "
        + "  WHERE DATE_TRUNC('month', usagestartdate) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND usageaccountid IN ("
        + String.join(", ",
            usageAccountIds.stream().map(usageAccountId -> "'" + usageAccountId + "'").collect(Collectors.toList()))
        + ");";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public void ingestDataIntoAwsCur(String awsBillingTableId, List<String> usageAccountIds, String month)
      throws Exception {
    String awsBillingTableName = awsBillingTableId.split("\\.")[1];
    String tagColumnsQuery = "SELECT column_name FROM INFORMATION_SCHEMA.COLUMNS "
        + "WHERE (column_name LIKE 'TAG_%') AND (table_schema = 'ccm') AND (table_name = '" + awsBillingTableName + "');";
    List<String> tagColumns =
        clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), tagColumnsQuery, Boolean.TRUE);
    String tagsQueryStatement1 = "";
    String tagsQueryStatement2 = "";
    String tagsQueryStatement3 = " array() ";
    for (String tagColumn : tagColumns) {
      tagsQueryStatement1 += (tagsQueryStatement1.isEmpty() ? "" : ", ");
      tagsQueryStatement1 += ("'" + tagColumn.replaceFirst("TAG_", "") + "'");

      tagsQueryStatement2 += (tagsQueryStatement2.isEmpty() ? "" : ", ");
      tagsQueryStatement2 += ("ifNull(" + tagColumn + ", toString(NULL))");
    }
    if (!tagColumns.isEmpty()) {
      tagsQueryStatement3 =
          " arrayMap(i -> if((tagsPresent[i]) = 0, toString(NULL), tagsAllKey[i]), arrayEnumerate(tagsPresent)) ";
    }

    String deleteQuery =
        "DELETE from ccm.awscur WHERE DATE_TRUNC('month', usagestartdate) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND usageaccountid IN ("
        + String.join(", ",
            usageAccountIds.stream().map(usageAccountId -> "'" + usageAccountId + "'").collect(Collectors.toList()))
        + ");";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), deleteQuery, Boolean.FALSE);
    // TODO: generate the tags query programmatically using separate query to awsBilling table - currently using
    // hardcoded values.
    String insertQuery =
        "INSERT INTO ccm.awscur (productfamily, unblendedrate, billingentity, usagetype, servicecode, region, blendedcost, unblendedcost, resourceid, productname, availabilityzone, servicename, effectivecost, usageamount, lineitemtype, usagestartdate, instancetype, usageaccountid, blendedrate, amortisedCost, netAmortisedCost, tags)  "
        + "SELECT * EXCEPT (tagsKey, tagsAllKey, tagsValue, tagsPresent) "
        + "FROM "
        + "( "
        + "    SELECT "
        + "        productfamily, "
        + "        unblendedrate, "
        + "        billingentity, "
        + "        usagetype, "
        + "        servicecode, "
        + "        region, "
        + "        blendedcost, "
        + "        unblendedcost, "
        + "        resourceid, "
        + "        productname, "
        + "        availabilityzone, "
        + "        servicename, "
        + "        effectivecost, "
        + "        usageamount, "
        + "        lineitemtype, "
        + "        usagestartdate, "
        + "        instancetype, "
        + "        usageaccountid, "
        + "        blendedrate, "
        + "        multiIf(lineitemtype = 'SavingsPlanNegation', 0, lineitemtype = 'SavingsPlanUpfrontFee', 0, lineitemtype = 'SavingsPlanCoveredUsage', savingsplaneffectivecost, lineitemtype = 'SavingsPlanRecurringFee', totalcommitmenttodate - usedcommitment, lineitemtype = 'DiscountedUsage', effectivecost, lineitemtype = 'RIFee', unusedamortizedupfrontfeeforbillingperiod + unusedrecurringfee, unblendedcost) AS amortisedCost, "
        + "        multiIf(lineitemtype = 'SavingsPlanNegation', 0, lineitemtype = 'SavingsPlanUpfrontFee', 0, lineitemtype = 'SavingsPlanRecurringFee', totalcommitmenttodate - usedcommitment, 0) AS netAmortisedCost, "
        + "        arrayFilter(x -> isNotNull(x)," + tagsQueryStatement3 + " ) AS tagsKey, "
        + "        array(" + tagsQueryStatement1 + ") AS tagsAllKey, "
        + "        arrayFilter(x -> isNotNull(x), array(" + tagsQueryStatement2 + ") ) AS tagsValue, "
        + "        arrayMap(x -> isNotNull(x), array(" + tagsQueryStatement2 + ") ) AS tagsPresent, "
        + "        CAST((tagsKey, tagsValue), 'Map(String, String)') AS tags "
        + "    FROM " + awsBillingTableId
        + "    WHERE DATE_TRUNC('month', usagestartdate) = DATE_TRUNC('month', toDateTime('" + month + " 00:00:00') ) "
        + ");";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public List<String> getUniqueAccountIds(String awsBillingTableId) throws Exception {
    String selectQuery = "SELECT DISTINCT(usageaccountid) FROM " + awsBillingTableId + ";";
    return clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), selectQuery, Boolean.TRUE);
  }

  public void insertIntoAwsBillingTableFromS3Bucket(String awsBillingTableId, String csvFolderPath) throws Exception {
    String insertQuery = "SET input_format_csv_skip_first_lines=1; INSERT INTO " + awsBillingTableId
        + " SELECT * FROM s3('https://" + configuration.getAwsS3SyncConfig().getAwsS3BucketName() + ".s3.amazonaws.com/"
        + csvFolderPath + "/*.csv.gz','" + configuration.getAwsS3SyncConfig().getAwsAccessKey() + "','"
        + configuration.getAwsS3SyncConfig().getAwsSecretKey()
        + "', 'CSV') SETTINGS date_time_input_format='best_effort'";
    // FORMAT CSV format_csv_delimiter=',' input_format_csv_skip_first_lines=1;
    // System.out.println("\n" + insertQuery);
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public String getMappedDataColumn(String dataType) throws Exception {
    // System.out.println("DataType = " + dataType);
    String modifiedDataType = "String";
    if (dataType.equals("String")) {
      modifiedDataType = "String";
    } else if (dataType.equals("OptionalString")) {
      modifiedDataType = "String";
    } else if (dataType.equals("Interval")) {
      modifiedDataType = "String";
    } else if (dataType.equals("DateTime")) {
      modifiedDataType = "DateTime('UTC')";
    } else if (dataType.equals("BigDecimal")) {
      modifiedDataType = "Float";
    } else if (dataType.equals("OptionalBigDecimal")) {
      modifiedDataType = "Float";
    } else {
      modifiedDataType = "String";
    }
    // System.out.println("Returning = " + modifiedDataType);

    return modifiedDataType;
  }

  public String fetchSchemaFromManifestFileInFolder(String folderPath) throws Exception {
    AWSCredentialsProvider credentials = awsClient.constructStaticBasicAwsCredentials(
        configuration.getAwsS3SyncConfig().getAwsAccessKey(), configuration.getAwsS3SyncConfig().getAwsSecretKey());
    S3Objects s3Objects = awsClient.getIterableS3ObjectSummaries(
        credentials, configuration.getAwsS3SyncConfig().getAwsS3BucketName(), folderPath);
    for (S3ObjectSummary objectSummary : s3Objects) {
      List<String> objectPathAsList = Arrays.asList(objectSummary.getKey().split("/"));
      // System.out.println(String.join("/", objectPathAsList.subList(0, objectPathAsList.size()-1) ));
      // System.out.println(folderPath + "<--");
      // if (folderPath != String.join("/", objectPathAsList.subList(0, objectPathAsList.size()-1) ) ) {
      //     // fix this if condition.
      //     continue;
      // }
      // System.out.println(objectSummary.getKey() + " " +objectSummary.getKey().endsWith("Manifest.json") + "<--");
      if (objectSummary.getKey().endsWith("Manifest.json")) {
        AmazonS3Client s3 = awsClient.getAmazonS3Client(credentials);
        S3Object o = s3.getObject(configuration.getAwsS3SyncConfig().getAwsS3BucketName(), objectSummary.getKey());
        S3ObjectInputStream s3is = o.getObjectContent();
        String str = getAsString(s3is);
        return str;
      }
    }

    return null;
  }

  private String getAsString(InputStream is) throws IOException {
    if (is == null)
      return "";
    StringBuilder sb = new StringBuilder();
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    } finally {
      is.close();
    }
    return sb.toString();
  }

  public boolean isValidMonthFolder(String folderName) throws Exception {
    try {
      List<String> reportMonth = Arrays.asList(folderName.split("-"));
      String startStr = reportMonth.get(0);
      String endStr = reportMonth.get(1);
      if (startStr.length() != 8 || endStr.length() != 8 || Integer.parseInt(endStr) <= Integer.parseInt(startStr))
        return Boolean.FALSE;
      java.util.Date startDate = new SimpleDateFormat("yyyyMMdd").parse(startStr);
      java.util.Date endDate = new SimpleDateFormat("yyyyMMdd").parse(endStr);
    } catch (Exception e) {
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  public void createDBAndTables() throws Exception {
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), createCCMDBQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), createAwsCurTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), createUnifiedTableTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), createPreAggregatedTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), createCostAggregatedTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), createConnectorDataSyncStatusTableQuery, Boolean.FALSE);
  }
}
