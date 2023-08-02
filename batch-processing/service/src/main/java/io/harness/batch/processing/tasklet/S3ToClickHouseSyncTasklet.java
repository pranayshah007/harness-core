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
import io.harness.ccm.clickHouse.ClickHouseConstants;
import io.harness.ccm.clickHouse.ClickHouseServiceImpl;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.configuration.DeployMode;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    log.info("isDeploymentOnPrem: " + configuration.getDeployMode());
    if (!DeployMode.isOnPrem(configuration.getDeployMode().name()) || !configuration.isClickHouseEnabled()) {
      return null;
    }
    log.info("Running the S3ToClickHouseSync job");
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    log.info("Running s3ToCH for account: " + jobConstants.getAccountId());
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());
    // Instant endTime = Instant.now();
    // Instant startTime = endTime.minus(15, ChronoUnit.HOURS);

    createDBAndTables();

    Map<String, List<String>> uniqueMonthFolders = new HashMap<>();
    Map<String, Long> csvFolderSizeMap = new HashMap<>();

    AWSCredentialsProvider credentials = awsClient.constructStaticBasicAwsCredentials(
        configuration.getAwsS3SyncConfig().getAwsAccessKey(), configuration.getAwsS3SyncConfig().getAwsSecretKey());
    S3Objects s3Objects = awsClient.getIterableS3ObjectSummaries(
        credentials, configuration.getAwsS3SyncConfig().getAwsS3BucketName(), "");
    for (S3ObjectSummary objectSummary : s3Objects) {
      try {
        List<String> path = Arrays.asList(objectSummary.getKey().split("/"));
        if (path.size() != 5 && path.size() != 6) {
          continue;
        }

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
        }
      } catch (Exception e) {
        log.error(String.format("Exception while processing s3 object: %s", objectSummary.getKey()), e);
      }
    }

    Set<String> foldersToIngestSet = new HashSet<>();

    for (String monthFolder : uniqueMonthFolders.keySet()) {
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
    List<String> foldersToIngest = new ArrayList<>(foldersToIngestSet);

    log.info("\nFollowing folders will be ingested:\n" + String.join(", ", foldersToIngest));

    for (String folderPath : foldersToIngest) {
      try {
        String jsonString = fetchSchemaFromManifestFileInFolder(folderPath);
        JSONObject obj = new JSONObject(jsonString);

        JSONArray column_list = obj.getJSONArray("columns");
        Map<String, Integer> map_tags = new HashMap<>();
        String schema = "";
        List<String> availableColumns = new ArrayList<>();

        for (int i = 0; i < column_list.length(); i++) {
          if (!schema.isEmpty()) {
            schema += ", ";
          }

          JSONObject column = column_list.getJSONObject(i);
          String name = column.getString("name").toLowerCase().replaceAll("\\s+", "");
          // uncomment following line to test for tag column
          // name = "aws:autoscaling:groupName";
          String nameConverted = name;
          if (!name.replaceAll("[^a-zA-Z0-9_]", "_").equals(name)) {
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
          schema += "`" + name + "` " + dataType + " NULL";
          availableColumns.add(name);
        }

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

        // DROP existing table if found
        clickHouseService.executeClickHouseQuery(
            configuration.getClickHouseConfig(), "DROP TABLE IF EXISTS " + awsBillingTableId, Boolean.FALSE);

        String createAwsBillingTableQuery = "CREATE TABLE IF NOT EXISTS " + awsBillingTableId + " (" + schema
            + " ) ENGINE = MergeTree ORDER BY tuple(usagestartdate) SETTINGS allow_nullable_key = 1;";
        log.info(createAwsBillingTableQuery);
        clickHouseService.executeClickHouseQuery(
            configuration.getClickHouseConfig(), createAwsBillingTableQuery, Boolean.FALSE);

        insertIntoAwsBillingTableFromS3Bucket(awsBillingTableId, folderPath);

        List<String> usageAccountIds = getUniqueAccountIds(awsBillingTableId);
        log.info(String.join(", ", usageAccountIds));

        ingestDataIntoAwsCur(awsBillingTableId, usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");
        ingestDataIntoUnified(usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");
        ingestDataIntoPreAgg(usageAccountIds, "" + reportYear + "-" + reportMonth + "-01");

        updateConnectorDataSyncStatus(accountId, connectorId);
        ingestDataIntoCostAgg(accountId, "" + reportYear + "-" + reportMonth + "-01");
      } catch (Exception e) {
        log.error(String.format("Exception while processing s3 bucket path: %s", folderPath), e);
      }
    }

    return null;
  }

  public void updateConnectorDataSyncStatus(String accountId, String connectorId) throws Exception {
    final String PATTERN_FORMAT = "yyyy-MM-dd HH:mm:ss";
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT).withZone(ZoneOffset.UTC);
    String currentInstant = formatter.format(Instant.now());
    String insertQuery =
        "INSERT INTO ccm.connectorDataSyncStatus (accountId, connectorId, lastSuccessfullExecutionAt, jobType, cloudProviderId)  "
        + "  VALUES ('" + accountId + "', '" + connectorId + "', '" + currentInstant + "', 'cloudfunction', 'AWS');";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
  }

  public void ingestDataIntoCostAgg(String accountId, String month) throws Exception {
    String deleteQuery =
        "DELETE from ccm.costAggregated WHERE DATE_TRUNC('month', day) = DATE_TRUNC('month', toDateTime('" + month
        + " 00:00:00')) AND cloudProvider = 'AWS' AND accountId='" + accountId + "';";
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), deleteQuery, Boolean.FALSE);

    String insertQuery = "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId)  "
        + "  SELECT date_trunc('day', startTime) AS day, SUM(cost) AS cost, 'AWS' AS cloudProvider, '" + accountId
        + "' as accountId "
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
        + "      cloudProvider, awsBillingEntity, labels, awsInstancetype, awsUsagetype) "
        + "  SELECT productname AS product, date_trunc('day', usagestartdate) as startTime,  "
        + "      blendedrate AS awsBlendedRate, blendedcost AS awsBlendedCost, unblendedrate AS awsUnblendedRate,  "
        + "      unblendedcost AS awsUnblendedCost, unblendedcost AS cost, servicename AS awsServicecode, region,  "
        + "      availabilityzone AS awsAvailabilityzone, usageaccountid AS awsUsageaccountid,  "
        + "      'AWS' AS cloudProvider, billingentity as awsBillingEntity, tags AS labels, instancetype as awsInstancetype, usagetype as awsUsagetype  "
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
        + "WHERE (column_name LIKE 'TAG_%') AND (table_schema = 'ccm') AND (table_name = '" + awsBillingTableName
        + "');";
    List<String> tagColumns =
        clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), tagColumnsQuery, Boolean.TRUE);
    String tagsQueryStatement1 = "";
    String tagsQueryStatement2 = "";
    String tagsQueryStatement3 = " array() ";
    for (String tagColumn : tagColumns) {
      tagsQueryStatement1 += tagsQueryStatement1.isEmpty() ? "" : ", ";
      tagsQueryStatement1 += "'" + tagColumn.replaceFirst("TAG_", "") + "'";

      tagsQueryStatement2 += tagsQueryStatement2.isEmpty() ? "" : ", ";
      tagsQueryStatement2 += "ifNull(" + tagColumn + ", toString(NULL))";
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
    AWSCredentialsProvider credentials = awsClient.constructStaticBasicAwsCredentials(
        configuration.getAwsS3SyncConfig().getAwsAccessKey(), configuration.getAwsS3SyncConfig().getAwsSecretKey());
    S3Objects s3Objects = awsClient.getIterableS3ObjectSummaries(
        credentials, configuration.getAwsS3SyncConfig().getAwsS3BucketName(), csvFolderPath);
    for (S3ObjectSummary objectSummary : s3Objects) {
      if (objectSummary.getKey().endsWith(".csv.gz")) {
        log.info("Ingesting CSV: {}", objectSummary.getKey());
        try {
          String insertQuery =
              "SET input_format_csv_skip_first_lines=1; SET max_memory_usage=1000000000000; INSERT INTO "
              + awsBillingTableId + " SELECT * FROM s3('https://"
              + configuration.getAwsS3SyncConfig().getAwsS3BucketName() + ".s3.amazonaws.com/" + objectSummary.getKey()
              + "','" + configuration.getAwsS3SyncConfig().getAwsAccessKey() + "','"
              + configuration.getAwsS3SyncConfig().getAwsSecretKey()
              + "', 'CSV') SETTINGS date_time_input_format='best_effort'";
          clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(), insertQuery, Boolean.FALSE);
        } catch (Exception e) {
          log.error(String.format("Exception while ingesting CSV: %s", objectSummary.getKey()), e);
        }
      }
    }
  }

  public String getMappedDataColumn(String dataType) throws Exception {
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
      if (objectSummary.getKey().endsWith("Manifest.json")) {
        AmazonS3Client s3 = awsClient.getAmazonS3Client(credentials);
        S3Object o = s3.getObject(configuration.getAwsS3SyncConfig().getAwsS3BucketName(), objectSummary.getKey());
        S3ObjectInputStream s3is = o.getObjectContent();
        return getAsString(s3is);
      }
    }

    return null;
  }

  private String getAsString(InputStream is) throws IOException {
    if (is == null) {
      return "";
    }
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
      if (startStr.length() != 8 || endStr.length() != 8 || Integer.parseInt(endStr) <= Integer.parseInt(startStr)) {
        return Boolean.FALSE;
      }
      java.util.Date startDate = new SimpleDateFormat("yyyyMMdd").parse(startStr);
      java.util.Date endDate = new SimpleDateFormat("yyyyMMdd").parse(endStr);
    } catch (Exception e) {
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  public void createDBAndTables() throws Exception {
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), ClickHouseConstants.createCCMDBQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), ClickHouseConstants.createAwsCurTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), ClickHouseConstants.createUnifiedTableTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), ClickHouseConstants.createPreAggregatedTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(
        configuration.getClickHouseConfig(), ClickHouseConstants.createCostAggregatedTableQuery, Boolean.FALSE);
    clickHouseService.executeClickHouseQuery(configuration.getClickHouseConfig(),
        ClickHouseConstants.createConnectorDataSyncStatusTableQuery, Boolean.FALSE);
  }
}
