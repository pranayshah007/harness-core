/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

import io.harness.avro.ClusterBillingData;
import io.harness.avro.Label;
import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.ClickHouseClusterDataService;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.impl.GoogleCloudStorageServiceImpl;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.dto.HarnessTags;
import io.harness.batch.processing.tasklet.reader.BillingDataReader;
import io.harness.batch.processing.tasklet.support.HarnessEntitiesService;
import io.harness.batch.processing.tasklet.support.HarnessEntitiesService.HarnessEntities;
import io.harness.batch.processing.tasklet.support.HarnessTagService;
import io.harness.batch.processing.tasklet.support.K8SWorkloadService;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.clickhouse.ClickHouseService;
import io.harness.ff.FeatureFlagService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class ClusterDataToBigQueryTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private GoogleCloudStorageServiceImpl googleCloudStorageService;
  @Autowired private HarnessTagService harnessTagService;
  @Autowired private HarnessEntitiesService harnessEntitiesService;
  @Autowired private WorkloadRepository workloadRepository;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private ClickHouseService clickHouseService;

  @Autowired private ClickHouseClusterDataService clickHouseClusterDataService;

  @Autowired BatchMainConfig configuration;
  private static final String defaultParentWorkingDirectory = "./avro/";
  private static final String defaultBillingDataFileNameDaily = "billing_data_%s_%s_%s.avro";
  private static final String defaultBillingDataFileNameHourly = "billing_data_hourly_%s_%s_%s_%s.avro";
  private static final String gcsObjectNameFormat = "%s/%s";
  public static final long CACHE_SIZE = 10000;

  private boolean onPrem = true;

  LoadingCache<HarnessEntitiesService.CacheKey, String> entityIdToNameCache =
          Caffeine.newBuilder()
                  .maximumSize(CACHE_SIZE)
                  .build(key -> harnessEntitiesService.fetchEntityName(key.getEntity(), key.getEntityId()));

  @Value
  @EqualsAndHashCode
  @VisibleForTesting
  public static class Key {
    String accountId;
    String clusterId;
    String namespace;

    public static Key getKeyFromInstanceData(InstanceBillingData instanceBillingData) {
      return new Key(
              instanceBillingData.getAccountId(), instanceBillingData.getClusterId(), instanceBillingData.getNamespace());
    }
  }

  @Value
  @EqualsAndHashCode
  @VisibleForTesting
  public static class AccountClusterKey {
    String accountId;
    String clusterId;

    public static AccountClusterKey getAccountClusterKeyFromInstanceData(InstanceBillingData instanceBillingData) {
      return new AccountClusterKey(instanceBillingData.getAccountId(), instanceBillingData.getClusterId());
    }
  }

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobParameters parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters);
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    //    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();
    int batchSize = 100;

    BillingDataReader billingDataReader = new BillingDataReader(billingDataService, jobConstants.getAccountId(),
            Instant.ofEpochMilli(jobConstants.getJobStartTime()), Instant.ofEpochMilli(jobConstants.getJobEndTime()),
            batchSize, 0, batchJobType);

    ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(jobConstants.getJobStartTime()), ZoneId.of("GMT"));
    String billingDataFileName = "";
    String clusterDataTableName = "";
    String clusterDataAggregatedTableName = "";
    if (batchJobType == BatchJobType.CLUSTER_DATA_TO_BIG_QUERY) {
      billingDataFileName =
              String.format(defaultBillingDataFileNameDaily, zdt.getYear(), zdt.getMonth(), zdt.getDayOfMonth());
      clusterDataTableName = "clusterData";
      clusterDataAggregatedTableName = "clusterDataAggregated";
    } else if (batchJobType == BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY) {
      billingDataFileName = String.format(
              defaultBillingDataFileNameHourly, zdt.getYear(), zdt.getMonth(), zdt.getDayOfMonth(), zdt.getHour());
      clusterDataTableName = "clusterDataHourly";
      clusterDataAggregatedTableName = "clusterDataHourlyAggregated";
    }
    List<InstanceBillingData> instanceBillingDataList;

    if (!onPrem) {
      boolean avroFileWithSchemaExists = false;
      do {
        instanceBillingDataList = billingDataReader.getNext();
        List<ClusterBillingData> clusterBillingDataList =
                getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);
        log.debug("clusterBillingDataList size: {}", clusterBillingDataList.size());
        writeDataToAvro(
                jobConstants.getAccountId(), clusterBillingDataList, billingDataFileName, avroFileWithSchemaExists);
        avroFileWithSchemaExists = true;
      } while (instanceBillingDataList.size() == batchSize);

      final String gcsObjectName = String.format(gcsObjectNameFormat, jobConstants.getAccountId(), billingDataFileName);
      googleCloudStorageService.uploadObject(gcsObjectName, defaultParentWorkingDirectory + gcsObjectName);

      // Delete file once upload is complete
      File workingDirectory = new File(defaultParentWorkingDirectory + jobConstants.getAccountId());
      File billingDataFile = new File(workingDirectory, billingDataFileName);
      Files.delete(billingDataFile.toPath());

    } else {
      List<ClusterBillingData> allClusterBillingData = new ArrayList<>();
      do {
        instanceBillingDataList = billingDataReader.getNext();
        List<ClusterBillingData> clusterBillingDataList =
                getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);
        log.debug("clusterBillingDataList size: {}", clusterBillingDataList.size());
        allClusterBillingData.addAll(clusterBillingDataList);

      } while (instanceBillingDataList.size() == batchSize);

      log.info("onPrem ::: allClusterBillingData size: {}", allClusterBillingData.size());

      processClusterDataTable(jobConstants, clusterDataTableName, allClusterBillingData);

      // Delete old data and ingest in unifiedTable
      processUnifiedTable(zdt, clusterDataTableName);

      // Ingest data to clusterDataAggregated/clusterDataHourlyAggregated
      processAggregatedTable(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);

      // Ingest data to costAggregated Table
      processCostAggregaredTable(jobConstants, zdt);
    }
    return null;
  }

  private void processCostAggregaredTable(JobConstants jobConstants, ZonedDateTime zdt) throws Exception {
    clickHouseService.executeClickHouseQuery(getCreateCostAggregatedQuery(), false);
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouse(zdt.toLocalDate().toString(), jobConstants.getAccountId()), false);
    ingestToCostAggregatedTable(zdt.toLocalDate().toString());
  }

  private void ingestToCostAggregatedTable(String startTime) throws Exception {
    String costAggregatedIngestionQuery =
            "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId) SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_', clustercloudprovider) AS cloudProvider, accountid AS accountId FROM ccm.unifiedTable WHERE (toDate(startTime) = toDate('"
                    + startTime
                    + "')) AND (clustercloudprovider = 'CLUSTER') AND (clustertype = 'K8S') GROUP BY day, clustertype, accountid, clustercloudprovider";

    //    String costAggregatedIngestionQuery = "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId)
    //    \n"
    //        + "    SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_',
    //        clustercloudprovider) AS cloudProvider, accountid as accountId \n"
    //        + "    FROM ccm.unifiedTable  \n"
    //        + "    WHERE toDate(startTime) = toDate('" + startTime + "')"
    //        + "    and clustercloudprovider = 'CLUSTER' AND clustertype = 'K8S'\n"
    //        + "    GROUP BY day, clustercloudprovider;";
    clickHouseService.executeClickHouseQuery(costAggregatedIngestionQuery, false);
  }

  private static String deleteDataFromClickHouse(final String startTime, final String accountId) {
    return "DELETE FROM ccm.costAggregated WHERE toDate(day) = toDate('" + startTime
            + "') AND cloudProvider like 'K8S_%' AND accountId = '" + accountId + "';";
  }

  private void processAggregatedTable(
          JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    createOrDeleteExistingDataFromTable(jobConstants, clusterDataAggregatedTableName);
    ingestAggregatedData(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);
  }

  private void processUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    clickHouseService.executeClickHouseQuery(getUnifiedTableCreateQuery(), false);
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouseForUnifiedTable(zdt.toLocalDate().toString()), false);
    ingestIntoUnifiedTable(zdt, clusterDataTableName);
  }

  private void processClusterDataTable(JobConstants jobConstants, String clusterDataTableName,
                                       List<ClusterBillingData> allClusterBillingData) throws Exception {
    // Ingesting data to clusterData/clusterDataHourly table in clickhouse.
    createOrDeleteExistingDataFromTable(jobConstants, clusterDataTableName);
    ingestClusterData(clusterDataTableName, allClusterBillingData);
  }

  private void ingestIntoUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
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

    clickHouseService.executeClickHouseQuery(unifiedtableIngestQuery, false);
  }

  private String deleteDataFromClickHouseForUnifiedTable(String jobStartTime) {
    return "DELETE FROM ccm.unifiedTable WHERE toDate(startTime) = toDate('" + jobStartTime
            + "') AND cloudProvider = 'CLUSTER'";
  }

  private void ingestAggregatedData(
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

    clickHouseService.executeClickHouseQuery(insertQueryForPods, false);
    clickHouseService.executeClickHouseQuery(insertQueryForPodAndPv, false);
  }

  private void ingestClusterData(String clusterDataTableName, List<ClusterBillingData> allClusterBillingData)
          throws SQLException {
    try (Connection connection = clickHouseService.getConnection()) {
      String query = "INSERT INTO ccm." + clusterDataTableName
              + " ( starttime,  endtime,  accountid,  settingid,  instanceid,  instancetype,  billingaccountid,  clusterid,  clustername,  appid,  serviceid,  envid,  appname,  servicename,  envname,  cloudproviderid,  parentinstanceid,  region,  launchtype,  clustertype,  workloadname,  workloadtype,  namespace,  cloudservicename,  taskid,  cloudprovider,  billingamount,  cpubillingamount,  memorybillingamount,  idlecost,  cpuidlecost,  memoryidlecost,  usagedurationseconds,  cpuunitseconds,  memorymbseconds,  maxcpuutilization,  maxmemoryutilization,  avgcpuutilization,  avgmemoryutilization,  systemcost,  cpusystemcost,  memorysystemcost,  actualidlecost,  cpuactualidlecost,  memoryactualidlecost,  unallocatedcost,  cpuunallocatedcost,  memoryunallocatedcost,  instancename,  cpurequest,  memoryrequest,  cpulimit,  memorylimit,  maxcpuutilizationvalue,  maxmemoryutilizationvalue,  avgcpuutilizationvalue,  avgmemoryutilizationvalue,  networkcost,  pricingsource,  storageactualidlecost,  storageunallocatedcost,  storageutilizationvalue,  storagerequest,  storagembseconds,  storagecost,  maxstorageutilizationvalue,  maxstoragerequest,  orgIdentifier,  projectIdentifier,  labels) VALUES ( ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?)";
      PreparedStatement prepareStatement = connection.prepareStatement(query);
      connection.setAutoCommit(false);

      for (ClusterBillingData billingData : allClusterBillingData) {
        getBatchedPreparedStatement(prepareStatement, billingData);
      }
      int[] ints = prepareStatement.executeBatch();
      log.info(":::::::::::::::::::::::::::::::::::::: Ingested in " + clusterDataTableName + ",  results length: {}",
              ints.length);
    }
  }

  private void createOrDeleteExistingDataFromTable(JobConstants jobConstants, String tableName) throws Exception {
    if (!tableName.contains("Aggregated")) {
      clickHouseService.executeClickHouseQuery(clickHouseClusterDataService.getClusterDataCreationQuery(tableName), true);
    } else {
      clickHouseService.executeClickHouseQuery(getClusterDataAggregatedCreationQuery(tableName), true);
    }
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouse(tableName, jobConstants.getJobStartTime()), false);
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

  @VisibleForTesting
  public List<ClusterBillingData> getClusterBillingDataForBatch(
          String accountId, BatchJobType batchJobType, List<InstanceBillingData> instanceBillingDataList) {
    Map<String, Map<String, String>> instanceIdToLabelMapping = new HashMap<>();
    List<String> instanceIdList =
            instanceBillingDataList.stream()
                    .filter(instanceBillingData
                            -> ImmutableSet
                            .of(InstanceType.ECS_TASK_FARGATE.name(), InstanceType.ECS_CONTAINER_INSTANCE.name(),
                                    InstanceType.ECS_TASK_EC2.name())
                            .contains(instanceBillingData.getInstanceType()))
                    .map(InstanceBillingData::getInstanceId)
                    .collect(Collectors.toList());
    if (!instanceIdList.isEmpty()) {
      instanceIdToLabelMapping = instanceDataService.fetchLabelsForGivenInstances(accountId, instanceIdList);
    }

    return getClusterBillingDataForBatchWorkloadUid(instanceBillingDataList, instanceIdToLabelMapping);
  }

  public List<ClusterBillingData> getClusterBillingDataForBatchWorkloadUid(
          List<InstanceBillingData> instanceBillingDataList, Map<String, Map<String, String>> instanceIdToLabelMapping) {
    List<ClusterBillingData> clusterBillingDataList = new ArrayList<>();
    Map<AccountClusterKey, List<InstanceBillingData>> instanceBillingDataGrouped =
            instanceBillingDataList.stream().collect(
                    Collectors.groupingBy(AccountClusterKey::getAccountClusterKeyFromInstanceData));

    log.info("Started Querying data {}", instanceBillingDataGrouped.size());
    for (AccountClusterKey accountClusterKey : instanceBillingDataGrouped.keySet()) {
      List<InstanceBillingData> instances = instanceBillingDataGrouped.get(accountClusterKey);
      Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap =
              getLabelMapForClusterGroup(instances, accountClusterKey);

      for (InstanceBillingData instanceBillingData : instances) {
        Map<String, String> labels = labelMap.get(new K8SWorkloadService.WorkloadUidCacheKey(
                instanceBillingData.getAccountId(), instanceBillingData.getClusterId(), instanceBillingData.getTaskId()));
        ClusterBillingData clusterBillingData = convertInstanceBillingDataToAVROObjects(
                instanceBillingData, labels, instanceIdToLabelMapping.get(instanceBillingData.getInstanceId()));
        clusterBillingDataList.add(clusterBillingData);
      }
    }
    log.info("Finished Querying data");

    return clusterBillingDataList;
  }

  @VisibleForTesting
  public Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> getLabelMapForClusterGroup(
          List<InstanceBillingData> instanceBillingDataList, AccountClusterKey accountClusterKey) {
    String accountId = accountClusterKey.getAccountId();
    String clusterId = accountClusterKey.getClusterId();
    Set<String> workloadUids =
            instanceBillingDataList.stream()
                    .filter(instanceBillingData
                            -> ImmutableSet.of(InstanceType.K8S_POD_FARGATE.name(), InstanceType.K8S_POD.name())
                            .contains(instanceBillingData.getInstanceType()))
                    .map(InstanceBillingData::getTaskId)
                    .collect(Collectors.toSet());

    List<K8sWorkload> workloads = new ArrayList<>();
    if (featureFlagService.isNotEnabled(FeatureName.CCM_WORKLOAD_LABELS_OPTIMISATION, accountId)) {
      if (!workloadUids.isEmpty()) {
        workloads = workloadRepository.getWorkloadByWorkloadUid(accountId, clusterId, workloadUids);
      }
    } else {
      log.info("CCM_WORKLOAD_LABELS_OPTIMISATION is enabled for this account");
    }

    Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap = new HashMap<>();
    workloads.forEach(workload
            -> labelMap.put(
            new K8SWorkloadService.WorkloadUidCacheKey(accountId, clusterId, workload.getUid()), workload.getLabels()));
    return labelMap;
  }

  private void writeDataToAvro(String accountId, List<ClusterBillingData> instanceBillingDataAvro,
                               String billingDataFileName, boolean avroFileWithSchemaExists) throws IOException {
    String directoryPath = defaultParentWorkingDirectory + accountId;
    createDirectoryIfDoesNotExist(directoryPath);
    File workingDirectory = new File(directoryPath);
    File billingDataFile = new File(workingDirectory, billingDataFileName);
    DataFileWriter<ClusterBillingData> dataFileWriter = getInstanceBillingDataDataFileWriter();
    if (avroFileWithSchemaExists) {
      dataFileWriter.appendTo(billingDataFile);
    } else {
      dataFileWriter.create(ClusterBillingData.getClassSchema(), billingDataFile);
    }
    for (ClusterBillingData row : instanceBillingDataAvro) {
      dataFileWriter.append(row);
    }
    dataFileWriter.close();
  }

  private ClusterBillingData convertInstanceBillingDataToAVROObjects(
          InstanceBillingData instanceBillingData, Map<String, String> k8sWorkloadLabel, Map<String, String> labelMap) {
    String accountId = instanceBillingData.getAccountId();
    ClusterBillingData clusterBillingData = new ClusterBillingData();
    clusterBillingData.setAppid(instanceBillingData.getAppId());
    clusterBillingData.setEnvid(instanceBillingData.getEnvId());
    clusterBillingData.setRegion(instanceBillingData.getRegion());
    clusterBillingData.setServiceid(instanceBillingData.getServiceId());
    clusterBillingData.setCloudservicename(instanceBillingData.getCloudServiceName());
    clusterBillingData.setAccountid(accountId);
    clusterBillingData.setInstanceid(instanceBillingData.getInstanceId());
    clusterBillingData.setInstancename(instanceBillingData.getInstanceName());
    clusterBillingData.setClusterid(instanceBillingData.getClusterId());
    clusterBillingData.setSettingid(instanceBillingData.getSettingId());
    clusterBillingData.setLaunchtype(instanceBillingData.getLaunchType());
    clusterBillingData.setTaskid(instanceBillingData.getTaskId());
    clusterBillingData.setNamespace(instanceBillingData.getNamespace());
    clusterBillingData.setClustername(instanceBillingData.getClusterName());
    clusterBillingData.setClustertype(instanceBillingData.getClusterType());
    clusterBillingData.setInstancetype(instanceBillingData.getInstanceType());
    clusterBillingData.setWorkloadname(instanceBillingData.getWorkloadName());
    clusterBillingData.setWorkloadtype(instanceBillingData.getWorkloadType());
    clusterBillingData.setBillingaccountid(instanceBillingData.getBillingAccountId());
    clusterBillingData.setParentinstanceid(instanceBillingData.getParentInstanceId());
    clusterBillingData.setCloudproviderid(instanceBillingData.getCloudProviderId());
    clusterBillingData.setCloudprovider(instanceBillingData.getCloudProvider());
    clusterBillingData.setPricingsource(instanceBillingData.getPricingSource());

    clusterBillingData.setBillingamount(instanceBillingData.getBillingAmount().doubleValue());
    clusterBillingData.setCpubillingamount(instanceBillingData.getCpuBillingAmount().doubleValue());
    clusterBillingData.setMemorybillingamount(instanceBillingData.getMemoryBillingAmount().doubleValue());
    clusterBillingData.setIdlecost(instanceBillingData.getIdleCost().doubleValue());
    clusterBillingData.setCpuidlecost(instanceBillingData.getCpuIdleCost().doubleValue());
    clusterBillingData.setMemoryidlecost(instanceBillingData.getMemoryIdleCost().doubleValue());
    clusterBillingData.setSystemcost(instanceBillingData.getSystemCost().doubleValue());
    clusterBillingData.setCpusystemcost(instanceBillingData.getCpuSystemCost().doubleValue());
    clusterBillingData.setMemorysystemcost(instanceBillingData.getMemorySystemCost().doubleValue());
    clusterBillingData.setActualidlecost(instanceBillingData.getActualIdleCost().doubleValue());
    clusterBillingData.setCpuactualidlecost(instanceBillingData.getCpuActualIdleCost().doubleValue());
    clusterBillingData.setMemoryactualidlecost(instanceBillingData.getMemoryActualIdleCost().doubleValue());
    clusterBillingData.setNetworkcost(instanceBillingData.getNetworkCost());
    clusterBillingData.setUnallocatedcost(instanceBillingData.getUnallocatedCost().doubleValue());
    clusterBillingData.setCpuunallocatedcost(instanceBillingData.getCpuUnallocatedCost().doubleValue());
    clusterBillingData.setMemoryunallocatedcost(instanceBillingData.getMemoryUnallocatedCost().doubleValue());

    clusterBillingData.setMaxcpuutilization(instanceBillingData.getMaxCpuUtilization());
    clusterBillingData.setMaxmemoryutilization(instanceBillingData.getMaxMemoryUtilization());
    clusterBillingData.setAvgcpuutilization(instanceBillingData.getAvgCpuUtilization());
    clusterBillingData.setAvgmemoryutilization(instanceBillingData.getAvgMemoryUtilization());
    clusterBillingData.setMaxcpuutilizationvalue(instanceBillingData.getMaxCpuUtilizationValue());
    clusterBillingData.setMaxmemoryutilizationvalue(instanceBillingData.getMaxMemoryUtilizationValue());
    clusterBillingData.setAvgcpuutilizationvalue(instanceBillingData.getAvgCpuUtilizationValue());
    clusterBillingData.setAvgmemoryutilizationvalue(instanceBillingData.getAvgMemoryUtilizationValue());
    clusterBillingData.setCpurequest(instanceBillingData.getCpuRequest());
    clusterBillingData.setCpulimit(instanceBillingData.getCpuLimit());
    clusterBillingData.setMemoryrequest(instanceBillingData.getMemoryRequest());
    clusterBillingData.setMemorylimit(instanceBillingData.getMemoryLimit());
    clusterBillingData.setCpuunitseconds(instanceBillingData.getCpuUnitSeconds());
    clusterBillingData.setMemorymbseconds(instanceBillingData.getMemoryMbSeconds());
    clusterBillingData.setUsagedurationseconds(instanceBillingData.getUsageDurationSeconds());
    clusterBillingData.setEndtime(instanceBillingData.getEndTimestamp());
    clusterBillingData.setStarttime(instanceBillingData.getStartTimestamp());
    clusterBillingData.setStoragecost(getDoubleValueFromBigDecimal(instanceBillingData.getStorageBillingAmount()));
    clusterBillingData.setStorageactualidlecost(
            getDoubleValueFromBigDecimal(instanceBillingData.getStorageActualIdleCost()));
    clusterBillingData.setStorageunallocatedcost(
            getDoubleValueFromBigDecimal(instanceBillingData.getStorageUnallocatedCost()));
    clusterBillingData.setStorageutilizationvalue(instanceBillingData.getStorageUtilizationValue());
    clusterBillingData.setStoragerequest(instanceBillingData.getStorageRequest());
    clusterBillingData.setMaxstorageutilizationvalue(instanceBillingData.getMaxStorageUtilizationValue());
    clusterBillingData.setMaxstoragerequest(instanceBillingData.getMaxStorageRequest());
    clusterBillingData.setOrgIdentifier(instanceBillingData.getOrgIdentifier());
    clusterBillingData.setProjectIdentifier(instanceBillingData.getProjectIdentifier());

    if (instanceBillingData.getAppId() != null) {
      clusterBillingData.setAppname(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getAppId(), HarnessEntities.APP)));
    } else {
      clusterBillingData.setAppname(null);
    }

    if (instanceBillingData.getEnvId() != null) {
      clusterBillingData.setEnvname(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getEnvId(), HarnessEntities.ENV)));
    } else {
      clusterBillingData.setEnvname(null);
    }

    if (instanceBillingData.getServiceId() != null) {
      clusterBillingData.setServicename(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getServiceId(), HarnessEntities.SERVICE)));
    } else {
      clusterBillingData.setServicename(null);
    }

    List<Label> labels = new ArrayList<>();
    Set<String> labelKeySet = new HashSet<>();
    if (ImmutableSet.of(InstanceType.K8S_POD.name(), InstanceType.K8S_POD_FARGATE.name())
            .contains(instanceBillingData.getInstanceType())) {
      if (null != k8sWorkloadLabel) {
        k8sWorkloadLabel.forEach((key, value) -> appendLabel(key, value, labelKeySet, labels));
      }
    }

    if (null != labelMap) {
      labelMap.forEach((key, value) -> appendLabel(key, value, labelKeySet, labels));
    }

    if (null != instanceBillingData.getAppId()) {
      List<HarnessTags> harnessTags = harnessTagService.getHarnessTags(accountId, instanceBillingData.getAppId());
      harnessTags.addAll(harnessTagService.getHarnessTags(accountId, instanceBillingData.getServiceId()));
      harnessTags.addAll(harnessTagService.getHarnessTags(accountId, instanceBillingData.getEnvId()));
      harnessTags.forEach(harnessTag -> appendLabel(harnessTag.getKey(), harnessTag.getValue(), labelKeySet, labels));
    }

    clusterBillingData.setLabels(Arrays.asList(labels.toArray()));
    return clusterBillingData;
  }

  @VisibleForTesting
  public void appendLabel(String key, String value, Set<String> labelKeySet, List<Label> labels) {
    Label label = new Label();
    if (!labelKeySet.contains(key)) {
      label.setKey(key);
      label.setValue(value);
      labels.add(label);
      labelKeySet.add(key);
    }
  }

  @NotNull
  private static DataFileWriter<ClusterBillingData> getInstanceBillingDataDataFileWriter() {
    DatumWriter<ClusterBillingData> userDatumWriter = new SpecificDatumWriter<>(ClusterBillingData.class);
    return new DataFileWriter<>(userDatumWriter);
  }

  private static double getDoubleValueFromBigDecimal(BigDecimal value) {
    if (value != null) {
      return value.doubleValue();
    }
    return 0D;
  }
}/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

        import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

        import io.harness.avro.ClusterBillingData;
        import io.harness.avro.Label;
        import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
        import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
        import io.harness.batch.processing.billing.timeseries.service.impl.ClickHouseClusterDataService;
        import io.harness.batch.processing.ccm.BatchJobType;
        import io.harness.batch.processing.ccm.CCMJobConstants;
        import io.harness.batch.processing.config.BatchMainConfig;
        import io.harness.batch.processing.service.impl.GoogleCloudStorageServiceImpl;
        import io.harness.batch.processing.service.intfc.WorkloadRepository;
        import io.harness.batch.processing.tasklet.dto.HarnessTags;
        import io.harness.batch.processing.tasklet.reader.BillingDataReader;
        import io.harness.batch.processing.tasklet.support.HarnessEntitiesService;
        import io.harness.batch.processing.tasklet.support.HarnessEntitiesService.HarnessEntities;
        import io.harness.batch.processing.tasklet.support.HarnessTagService;
        import io.harness.batch.processing.tasklet.support.K8SWorkloadService;
        import io.harness.beans.FeatureName;
        import io.harness.ccm.commons.beans.InstanceType;
        import io.harness.ccm.commons.beans.JobConstants;
        import io.harness.ccm.commons.entities.k8s.K8sWorkload;
        import io.harness.ccm.commons.service.intf.InstanceDataService;
        import io.harness.clickhouse.ClickHouseService;
        import io.harness.ff.FeatureFlagService;

        import com.github.benmanes.caffeine.cache.Caffeine;
        import com.github.benmanes.caffeine.cache.LoadingCache;
        import com.google.common.annotations.VisibleForTesting;
        import com.google.common.collect.ImmutableSet;
        import com.google.inject.Singleton;
        import java.io.File;
        import java.io.IOException;
        import java.math.BigDecimal;
        import java.nio.file.Files;
        import java.sql.*;
        import java.time.Instant;
        import java.time.ZoneId;
        import java.time.ZonedDateTime;
        import java.util.*;
        import java.util.stream.Collectors;
        import lombok.EqualsAndHashCode;
        import lombok.Value;
        import lombok.extern.slf4j.Slf4j;
        import org.apache.avro.file.DataFileWriter;
        import org.apache.avro.io.DatumWriter;
        import org.apache.avro.specific.SpecificDatumWriter;
        import org.jetbrains.annotations.NotNull;
        import org.springframework.batch.core.JobParameters;
        import org.springframework.batch.core.StepContribution;
        import org.springframework.batch.core.scope.context.ChunkContext;
        import org.springframework.batch.core.step.tasklet.Tasklet;
        import org.springframework.batch.repeat.RepeatStatus;
        import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class ClusterDataToBigQueryTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private GoogleCloudStorageServiceImpl googleCloudStorageService;
  @Autowired private HarnessTagService harnessTagService;
  @Autowired private HarnessEntitiesService harnessEntitiesService;
  @Autowired private WorkloadRepository workloadRepository;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private ClickHouseService clickHouseService;

  @Autowired private ClickHouseClusterDataService clickHouseClusterDataService;

  @Autowired BatchMainConfig configuration;
  private static final String defaultParentWorkingDirectory = "./avro/";
  private static final String defaultBillingDataFileNameDaily = "billing_data_%s_%s_%s.avro";
  private static final String defaultBillingDataFileNameHourly = "billing_data_hourly_%s_%s_%s_%s.avro";
  private static final String gcsObjectNameFormat = "%s/%s";
  public static final long CACHE_SIZE = 10000;

  private boolean onPrem = true;

  LoadingCache<HarnessEntitiesService.CacheKey, String> entityIdToNameCache =
          Caffeine.newBuilder()
                  .maximumSize(CACHE_SIZE)
                  .build(key -> harnessEntitiesService.fetchEntityName(key.getEntity(), key.getEntityId()));

  @Value
  @EqualsAndHashCode
  @VisibleForTesting
  public static class Key {
    String accountId;
    String clusterId;
    String namespace;

    public static Key getKeyFromInstanceData(InstanceBillingData instanceBillingData) {
      return new Key(
              instanceBillingData.getAccountId(), instanceBillingData.getClusterId(), instanceBillingData.getNamespace());
    }
  }

  @Value
  @EqualsAndHashCode
  @VisibleForTesting
  public static class AccountClusterKey {
    String accountId;
    String clusterId;

    public static AccountClusterKey getAccountClusterKeyFromInstanceData(InstanceBillingData instanceBillingData) {
      return new AccountClusterKey(instanceBillingData.getAccountId(), instanceBillingData.getClusterId());
    }
  }

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobParameters parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters);
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    //    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();
    int batchSize = 100;

    BillingDataReader billingDataReader = new BillingDataReader(billingDataService, jobConstants.getAccountId(),
            Instant.ofEpochMilli(jobConstants.getJobStartTime()), Instant.ofEpochMilli(jobConstants.getJobEndTime()),
            batchSize, 0, batchJobType);

    ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(jobConstants.getJobStartTime()), ZoneId.of("GMT"));
    String billingDataFileName = "";
    String clusterDataTableName = "";
    String clusterDataAggregatedTableName = "";
    if (batchJobType == BatchJobType.CLUSTER_DATA_TO_BIG_QUERY) {
      billingDataFileName =
              String.format(defaultBillingDataFileNameDaily, zdt.getYear(), zdt.getMonth(), zdt.getDayOfMonth());
      clusterDataTableName = "clusterData";
      clusterDataAggregatedTableName = "clusterDataAggregated";
    } else if (batchJobType == BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY) {
      billingDataFileName = String.format(
              defaultBillingDataFileNameHourly, zdt.getYear(), zdt.getMonth(), zdt.getDayOfMonth(), zdt.getHour());
      clusterDataTableName = "clusterDataHourly";
      clusterDataAggregatedTableName = "clusterDataHourlyAggregated";
    }
    List<InstanceBillingData> instanceBillingDataList;

    if (!onPrem) {
      boolean avroFileWithSchemaExists = false;
      do {
        instanceBillingDataList = billingDataReader.getNext();
        List<ClusterBillingData> clusterBillingDataList =
                getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);
        log.debug("clusterBillingDataList size: {}", clusterBillingDataList.size());
        writeDataToAvro(
                jobConstants.getAccountId(), clusterBillingDataList, billingDataFileName, avroFileWithSchemaExists);
        avroFileWithSchemaExists = true;
      } while (instanceBillingDataList.size() == batchSize);

      final String gcsObjectName = String.format(gcsObjectNameFormat, jobConstants.getAccountId(), billingDataFileName);
      googleCloudStorageService.uploadObject(gcsObjectName, defaultParentWorkingDirectory + gcsObjectName);

      // Delete file once upload is complete
      File workingDirectory = new File(defaultParentWorkingDirectory + jobConstants.getAccountId());
      File billingDataFile = new File(workingDirectory, billingDataFileName);
      Files.delete(billingDataFile.toPath());

    } else {
      List<ClusterBillingData> allClusterBillingData = new ArrayList<>();
      do {
        instanceBillingDataList = billingDataReader.getNext();
        List<ClusterBillingData> clusterBillingDataList =
                getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);
        log.debug("clusterBillingDataList size: {}", clusterBillingDataList.size());
        allClusterBillingData.addAll(clusterBillingDataList);

      } while (instanceBillingDataList.size() == batchSize);

      log.info("onPrem ::: allClusterBillingData size: {}", allClusterBillingData.size());

      processClusterDataTable(jobConstants, clusterDataTableName, allClusterBillingData);

      // Delete old data and ingest in unifiedTable
      processUnifiedTable(zdt, clusterDataTableName);

      // Ingest data to clusterDataAggregated/clusterDataHourlyAggregated
      processAggregatedTable(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);

      // Ingest data to costAggregated Table
      processCostAggregaredTable(jobConstants, zdt);
    }
    return null;
  }

  private void processCostAggregaredTable(JobConstants jobConstants, ZonedDateTime zdt) throws Exception {
    clickHouseService.executeClickHouseQuery(getCreateCostAggregatedQuery(), false);
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouse(zdt.toLocalDate().toString(), jobConstants.getAccountId()), false);
    ingestToCostAggregatedTable(zdt.toLocalDate().toString());
  }

  private void ingestToCostAggregatedTable(String startTime) throws Exception {
    String costAggregatedIngestionQuery =
            "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId) SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_', clustercloudprovider) AS cloudProvider, accountid AS accountId FROM ccm.unifiedTable WHERE (toDate(startTime) = toDate('"
                    + startTime
                    + "')) AND (clustercloudprovider = 'CLUSTER') AND (clustertype = 'K8S') GROUP BY day, clustertype, accountid, clustercloudprovider";

    //    String costAggregatedIngestionQuery = "INSERT INTO ccm.costAggregated (day, cost, cloudProvider, accountId)
    //    \n"
    //        + "    SELECT date_trunc('day', startTime) AS day, sum(cost) AS cost, concat(clustertype, '_',
    //        clustercloudprovider) AS cloudProvider, accountid as accountId \n"
    //        + "    FROM ccm.unifiedTable  \n"
    //        + "    WHERE toDate(startTime) = toDate('" + startTime + "')"
    //        + "    and clustercloudprovider = 'CLUSTER' AND clustertype = 'K8S'\n"
    //        + "    GROUP BY day, clustercloudprovider;";
    clickHouseService.executeClickHouseQuery(costAggregatedIngestionQuery, false);
  }

  private static String deleteDataFromClickHouse(final String startTime, final String accountId) {
    return "DELETE FROM ccm.costAggregated WHERE toDate(day) = toDate('" + startTime
            + "') AND cloudProvider like 'K8S_%' AND accountId = '" + accountId + "';";
  }

  private void processAggregatedTable(
          JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    createOrDeleteExistingDataFromTable(jobConstants, clusterDataAggregatedTableName);
    ingestAggregatedData(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);
  }

  private void processUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    clickHouseService.executeClickHouseQuery(getUnifiedTableCreateQuery(), false);
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouseForUnifiedTable(zdt.toLocalDate().toString()), false);
    ingestIntoUnifiedTable(zdt, clusterDataTableName);
  }

  private void processClusterDataTable(JobConstants jobConstants, String clusterDataTableName,
                                       List<ClusterBillingData> allClusterBillingData) throws Exception {
    // Ingesting data to clusterData/clusterDataHourly table in clickhouse.
    createOrDeleteExistingDataFromTable(jobConstants, clusterDataTableName);
    ingestClusterData(clusterDataTableName, allClusterBillingData);
  }

  private void ingestIntoUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
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

    clickHouseService.executeClickHouseQuery(unifiedtableIngestQuery, false);
  }

  private String deleteDataFromClickHouseForUnifiedTable(String jobStartTime) {
    return "DELETE FROM ccm.unifiedTable WHERE toDate(startTime) = toDate('" + jobStartTime
            + "') AND cloudProvider = 'CLUSTER'";
  }

  private void ingestAggregatedData(
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

    clickHouseService.executeClickHouseQuery(insertQueryForPods, false);
    clickHouseService.executeClickHouseQuery(insertQueryForPodAndPv, false);
  }

  private void ingestClusterData(String clusterDataTableName, List<ClusterBillingData> allClusterBillingData)
          throws SQLException {
    try (Connection connection = clickHouseService.getConnection()) {
      String query = "INSERT INTO ccm." + clusterDataTableName
              + " ( starttime,  endtime,  accountid,  settingid,  instanceid,  instancetype,  billingaccountid,  clusterid,  clustername,  appid,  serviceid,  envid,  appname,  servicename,  envname,  cloudproviderid,  parentinstanceid,  region,  launchtype,  clustertype,  workloadname,  workloadtype,  namespace,  cloudservicename,  taskid,  cloudprovider,  billingamount,  cpubillingamount,  memorybillingamount,  idlecost,  cpuidlecost,  memoryidlecost,  usagedurationseconds,  cpuunitseconds,  memorymbseconds,  maxcpuutilization,  maxmemoryutilization,  avgcpuutilization,  avgmemoryutilization,  systemcost,  cpusystemcost,  memorysystemcost,  actualidlecost,  cpuactualidlecost,  memoryactualidlecost,  unallocatedcost,  cpuunallocatedcost,  memoryunallocatedcost,  instancename,  cpurequest,  memoryrequest,  cpulimit,  memorylimit,  maxcpuutilizationvalue,  maxmemoryutilizationvalue,  avgcpuutilizationvalue,  avgmemoryutilizationvalue,  networkcost,  pricingsource,  storageactualidlecost,  storageunallocatedcost,  storageutilizationvalue,  storagerequest,  storagembseconds,  storagecost,  maxstorageutilizationvalue,  maxstoragerequest,  orgIdentifier,  projectIdentifier,  labels) VALUES ( ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?,  ?)";
      PreparedStatement prepareStatement = connection.prepareStatement(query);
      connection.setAutoCommit(false);

      for (ClusterBillingData billingData : allClusterBillingData) {
        getBatchedPreparedStatement(prepareStatement, billingData);
      }
      int[] ints = prepareStatement.executeBatch();
      log.info(":::::::::::::::::::::::::::::::::::::: Ingested in " + clusterDataTableName + ",  results length: {}",
              ints.length);
    }
  }

  private void createOrDeleteExistingDataFromTable(JobConstants jobConstants, String tableName) throws Exception {
    if (!tableName.contains("Aggregated")) {
      clickHouseService.executeClickHouseQuery(clickHouseClusterDataService.getClusterDataCreationQuery(tableName), true);
    } else {
      clickHouseService.executeClickHouseQuery(getClusterDataAggregatedCreationQuery(tableName), true);
    }
    clickHouseService.executeClickHouseQuery(
            deleteDataFromClickHouse(tableName, jobConstants.getJobStartTime()), false);
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

  @VisibleForTesting
  public List<ClusterBillingData> getClusterBillingDataForBatch(
          String accountId, BatchJobType batchJobType, List<InstanceBillingData> instanceBillingDataList) {
    Map<String, Map<String, String>> instanceIdToLabelMapping = new HashMap<>();
    List<String> instanceIdList =
            instanceBillingDataList.stream()
                    .filter(instanceBillingData
                            -> ImmutableSet
                            .of(InstanceType.ECS_TASK_FARGATE.name(), InstanceType.ECS_CONTAINER_INSTANCE.name(),
                                    InstanceType.ECS_TASK_EC2.name())
                            .contains(instanceBillingData.getInstanceType()))
                    .map(InstanceBillingData::getInstanceId)
                    .collect(Collectors.toList());
    if (!instanceIdList.isEmpty()) {
      instanceIdToLabelMapping = instanceDataService.fetchLabelsForGivenInstances(accountId, instanceIdList);
    }

    return getClusterBillingDataForBatchWorkloadUid(instanceBillingDataList, instanceIdToLabelMapping);
  }

  public List<ClusterBillingData> getClusterBillingDataForBatchWorkloadUid(
          List<InstanceBillingData> instanceBillingDataList, Map<String, Map<String, String>> instanceIdToLabelMapping) {
    List<ClusterBillingData> clusterBillingDataList = new ArrayList<>();
    Map<AccountClusterKey, List<InstanceBillingData>> instanceBillingDataGrouped =
            instanceBillingDataList.stream().collect(
                    Collectors.groupingBy(AccountClusterKey::getAccountClusterKeyFromInstanceData));

    log.info("Started Querying data {}", instanceBillingDataGrouped.size());
    for (AccountClusterKey accountClusterKey : instanceBillingDataGrouped.keySet()) {
      List<InstanceBillingData> instances = instanceBillingDataGrouped.get(accountClusterKey);
      Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap =
              getLabelMapForClusterGroup(instances, accountClusterKey);

      for (InstanceBillingData instanceBillingData : instances) {
        Map<String, String> labels = labelMap.get(new K8SWorkloadService.WorkloadUidCacheKey(
                instanceBillingData.getAccountId(), instanceBillingData.getClusterId(), instanceBillingData.getTaskId()));
        ClusterBillingData clusterBillingData = convertInstanceBillingDataToAVROObjects(
                instanceBillingData, labels, instanceIdToLabelMapping.get(instanceBillingData.getInstanceId()));
        clusterBillingDataList.add(clusterBillingData);
      }
    }
    log.info("Finished Querying data");

    return clusterBillingDataList;
  }

  @VisibleForTesting
  public Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> getLabelMapForClusterGroup(
          List<InstanceBillingData> instanceBillingDataList, AccountClusterKey accountClusterKey) {
    String accountId = accountClusterKey.getAccountId();
    String clusterId = accountClusterKey.getClusterId();
    Set<String> workloadUids =
            instanceBillingDataList.stream()
                    .filter(instanceBillingData
                            -> ImmutableSet.of(InstanceType.K8S_POD_FARGATE.name(), InstanceType.K8S_POD.name())
                            .contains(instanceBillingData.getInstanceType()))
                    .map(InstanceBillingData::getTaskId)
                    .collect(Collectors.toSet());

    List<K8sWorkload> workloads = new ArrayList<>();
    if (featureFlagService.isNotEnabled(FeatureName.CCM_WORKLOAD_LABELS_OPTIMISATION, accountId)) {
      if (!workloadUids.isEmpty()) {
        workloads = workloadRepository.getWorkloadByWorkloadUid(accountId, clusterId, workloadUids);
      }
    } else {
      log.info("CCM_WORKLOAD_LABELS_OPTIMISATION is enabled for this account");
    }

    Map<K8SWorkloadService.WorkloadUidCacheKey, Map<String, String>> labelMap = new HashMap<>();
    workloads.forEach(workload
            -> labelMap.put(
            new K8SWorkloadService.WorkloadUidCacheKey(accountId, clusterId, workload.getUid()), workload.getLabels()));
    return labelMap;
  }

  private void writeDataToAvro(String accountId, List<ClusterBillingData> instanceBillingDataAvro,
                               String billingDataFileName, boolean avroFileWithSchemaExists) throws IOException {
    String directoryPath = defaultParentWorkingDirectory + accountId;
    createDirectoryIfDoesNotExist(directoryPath);
    File workingDirectory = new File(directoryPath);
    File billingDataFile = new File(workingDirectory, billingDataFileName);
    DataFileWriter<ClusterBillingData> dataFileWriter = getInstanceBillingDataDataFileWriter();
    if (avroFileWithSchemaExists) {
      dataFileWriter.appendTo(billingDataFile);
    } else {
      dataFileWriter.create(ClusterBillingData.getClassSchema(), billingDataFile);
    }
    for (ClusterBillingData row : instanceBillingDataAvro) {
      dataFileWriter.append(row);
    }
    dataFileWriter.close();
  }

  private ClusterBillingData convertInstanceBillingDataToAVROObjects(
          InstanceBillingData instanceBillingData, Map<String, String> k8sWorkloadLabel, Map<String, String> labelMap) {
    String accountId = instanceBillingData.getAccountId();
    ClusterBillingData clusterBillingData = new ClusterBillingData();
    clusterBillingData.setAppid(instanceBillingData.getAppId());
    clusterBillingData.setEnvid(instanceBillingData.getEnvId());
    clusterBillingData.setRegion(instanceBillingData.getRegion());
    clusterBillingData.setServiceid(instanceBillingData.getServiceId());
    clusterBillingData.setCloudservicename(instanceBillingData.getCloudServiceName());
    clusterBillingData.setAccountid(accountId);
    clusterBillingData.setInstanceid(instanceBillingData.getInstanceId());
    clusterBillingData.setInstancename(instanceBillingData.getInstanceName());
    clusterBillingData.setClusterid(instanceBillingData.getClusterId());
    clusterBillingData.setSettingid(instanceBillingData.getSettingId());
    clusterBillingData.setLaunchtype(instanceBillingData.getLaunchType());
    clusterBillingData.setTaskid(instanceBillingData.getTaskId());
    clusterBillingData.setNamespace(instanceBillingData.getNamespace());
    clusterBillingData.setClustername(instanceBillingData.getClusterName());
    clusterBillingData.setClustertype(instanceBillingData.getClusterType());
    clusterBillingData.setInstancetype(instanceBillingData.getInstanceType());
    clusterBillingData.setWorkloadname(instanceBillingData.getWorkloadName());
    clusterBillingData.setWorkloadtype(instanceBillingData.getWorkloadType());
    clusterBillingData.setBillingaccountid(instanceBillingData.getBillingAccountId());
    clusterBillingData.setParentinstanceid(instanceBillingData.getParentInstanceId());
    clusterBillingData.setCloudproviderid(instanceBillingData.getCloudProviderId());
    clusterBillingData.setCloudprovider(instanceBillingData.getCloudProvider());
    clusterBillingData.setPricingsource(instanceBillingData.getPricingSource());

    clusterBillingData.setBillingamount(instanceBillingData.getBillingAmount().doubleValue());
    clusterBillingData.setCpubillingamount(instanceBillingData.getCpuBillingAmount().doubleValue());
    clusterBillingData.setMemorybillingamount(instanceBillingData.getMemoryBillingAmount().doubleValue());
    clusterBillingData.setIdlecost(instanceBillingData.getIdleCost().doubleValue());
    clusterBillingData.setCpuidlecost(instanceBillingData.getCpuIdleCost().doubleValue());
    clusterBillingData.setMemoryidlecost(instanceBillingData.getMemoryIdleCost().doubleValue());
    clusterBillingData.setSystemcost(instanceBillingData.getSystemCost().doubleValue());
    clusterBillingData.setCpusystemcost(instanceBillingData.getCpuSystemCost().doubleValue());
    clusterBillingData.setMemorysystemcost(instanceBillingData.getMemorySystemCost().doubleValue());
    clusterBillingData.setActualidlecost(instanceBillingData.getActualIdleCost().doubleValue());
    clusterBillingData.setCpuactualidlecost(instanceBillingData.getCpuActualIdleCost().doubleValue());
    clusterBillingData.setMemoryactualidlecost(instanceBillingData.getMemoryActualIdleCost().doubleValue());
    clusterBillingData.setNetworkcost(instanceBillingData.getNetworkCost());
    clusterBillingData.setUnallocatedcost(instanceBillingData.getUnallocatedCost().doubleValue());
    clusterBillingData.setCpuunallocatedcost(instanceBillingData.getCpuUnallocatedCost().doubleValue());
    clusterBillingData.setMemoryunallocatedcost(instanceBillingData.getMemoryUnallocatedCost().doubleValue());

    clusterBillingData.setMaxcpuutilization(instanceBillingData.getMaxCpuUtilization());
    clusterBillingData.setMaxmemoryutilization(instanceBillingData.getMaxMemoryUtilization());
    clusterBillingData.setAvgcpuutilization(instanceBillingData.getAvgCpuUtilization());
    clusterBillingData.setAvgmemoryutilization(instanceBillingData.getAvgMemoryUtilization());
    clusterBillingData.setMaxcpuutilizationvalue(instanceBillingData.getMaxCpuUtilizationValue());
    clusterBillingData.setMaxmemoryutilizationvalue(instanceBillingData.getMaxMemoryUtilizationValue());
    clusterBillingData.setAvgcpuutilizationvalue(instanceBillingData.getAvgCpuUtilizationValue());
    clusterBillingData.setAvgmemoryutilizationvalue(instanceBillingData.getAvgMemoryUtilizationValue());
    clusterBillingData.setCpurequest(instanceBillingData.getCpuRequest());
    clusterBillingData.setCpulimit(instanceBillingData.getCpuLimit());
    clusterBillingData.setMemoryrequest(instanceBillingData.getMemoryRequest());
    clusterBillingData.setMemorylimit(instanceBillingData.getMemoryLimit());
    clusterBillingData.setCpuunitseconds(instanceBillingData.getCpuUnitSeconds());
    clusterBillingData.setMemorymbseconds(instanceBillingData.getMemoryMbSeconds());
    clusterBillingData.setUsagedurationseconds(instanceBillingData.getUsageDurationSeconds());
    clusterBillingData.setEndtime(instanceBillingData.getEndTimestamp());
    clusterBillingData.setStarttime(instanceBillingData.getStartTimestamp());
    clusterBillingData.setStoragecost(getDoubleValueFromBigDecimal(instanceBillingData.getStorageBillingAmount()));
    clusterBillingData.setStorageactualidlecost(
            getDoubleValueFromBigDecimal(instanceBillingData.getStorageActualIdleCost()));
    clusterBillingData.setStorageunallocatedcost(
            getDoubleValueFromBigDecimal(instanceBillingData.getStorageUnallocatedCost()));
    clusterBillingData.setStorageutilizationvalue(instanceBillingData.getStorageUtilizationValue());
    clusterBillingData.setStoragerequest(instanceBillingData.getStorageRequest());
    clusterBillingData.setMaxstorageutilizationvalue(instanceBillingData.getMaxStorageUtilizationValue());
    clusterBillingData.setMaxstoragerequest(instanceBillingData.getMaxStorageRequest());
    clusterBillingData.setOrgIdentifier(instanceBillingData.getOrgIdentifier());
    clusterBillingData.setProjectIdentifier(instanceBillingData.getProjectIdentifier());

    if (instanceBillingData.getAppId() != null) {
      clusterBillingData.setAppname(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getAppId(), HarnessEntities.APP)));
    } else {
      clusterBillingData.setAppname(null);
    }

    if (instanceBillingData.getEnvId() != null) {
      clusterBillingData.setEnvname(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getEnvId(), HarnessEntities.ENV)));
    } else {
      clusterBillingData.setEnvname(null);
    }

    if (instanceBillingData.getServiceId() != null) {
      clusterBillingData.setServicename(entityIdToNameCache.get(
              new HarnessEntitiesService.CacheKey(instanceBillingData.getServiceId(), HarnessEntities.SERVICE)));
    } else {
      clusterBillingData.setServicename(null);
    }

    List<Label> labels = new ArrayList<>();
    Set<String> labelKeySet = new HashSet<>();
    if (ImmutableSet.of(InstanceType.K8S_POD.name(), InstanceType.K8S_POD_FARGATE.name())
            .contains(instanceBillingData.getInstanceType())) {
      if (null != k8sWorkloadLabel) {
        k8sWorkloadLabel.forEach((key, value) -> appendLabel(key, value, labelKeySet, labels));
      }
    }

    if (null != labelMap) {
      labelMap.forEach((key, value) -> appendLabel(key, value, labelKeySet, labels));
    }

    if (null != instanceBillingData.getAppId()) {
      List<HarnessTags> harnessTags = harnessTagService.getHarnessTags(accountId, instanceBillingData.getAppId());
      harnessTags.addAll(harnessTagService.getHarnessTags(accountId, instanceBillingData.getServiceId()));
      harnessTags.addAll(harnessTagService.getHarnessTags(accountId, instanceBillingData.getEnvId()));
      harnessTags.forEach(harnessTag -> appendLabel(harnessTag.getKey(), harnessTag.getValue(), labelKeySet, labels));
    }

    clusterBillingData.setLabels(Arrays.asList(labels.toArray()));
    return clusterBillingData;
  }

  @VisibleForTesting
  public void appendLabel(String key, String value, Set<String> labelKeySet, List<Label> labels) {
    Label label = new Label();
    if (!labelKeySet.contains(key)) {
      label.setKey(key);
      label.setValue(value);
      labels.add(label);
      labelKeySet.add(key);
    }
  }

  @NotNull
  private static DataFileWriter<ClusterBillingData> getInstanceBillingDataDataFileWriter() {
    DatumWriter<ClusterBillingData> userDatumWriter = new SpecificDatumWriter<>(ClusterBillingData.class);
    return new DataFileWriter<>(userDatumWriter);
  }

  private static double getDoubleValueFromBigDecimal(BigDecimal value) {
    if (value != null) {
      return value.doubleValue();
    }
    return 0D;
  }
}