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
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.configuration.DeployMode;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  @Autowired private ClickHouseClusterDataService clusterDataService;
  @Autowired private ClickHouseConfig clickHouseConfig;
  private static final String defaultParentWorkingDirectory = "./avro/";
  private static final String defaultBillingDataFileNameDaily = "billing_data_%s_%s_%s.avro";
  private static final String defaultBillingDataFileNameHourly = "billing_data_hourly_%s_%s_%s_%s.avro";
  private static final String gcsObjectNameFormat = "%s/%s";
  public static final long CACHE_SIZE = 10000;

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
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

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

    log.info("ClusterDataToBigQuery Job- isDeploymentOnPrem: {} and isClickHouseEnabled: {}", config.getDeployMode(),
        config.isClickHouseEnabled());

    if (DeployMode.isOnPrem(config.getDeployMode().name()) && config.isClickHouseEnabled()) {
      handleDataForClickHouse(batchJobType, jobConstants, batchSize, billingDataReader, zdt, clusterDataTableName,
          clusterDataAggregatedTableName);
    } else {
      handleDataForBigQuery(batchJobType, jobConstants, batchSize, billingDataReader, billingDataFileName);
    }
    return null;
  }

  private void handleDataForBigQuery(BatchJobType batchJobType, JobConstants jobConstants, int batchSize,
      BillingDataReader billingDataReader, String billingDataFileName) throws IOException {
    List<InstanceBillingData> instanceBillingDataList;
    boolean avroFileWithSchemaExists = false;
    do {
      instanceBillingDataList = billingDataReader.getNext();
      List<ClusterBillingData> clusterBillingDataList =
          getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);
      log.info("clusterBillingDataList size: {}", clusterBillingDataList.size());
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
  }

  private void handleDataForClickHouse(BatchJobType batchJobType, JobConstants jobConstants, int batchSize,
      BillingDataReader billingDataReader, ZonedDateTime zdt, String clusterDataTableName,
      String clusterDataAggregatedTableName) throws Exception {
    List<InstanceBillingData> instanceBillingDataList;
    clusterDataService.createClickHouseDataBaseIfNotExist();
    clusterDataService.createTableAndDeleteExistingDataFromClickHouse(jobConstants, clusterDataTableName);
    do {
      instanceBillingDataList = billingDataReader.getNext();
      List<ClusterBillingData> clusterBillingDataList =
          getClusterBillingDataForBatch(jobConstants.getAccountId(), batchJobType, instanceBillingDataList);

      log.info("clusterBillingDataList size: {}", clusterBillingDataList.size());
      ingestClusterDataTable(clusterDataTableName, clusterBillingDataList);
      log.info("Ingestion Completed for ClusterDataTable");
    } while (instanceBillingDataList.size() == batchSize);

    deleteExistingAndIngestToClickHouse(
        jobConstants, zdt, clusterDataTableName, clusterDataAggregatedTableName, batchJobType);
  }

  private void deleteExistingAndIngestToClickHouse(JobConstants jobConstants, ZonedDateTime zdt,
      String clusterDataTableName, String clusterDataAggregatedTableName, BatchJobType batchJobType) throws Exception {
    if (batchJobType != BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY) {
      clusterDataService.processUnifiedTableToCLickHouse(zdt, clusterDataTableName);
      ingestUnifiedTable(zdt, clusterDataTableName);
      log.info("Ingestion Completed for UnifiedTable");
    }

    clusterDataService.processAggregatedTable(jobConstants, clusterDataAggregatedTableName);
    ingestAggregatedTable(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);
    log.info("Ingestion Completed for AggregatedTable");

    if (batchJobType != BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY) {
      clusterDataService.processCostAggregatedData(jobConstants, zdt);
      ingestCostAggregatedTable(zdt);
      log.info("Ingestion Completed for CostAggregatedTable");
    }
  }

  private void ingestCostAggregatedTable(ZonedDateTime zdt) throws Exception {
    clusterDataService.ingestToCostAggregatedTable(zdt.toLocalDate().toString());
  }

  private void ingestAggregatedTable(
      JobConstants jobConstants, String clusterDataTableName, String clusterDataAggregatedTableName) throws Exception {
    clusterDataService.ingestAggregatedData(jobConstants, clusterDataTableName, clusterDataAggregatedTableName);
  }

  private void ingestUnifiedTable(ZonedDateTime zdt, String clusterDataTableName) throws Exception {
    clusterDataService.ingestIntoUnifiedTable(zdt, clusterDataTableName);
  }

  private void ingestClusterDataTable(String clusterDataTableName, List<ClusterBillingData> allClusterBillingData)
      throws Exception {
    clusterDataService.ingestClusterData(clusterDataTableName, allClusterBillingData);
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
