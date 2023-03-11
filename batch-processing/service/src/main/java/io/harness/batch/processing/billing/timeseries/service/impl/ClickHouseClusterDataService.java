/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.batch.processing.tasklet.util.ClickHouseConstants.GET_CLUSTER_DATA_ENTRIES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.avro.ClusterBillingData;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.entities.ClusterDataDetails;
import io.harness.batch.processing.tasklet.util.ClickHouseConstants;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@OwnedBy(HarnessTeam.CE)
@Slf4j
public class ClickHouseClusterDataService {
  private static final String AGGREGATED_KEYWORD = "Aggregated";
  private static final String CLUSTER = "CLUSTER";
  private static final String KEY = "key";
  private static final String VALUE = "value";
  @Autowired private ClickHouseService clickHouseService;
  @Autowired private ClickHouseConfig clickHouseConfig;
  @Autowired private BatchMainConfig batchMainConfig;

  public void createClickHouseDataBaseIfNotExist() throws Exception {
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), ClickHouseConstants.createCCMDBQuery);
  }

  public void createTableAndDeleteExistingDataFromClickHouse(JobConstants jobConstants, String tableName)
      throws Exception {
    if (!tableName.contains(AGGREGATED_KEYWORD)) {
      clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), getClusterDataCreationQuery(tableName));
    } else {
      clickHouseService.getQueryResult(
          batchMainConfig.getClickHouseConfig(), getClusterDataAggregatedCreationQuery(tableName));
    }
    clickHouseService.getQueryResult(
        batchMainConfig.getClickHouseConfig(), deleteDataFromClickHouse(tableName, jobConstants.getJobStartTime()));
  }

  public void ingestClusterData(String clusterDataTableName, List<ClusterBillingData> allClusterBillingData)
      throws SQLException {
    try (Connection connection = clickHouseService.getConnection(batchMainConfig.getClickHouseConfig())) {
      String query = String.format(ClickHouseConstants.CLUSTER_DATA_INGESTION_QUERY, clusterDataTableName);
      PreparedStatement prepareStatement = connection.prepareStatement(query);
      connection.setAutoCommit(false);

      for (ClusterBillingData billingData : allClusterBillingData) {
        getBatchedPreparedStatement(prepareStatement, billingData);
      }
      int[] ints = prepareStatement.executeBatch();
      log.info("Ingested in " + clusterDataTableName + ",  results length: {}", ints.length);
    }
  }

  public void processUnifiedTableToCLickHouse(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), getUnifiedTableCreateQuery());
    clickHouseService.getQueryResult(
        batchMainConfig.getClickHouseConfig(), deleteDataFromClickHouseForUnifiedTable(zdt.toLocalDate().toString()));
  }

  public void processAggregatedTable(JobConstants jobConstants, String clusterDataAggregatedTableName)
      throws Exception {
    createTableAndDeleteExistingDataFromClickHouse(jobConstants, clusterDataAggregatedTableName);
  }

  public void processCostAggregatedData(JobConstants jobConstants, ZonedDateTime zdt) throws SQLException {
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), getCreateCostAggregatedQuery());
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(),
        deleteCostAggregatedDataFromClickHouse(zdt.toLocalDate().toString(), jobConstants.getAccountId()));
  }

  public void ingestToCostAggregatedTable(String startTime) throws Exception {
    String costAggregatedIngestionQuery = String.format(ClickHouseConstants.COST_AGGREGATED_INGESTION_QUERY, startTime);
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), costAggregatedIngestionQuery);
  }

  private static String getCreateCostAggregatedQuery() {
    return ClickHouseConstants.createCostAggregatedTableQuery;
  }

  private static String deleteCostAggregatedDataFromClickHouse(final String startTime, final String accountId) {
    return String.format(ClickHouseConstants.COST_AGGREGATED_DELETION_QUERY, startTime, accountId);
  }

  public void ingestAggregatedData(
      JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    String insertQueryForPods = String.format(ClickHouseConstants.COST_AGGREGATION_INGESTION_QUERY,
        clusterDataAggregatedTableName, clusterDataTableName, jobConstants.getJobStartTime());

    String insertQueryForPodAndPv = String.format(ClickHouseConstants.CLUSTER_DATA_AGGREGATED_INGESTION_QUERY,
        clusterDataAggregatedTableName, clusterDataTableName, jobConstants.getJobStartTime());

    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), insertQueryForPods);
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), insertQueryForPodAndPv);
  }

  private String deleteDataFromClickHouseForUnifiedTable(String jobStartTime) {
    return String.format(ClickHouseConstants.UNIFIED_TABLE_DELETION_QUERY, jobStartTime);
  }

  public void ingestIntoUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    String unifiedTableIngestQuery =
        String.format(ClickHouseConstants.UNIFIED_TABLE_INGESTION_QUERY, clusterDataTableName, zdt.toLocalDate());
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), unifiedTableIngestQuery);
  }

  public static String getUnifiedTableCreateQuery() {
    return ClickHouseConstants.createUnifiedTableTableQuery;
  }

  private static String deleteDataFromClickHouse(String clusterDataTableName, long startTime) {
    return String.format(ClickHouseConstants.CLUSTER_DATA_DELETION_QUERY, clusterDataTableName, startTime);
  }

  private static String getClusterDataCreationQuery(String clusterDataTableName) {
    return String.format(ClickHouseConstants.CLUSTER_DATA_TABLE_CREATION_QUERY, clusterDataTableName);
  }

  private static String getClusterDataAggregatedCreationQuery(String clusterDataTableName) {
    return String.format(ClickHouseConstants.CLUSTER_DATA_AGGREGATED_TABLE_CREATION_QUERY, clusterDataTableName);
  }

  private static void getBatchedPreparedStatement(PreparedStatement prepareStatement, ClusterBillingData billingData)
      throws SQLException {
    prepareStatement.setLong(1, billingData.getStarttime());
    prepareStatement.setLong(2, billingData.getEndtime());
    prepareStatement.setString(3, (String) billingData.getAccountid());
    prepareStatement.setString(4, (String) billingData.getSettingid());
    prepareStatement.setString(5, (String) billingData.getInstanceid());
    prepareStatement.setString(6, (String) billingData.getInstancetype());
    prepareStatement.setString(7, (String) billingData.getBillingaccountid());
    prepareStatement.setString(8, (String) billingData.getClusterid());
    prepareStatement.setString(9, (String) billingData.getClustername());
    prepareStatement.setString(10, (String) billingData.getAppid());
    prepareStatement.setString(11, (String) billingData.getServiceid());
    prepareStatement.setString(12, (String) billingData.getEnvid());
    prepareStatement.setString(13, (String) billingData.getAppname());
    prepareStatement.setString(14, (String) billingData.getServicename());
    prepareStatement.setString(15, (String) billingData.getEnvname());
    prepareStatement.setString(16, (String) billingData.getCloudproviderid());
    prepareStatement.setString(17, (String) billingData.getParentinstanceid());
    prepareStatement.setString(18, (String) billingData.getRegion());
    prepareStatement.setString(19, (String) billingData.getLaunchtype());
    prepareStatement.setString(20, (String) billingData.getClustertype());
    prepareStatement.setString(21, (String) billingData.getWorkloadname());
    prepareStatement.setString(22, (String) billingData.getWorkloadtype());
    prepareStatement.setString(23, (String) billingData.getNamespace());
    prepareStatement.setString(24, (String) billingData.getCloudservicename());
    prepareStatement.setString(25, (String) billingData.getTaskid());
    prepareStatement.setString(26, CLUSTER);
    prepareStatement.setBigDecimal(27, BigDecimal.valueOf(billingData.getBillingamount()));
    prepareStatement.setBigDecimal(28, BigDecimal.valueOf(billingData.getCpubillingamount()));
    prepareStatement.setBigDecimal(29, BigDecimal.valueOf(billingData.getMemorybillingamount()));
    prepareStatement.setBigDecimal(30, BigDecimal.valueOf(billingData.getIdlecost()));
    prepareStatement.setBigDecimal(31, BigDecimal.valueOf(billingData.getCpuidlecost()));
    prepareStatement.setBigDecimal(32, BigDecimal.valueOf(billingData.getMemoryidlecost()));
    prepareStatement.setBigDecimal(33, BigDecimal.valueOf(billingData.getUsagedurationseconds()));
    prepareStatement.setBigDecimal(34, BigDecimal.valueOf(billingData.getCpuunitseconds()));
    prepareStatement.setBigDecimal(35, BigDecimal.valueOf(billingData.getMemorymbseconds()));
    prepareStatement.setBigDecimal(36, BigDecimal.valueOf(billingData.getMaxcpuutilization()));
    prepareStatement.setBigDecimal(37, BigDecimal.valueOf(billingData.getMaxmemoryutilization()));
    prepareStatement.setBigDecimal(38, BigDecimal.valueOf(billingData.getAvgcpuutilization()));
    prepareStatement.setBigDecimal(39, BigDecimal.valueOf(billingData.getAvgmemoryutilization()));
    prepareStatement.setBigDecimal(40, BigDecimal.valueOf(billingData.getSystemcost()));
    prepareStatement.setBigDecimal(41, BigDecimal.valueOf(billingData.getCpusystemcost()));
    prepareStatement.setBigDecimal(42, BigDecimal.valueOf(billingData.getMemorysystemcost()));
    prepareStatement.setBigDecimal(43, BigDecimal.valueOf(billingData.getActualidlecost()));
    prepareStatement.setBigDecimal(44, BigDecimal.valueOf(billingData.getCpuactualidlecost()));
    prepareStatement.setBigDecimal(45, BigDecimal.valueOf(billingData.getMemoryactualidlecost()));
    prepareStatement.setBigDecimal(46, BigDecimal.valueOf(billingData.getUnallocatedcost()));
    prepareStatement.setBigDecimal(47, BigDecimal.valueOf(billingData.getCpuunallocatedcost()));
    prepareStatement.setBigDecimal(48, BigDecimal.valueOf(billingData.getMemoryunallocatedcost()));
    prepareStatement.setString(49, (String) billingData.getInstancename());
    prepareStatement.setBigDecimal(50, BigDecimal.valueOf(billingData.getCpurequest()));
    prepareStatement.setBigDecimal(51, BigDecimal.valueOf(billingData.getMemoryrequest()));
    prepareStatement.setBigDecimal(52, BigDecimal.valueOf(billingData.getCpulimit()));
    prepareStatement.setBigDecimal(53, BigDecimal.valueOf(billingData.getMemorylimit()));
    prepareStatement.setBigDecimal(54, BigDecimal.valueOf(billingData.getMaxcpuutilizationvalue()));
    prepareStatement.setBigDecimal(55, BigDecimal.valueOf(billingData.getMaxmemoryutilizationvalue()));
    prepareStatement.setBigDecimal(56, BigDecimal.valueOf(billingData.getAvgcpuutilizationvalue()));
    prepareStatement.setBigDecimal(57, BigDecimal.valueOf(billingData.getAvgmemoryutilizationvalue()));
    prepareStatement.setBigDecimal(58, BigDecimal.valueOf(billingData.getNetworkcost()));
    prepareStatement.setString(59, (String) billingData.getPricingsource());
    prepareStatement.setBigDecimal(60, BigDecimal.valueOf(billingData.getStorageactualidlecost()));
    prepareStatement.setBigDecimal(61, BigDecimal.valueOf(billingData.getStorageunallocatedcost()));
    prepareStatement.setBigDecimal(62, BigDecimal.valueOf(billingData.getStorageutilizationvalue()));
    prepareStatement.setBigDecimal(63, BigDecimal.valueOf(billingData.getStoragerequest()));
    prepareStatement.setBigDecimal(64, BigDecimal.valueOf(billingData.getMemorymbseconds())); // storagembseconds
    prepareStatement.setBigDecimal(65, BigDecimal.valueOf(billingData.getStoragecost()));
    prepareStatement.setBigDecimal(66, BigDecimal.valueOf(billingData.getMaxstorageutilizationvalue()));
    prepareStatement.setBigDecimal(67, BigDecimal.valueOf(billingData.getMaxstoragerequest()));
    prepareStatement.setString(68, (String) billingData.getOrgIdentifier());
    prepareStatement.setString(69, (String) billingData.getProjectIdentifier());
    prepareStatement.setObject(70, getLabelMapFromLabelsArray(billingData));

    prepareStatement.addBatch();
  }

  @NotNull
  private static Map<String, String> getLabelMapFromLabelsArray(ClusterBillingData billingData) {
    List<Object> labels = billingData.getLabels();
    List<JsonObject> jsonLabels = labels.stream()
                                      .map(label -> new JsonParser().parse(label.toString()).getAsJsonObject())
                                      .collect(Collectors.toList());

    Map<String, String> labelMap = jsonLabels.stream().collect(
        Collectors.toMap(label -> label.get(KEY).getAsString(), label -> label.get(VALUE).getAsString(), (a, b) -> b));
    return labelMap;
  }

  public ClusterDataDetails getClusterDataEntriesDetails(String accountId, long startTime) throws SQLException {
    String query = String.format(GET_CLUSTER_DATA_ENTRIES, accountId, startTime);

    Connection connection = clickHouseService.getConnection(batchMainConfig.getClickHouseConfig(), new Properties());
    try (Statement statement = connection.createStatement()) {
      try (ResultSet resultSet = statement.executeQuery(query)) {
        while (resultSet.next()) {
          return ClusterDataDetails.builder()
              .entriesCount(resultSet.getInt("ENTRIESCOUNT"))
              .billingAmountSum(resultSet.getDouble("BILLINGAMOUNTSUM"))
              .build();
        }
      }
    }
    return null;
  }
}
