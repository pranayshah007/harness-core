/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;
import static io.harness.ccm.commons.beans.InstanceType.ECS_CONTAINER_INSTANCE;
import static io.harness.ccm.commons.beans.InstanceType.ECS_TASK_EC2;
import static io.harness.ccm.commons.beans.InstanceType.ECS_TASK_FARGATE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_NODE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD_FARGATE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PV;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PVC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.reader.InstanceDataReader;
import io.harness.batch.processing.billing.service.BillingCalculationService;
import io.harness.batch.processing.billing.service.BillingData;
import io.harness.batch.processing.billing.service.UtilizationData;
import io.harness.batch.processing.billing.timeseries.data.InstanceBillingData;
import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.billing.writer.support.BillingDataGenerationValidator;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.AzureCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.GcpCustomBillingService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.beans.FeatureName;
import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.commons.service.intf.ClusterRecordService;
import io.harness.ff.FeatureFlagService;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class InstanceBillingDataTasklet implements Tasklet {
  @Autowired private BillingCalculationService billingCalculationService;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private BillingDataGenerationValidator billingDataGenerationValidator;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private AwsCustomBillingService awsCustomBillingService;
  @Autowired private AzureCustomBillingService azureCustomBillingService;
  @Autowired private GcpCustomBillingService gcpCustomBillingService;
  @Autowired private CustomBillingMetaDataService customBillingMetaDataService;
  @Autowired private InstanceDataDao instanceDataDao;
  @Autowired private BatchMainConfig config;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private ClusterRecordService eventsClusterRecordService;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private CEClusterDao ceClusterDao;

  private static final String CLAIM_REF_SEPARATOR = "/";
  private int batchSize;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());
    batchSize = config.getBatchQueryConfig().getInstanceDataBatchSize();

    Set<String> clusterIds = new HashSet<>();
    boolean isClusterQueryEnabled = isClusterIdFilterQueryEnabled(accountId);
    if (isClusterQueryEnabled) {
      clusterIds = getClusterIdsFromClusterRecords(accountId, startTime);
    }

    BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(
        chunkContext.getStepContext().getStepExecution().getJobParameters());
    // bill PV first
    List<InstanceBillingData> pvInstanceBillingDataList =
        billPVInstances(batchJobType, accountId, startTime, endTime, clusterIds, isClusterQueryEnabled);

    Map<String, InstanceBillingData> claimRefToPVInstanceBillingData =
        pvInstanceBillingDataList.stream().collect(Collectors.toMap(e
            -> e.getNamespace() + CLAIM_REF_SEPARATOR + e.getWorkloadName(),
            e -> e, (e1, e2) -> e1.getStartTimestamp() > e2.getStartTimestamp() ? e1 : e2));

    Map<String, MutableInt> pvcClaimCount =
        getPvcClaimCount(accountId, startTime, endTime, clusterIds, isClusterQueryEnabled);
    if (isClusterQueryEnabled) {
      for (String clusterId : clusterIds) {
        billAllInstances(
            accountId, startTime, endTime, batchJobType, claimRefToPVInstanceBillingData, pvcClaimCount, clusterId);
      }
    } else {
      billAllInstances(
          accountId, startTime, endTime, batchJobType, claimRefToPVInstanceBillingData, pvcClaimCount, null);
    }
    return null;
  }

  private void billAllInstances(String accountId, Instant startTime, Instant endTime, BatchJobType batchJobType,
      Map<String, InstanceBillingData> claimRefToPVInstanceBillingData, Map<String, MutableInt> pvcClaimCount,
      String clusterId) {
    List<InstanceData> instanceDataLists;
    InstanceDataReader instanceDataReader = new InstanceDataReader(instanceDataDao, accountId, clusterId,
        ImmutableList.of(
            ECS_TASK_FARGATE, ECS_TASK_EC2, ECS_CONTAINER_INSTANCE, K8S_POD, K8S_POD_FARGATE, K8S_NODE, K8S_PVC),
        startTime, endTime, batchSize);
    do {
      instanceDataLists = instanceDataReader.getNext();
      try {
        createBillingData(accountId, startTime, endTime, batchJobType, instanceDataLists,
            claimRefToPVInstanceBillingData, pvcClaimCount);
      } catch (Exception ex) {
        log.error("Exception in billing step", ex);
        throw ex;
      }
    } while (instanceDataLists.size() == batchSize);
  }

  private boolean isClusterIdFilterQueryEnabled(String accountId) {
    return featureFlagService.isEnabled(FeatureName.CCM_INSTANCE_DATA_CLUSTERID_FILTER, accountId);
  }

  @NotNull
  private Set<String> getClusterIdsFromClusterRecords(String accountId, Instant startTime) {
    List<ClusterRecord> clusterRecords = cloudToHarnessMappingService.listCeEnabledClusters(accountId);
    List<io.harness.ccm.commons.entities.ClusterRecord> eventsClusterRecords =
        eventsClusterRecordService.getByAccountId(accountId);
    List<CECluster> ceClusterList = ceClusterDao.getCECluster(accountId);
    Set<String> clusterIds = new HashSet<>();

    for (ClusterRecord clusterRecord : clusterRecords) {
      if (billingDataGenerationValidator.shouldGenerateBillingData(accountId, clusterRecord.getUuid(), startTime)) {
        clusterIds.add(clusterRecord.getUuid());
      }
    }

    for (io.harness.ccm.commons.entities.ClusterRecord eventsClusterRecord : eventsClusterRecords) {
      if (billingDataGenerationValidator.shouldGenerateBillingData(
              accountId, eventsClusterRecord.getUuid(), startTime)) {
        clusterIds.add(eventsClusterRecord.getUuid());
      }
    }

    for (CECluster ceCluster : ceClusterList) {
      if (billingDataGenerationValidator.shouldGenerateBillingData(accountId, ceCluster.getUuid(), startTime)) {
        clusterIds.add(ceCluster.getUuid());
      }
    }

    log.info("Total clusterIds: {} for accountId: {}", clusterIds.size(), accountId);
    return clusterIds;
  }

  private Map<String, MutableInt> getPvcClaimCount(
      String accountId, Instant startTime, Instant endTime, Set<String> clusterIds, boolean isClusterQueryEnabled) {
    Map<String, MutableInt> result = new HashMap<>();
    if (isClusterQueryEnabled) {
      for (String clusterId : clusterIds) {
        fetchPvcClaimCount(accountId, startTime, endTime, result, clusterId);
      }
    } else {
      fetchPvcClaimCount(accountId, startTime, endTime, result, null);
    }

    return result;
  }

  private void fetchPvcClaimCount(
      String accountId, Instant startTime, Instant endTime, Map<String, MutableInt> result, String clusterId) {
    List<InstanceData> instanceDataLists;
    InstanceDataReader instanceDataReader = new InstanceDataReader(
        instanceDataDao, accountId, clusterId, ImmutableList.of(K8S_POD), startTime, endTime, batchSize);
    do {
      // TODO change here
      instanceDataLists = instanceDataReader.getNext();
      for (InstanceData instanceData : instanceDataLists) {
        List<String> pvcClaimNames = firstNonNull(instanceData.getPvcClaimNames(), Collections.emptyList());
        String namespace = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.NAMESPACE, instanceData);
        for (String claimName : pvcClaimNames) {
          String claimRef = namespace + CLAIM_REF_SEPARATOR + claimName;
          result.computeIfAbsent(claimRef, k -> new MutableInt(0));
          result.get(claimRef).increment();
        }
      }
    } while (instanceDataLists.size() == batchSize);
  }

  private List<InstanceBillingData> billPVInstances(BatchJobType batchJobType, String accountId, Instant startTime,
      Instant endTime, Set<String> clusterIds, boolean isClusterQueryEnabled) {
    List<InstanceBillingData> instanceBillingDataList = new ArrayList<>();
    if (isClusterQueryEnabled) {
      for (String clusterId : clusterIds) {
        getPvInstanceBillingData(batchJobType, accountId, startTime, endTime, instanceBillingDataList, clusterId);
      }
    } else {
      getPvInstanceBillingData(batchJobType, accountId, startTime, endTime, instanceBillingDataList, null);
    }
    return instanceBillingDataList;
  }

  private void getPvInstanceBillingData(BatchJobType batchJobType, String accountId, Instant startTime, Instant endTime,
      List<InstanceBillingData> instanceBillingDataList, String clusterId) {
    List<InstanceData> instanceDataLists;
    InstanceDataReader instanceDataReader = new InstanceDataReader(
        instanceDataDao, accountId, clusterId, ImmutableList.of(K8S_PV), startTime, endTime, batchSize);
    do {
      instanceDataLists = instanceDataReader.getNext();
      try {
        instanceBillingDataList.addAll(createBillingData(
            accountId, startTime, endTime, batchJobType, instanceDataLists, ImmutableMap.of(), ImmutableMap.of()));
      } catch (Exception ex) {
        log.error("Exception in billing step", ex);
        throw ex;
      }
    } while (instanceDataLists.size() == batchSize);
  }

  List<InstanceBillingData> createBillingData(String accountId, Instant startTime, Instant endTime,
      BatchJobType batchJobType, List<InstanceData> instanceDataLists,
      Map<String, InstanceBillingData> claimRefToPVInstanceBillingData, Map<String, MutableInt> pvcClaimCount) {
    Set<String> parentInstanceIds = new HashSet<>();
    Instant prevStartTime = startTime.minus(3, ChronoUnit.DAYS);

    instanceDataLists.forEach(instanceData -> {
      if (null == instanceData.getActiveInstanceIterator() && null == instanceData.getUsageStopTime()) {
        instanceDataDao.updateInstanceActiveIterationTime(instanceData);
      }

      if (null != instanceData.getUsageStopTime() && instanceData.getInstanceState() == InstanceState.RUNNING
          && prevStartTime.compareTo(instanceData.getUsageStopTime()) > 0) {
        log.info("correcting instance state {} {} {} {}", instanceData.getInstanceId(),
            instanceData.getActiveInstanceIterator(), instanceData.getUsageStopTime(), instanceData.getInstanceState());
        instanceDataDao.correctInstanceStateActiveIterationTime(instanceData);
      }
    });

    Map<String, List<InstanceData>> instanceDataGroupedCluster =
        instanceDataLists.stream()
            .filter(this::validInstanceForBilling)
            .collect(Collectors.groupingBy(InstanceData::getClusterId));
    String awsDataSetId = customBillingMetaDataService.getAwsDataSetId(accountId);
    log.debug("AWS data set {}", awsDataSetId);
    if (awsDataSetId != null) {
      Set<String> resourceIds = new HashSet<>();
      Set<String> eksFargateResourceIds = new HashSet<>();
      instanceDataLists.forEach(instanceData -> {
        addParentInstanceId(instanceData, parentInstanceIds);
        String resourceId =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, instanceData);
        String cloudProvider =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData);
        if (null != resourceId && cloudProvider.equals(CloudProvider.AWS.name())) {
          resourceIds.add(resourceId);
        }

        String computeType = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
            InstanceMetaDataConstants.COMPUTE_TYPE, instanceData.getMetaData());
        if (instanceData.getInstanceType() == K8S_POD_FARGATE
            || (instanceData.getInstanceType() == K8S_POD
                && K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType))) {
          // instanceId is resourceId
          eksFargateResourceIds.add(instanceData.getInstanceId());
        }
      });
      if (isNotEmpty(resourceIds)) {
        awsCustomBillingService.updateAwsEC2BillingDataCache(
            new ArrayList<>(resourceIds), startTime, endTime, awsDataSetId, accountId);
      }

      if (isNotEmpty(eksFargateResourceIds)) {
        log.info("Updating EKS Fargate Cache for Resource Id's List of Size: {}", eksFargateResourceIds.size());
        awsCustomBillingService.updateEksFargateDataCache(
            new ArrayList<>(eksFargateResourceIds), startTime, endTime, awsDataSetId);
      }
    }

    String azureDataSetId = customBillingMetaDataService.getAzureDataSetId(accountId);
    log.debug("Azure data set {}", azureDataSetId);
    if (azureDataSetId != null) {
      Set<String> resourceIds = new HashSet<>();
      instanceDataLists.forEach(instanceData -> {
        addParentInstanceId(instanceData, parentInstanceIds);
        String resourceId =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, instanceData);
        String cloudProvider =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData);
        if (null != resourceId && cloudProvider.equals(CloudProvider.AZURE.name())) {
          resourceIds.add(resourceId.toLowerCase());
        }
      });
      if (isNotEmpty(resourceIds)) {
        azureCustomBillingService.updateAzureVMBillingDataCache(
            new ArrayList<>(resourceIds), startTime, endTime, azureDataSetId);
      }
    }

    String gcpDataSetId = customBillingMetaDataService.getGcpDataSetId(accountId);
    if (gcpDataSetId != null) {
      Set<String> resourceIds = new HashSet<>();
      instanceDataLists.forEach(instanceData -> {
        addParentInstanceId(instanceData, parentInstanceIds);
        String resourceId =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, instanceData);
        String cloudProvider =
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData);

        if (null != resourceId && cloudProvider.equals(CloudProvider.GCP.name())) {
          resourceIds.add(resourceId);
        }
      });

      if (isNotEmpty(resourceIds)) {
        gcpCustomBillingService.updateGcpVMBillingDataCache(
            new ArrayList<>(resourceIds), startTime, endTime, gcpDataSetId);
      }
    }

    List<InstanceBillingData> instanceBillingDataList = new ArrayList<>();
    instanceDataGroupedCluster.forEach((clusterRecordId, instanceDataList) -> {
      InstanceData firstInstanceData = instanceDataList.get(0);
      Map<String, UtilizationData> utilizationDataForInstances = utilizationDataService.getUtilizationDataForInstances(
          instanceDataList, startTime.toString(), endTime.toString(), firstInstanceData.getAccountId(),
          firstInstanceData.getSettingId(), firstInstanceData.getClusterId());

      List<InstanceData> parentInstanceDataList = instanceDataDao.fetchInstanceData(accountId, parentInstanceIds);
      Map<String, Double> parentInstanceActiveSecondMap =
          billingCalculationService.getInstanceActiveSeconds(parentInstanceDataList, startTime, endTime);

      for (InstanceData instanceData : instanceDataList) {
        if (instanceData.getInstanceType() != null
            && billingDataGenerationValidator.shouldGenerateBillingData(
                instanceData.getAccountId(), instanceData.getClusterId(), startTime)) {
          Double parentInstanceActiveSecond = null;
          String parentInstanceId = getParentInstanceId(instanceData);
          if (null != parentInstanceId) {
            parentInstanceActiveSecond = parentInstanceActiveSecondMap.getOrDefault(
                billingCalculationService.getInstanceClusterIdKey(parentInstanceId, instanceData.getClusterId()), null);
          }
          InstanceBillingData instanceBillingData = getInstanceBillingData(instanceData, utilizationDataForInstances,
              startTime, endTime, claimRefToPVInstanceBillingData, pvcClaimCount, parentInstanceActiveSecond);
          instanceBillingDataList.add(instanceBillingData);
        }
      }
    });

    billingDataService.create(instanceBillingDataList, batchJobType);
    return instanceBillingDataList;
  }

  private void addParentInstanceId(InstanceData instanceData, Set<String> parentInstanceIds) {
    if (ImmutableSet.of(InstanceType.K8S_POD).contains(instanceData.getInstanceType())) {
      parentInstanceIds.add(getParentInstanceId(instanceData));
    }
  }

  public boolean validInstanceForBilling(InstanceData instanceData) {
    String computeType = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
        InstanceMetaDataConstants.COMPUTE_TYPE, instanceData.getMetaData());
    boolean validInstance = true;
    if ((instanceData.getInstanceType() == null)
        || (instanceData.getInstanceType() == K8S_NODE
            && (K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType)
                || InstanceMetaDataUtils.isAzureVirtualNode(instanceData)))) {
      validInstance = false;
    }
    return validInstance;
  }

  private InstanceBillingData getInstanceBillingData(final InstanceData instanceData,
      Map<String, UtilizationData> utilizationDataForInstances, Instant startTime, Instant endTime,
      Map<String, InstanceBillingData> claimRefToPVInstanceBillingData, Map<String, MutableInt> pvcClaimCount,
      Double parentInstanceActiveSecond) {
    InstanceType instanceType = instanceData.getInstanceType();
    String computeType = InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
        InstanceMetaDataConstants.COMPUTE_TYPE, instanceData.getMetaData());

    if (instanceData.getInstanceType() == K8S_POD && K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType)) {
      instanceType = InstanceType.K8S_POD_FARGATE;
      instanceData.setInstanceType(instanceType);
    }
    UtilizationData utilizationData = utilizationDataForInstances.get(instanceData.getInstanceId());
    BillingData billingData = billingCalculationService.getInstanceBillingAmount(
        instanceData, utilizationData, parentInstanceActiveSecond, startTime, endTime);

    log.trace("Instance detail {} :: {} ", instanceData.getInstanceId(), billingData.getBillingAmountBreakup());

    HarnessServiceInfo harnessServiceInfo = getHarnessServiceInfo(instanceData);
    HarnessServiceInfoNG harnessServiceInfoNG = getHarnessServiceInfoNG(instanceData);
    String settingId = instanceData.getSettingId();
    String clusterId = instanceData.getClusterId();
    String serviceId = harnessServiceInfo.getServiceId() == null ? harnessServiceInfoNG.getServiceId()
                                                                 : harnessServiceInfo.getServiceId();
    String envId =
        harnessServiceInfo.getEnvId() == null ? harnessServiceInfoNG.getEnvId() : harnessServiceInfo.getEnvId();
    String projectIdentifier = harnessServiceInfoNG.getProjectIdentifier();
    String orgIdentifier = harnessServiceInfoNG.getOrgIdentifier();
    if (instanceType == InstanceType.EC2_INSTANCE) {
      settingId = null;
      clusterId = null;
    } else if (settingId == null) {
      settingId = clusterId;
    }
    String instanceName =
        (instanceData.getInstanceName() == null) ? instanceData.getInstanceId() : instanceData.getInstanceName();
    String region = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.REGION, instanceData);
    String namespace = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.NAMESPACE, instanceData);
    String workloadType = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.WORKLOAD_TYPE, instanceData);
    String workloadName = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.WORKLOAD_NAME, instanceData);

    Resource totalResource = instanceData.getTotalResource();
    Resource limitResource = null == instanceData.getLimitResource()
        ? Resource.builder().cpuUnits(0.0).memoryMb(0.0).build()
        : instanceData.getLimitResource();

    BigDecimal billingAmount = billingData.getBillingAmountBreakup().getBillingAmount();
    BigDecimal actualIdleCost = billingData.getIdleCostData().getIdleCost();
    BigDecimal unallocatedCost = BigDecimal.ZERO;

    BigDecimal storageBillingAmount = billingData.getBillingAmountBreakup().getStorageBillingAmount();
    BigDecimal storageActualIdleCost = billingData.getIdleCostData().getStorageIdleCost();
    BigDecimal storageUnallocatedCost = getStorageUnallocatedCost(billingData, utilizationData, instanceData);

    double maxStorageRequest = utilizationData.getMaxStorageRequestValue();
    double maxStorageUtilization = utilizationData.getMaxStorageUsageValue();

    double avgStorageRequest = utilizationData.getAvgStorageRequestValue();
    double avgStorageUtilization = utilizationData.getAvgStorageUsageValue();

    double storageMBSeconds = billingData.getStorageMbSeconds();

    if (K8S_PV == instanceType) {
      totalResource = Resource.builder().cpuUnits(0D).memoryMb(0D).build();
      workloadName = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAME, instanceData);
      namespace = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAMESPACE, instanceData);
      unallocatedCost = unallocatedCost.add(storageUnallocatedCost);
    } else if (K8S_POD == instanceType) {
      List<String> pvcClaimNames = firstNonNull(instanceData.getPvcClaimNames(), ImmutableList.of());
      for (String claimName : pvcClaimNames) {
        String claimRef = namespace + CLAIM_REF_SEPARATOR + claimName;
        InstanceBillingData storageInstanceBillingData = claimRefToPVInstanceBillingData.get(claimRef);

        if (storageInstanceBillingData != null) {
          Integer claimCount = pvcClaimCount.getOrDefault(claimRef, new MutableInt(1)).getValue();
          // only costs gets divided by claimCount to handle double spending, the requests will remain the same.
          storageBillingAmount = storageBillingAmount.add(storageInstanceBillingData.getStorageBillingAmount().divide(
              BigDecimal.valueOf(claimCount), MathContext.DECIMAL128));
          storageActualIdleCost =
              storageActualIdleCost.add(storageInstanceBillingData.getStorageActualIdleCost().divide(
                  BigDecimal.valueOf(claimCount), MathContext.DECIMAL128));
          storageUnallocatedCost =
              storageUnallocatedCost.add(storageInstanceBillingData.getStorageUnallocatedCost().divide(
                  BigDecimal.valueOf(claimCount), MathContext.DECIMAL128));

          avgStorageRequest += storageInstanceBillingData.getStorageRequest();
          avgStorageUtilization += storageInstanceBillingData.getStorageUtilizationValue();

          maxStorageRequest += storageInstanceBillingData.getMaxStorageRequest();
          maxStorageUtilization += storageInstanceBillingData.getMaxStorageUtilizationValue();

          storageMBSeconds += storageInstanceBillingData.getStorageMbSeconds();
        }
      }
      billingAmount = billingAmount.add(storageBillingAmount);
      actualIdleCost = actualIdleCost.add(storageActualIdleCost);
      unallocatedCost = unallocatedCost.add(storageUnallocatedCost);
    }

    return InstanceBillingData.builder()
        .accountId(instanceData.getAccountId())
        .settingId(settingId)
        .clusterId(clusterId)
        .instanceType(instanceType.toString())
        .billingAccountId("BILLING_ACCOUNT_ID")
        .startTimestamp(startTime.toEpochMilli())
        .endTimestamp(endTime.toEpochMilli())
        .billingAmount(billingAmount)
        .cpuBillingAmount(billingData.getBillingAmountBreakup().getCpuBillingAmount())
        .memoryBillingAmount(billingData.getBillingAmountBreakup().getMemoryBillingAmount())
        .systemCost(billingData.getSystemCostData().getSystemCost())
        .cpuSystemCost(billingData.getSystemCostData().getCpuSystemCost())
        .memorySystemCost(billingData.getSystemCostData().getMemorySystemCost())
        .idleCost(billingData.getIdleCostData().getIdleCost())
        .cpuIdleCost(billingData.getIdleCostData().getCpuIdleCost())
        .memoryIdleCost(billingData.getIdleCostData().getMemoryIdleCost())
        .usageDurationSeconds(billingData.getUsageDurationSeconds())
        .instanceId(instanceData.getInstanceId())
        .instanceName(instanceName)
        .clusterName(instanceData.getClusterName())
        .appId(harnessServiceInfo.getAppId())
        .serviceId(serviceId)
        .cloudProviderId(harnessServiceInfo.getCloudProviderId())
        .envId(envId)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .cpuUnitSeconds(billingData.getCpuUnitSeconds())
        .memoryMbSeconds(billingData.getMemoryMbSeconds())
        .parentInstanceId(getParentInstanceId(instanceData))
        .launchType(getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.LAUNCH_TYPE, instanceData))
        .taskId(getTaskIdOrWorkloadId(instanceData))
        .namespace(namespace)
        .region(firstNonNull(region, "on_prem"))
        .clusterType(getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLUSTER_TYPE, instanceData))
        .cloudProvider(getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData))
        .workloadName(workloadName)
        .workloadType(workloadType)
        .cloudServiceName(getCloudServiceName(instanceData))
        .maxCpuUtilization(utilizationData.getMaxCpuUtilization())
        .maxMemoryUtilization(utilizationData.getMaxMemoryUtilization())
        .avgCpuUtilization(utilizationData.getAvgCpuUtilization())
        .avgMemoryUtilization(utilizationData.getAvgMemoryUtilization())
        .cpuRequest(totalResource.getCpuUnits())
        .memoryRequest(totalResource.getMemoryMb())
        .cpuLimit(limitResource.getCpuUnits())
        .memoryLimit(limitResource.getMemoryMb())
        .maxCpuUtilizationValue(utilizationData.getMaxCpuUtilizationValue())
        .maxMemoryUtilizationValue(utilizationData.getMaxMemoryUtilizationValue())
        .avgCpuUtilizationValue(utilizationData.getAvgCpuUtilizationValue())
        .avgMemoryUtilizationValue(utilizationData.getAvgMemoryUtilizationValue())
        // Actual idle cost and unallocated cost for node/container will get updated by actualIdleCost job
        .actualIdleCost(actualIdleCost)
        .cpuActualIdleCost(billingData.getIdleCostData().getCpuIdleCost())
        .memoryActualIdleCost(billingData.getIdleCostData().getMemoryIdleCost())
        .unallocatedCost(unallocatedCost)
        .cpuUnallocatedCost(BigDecimal.ZERO)
        .memoryUnallocatedCost(BigDecimal.ZERO)
        .networkCost(billingData.getNetworkCost())
        .pricingSource(billingData.getPricingSource().name())
        .storageBillingAmount(storageBillingAmount)
        .storageActualIdleCost(storageActualIdleCost)
        .storageUnallocatedCost(storageUnallocatedCost)
        .storageUtilizationValue(avgStorageUtilization)
        .storageRequest(avgStorageRequest)
        .storageMbSeconds(storageMBSeconds)
        .maxStorageRequest(maxStorageRequest)
        .maxStorageUtilizationValue(maxStorageUtilization)
        .build();
  }

  private String getTaskIdOrWorkloadId(InstanceData instanceData) {
    String taskId = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.TASK_ID, instanceData);
    if (ImmutableSet.of(InstanceType.K8S_POD, InstanceType.K8S_POD_FARGATE).contains(instanceData.getInstanceType())) {
      taskId = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.WORKLOAD_ID, instanceData);
    }
    return taskId;
  }

  private BigDecimal getStorageUnallocatedCost(
      BillingData billingData, UtilizationData utilizationData, InstanceData instanceData) {
    if (K8S_PV == instanceData.getInstanceType()) {
      BigDecimal storageUnallocatedFraction = BigDecimal.ZERO;
      if (instanceData.getStorageResource() != null && instanceData.getStorageResource().getCapacity() > 0
          && utilizationData.getAvgStorageRequestValue() > 0) {
        BigDecimal capacityFromInstanceData = BigDecimal.valueOf(instanceData.getStorageResource().getCapacity());
        storageUnallocatedFraction =
            capacityFromInstanceData.subtract(BigDecimal.valueOf(utilizationData.getAvgStorageRequestValue()))
                .divide(capacityFromInstanceData, MathContext.DECIMAL128);
      }
      if (storageUnallocatedFraction.compareTo(BigDecimal.ZERO) < 0) {
        log.debug("-ve storageUnallocatedCost, Request:{}/Capacity:{} {}", utilizationData.getAvgStorageRequestValue(),
            utilizationData.getAvgStorageCapacityValue(), instanceData.toString());
        return BigDecimal.ZERO;
      }
      BigDecimal storageUnallocatedCost =
          billingData.getBillingAmountBreakup().getStorageBillingAmount().multiply(storageUnallocatedFraction);
      if (storageUnallocatedCost.compareTo(billingData.getBillingAmountBreakup().getStorageBillingAmount()) > 0) {
        return billingData.getBillingAmountBreakup().getStorageBillingAmount();
      }
      return storageUnallocatedCost;
    }
    return BigDecimal.ZERO;
  }

  String getParentInstanceId(InstanceData instanceData) {
    String actualParentResourceId =
        getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, instanceData);
    if (null == actualParentResourceId
        && ImmutableSet.of(InstanceType.K8S_POD, InstanceType.K8S_POD_FARGATE)
               .contains(instanceData.getInstanceType())) {
      String parentResourceId =
          getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.PARENT_RESOURCE_ID, instanceData);
      if (null != parentResourceId) {
        InstanceData parentInstanceData = instanceDataService.fetchInstanceDataWithName(
            instanceData.getAccountId(), instanceData.getClusterId(), parentResourceId, Instant.now().toEpochMilli());
        if (null != parentInstanceData) {
          return parentInstanceData.getInstanceId();
        } else {
          return parentResourceId;
        }
      }
    }
    return actualParentResourceId;
  }

  String getCloudServiceName(InstanceData instanceData) {
    String cloudServiceName =
        getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.ECS_SERVICE_NAME, instanceData);
    InstanceType instanceType = instanceData.getInstanceType();
    if (null == cloudServiceName && ImmutableSet.of(ECS_TASK_FARGATE, ECS_TASK_EC2).contains(instanceType)) {
      cloudServiceName = "none";
    }
    return cloudServiceName;
  }

  HarnessServiceInfo getHarnessServiceInfo(InstanceData instanceData) {
    if (null != instanceData.getHarnessServiceInfo()) {
      return instanceData.getHarnessServiceInfo();
    }
    return new HarnessServiceInfo(null, null, null, null, null, null);
  }

  HarnessServiceInfoNG getHarnessServiceInfoNG(InstanceData instanceData) {
    if (null != instanceData.getHarnessServiceInfoNG()) {
      return instanceData.getHarnessServiceInfoNG();
    }
    return new HarnessServiceInfoNG(null, null, null, null, null);
  }
}
