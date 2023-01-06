package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.avro.ClusterBillingData;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;

import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@OwnedBy(HarnessTeam.CE)
@Slf4j
public class ClickHouseClusterDataService {
  @Autowired private ClickHouseService clickHouseService;
  @Autowired ClickHouseConfig clickHouseConfig;

  public void deleteExistingDataFromClusterDataTable(JobConstants jobConstants, String tableName) throws Exception {
    if (!tableName.contains("Aggregated")) {
      clickHouseService.getQueryResult(clickHouseConfig, getClusterDataCreationQuery(tableName));
    } else {
      clickHouseService.getQueryResult(clickHouseConfig, getClusterDataAggregatedCreationQuery(tableName));
    }
    clickHouseService.getQueryResult(
        clickHouseConfig, deleteDataFromClickHouse(tableName, jobConstants.getJobStartTime()));
  }

  public void ingestClusterData(String clusterDataTableName, List<ClusterBillingData> allClusterBillingData)
      throws SQLException {
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig)) {
      String query = "INSERT INTO ccm." + clusterDataTableName
          + " ( starttime,  endtime,  accountid,  settingid,  instanceid,  instancetype,  billingaccountid,  clusterid,  clustername,  appid,  serviceid,  envid,  appname,  servicename,  envname,  cloudproviderid,  parentinstanceid,  region,  launchtype,  clustertype,  workloadname,  workloadtype,  namespace,  cloudservicename,  taskid,  cloudprovider,  billingamount,  cpubillingamount,  memorybillingamount,  idlecost,  cpuidlecost,  memoryidlecost,  usagedurationseconds,  cpuunitseconds,  memorymbseconds,  maxcpuutilization,  maxmemoryutilization,  avgcpuutilization,  avgmemoryutilization,  systemcost,  cpusystemcost,  memorysystemcost,  actualidlecost,  cpuactualidlecost,  memoryactualidlecost,  unallocatedcost,  cpuunallocatedcost,  memoryunallocatedcost,  instancename,  cpurequest,  memoryrequest,  cpulimit,  memorylimit,  maxcpuutilizationvalue,  maxmemoryutilizationvalue,  avgcpuutilizationvalue,  avgmemoryutilizationvalue,  networkcost,  pricingsource,  storageactualidlecost,  storageunallocatedcost,  storageutilizationvalue,  storagerequest,  storagembseconds,  storagecost,  maxstorageutilizationvalue,  maxstoragerequest,  orgIdentifier,  projectIdentifier,  labels) VALUES ( ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?)";
      PreparedStatement prepareStatement = connection.prepareStatement(query);
      connection.setAutoCommit(false);

      for (ClusterBillingData billingData : allClusterBillingData) {
        getBatchedPreparedStatement(prepareStatement, billingData);
      }
      int[] ints = prepareStatement.executeBatch();
      log.info("Ingested in " + clusterDataTableName + ",  results length: {}", ints.length);
    }
  }

  public void procesUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    clickHouseService.getQueryResult(clickHouseConfig, getUnifiedTableCreateQuery());
    clickHouseService.getQueryResult(
        clickHouseConfig, deleteDataFromClickHouseForUnifiedTable(zdt.toLocalDate().toString()));
  }

  public void processAggregatedTable(JobConstants jobConstants, String clusterDataAggregatedTableName)
      throws Exception {
    deleteExistingDataFromClusterDataTable(jobConstants, clusterDataAggregatedTableName);
  }

  public void processCostAggregaredData(JobConstants jobConstants, ZonedDateTime zdt) throws SQLException {
    clickHouseService.getQueryResult(clickHouseConfig, getCreateCostAggregatedQuery());
    clickHouseService.getQueryResult(clickHouseConfig,
        deleteCostAggregatedDataFromClickHouse(zdt.toLocalDate().toString(), jobConstants.getAccountId()));
  }

  public void ingestToCostAggregatedTable(String startTime) throws Exception {
    String costAggregatedIngestionQuery =
        "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId) SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_', clustercloudprovider) AS cloudProvider, accountid AS accountId FROM ccm.unifiedTable WHERE (toDate(startTime) = toDate('"
        + startTime
        + "')) AND (clustercloudprovider = 'CLUSTER') AND (clustertype = 'K8S') GROUP BY day, clustertype, accountid, clustercloudprovider";
    clickHouseService.getQueryResult(clickHouseConfig, costAggregatedIngestionQuery);
  }

  private static String getCreateCostAggregatedQuery() {
    return "CREATE TABLE IF NOT EXISTS ccm.costAggregated"
        + "              (\n"
        + "                  `accountId` String NOT NULL, \n"
        + "                  `cloudProvider` String NOT NULL, \n"
        + "                  `cost` Float NOT NULL, \n"
        + "                  `day` DateTime('UTC') NOT NULL \n"
        + "              )\n"
        + "              ENGINE = MergeTree \n"
        + "              PARTITION BY toYYYYMMDD(day) \n"
        + "              ORDER BY tuple()";
  }

  private static String deleteCostAggregatedDataFromClickHouse(final String startTime, final String accountId) {
    return "DELETE FROM ccm.costAggregated WHERE toDate(day) = toDate('" + startTime
        + "') AND cloudProvider like 'K8S_%' AND accountId = '" + accountId + "';";
  }

  public void ingestAggregatedData(
      JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    String insertQueryForPods = "INSERT INTO ccm." + clusterDataAggregatedTableName
        + " (memoryactualidlecost, cpuactualidlecost, starttime, endtime, billingamount, actualidlecost, unallocatedcost, systemcost, storageactualidlecost, storageunallocatedcost, storageutilizationvalue, storagerequest, storagecost, memoryunallocatedcost, cpuunallocatedcost, cpubillingamount, memorybillingamount, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, instancename, cloudprovider, networkcost, appname, servicename, envname, orgIdentifier, projectIdentifier, labels) SELECT\n"
        + "    SUM(memoryactualidlecost) AS memoryactualidlecost,\n"
        + "    SUM(cpuactualidlecost) AS cpuactualidlecost,\n"
        + "    starttime,\n"
        + "    max(endtime) AS endtime,\n"
        + "    sum(billingamount) AS billingamount,\n"
        + "    sum(actualidlecost) AS actualidlecost,\n"
        + "    sum(unallocatedcost) AS unallocatedcost,\n"
        + "    sum(systemcost) AS systemcost,\n"
        + "    SUM(storageactualidlecost) AS storageactualidlecost,\n"
        + "    SUM(storageunallocatedcost) AS storageunallocatedcost,\n"
        + "    MAX(storageutilizationvalue) AS storageutilizationvalue,\n"
        + "    MAX(storagerequest) AS storagerequest,\n"
        + "    SUM(storagecost) AS storagecost,\n"
        + "    SUM(memoryunallocatedcost) AS memoryunallocatedcost,\n"
        + "    SUM(cpuunallocatedcost) AS cpuunallocatedcost,\n"
        + "    SUM(cpubillingamount) AS cpubillingamount,\n"
        + "    SUM(memorybillingamount) AS memorybillingamount,\n"
        + "    accountid,\n"
        + "    clusterid,\n"
        + "    clustername,\n"
        + "    clustertype,\n"
        + "    region,\n"
        + "    namespace,\n"
        + "    workloadname,\n"
        + "    workloadtype,\n"
        + "    instancetype,\n"
        + "    appid,\n"
        + "    serviceid,\n"
        + "    envid,\n"
        + "    cloudproviderid,\n"
        + "    launchtype,\n"
        + "    cloudservicename,\n"
        + "    instancename,\n"
        + "    cloudprovider,\n"
        + "    SUM(networkcost) AS networkcost,\n"
        + "    appname,\n"
        + "    servicename,\n"
        + "    envname,\n"
        + "    orgIdentifier,\n"
        + "    projectIdentifier,\n"
        + "    any(labels) AS labels\n"
        + "FROM ccm." + clusterDataTableName + "\n"
        + "WHERE starttime = " + jobConstants.getJobStartTime()
        + " AND (instancetype IN ('K8S_POD', 'ECS_CONTAINER_INSTANCE', 'ECS_TASK_EC2', 'ECS_TASK_FARGATE', 'K8S_POD_FARGATE'))\n"
        + "GROUP BY\n"
        + "    starttime,\n"
        + "    accountid,\n"
        + "    clusterid,\n"
        + "    clustername,\n"
        + "    clustertype,\n"
        + "    region,\n"
        + "    namespace,\n"
        + "    workloadname,\n"
        + "    workloadtype,\n"
        + "    instancetype,\n"
        + "    appid,\n"
        + "    serviceid,\n"
        + "    envid,\n"
        + "    cloudproviderid,\n"
        + "    launchtype,\n"
        + "    cloudservicename,\n"
        + "    instancename,\n"
        + "    cloudprovider,\n"
        + "    appname,\n"
        + "    servicename,\n"
        + "    envname,\n"
        + "    orgIdentifier,\n"
        + "    projectIdentifier\n"
        + "\n";

    String insertQueryForPodAndPv = "INSERT INTO ccm." + clusterDataAggregatedTableName
        + " (memoryactualidlecost, cpuactualidlecost, starttime, endtime, billingamount, actualidlecost, unallocatedcost, systemcost, storageactualidlecost, storageunallocatedcost, storageutilizationvalue, storagerequest, storagecost, memoryunallocatedcost, cpuunallocatedcost, cpubillingamount, memorybillingamount, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, instanceid, instancename, cloudprovider, networkcost, appname, servicename, envname, orgIdentifier, projectIdentifier, labels) SELECT\n"
        + "    SUM(memoryactualidlecost) AS memoryactualidlecost,\n"
        + "    SUM(cpuactualidlecost) AS cpuactualidlecost,\n"
        + "    starttime,\n"
        + "    max(endtime) AS endtime,\n"
        + "    sum(billingamount) AS billingamount,\n"
        + "    sum(actualidlecost) AS actualidlecost,\n"
        + "    sum(unallocatedcost) AS unallocatedcost,\n"
        + "    sum(systemcost) AS systemcost,\n"
        + "    SUM(storageactualidlecost) AS storageactualidlecost,\n"
        + "    SUM(storageunallocatedcost) AS storageunallocatedcost,\n"
        + "    MAX(storageutilizationvalue) AS storageutilizationvalue,\n"
        + "    MAX(storagerequest) AS storagerequest,\n"
        + "    SUM(storagecost) AS storagecost,\n"
        + "    SUM(memoryunallocatedcost) AS memoryunallocatedcost,\n"
        + "    SUM(cpuunallocatedcost) AS cpuunallocatedcost,\n"
        + "    SUM(cpubillingamount) AS cpubillingamount,\n"
        + "    SUM(memorybillingamount) AS memorybillingamount,\n"
        + "    accountid,\n"
        + "    clusterid,\n"
        + "    clustername,\n"
        + "    clustertype,\n"
        + "    region,\n"
        + "    namespace,\n"
        + "    workloadname,\n"
        + "    workloadtype,\n"
        + "    instancetype,\n"
        + "    appid,\n"
        + "    serviceid,\n"
        + "    envid,\n"
        + "    cloudproviderid,\n"
        + "    launchtype,\n"
        + "    cloudservicename,\n"
        + "    instanceid,\n"
        + "    instancename,\n"
        + "    cloudprovider,\n"
        + "    SUM(networkcost) AS networkcost,\n"
        + "    appname,\n"
        + "    servicename,\n"
        + "    envname,\n"
        + "    orgIdentifier,\n"
        + "    projectIdentifier,\n"
        + "    any(labels) AS labels\n"
        + "FROM ccm." + clusterDataTableName + "\n"
        + "WHERE starttime = " + jobConstants.getJobStartTime() + " AND (instancetype IN ('K8S_NODE', 'K8S_PV'))\n"
        + "GROUP BY\n"
        + "    starttime,\n"
        + "    accountid,\n"
        + "    clusterid,\n"
        + "    clustername,\n"
        + "    clustertype,\n"
        + "    region,\n"
        + "    namespace,\n"
        + "    workloadname,\n"
        + "    workloadtype,\n"
        + "    instancetype,\n"
        + "    appid,\n"
        + "    serviceid,\n"
        + "    envid,\n"
        + "    cloudproviderid,\n"
        + "    launchtype,\n"
        + "    cloudservicename,\n"
        + "    instanceid,\n"
        + "    instancename,\n"
        + "    cloudprovider,\n"
        + "    appname,\n"
        + "    servicename,\n"
        + "    envname,\n"
        + "    orgIdentifier,\n"
        + "    projectIdentifier\n";

    clickHouseService.getQueryResult(clickHouseConfig, insertQueryForPods);
    clickHouseService.getQueryResult(clickHouseConfig, insertQueryForPodAndPv);
  }

  private String deleteDataFromClickHouseForUnifiedTable(String jobStartTime) {
    return "DELETE FROM ccm.unifiedTable WHERE toDate(startTime) = toDate('" + jobStartTime
        + "') AND cloudProvider = 'CLUSTER'";
  }

  public void ingestIntoUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    String unifiedtableIngestQuery =
        "INSERT INTO ccm.unifiedTable (cloudProvider, product, startTime, endtime, cost, cpubillingamount, memorybillingamount, actualidlecost, systemcost, unallocatedcost, networkcost, clustercloudprovider, accountid, clusterid, clustername, clustertype, region, namespace, workloadname, workloadtype, instancetype, appid, serviceid, envid, cloudproviderid, launchtype, cloudservicename, orgIdentifier, projectIdentifier, labels)\n"
        + "  SELECT\n"
        + "      'CLUSTER' AS cloudProvider,\n"
        + "      if(clustertype = 'K8S', 'Kubernetes Cluster', 'ECS Cluster') AS product,\n"
        + "      date_trunc('day', toDateTime64(starttime / 1000, 3, 'UTC')) AS startTime,\n"
        + "      date_trunc('day', toDateTime64(endtime / 1000, 3, 'UTC')) AS endtime,\n"
        + "      SUM(billingamount) AS cost,\n"
        + "      SUM(cpubillingamount) AS cpubillingamount,\n"
        + "      SUM(memorybillingamount) AS memorybillingamount,\n"
        + "      SUM(actualidlecost) AS actualidlecost,\n"
        + "      SUM(systemcost) AS systemcost,\n"
        + "      SUM(unallocatedcost) AS unallocatedcost,\n"
        + "      SUM(networkcost) AS networkcost,\n"
        + "      cloudprovider AS clustercloudprovider,\n"
        + "      accountid AS accountid,\n"
        + "      clusterid AS clusterid,\n"
        + "      clustername AS clustername,\n"
        + "      clustertype AS clustertype,\n"
        + "      region AS region,\n"
        + "      namespace AS namespace,\n"
        + "      workloadname AS workloadname,\n"
        + "      workloadtype AS workloadtype,\n"
        + "      instancetype AS instancetype,\n"
        + "      appname AS appid,\n"
        + "      servicename AS serviceid,\n"
        + "      envname AS envid,\n"
        + "      cloudproviderid AS cloudproviderid,\n"
        + "      launchtype AS launchtype,\n"
        + "      cloudservicename AS cloudservicename,\n"
        + "      orgIdentifier,\n"
        + "      projectIdentifier,\n"
        + "      any(labels) AS labels\n"
        + "  FROM ccm." + clusterDataTableName + "\n"
        + "  WHERE (toDate(date_trunc('day', toDateTime64(starttime / 1000, 3, 'UTC'))) = toDate('" + zdt.toLocalDate()
        + "')) AND (instancetype != 'CLUSTER_UNALLOCATED')\n"
        + "  GROUP BY\n"
        + "      accountid,\n"
        + "      clusterid,\n"
        + "      clustername,\n"
        + "      clustertype,\n"
        + "      region,\n"
        + "      namespace,\n"
        + "      workloadname,\n"
        + "      workloadtype,\n"
        + "      instancetype,\n"
        + "      appid,\n"
        + "      serviceid,\n"
        + "      envid,\n"
        + "      cloudproviderid,\n"
        + "      launchtype,\n"
        + "      cloudservicename,\n"
        + "      startTime,\n"
        + "      endtime,\n"
        + "      clustercloudprovider,\n"
        + "      orgIdentifier,\n"
        + "      projectIdentifier\n";

    clickHouseService.getQueryResult(clickHouseConfig, unifiedtableIngestQuery);
  }

  public static String getUnifiedTableCreateQuery() {
    return "CREATE TABLE IF NOT EXISTS ccm.unifiedTable\n"
        + "(\n"
        + "    `startTime` DateTime('UTC') NOT NULL,\n"
        + "    `cost` Float NULL,\n"
        + "    `gcpProduct` String NULL,\n"
        + "    `gcpSkuId` String NULL,\n"
        + "    `gcpSkuDescription` String NULL,\n"
        + "    `gcpProjectId` String NULL,\n"
        + "    `region` String NULL,\n"
        + "    `zone` String NULL,\n"
        + "    `gcpBillingAccountId` String NULL,\n"
        + "    `cloudProvider` String NULL,\n"
        + "    `awsBlendedRate` String NULL,\n"
        + "    `awsBlendedCost` Float NULL,\n"
        + "    `awsUnblendedRate` String NULL,\n"
        + "    `awsUnblendedCost` Float NULL,\n"
        + "    `awsServicecode` String NULL,\n"
        + "    `awsAvailabilityzone` String NULL,\n"
        + "    `awsUsageaccountid` String NULL,\n"
        + "    `awsInstancetype` String NULL,\n"
        + "    `awsUsagetype` String NULL,\n"
        + "    `awsBillingEntity` String NULL,\n"
        + "    `discount` Float NULL,\n"
        + "    `endtime` DateTime('UTC') NULL,\n"
        + "    `accountid` String NULL,\n"
        + "    `instancetype` String NULL,\n"
        + "    `clusterid` String NULL,\n"
        + "    `clustername` String NULL,\n"
        + "    `appid` String NULL,\n"
        + "    `serviceid` String NULL,\n"
        + "    `envid` String NULL,\n"
        + "    `cloudproviderid` String NULL,\n"
        + "    `launchtype` String NULL,\n"
        + "    `clustertype` String NULL,\n"
        + "    `workloadname` String NULL,\n"
        + "    `workloadtype` String NULL,\n"
        + "    `namespace` String NULL,\n"
        + "    `cloudservicename` String NULL,\n"
        + "    `taskid` String NULL,\n"
        + "    `clustercloudprovider` String NULL,\n"
        + "    `billingamount` Float NULL,\n"
        + "    `cpubillingamount` Float NULL,\n"
        + "    `memorybillingamount` Float NULL,\n"
        + "    `idlecost` Float NULL,\n"
        + "    `maxcpuutilization` Float NULL,\n"
        + "    `avgcpuutilization` Float NULL,\n"
        + "    `systemcost` Float NULL,\n"
        + "    `actualidlecost` Float NULL,\n"
        + "    `unallocatedcost` Float NULL,\n"
        + "    `networkcost` Float NULL,\n"
        + "    `product` String NULL,\n"
        + "    `labels` Map(String, String),\n"
        + "    `azureMeterCategory` String NULL,\n"
        + "    `azureMeterSubcategory` String NULL,\n"
        + "    `azureMeterId` String NULL,\n"
        + "    `azureMeterName` String NULL,\n"
        + "    `azureResourceType` String NULL,\n"
        + "    `azureServiceTier` String NULL,\n"
        + "    `azureInstanceId` String NULL,\n"
        + "    `azureResourceGroup` String NULL,\n"
        + "    `azureSubscriptionGuid` String NULL,\n"
        + "    `azureAccountName` String NULL,\n"
        + "    `azureFrequency` String NULL,\n"
        + "    `azurePublisherType` String NULL,\n"
        + "    `azurePublisherName` String NULL,\n"
        + "    `azureServiceName` String NULL,\n"
        + "    `azureSubscriptionName` String NULL,\n"
        + "    `azureReservationId` String NULL,\n"
        + "    `azureReservationName` String NULL,\n"
        + "    `azureResource` String NULL,\n"
        + "    `azureVMProviderId` String NULL,\n"
        + "    `azureTenantId` String NULL,\n"
        + "    `azureBillingCurrency` String NULL,\n"
        + "    `azureCustomerName` String NULL,\n"
        + "    `azureResourceRate` Float NULL,\n"
        + "    `orgIdentifier` String NULL,\n"
        + "    `projectIdentifier` String NULL\n"
        + ")\n"
        + "ENGINE = MergeTree\n"
        + "PARTITION BY toYYYYMMDD(startTime)\n"
        + "ORDER BY tuple()\n";
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
    prepareStatement.setObject(70, billingData.getLabels());

    prepareStatement.addBatch();
  }

  private static String deleteDataFromClickHouse(String clusterDataTableName, long startTime) {
    return "DELETE FROM ccm." + clusterDataTableName + " WHERE starttime = " + startTime;
  }

  private static String getClusterDataCreationQuery(String clusterDataTableName) {
    String clusterDataAggregatedCreateQuery = "CREATE TABLE IF NOT EXISTS ccm." + clusterDataTableName + "\n"
        + "(\n"
        + "    `starttime` Int64 NOT NULL,\n"
        + "    `endtime` Int64 NOT NULL,\n"
        + "    `accountid` String NOT NULL,\n"
        + "    `settingid` String NULL,\n"
        + "    `instanceid` String NOT NULL,\n"
        + "    `instancetype` String NOT NULL,\n"
        + "    `billingaccountid` String NULL,\n"
        + "    `clusterid` String NULL,\n"
        + "    `clustername` String NULL,\n"
        + "    `appid` String NULL,\n"
        + "    `serviceid` String NULL,\n"
        + "    `envid` String NULL,\n"
        + "    `appname` String NULL,\n"
        + "    `servicename` String NULL,\n"
        + "    `envname` String NULL,\n"
        + "    `cloudproviderid` String NULL,\n"
        + "    `parentinstanceid` String NULL,\n"
        + "    `region` String NULL,\n"
        + "    `launchtype` String NULL,\n"
        + "    `clustertype` String NULL,\n"
        + "    `workloadname` String NULL,\n"
        + "    `workloadtype` String NULL,\n"
        + "    `namespace` String NULL,\n"
        + "    `cloudservicename` String NULL,\n"
        + "    `taskid` String NULL,\n"
        + "    `cloudprovider` String NULL,\n"
        + "    `billingamount` Float NOT NULL,\n"
        + "    `cpubillingamount` Float NULL,\n"
        + "    `memorybillingamount` Float NULL,\n"
        + "    `idlecost` Float NULL,\n"
        + "    `cpuidlecost` Float NULL,\n"
        + "    `memoryidlecost` Float NULL,\n"
        + "    `usagedurationseconds` Float NULL,\n"
        + "    `cpuunitseconds` Float NULL,\n"
        + "    `memorymbseconds` Float NULL,\n"
        + "    `maxcpuutilization` Float NULL,\n"
        + "    `maxmemoryutilization` Float NULL,\n"
        + "    `avgcpuutilization` Float NULL,\n"
        + "    `avgmemoryutilization` Float NULL,\n"
        + "    `systemcost` Float NULL,\n"
        + "    `cpusystemcost` Float NULL,\n"
        + "    `memorysystemcost` Float NULL,\n"
        + "    `actualidlecost` Float NULL,\n"
        + "    `cpuactualidlecost` Float NULL,\n"
        + "    `memoryactualidlecost` Float NULL,\n"
        + "    `unallocatedcost` Float NULL,\n"
        + "    `cpuunallocatedcost` Float NULL,\n"
        + "    `memoryunallocatedcost` Float NULL,\n"
        + "    `instancename` String NULL,\n"
        + "    `cpurequest` Float NULL,\n"
        + "    `memoryrequest` Float NULL,\n"
        + "    `cpulimit` Float NULL,\n"
        + "    `memorylimit` Float NULL,\n"
        + "    `maxcpuutilizationvalue` Float NULL,\n"
        + "    `maxmemoryutilizationvalue` Float NULL,\n"
        + "    `avgcpuutilizationvalue` Float NULL,\n"
        + "    `avgmemoryutilizationvalue` Float NULL,\n"
        + "    `networkcost` Float NULL,\n"
        + "    `pricingsource` String NULL,\n"
        + "    `storageactualidlecost` Float NULL,\n"
        + "    `storageunallocatedcost` Float NULL,\n"
        + "    `storageutilizationvalue` Float NULL,\n"
        + "    `storagerequest` Float NULL,\n"
        + "    `storagembseconds` Float NULL,\n"
        + "    `storagecost` Float NULL,\n"
        + "    `maxstorageutilizationvalue` Float NULL,\n"
        + "    `maxstoragerequest` Float NULL,\n"
        + "    `orgIdentifier` String NULL,\n"
        + "    `projectIdentifier` String NULL,\n"
        + "    `labels` Map(String, String)\n"
        + ")\n"
        + "ENGINE = MergeTree\n"
        + "PARTITION BY toStartOfInterval(toDate(starttime), toIntervalDay(1))\n"
        + "ORDER BY tuple()";
    return clusterDataAggregatedCreateQuery;
  }

  private static String getClusterDataAggregatedCreationQuery(String clusterDataTableName) {
    String clusterDataCreateQuery = "CREATE TABLE IF NOT EXISTS ccm." + clusterDataTableName + "\n"
        + "(\n"
        + "    `starttime` Int64 NOT NULL,\n"
        + "    `endtime` Int64 NOT NULL,\n"
        + "    `accountid` String NOT NULL,\n"
        + "    `instancetype` String NOT NULL,\n"
        + "    `instancename` String NULL,\n"
        + "    `clustername` String NULL,\n"
        + "    `billingamount` Float NOT NULL,\n"
        + "    `actualidlecost` Float NULL,\n"
        + "    `unallocatedcost` Float NULL,\n"
        + "    `systemcost` Float NULL,\n"
        + "    `clusterid` String NULL,\n"
        + "    `clustertype` String NULL,\n"
        + "    `region` String NULL,\n"
        + "    `workloadname` String NULL,\n"
        + "    `workloadtype` String NULL,\n"
        + "    `namespace` String NULL,\n"
        + "    `appid` String NULL,\n"
        + "    `serviceid` String NULL,\n"
        + "    `envid` String NULL,\n"
        + "    `cloudproviderid` String NULL,\n"
        + "    `launchtype` String NULL,\n"
        + "    `cloudservicename` String NULL,\n"
        + "    `storageactualidlecost` Float NULL,\n"
        + "    `cpuactualidlecost` Float NULL,\n"
        + "    `memoryactualidlecost` Float NULL,\n"
        + "    `storageunallocatedcost` Float NULL,\n"
        + "    `memoryunallocatedcost` Float NULL,\n"
        + "    `cpuunallocatedcost` Float NULL,\n"
        + "    `storagecost` Float NULL,\n"
        + "    `cpubillingamount` Float NULL,\n"
        + "    `memorybillingamount` Float NULL,\n"
        + "    `storagerequest` Float NULL,\n"
        + "    `storageutilizationvalue` Float NULL,\n"
        + "    `instanceid` String NULL,\n"
        + "    `networkcost` Float NULL,\n"
        + "    `appname` String NULL,\n"
        + "    `servicename` String NULL,\n"
        + "    `envname` String NULL,\n"
        + "    `cloudprovider` String NULL,\n"
        + "    `maxstorageutilizationvalue` Float NULL,\n"
        + "    `maxstoragerequest` Float NULL,\n"
        + "    `orgIdentifier` String NULL,\n"
        + "    `projectIdentifier` String NULL,\n"
        + "    `labels` Map(String, String)\n"
        + ")\n"
        + "ENGINE = MergeTree\n"
        + "PARTITION BY toStartOfInterval(toDate(starttime), toIntervalDay(1))\n"
        + "ORDER BY tuple()";
    return clusterDataCreateQuery;
  }
}
