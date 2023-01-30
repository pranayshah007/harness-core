/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.avro.ClusterBillingData;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
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
  private static final String CREATE_CCM_DB_QUERY = "create database if not exists ccm;";
  public static final String COST_AGGREGATED_CREATION_QUERY =
      "CREATE TABLE IF NOT EXISTS ccm.costAggregated (`accountId` String NOT NULL, `cloudProvider` String NOT NULL, `cost` Float NOT NULL, `day` DateTime('UTC') NOT NULL) ENGINE = MergeTree PARTITION BY toYYYYMMDD(day) ORDER BY tuple()";
  public static final String UNIFIED_TABLE_CREATION_QUERY =
      "CREATE TABLE IF NOT EXISTS ccm.unifiedTable(`startTime` DateTime('UTC') NOT NULL, `cost` Float NULL, `gcpProduct` String NULL, `gcpSkuId` String NULL, `gcpSkuDescription` String NULL, `gcpProjectId` String NULL, `region` String NULL, `zone` String NULL, `gcpBillingAccountId` String NULL, `cloudProvider` String NULL, `awsBlendedRate` String NULL, `awsBlendedCost` Float NULL, `awsUnblendedRate` String NULL, `awsUnblendedCost` Float NULL, `awsServicecode` String NULL, `awsAvailabilityzone` String NULL, `awsUsageaccountid` String NULL, `awsInstancetype` String NULL, `awsUsagetype` String NULL, `awsBillingEntity` String NULL, `discount` Float NULL, `endtime` DateTime('UTC') NULL, `accountid` String NULL, `instancetype` String NULL, `clusterid` String NULL, `clustername` String NULL, `appid` String NULL, `serviceid` String NULL, `envid` String NULL, `cloudproviderid` String NULL, `launchtype` String NULL, `clustertype` String NULL, `workloadname` String NULL, `workloadtype` String NULL, `namespace` String NULL, `cloudservicename` String NULL, `taskid` String NULL, `clustercloudprovider` String NULL, `billingamount` Float NULL, `cpubillingamount` Float NULL,     `memorybillingamount` Float NULL,     `idlecost` Float NULL,     `maxcpuutilization` Float NULL,     `avgcpuutilization` Float NULL,     `systemcost` Float NULL,     `actualidlecost` Float NULL,     `unallocatedcost` Float NULL,     `networkcost` Float NULL,     `product` String NULL,     `labels` Map(String, String),     `azureMeterCategory` String NULL,     `azureMeterSubcategory` String NULL,     `azureMeterId` String NULL,     `azureMeterName` String NULL,     `azureResourceType` String NULL,     `azureServiceTier` String NULL,     `azureInstanceId` String NULL,     `azureResourceGroup` String NULL,     `azureSubscriptionGuid` String NULL,     `azureAccountName` String NULL,     `azureFrequency` String NULL,     `azurePublisherType` String NULL,     `azurePublisherName` String NULL,     `azureServiceName` String NULL,     `azureSubscriptionName` String NULL,     `azureReservationId` String NULL,     `azureReservationName` String NULL,     `azureResource` String NULL,     `azureVMProviderId` String NULL,     `azureTenantId` String NULL,     `azureBillingCurrency` String NULL,     `azureCustomerName` String NULL,     `azureResourceRate` Float NULL,     `orgIdentifier` String NULL,     `projectIdentifier` String NULL ) ENGINE = MergeTree PARTITION BY toYYYYMMDD(startTime) ORDER BY tuple() ";
  public static final String CLUSTER_DATA_TABLE_CREATION_QUERY =
      "CREATE TABLE IF NOT EXISTS ccm.%s (`starttime` Int64 NOT NULL, `endtime` Int64 NOT NULL, `accountid` String NOT NULL,     `settingid` String NULL,     `instanceid` String NOT NULL,     `instancetype` String NOT NULL,     `billingaccountid` String NULL,     `clusterid` String NULL,     `clustername` String NULL,     `appid` String NULL,     `serviceid` String NULL,     `envid` String NULL,     `appname` String NULL,     `servicename` String NULL,     `envname` String NULL,     `cloudproviderid` String NULL,     `parentinstanceid` String NULL,     `region` String NULL,     `launchtype` String NULL,     `clustertype` String NULL,     `workloadname` String NULL,     `workloadtype` String NULL,     `namespace` String NULL,     `cloudservicename` String NULL,     `taskid` String NULL,     `cloudprovider` String NULL,     `billingamount` Float NOT NULL,     `cpubillingamount` Float NULL,     `memorybillingamount` Float NULL,     `idlecost` Float NULL,     `cpuidlecost` Float NULL,     `memoryidlecost` Float NULL,     `usagedurationseconds` Float NULL,     `cpuunitseconds` Float NULL,     `memorymbseconds` Float NULL,     `maxcpuutilization` Float NULL,     `maxmemoryutilization` Float NULL,     `avgcpuutilization` Float NULL,     `avgmemoryutilization` Float NULL,     `systemcost` Float NULL,     `cpusystemcost` Float NULL,     `memorysystemcost` Float NULL,     `actualidlecost` Float NULL,     `cpuactualidlecost` Float NULL,     `memoryactualidlecost` Float NULL,     `unallocatedcost` Float NULL,     `cpuunallocatedcost` Float NULL,     `memoryunallocatedcost` Float NULL,     `instancename` String NULL,     `cpurequest` Float NULL,     `memoryrequest` Float NULL,     `cpulimit` Float NULL,     `memorylimit` Float NULL,     `maxcpuutilizationvalue` Float NULL,     `maxmemoryutilizationvalue` Float NULL,     `avgcpuutilizationvalue` Float NULL,     `avgmemoryutilizationvalue` Float NULL,     `networkcost` Float NULL,     `pricingsource` String NULL,     `storageactualidlecost` Float NULL,     `storageunallocatedcost` Float NULL,     `storageutilizationvalue` Float NULL,     `storagerequest` Float NULL,     `storagembseconds` Float NULL,     `storagecost` Float NULL,     `maxstorageutilizationvalue` Float NULL,     `maxstoragerequest` Float NULL,     `orgIdentifier` String NULL,     `projectIdentifier` String NULL,     `labels` Map(String, String) ) ENGINE = MergeTree PARTITION BY toStartOfInterval(toDate(starttime), toIntervalDay(1)) ORDER BY tuple()";
  public static final String CLUSTER_DATA_AGGREGATED_TABLE_CREATION_QUERY =
      "CREATE TABLE IF NOT EXISTS ccm.%s (`starttime` Int64 NOT NULL, `endtime` Int64 NOT NULL, `accountid` String NOT NULL,     `instancetype` String NOT NULL,     `instancename` String NULL,     `clustername` String NULL,     `billingamount` Float NOT NULL,     `actualidlecost` Float NULL,     `unallocatedcost` Float NULL,     `systemcost` Float NULL,     `clusterid` String NULL,     `clustertype` String NULL,     `region` String NULL,     `workloadname` String NULL,     `workloadtype` String NULL,     `namespace` String NULL,     `appid` String NULL,     `serviceid` String NULL,     `envid` String NULL,     `cloudproviderid` String NULL,     `launchtype` String NULL,     `cloudservicename` String NULL,     `storageactualidlecost` Float NULL,     `cpuactualidlecost` Float NULL,     `memoryactualidlecost` Float NULL,     `storageunallocatedcost` Float NULL,     `memoryunallocatedcost` Float NULL,     `cpuunallocatedcost` Float NULL,     `storagecost` Float NULL,     `cpubillingamount` Float NULL,     `memorybillingamount` Float NULL,     `storagerequest` Float NULL,     `storageutilizationvalue` Float NULL,     `instanceid` String NULL,     `networkcost` Float NULL,     `appname` String NULL,     `servicename` String NULL,     `envname` String NULL,     `cloudprovider` String NULL,     `maxstorageutilizationvalue` Float NULL,     `maxstoragerequest` Float NULL,     `orgIdentifier` String NULL,     `projectIdentifier` String NULL,     `labels` Map(String, String) ) ENGINE = MergeTree PARTITION BY toStartOfInterval(toDate(starttime), toIntervalDay(1)) ORDER BY tuple()";

  public static final String COST_AGGREGATED_INGESTION_QUERY =
      "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId) SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_', clustercloudprovider) AS cloudProvider, accountid AS accountId FROM ccm.unifiedTable WHERE (toDate(startTime) = toDate('%s')) AND (clustercloudprovider = 'CLUSTER') AND (clustertype = 'K8S') GROUP BY day, clustertype, accountid, clustercloudprovider";

  private static final String COST_AGGREGATED_DELETION_QUERY =
      "DELETE FROM ccm.costAggregated WHERE toDate(day) = toDate('%s') AND cloudProvider like 'K8S_%%' AND accountId = '%s';";

  private static final String CLUSTER_DATA_INGESTION_QUERY =
      "INSERT INTO ccm.%s ( starttime,  endtime,  accountid,  settingid,  instanceid,  instancetype,  billingaccountid,  clusterid,  clustername,  appid,  serviceid,  envid,  appname,  servicename,  envname,  cloudproviderid,  parentinstanceid,  region,  launchtype,  clustertype,  workloadname,  workloadtype,  namespace,  cloudservicename,  taskid,  cloudprovider,  billingamount,  cpubillingamount,  memorybillingamount,  idlecost,  cpuidlecost,  memoryidlecost,  usagedurationseconds,  cpuunitseconds,  memorymbseconds,  maxcpuutilization,  maxmemoryutilization,  avgcpuutilization,  avgmemoryutilization,  systemcost,  cpusystemcost,  memorysystemcost,  actualidlecost,  cpuactualidlecost,  memoryactualidlecost,  unallocatedcost,  cpuunallocatedcost,  memoryunallocatedcost,  instancename,  cpurequest,  memoryrequest,  cpulimit,  memorylimit,  maxcpuutilizationvalue,  maxmemoryutilizationvalue,  avgcpuutilizationvalue,  avgmemoryutilizationvalue,  networkcost,  pricingsource,  storageactualidlecost,  storageunallocatedcost,  storageutilizationvalue,  storagerequest,  storagembseconds,  storagecost,  maxstorageutilizationvalue,  maxstoragerequest,  orgIdentifier,  projectIdentifier,  labels) VALUES ( ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?)";

  private static final String UNIFIED_TABLE_DELETION_QUERY =
      "DELETE FROM ccm.unifiedTable WHERE toDate(startTime) = toDate('%s') AND cloudProvider = 'CLUSTER'";

  private static final String CLUSTER_DATA_DELETION_QUERY = "DELETE FROM ccm.%s WHERE starttime = %d";
  public static final String UNIFIED_TABLE_INGESTION_QUERY =
      "INSERT INTO ccm.unifiedTable (cloudProvider, product, startTime, endtime, cost, cpubillingamount, memorybillingamount, actualidlecost, systemcost, unallocatedcost, networkcost, clustercloudprovider, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, orgIdentifier, projectIdentifier, labels)  SELECT 'CLUSTER' AS cloudProvider, if(clustertype = 'K8S', 'Kubernetes Cluster', 'ECS Cluster') AS product, date_trunc('day', toDateTime64(starttime / 1000, 3, 'UTC')) AS startTime, date_trunc('day', toDateTime64(endtime / 1000, 3, 'UTC')) AS endtime, SUM(billingamount) AS cost, SUM(cpubillingamount) AS cpubillingamount, SUM(memorybillingamount) AS memorybillingamount, SUM(actualidlecost) AS actualidlecost, SUM(systemcost) AS systemcost, SUM(unallocatedcost) AS unallocatedcost, SUM(networkcost) AS networkcost, cloudprovider AS clustercloudprovider, accountid AS accountid, clusterid AS clusterid, clustername AS clustername, clustertype AS clustertype, region AS region, namespace AS namespace, workloadname AS workloadname,      workloadtype AS workloadtype,      instancetype AS instancetype,      appname AS appid,      servicename AS serviceid,      envname AS envid,      cloudproviderid AS cloudproviderid,      launchtype AS launchtype,      cloudservicename AS cloudservicename,      orgIdentifier,      projectIdentifier,      any(labels) AS labels  FROM ccm.%s  WHERE (toDate(date_trunc('day', toDateTime64(starttime / 1000, 3, 'UTC'))) = toDate('%s')) AND (instancetype != 'CLUSTER_UNALLOCATED')  GROUP BY      accountid,      clusterid,      clustername,      clustertype,      region,      namespace,      workloadname,      workloadtype,      instancetype,      appid,      serviceid,      envid,      cloudproviderid,      launchtype,      cloudservicename,      startTime,      endtime,      clustercloudprovider,      orgIdentifier,      projectIdentifier";
  public static final String COST_AGGREGATION_INGESTION_QUERY =
      "INSERT INTO ccm.%s (memoryactualidlecost, cpuactualidlecost, starttime, endtime, billingamount, actualidlecost, unallocatedcost, systemcost, storageactualidlecost, storageunallocatedcost, storageutilizationvalue, storagerequest, storagecost, memoryunallocatedcost, cpuunallocatedcost, cpubillingamount, memorybillingamount, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, instancename, cloudprovider, networkcost, appname, servicename, envname, orgIdentifier, projectIdentifier, labels) SELECT     SUM(memoryactualidlecost) AS memoryactualidlecost,     SUM(cpuactualidlecost) AS cpuactualidlecost,     starttime,     max(endtime) AS endtime,     sum(billingamount) AS billingamount,     sum(actualidlecost) AS actualidlecost,     sum(unallocatedcost) AS unallocatedcost,     sum(systemcost) AS systemcost,     SUM(storageactualidlecost) AS storageactualidlecost,     SUM(storageunallocatedcost) AS storageunallocatedcost,     MAX(storageutilizationvalue) AS storageutilizationvalue,     MAX(storagerequest) AS storagerequest,     SUM(storagecost) AS storagecost,     SUM(memoryunallocatedcost) AS memoryunallocatedcost,     SUM(cpuunallocatedcost) AS cpuunallocatedcost,     SUM(cpubillingamount) AS cpubillingamount,     SUM(memorybillingamount) AS memorybillingamount,     accountid,     clusterid,     clustername,     clustertype,     region,     namespace,     workloadname,     workloadtype,     instancetype,     appid,     serviceid,     envid,     cloudproviderid,     launchtype,     cloudservicename,     instancename,     cloudprovider,     SUM(networkcost) AS networkcost,     appname,     servicename,     envname,     orgIdentifier,     projectIdentifier,     any(labels) AS labels FROM ccm.%s WHERE starttime = %d AND (instancetype IN ('K8S_POD', 'ECS_CONTAINER_INSTANCE', 'ECS_TASK_EC2', 'ECS_TASK_FARGATE', 'K8S_POD_FARGATE')) GROUP BY     starttime,     accountid,     clusterid,     clustername,     clustertype,     region,     namespace,     workloadname,     workloadtype,     instancetype,     appid,     serviceid,     envid,     cloudproviderid,     launchtype,     cloudservicename,     instancename,     cloudprovider,     appname,     servicename,     envname,     orgIdentifier,     projectIdentifier ";
  public static final String CLUSTER_DATA_AGGREGATED_INGESTION_QUERY =
      "INSERT INTO ccm.%s (memoryactualidlecost, cpuactualidlecost, starttime, endtime, billingamount, actualidlecost, unallocatedcost, systemcost, storageactualidlecost, storageunallocatedcost, storageutilizationvalue, storagerequest, storagecost, memoryunallocatedcost, cpuunallocatedcost, cpubillingamount, memorybillingamount, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, instanceid, instancename, cloudprovider, networkcost, appname, servicename, envname, orgIdentifier, projectIdentifier, labels) SELECT     SUM(memoryactualidlecost) AS memoryactualidlecost,     SUM(cpuactualidlecost) AS cpuactualidlecost,     starttime,     max(endtime) AS endtime,     sum(billingamount) AS billingamount,     sum(actualidlecost) AS actualidlecost,     sum(unallocatedcost) AS unallocatedcost,     sum(systemcost) AS systemcost,     SUM(storageactualidlecost) AS storageactualidlecost,     SUM(storageunallocatedcost) AS storageunallocatedcost,     MAX(storageutilizationvalue) AS storageutilizationvalue,     MAX(storagerequest) AS storagerequest,     SUM(storagecost) AS storagecost,     SUM(memoryunallocatedcost) AS memoryunallocatedcost,     SUM(cpuunallocatedcost) AS cpuunallocatedcost,     SUM(cpubillingamount) AS cpubillingamount,     SUM(memorybillingamount) AS memorybillingamount,     accountid,     clusterid,     clustername,     clustertype,     region,     namespace,     workloadname,     workloadtype,     instancetype,     appid,     serviceid,     envid,     cloudproviderid,     launchtype,     cloudservicename,     instanceid,     instancename,     cloudprovider,     SUM(networkcost) AS networkcost,     appname,     servicename,     envname,     orgIdentifier,     projectIdentifier,     any(labels) AS labels FROM ccm.%s WHERE starttime = %d AND (instancetype IN ('K8S_NODE', 'K8S_PV')) GROUP BY     starttime,     accountid,     clusterid,     clustername,     clustertype,     region,     namespace,     workloadname,     workloadtype,     instancetype,     appid,     serviceid,     envid,     cloudproviderid,     launchtype,     cloudservicename,     instanceid,     instancename,     cloudprovider,     appname,     servicename,     envname,     orgIdentifier,     projectIdentifier ";

  @Autowired private ClickHouseService clickHouseService;
  @Autowired ClickHouseConfig clickHouseConfig;
  @Autowired BatchMainConfig batchMainConfig;

  public void createClickHouseDataBaseIfNotExist() throws Exception {
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), CREATE_CCM_DB_QUERY);
  }

  public void createTableAndDeleteExistingDataFromClickHouse(JobConstants jobConstants, String tableName)
      throws Exception {
    if (!tableName.contains("Aggregated")) {
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
      String query = String.format(CLUSTER_DATA_INGESTION_QUERY, clusterDataTableName);
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
    String costAggregatedIngestionQuery = String.format(COST_AGGREGATED_INGESTION_QUERY, startTime);
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), costAggregatedIngestionQuery);
  }

  private static String getCreateCostAggregatedQuery() {
    return COST_AGGREGATED_CREATION_QUERY;
  }

  private static String deleteCostAggregatedDataFromClickHouse(final String startTime, final String accountId) {
    return String.format(COST_AGGREGATED_DELETION_QUERY, startTime, accountId);
  }

  public void ingestAggregatedData(
      JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    String insertQueryForPods = String.format(COST_AGGREGATION_INGESTION_QUERY, clusterDataAggregatedTableName,
        clusterDataTableName, jobConstants.getJobStartTime());

    String insertQueryForPodAndPv = String.format(CLUSTER_DATA_AGGREGATED_INGESTION_QUERY,
        clusterDataAggregatedTableName, clusterDataTableName, jobConstants.getJobStartTime());

    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), insertQueryForPods);
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), insertQueryForPodAndPv);
  }

  private String deleteDataFromClickHouseForUnifiedTable(String jobStartTime) {
    return String.format(UNIFIED_TABLE_DELETION_QUERY, jobStartTime);
  }

  public void ingestIntoUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    String unifiedTableIngestQuery =
        String.format(UNIFIED_TABLE_INGESTION_QUERY, clusterDataTableName, zdt.toLocalDate());
    clickHouseService.getQueryResult(batchMainConfig.getClickHouseConfig(), unifiedTableIngestQuery);
  }

  public static String getUnifiedTableCreateQuery() {
    return String.format(UNIFIED_TABLE_CREATION_QUERY);
  }

  private static String deleteDataFromClickHouse(String clusterDataTableName, long startTime) {
    return String.format(CLUSTER_DATA_DELETION_QUERY, clusterDataTableName, startTime);
  }

  private static String getClusterDataCreationQuery(String clusterDataTableName) {
    String clusterDataAggregatedCreateQuery = String.format(CLUSTER_DATA_TABLE_CREATION_QUERY, clusterDataTableName);
    return clusterDataAggregatedCreateQuery;
  }

  private static String getClusterDataAggregatedCreationQuery(String clusterDataTableName) {
    String clusterDataCreateQuery = String.format(CLUSTER_DATA_AGGREGATED_TABLE_CREATION_QUERY, clusterDataTableName);
    return clusterDataCreateQuery;
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
    prepareStatement.setString(26, "CLUSTER");
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

    Map<String, String> labelMap = jsonLabels.stream().collect(Collectors.toMap(
        label -> label.get("key").getAsString(), label -> label.get("value").getAsString(), (a, b) -> b));
    log.info("labelMap size :: {} \n labelMap: {}", labelMap.size(), labelMap);
    return labelMap;
  }
}
