/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet;

import static io.harness.batch.processing.ccm.UtilizationInstanceType.ECS_CLUSTER;
import static io.harness.batch.processing.ccm.UtilizationInstanceType.ECS_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

import static java.util.function.Function.identity;

import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsEC2HelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.EcsMetricClient;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.EcsUtilizationData;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.MetricValue;
import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.batch.processing.support.ActiveInstanceIterator;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.commons.entities.ecs.ECSService;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.ManagedExecutorService;

import software.wings.api.ContainerDeploymentInfoWithNames;
import software.wings.api.DeploymentSummary;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ecs.model.Attribute;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.Deployment;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Tag;
import com.amazonaws.services.ecs.model.Task;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

@Slf4j
@Singleton
public class AwsECSClusterDataSyncTasklet implements Tasklet {
  @Autowired private CEClusterDao ceClusterDao;
  @Autowired private EcsMetricClient ecsMetricClient;
  @Autowired private InstanceDataDao instanceDataDao;
  @Autowired private ECSServiceDao ecsServiceDao;
  @Autowired private CECloudAccountDao ceCloudAccountDao;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  @Autowired private AwsECSHelperService awsECSHelperService;
  @Autowired private AwsEC2HelperService awsEC2HelperService;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private InstanceResourceService instanceResourceService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  private final ExecutorService ecsSyncClusterExecutor = new ManagedExecutorService(Executors.newWorkStealingPool(3));

  private static final String ECS_OS_TYPE = "ecs.os-type";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    List<CECluster> ceClusters = ceClusterDao.getCECluster(accountId);
    if (CollectionUtils.isEmpty(ceClusters)) {
      return null;
    }
    Map<String, AwsCrossAccountAttributes> infraAccCrossArnMap = getCrossAccountAttributes(accountId);

    List<Callable<Void>> tasks = new ArrayList<>();

    for (CECluster ceCluster : ceClusters) {
      tasks.add(() -> {
        Thread.currentThread().setName("sync-cluster-" + ceCluster.getUuid());

        if (infraAccCrossArnMap.containsKey(ceCluster.getInfraAccountId())) {
          AwsCrossAccountAttributes awsCrossArn = infraAccCrossArnMap.get(ceCluster.getInfraAccountId());

          log.info("Sync for cluster {}", ceCluster.getUuid());
          syncECSClusterData(accountId, awsCrossArn, ceCluster, startTime);
          lastReceivedPublishedMessageDao.upsert(accountId, ceCluster.getUuid());
        }

        return null;
      });
    }

    final List<Future<Void>> futures = ecsSyncClusterExecutor.invokeAll(tasks);

    // wait for tasks to finish before exiting the tasklet execution
    for (Future<Void> f : futures) {
      try {
        f.get();
      } catch (InterruptedException e) {
        throw new Exception("failed to sync ecs clusters", e);
      }
    }
    return null;
  }

  private void syncECSClusterData(
      String accountId, AwsCrossAccountAttributes awsCrossAccountAttributes, CECluster ceCluster, Instant startTime) {
    List<ContainerInstance> containerInstances = listContainerInstances(awsCrossAccountAttributes, ceCluster);
    log.debug("cluster {} Container instances {}", containerInstances, ceCluster);

    updateContainerInstance(accountId, ceCluster, awsCrossAccountAttributes, containerInstances);
    List<Service> services = listServices(awsCrossAccountAttributes, ceCluster.getClusterArn(), ceCluster.getRegion());
    Map<String, String> deploymentIdServiceMap = getDeploymentIdsForService(services);
    Map<String, List<Tag>> serviceArnTagsMap = getServiceArnTagsMap(services);
    List<Task> tasks = listTask(awsCrossAccountAttributes, ceCluster.getClusterArn(), ceCluster.getRegion());
    log.debug("Task list {}", tasks);
    updateTasks(accountId, ceCluster, tasks, deploymentIdServiceMap, serviceArnTagsMap, startTime);
    publishUtilizationMetrics(awsCrossAccountAttributes, ceCluster, services);
  }

  @VisibleForTesting
  void publishUtilizationMetrics(
      AwsCrossAccountAttributes awsCrossAccountAttributes, CECluster ceCluster, List<Service> services) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    String clusterName = ceCluster.getClusterName();
    Cluster cluster = new Cluster().withClusterName(clusterName).withClusterArn(ceCluster.getClusterArn());

    List<EcsUtilizationData> utilizationMetrics =
        ecsMetricClient.getUtilizationMetrics(awsCrossAccountAttributes, Date.from(now.minus(2, ChronoUnit.HOURS)),
            Date.from(now.minus(1, ChronoUnit.HOURS)), cluster, services, ceCluster);
    updateUtilData(ceCluster, utilizationMetrics);
  }

  private void updateUtilData(CECluster ceCluster, List<EcsUtilizationData> utilizationMetricsList) {
    List<InstanceUtilizationData> instanceUtilizationDataList = new ArrayList<>();
    String accountId = ceCluster.getAccountId();
    utilizationMetricsList.forEach(utilizationMetrics -> {
      String serviceArn = utilizationMetrics.getServiceArn();
      String clusterId = utilizationMetrics.getClusterId();
      String settingId = utilizationMetrics.getSettingId();
      String instanceId;
      String instanceType;
      if (null == serviceArn) {
        instanceId = utilizationMetrics.getClusterArn();
        instanceType = ECS_CLUSTER;
      } else {
        instanceId = serviceArn;
        instanceType = ECS_SERVICE;
      }
      // Initialising List of Metrics to handle Utilization Metrics Downtime (Ideally this will be of size 1)
      // We do not need a Default value as such a scenario will never exist, if there is no data. It will not be
      // inserted to DB.
      List<Double> cpuUtilizationAvgList = new ArrayList<>();
      List<Double> cpuUtilizationMaxList = new ArrayList<>();
      List<Double> memoryUtilizationAvgList = new ArrayList<>();
      List<Double> memoryUtilizationMaxList = new ArrayList<>();
      List<Date> startTimestampList = new ArrayList<>();
      int metricsListSize = 0;

      for (MetricValue utilizationMetric : utilizationMetrics.getMetricValues()) {
        // Assumption that size of all the metrics and timestamps will be same across the 4 metrics
        startTimestampList = utilizationMetric.getTimestamps();
        List<Double> metricsList = utilizationMetric.getValues();
        metricsListSize = metricsList.size();

        switch (utilizationMetric.getStatistic()) {
          case "Maximum":
            switch (utilizationMetric.getMetricName()) {
              case "MemoryUtilization":
                memoryUtilizationMaxList = metricsList;
                break;
              case "CPUUtilization":
                cpuUtilizationMaxList = metricsList;
                break;
              default:
                throw new InvalidRequestException("Invalid Utilization metric name");
            }
            break;
          case "Average":
            switch (utilizationMetric.getMetricName()) {
              case "MemoryUtilization":
                memoryUtilizationAvgList = metricsList;
                break;
              case "CPUUtilization":
                cpuUtilizationAvgList = metricsList;
                break;
              default:
                throw new InvalidRequestException("Invalid Utilization metric name");
            }
            break;
          default:
            throw new InvalidRequestException("Invalid Utilization metric Statistic");
        }
      }

      // POJO and insertion to DB
      for (int metricIndex = 0; metricIndex < metricsListSize; metricIndex++) {
        long startTime = startTimestampList.get(metricIndex).toInstant().toEpochMilli();
        long oneHourMillis = Duration.ofHours(1).toMillis();

        if (null == settingId) {
          settingId = clusterId;
        }
        InstanceUtilizationData utilizationData =
            InstanceUtilizationData.builder()
                .accountId(accountId)
                .instanceId(instanceId)
                .instanceType(instanceType)
                .clusterId(clusterId)
                .settingId(settingId)
                .cpuUtilizationMax(getScaledUtilValue(cpuUtilizationMaxList.get(metricIndex)))
                .cpuUtilizationAvg(getScaledUtilValue(cpuUtilizationAvgList.get(metricIndex)))
                .memoryUtilizationMax(getScaledUtilValue(memoryUtilizationMaxList.get(metricIndex)))
                .memoryUtilizationAvg(getScaledUtilValue(memoryUtilizationAvgList.get(metricIndex)))
                .startTimestamp(startTime)
                .endTimestamp(startTime + oneHourMillis)
                .build();

        instanceUtilizationDataList.add(utilizationData);
      }
    });

    utilizationDataService.create(instanceUtilizationDataList);
  }

  private double getScaledUtilValue(double value) {
    return value / 100;
  }

  @VisibleForTesting
  void updateTasks(String accountId, CECluster ceCluster, List<Task> tasks, Map<String, String> deploymentIdServiceMap,
      Map<String, List<Tag>> serviceArnTagsMap, Instant startTime) {
    Instant stopTime = Instant.now();
    String clusterId = ceCluster.getUuid();
    String settingId = ceCluster.getParentAccountSettingId();
    String region = ceCluster.getRegion();
    String awsAccountId = ceCluster.getInfraAccountId();
    Map<String, InstanceData> activeInstanceDataMap = getInstanceDataMap(accountId, clusterId,
        ImmutableList.of(InstanceType.ECS_TASK_FARGATE, InstanceType.ECS_TASK_EC2), InstanceState.RUNNING);
    Set<String> activeTaskIds = activeInstanceDataMap.keySet();
    Set<String> activeTaskArns =
        tasks.stream().map(task -> getIdFromArn(task.getTaskArn())).collect(Collectors.toSet());
    SetView<String> inactiveTaskArns = Sets.difference(activeTaskIds, activeTaskArns);
    activeInstanceDataMap.values()
        .stream()
        .filter(instanceData -> inactiveTaskArns.contains(instanceData.getInstanceId()))
        .forEach(instanceData -> instanceDataDao.updateInstanceStopTime(instanceData, stopTime));

    Set<String> containerInstanceArn = tasks.stream()
                                           .filter(task -> null != task.getContainerInstanceArn())
                                           .map(task -> getIdFromArn(task.getContainerInstanceArn()))
                                           .collect(Collectors.toSet());
    Map<String, InstanceData> instanceDataMap = getInstanceDataMap(accountId, containerInstanceArn);
    tasks.stream()
        .filter(task -> null != task.getPullStartedAt() && listTaskDesiredStatus().contains(task.getDesiredStatus()))
        .forEach(task -> {
          String taskId = getIdFromArn(task.getTaskArn());
          if (null != activeInstanceDataMap.get(taskId)) {
            InstanceData instanceData = activeInstanceDataMap.get(taskId);
            ECSService ecsService =
                getECSService(accountId, awsAccountId, clusterId, task, deploymentIdServiceMap, getLaunchType(task));
            boolean updated = updateInstanceStopTimeForTask(instanceData, task);
            boolean updatedLabels = false;
            // Labels will only be updated once in a day - If we don't have this updating labels every hour is costly
            if (startTime.atZone(ZoneOffset.UTC).getHour() == 1) {
              updatedLabels =
                  updateLabels(instanceData, ecsService, task, ceCluster, serviceArnTagsMap, deploymentIdServiceMap);
            }
            if (updated || updatedLabels) {
              instanceDataDao.create(instanceData);
              ecsServiceDao.create(ecsService);
            }
          } else {
            String clusterName = getIdFromArn(task.getClusterArn());
            InstanceType instanceType = getInstanceType(task);
            double memory = Integer.parseInt(task.getMemory());
            double cpu = Integer.parseInt(task.getCpu());
            Resource resource = Resource.builder().cpuUnits(cpu).memoryMb(memory).build();
            Map<String, String> metaData = new HashMap<>();
            InstanceData containerInstantData = null;
            if (null != task.getContainerInstanceArn()) {
              containerInstantData = instanceDataMap.get(getIdFromArn(task.getContainerInstanceArn()));
            }

            if (InstanceType.ECS_TASK_EC2 == instanceType && null == containerInstantData) {
              return;
            } else if (InstanceType.ECS_TASK_EC2 == instanceType) {
              String containerInstanceId = getIdFromArn(task.getContainerInstanceArn());

              metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY,
                  containerInstantData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_FAMILY));
              metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY,
                  containerInstantData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_CATEGORY));
              metaData.put(InstanceMetaDataConstants.OPERATING_SYSTEM,
                  containerInstantData.getMetaData().get(InstanceMetaDataConstants.OPERATING_SYSTEM));
              metaData.put(InstanceMetaDataConstants.CONTAINER_INSTANCE_ARN, containerInstanceId);
              metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_CPU,
                  String.valueOf(containerInstantData.getTotalResource().getCpuUnits()));
              metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY,
                  String.valueOf(containerInstantData.getTotalResource().getMemoryMb()));
              metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, containerInstanceId);
              metaData.put(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, containerInstanceId);
            } else {
              metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY,
                  InstanceMetaDataUtils.getInstanceCategoryECSFargate(task.getCapacityProviderName()).name());
            }
            metaData.put(InstanceMetaDataConstants.TASK_ID, taskId);
            metaData.put(InstanceMetaDataConstants.REGION, region);
            metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
            metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
            metaData.put(InstanceMetaDataConstants.LAUNCH_TYPE, task.getLaunchType());

            HarnessServiceInfo harnessServiceInfo = null;
            if (serviceExistsForTask(task, deploymentIdServiceMap)) {
              String serviceArn = deploymentIdServiceMap.get(task.getStartedBy());
              String serviceName = getIdFromArn(serviceArn);
              metaData.put(InstanceMetaDataConstants.ECS_SERVICE_NAME, serviceName);
              metaData.put(InstanceMetaDataConstants.ECS_SERVICE_ARN, serviceArn);
              harnessServiceInfo = getHarnessServiceInfo(accountId, clusterName, serviceName);
            }

            Instant startInstant = task.getPullStartedAt().toInstant();
            InstanceData instanceData =
                InstanceData.builder()
                    .accountId(accountId)
                    .instanceId(taskId)
                    .clusterName(clusterName)
                    .clusterId(clusterId)
                    .settingId(settingId)
                    .instanceType(instanceType)
                    .usageStartTime(startInstant)
                    .activeInstanceIterator(ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(startInstant))
                    .instanceState(InstanceState.RUNNING)
                    .totalResource(resource)
                    .allocatableResource(resource)
                    .metaData(metaData)
                    .harnessServiceInfo(harnessServiceInfo)
                    .build();
            ECSService ecsService =
                getECSService(accountId, awsAccountId, clusterId, task, deploymentIdServiceMap, getLaunchType(task));

            updateInstanceStopTimeForTask(instanceData, task);
            updateLabels(instanceData, ecsService, task, ceCluster, serviceArnTagsMap, deploymentIdServiceMap);
            log.debug("Creating task {} ", taskId);
            instanceDataService.create(instanceData);
            ecsServiceDao.create(ecsService);
          }
        });
  }

  private boolean updateLabels(InstanceData instanceData, ECSService ecsService, Task task, CECluster ceCluster,
      Map<String, List<Tag>> serviceArnTagsMap, Map<String, String> deploymentIdServiceMap) {
    Map<String, String> labels = new HashMap<>();
    // Add Cluster Tags to the Task Labels
    if (isNotEmpty(ceCluster.getLabels())) {
      labels.putAll(ceCluster.getLabels());
    }
    // Add Task Level Tags
    for (Tag tag : task.getTags()) {
      labels.put(encode(tag.getKey()), tag.getValue());
    }

    Map<String, String> serviceLabels = Collections.emptyMap();
    if (serviceExistsForTask(task, deploymentIdServiceMap)) {
      String serviceArn = deploymentIdServiceMap.get(task.getStartedBy());
      // Fetch Service Tags and add to the Task Labels
      List<Tag> serviceTagList = serviceArnTagsMap.get(serviceArn);
      if (isNotEmpty(serviceTagList)) {
        serviceLabels = new HashMap<>();
        for (Tag tag : serviceTagList) {
          serviceLabels.put(encode(tag.getKey()), tag.getValue());
        }
        labels.putAll(serviceLabels);
      }
    }
    instanceData.setLabels(labels);
    if (null != ecsService) {
      ecsService.setLabels(serviceLabels);
    }
    return true;
  }

  private String encode(String decoded) {
    return decoded.replace('.', '~');
  }

  private boolean updateInstanceStopTimeForTask(InstanceData instanceData, Task task) {
    if (null != task.getStoppedAt()) {
      Instant usageStopInstant = task.getStoppedAt().toInstant();
      instanceData.setUsageStopTime(usageStopInstant);
      instanceData.setActiveInstanceIterator(
          ActiveInstanceIterator.getActiveInstanceIteratorFromStopTime(usageStopInstant));
      instanceData.setInstanceState(InstanceState.STOPPED);
      instanceData.setTtl(new Date(usageStopInstant.plus(30, ChronoUnit.DAYS).toEpochMilli()));
      return true;
    }
    return false;
  }

  ECSService getECSService(String accountId, String awsAccountId, String clusterId, Task task,
      Map<String, String> deploymentIdServiceMap, LaunchType launchType) {
    if (!serviceExistsForTask(task, deploymentIdServiceMap)) {
      return null;
    }
    String serviceArn = deploymentIdServiceMap.get(task.getStartedBy());
    String serviceName = getIdFromArn(serviceArn);
    double memory = Integer.parseInt(task.getMemory());
    double cpu = Integer.parseInt(task.getCpu());
    Resource resource = Resource.builder().cpuUnits(cpu).memoryMb(memory).build();
    return ECSService.builder()
        .accountId(accountId)
        .awsAccountId(awsAccountId)
        .clusterId(clusterId)
        .serviceArn(serviceArn)
        .serviceName(serviceName)
        .launchType(launchType)
        .resource(resource)
        .labels(Collections.emptyMap())
        .build();
  }

  @VisibleForTesting
  HarnessServiceInfo getHarnessServiceInfo(String accountId, String clusterName, String serviceName) {
    ContainerDeploymentKey containerDeploymentKey =
        ContainerDeploymentKey.builder().containerServiceName(serviceName).build();
    ContainerDeploymentInfoWithNames containerDeploymentInfoWithNames =
        ContainerDeploymentInfoWithNames.builder().clusterName(clusterName).containerSvcName(serviceName).build();
    DeploymentSummary deploymentSummary = DeploymentSummary.builder()
                                              .accountId(accountId)
                                              .containerDeploymentKey(containerDeploymentKey)
                                              .deploymentInfo(containerDeploymentInfoWithNames)
                                              .build();
    Optional<HarnessServiceInfo> harnessServiceInfo =
        cloudToHarnessMappingService.getHarnessServiceInfo(deploymentSummary);
    return harnessServiceInfo.orElse(null);
  }

  private boolean serviceExistsForTask(Task task, Map<String, String> deploymentIdServiceMap) {
    return null != task.getStartedBy() && null != deploymentIdServiceMap.get(task.getStartedBy());
  }

  private Map<String, InstanceData> getInstanceDataMap(String accountId, Set<String> instanceIds) {
    return getInstanceDataMap(instanceDataDao.fetchInstanceData(accountId, instanceIds));
  }

  private Map<String, InstanceData> getInstanceDataMap(
      String accountId, String clusterId, List<InstanceType> instanceTypes, InstanceState instanceState) {
    List<InstanceData> activeInstanceData =
        instanceDataDao.fetchClusterActiveInstanceData(accountId, clusterId, instanceTypes, instanceState);
    return getInstanceDataMap(activeInstanceData);
  }

  private Map<String, InstanceData> getInstanceDataMap(List<InstanceData> instanceDataList) {
    return instanceDataList.stream().collect(
        Collectors.toMap(InstanceData::getInstanceId, Function.identity(), (existing, replacement) -> existing));
  }

  private InstanceType getInstanceType(Task task) {
    if (task.getLaunchType().equals(LaunchType.EC2.toString())) {
      return InstanceType.ECS_TASK_EC2;
    } else if (task.getLaunchType().equals(LaunchType.FARGATE.toString())) {
      return InstanceType.ECS_TASK_FARGATE;
    }
    return null;
  }

  private LaunchType getLaunchType(Task task) {
    if (task.getLaunchType().equals(LaunchType.EC2.toString())) {
      return LaunchType.EC2;
    } else if (task.getLaunchType().equals(LaunchType.FARGATE.toString())) {
      return LaunchType.FARGATE;
    } else if (task.getLaunchType().equals(LaunchType.EXTERNAL.toString())) {
      return LaunchType.EXTERNAL;
    }
    return null;
  }

  // TODO check for stop time
  @VisibleForTesting
  void updateContainerInstance(String accountId, CECluster ceCluster,
      AwsCrossAccountAttributes awsCrossAccountAttributes, List<ContainerInstance> containerInstances) {
    Instant stopTime = Instant.now();
    String clusterId = ceCluster.getUuid();
    String clusterArn = ceCluster.getClusterArn();
    String settingId = ceCluster.getParentAccountSettingId();
    Map<String, InstanceData> activeInstanceDataMap = getInstanceDataMap(
        accountId, clusterId, ImmutableList.of(InstanceType.ECS_CONTAINER_INSTANCE), InstanceState.RUNNING);
    Set<String> activeInstanceIds = activeInstanceDataMap.keySet();
    Set<String> activeInstanceArns =
        containerInstances.stream()
            .map(containerInstance -> getIdFromArn(containerInstance.getContainerInstanceArn()))
            .collect(Collectors.toSet());
    SetView<String> inactiveInstanceArns = Sets.difference(activeInstanceIds, activeInstanceArns);

    activeInstanceDataMap.values()
        .stream()
        .filter(instanceData -> inactiveInstanceArns.contains(instanceData.getInstanceId()))
        .forEach(instanceData -> instanceDataDao.updateInstanceStopTime(instanceData, stopTime));

    Set<String> instanceIds = fetchEc2InstanceIds(containerInstances);
    Map<String, Instance> instanceMap = listEc2Instances(awsCrossAccountAttributes, ceCluster.getRegion(), instanceIds);
    containerInstances.stream()
        .filter(containerInstance
            -> instanceMap.get(containerInstance.getEc2InstanceId()) != null
                && listContainerInstanceStatus().contains(containerInstance.getStatus()))
        .forEach(containerInstance -> {
          String containerInstanceId = getIdFromArn(containerInstance.getContainerInstanceArn());
          if (null == activeInstanceDataMap.get(containerInstanceId)) {
            String ec2InstanceId = containerInstance.getEc2InstanceId();
            Instance instance = instanceMap.get(ec2InstanceId);

            List<com.amazonaws.services.ecs.model.Resource> registeredResources =
                containerInstance.getRegisteredResources();
            Map<String, com.amazonaws.services.ecs.model.Resource> resourceMap = registeredResources.stream().collect(
                Collectors.toMap(com.amazonaws.services.ecs.model.Resource::getName, identity()));

            double memory = resourceMap.get("MEMORY").getIntegerValue();
            double cpu = resourceMap.get("CPU").getIntegerValue();

            Map<String, Attribute> attributeMap =
                containerInstance.getAttributes().stream().collect(Collectors.toMap(Attribute::getName, identity()));
            Attribute attribute = attributeMap.get(ECS_OS_TYPE);

            Map<String, String> metaData = new HashMap<>();
            metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, instance.getInstanceType());
            metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY, getInstanceCategory(instance).name());
            metaData.put(InstanceMetaDataConstants.EC2_INSTANCE_ID, ec2InstanceId);
            metaData.put(InstanceMetaDataConstants.OPERATING_SYSTEM, attribute.getValue());
            metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
            metaData.put(InstanceMetaDataConstants.REGION, ceCluster.getRegion());
            metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.ECS.name());
            metaData.put(InstanceMetaDataConstants.CLUSTER_ARN, clusterArn);
            metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, ec2InstanceId);
            metaData.put(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, ec2InstanceId);
            Resource resource = Resource.builder().cpuUnits(cpu).memoryMb(memory).build();

            Resource totalResource = instanceResourceService.getComputeVMResource(
                InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(
                    InstanceMetaDataConstants.INSTANCE_FAMILY, metaData),
                InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.REGION, metaData),
                CloudProvider.AWS);
            Map<String, String> labels = new HashMap<>();
            if (isNotEmpty(ceCluster.getLabels())) {
              labels.putAll(ceCluster.getLabels());
            }
            for (Tag tag : containerInstance.getTags()) {
              labels.put(encode(tag.getKey()), tag.getValue());
            }
            if (null != totalResource) {
              Instant startInstant = containerInstance.getRegisteredAt().toInstant();
              InstanceData instanceData =
                  InstanceData.builder()
                      .accountId(accountId)
                      .instanceId(containerInstanceId)
                      .clusterName(getIdFromArn(clusterArn))
                      .clusterId(clusterId)
                      .settingId(settingId)
                      .instanceType(InstanceType.ECS_CONTAINER_INSTANCE)
                      .instanceState(InstanceState.RUNNING)
                      .usageStartTime(startInstant)
                      .activeInstanceIterator(
                          ActiveInstanceIterator.getActiveInstanceIteratorFromStartTime(startInstant))
                      .totalResource(totalResource)
                      .allocatableResource(resource)
                      .metaData(metaData)
                      .labels(labels)
                      .build();
              log.debug("Creating container instance {} ", containerInstanceId);
              instanceDataService.create(instanceData);
            }
          }
        });
  }

  private InstanceCategory getInstanceCategory(Instance instance) {
    if (null != instance.getSpotInstanceRequestId()) {
      return InstanceCategory.SPOT;
    } else if (null != instance.getCapacityReservationId()) {
      return InstanceCategory.RESERVED;
    }
    return InstanceCategory.ON_DEMAND;
  }

  private String getIdFromArn(String arn) {
    return arn.substring(arn.lastIndexOf('/') + 1);
  }

  private List<Task> listTask(AwsCrossAccountAttributes awsCrossAccountAttributes, String clusterName, String region) {
    return awsECSHelperService.listTasksForService(awsCrossAccountAttributes, region, clusterName, null, null);
  }

  private Map<String, String> getDeploymentIdsForService(List<Service> services) {
    Map<String, List<String>> deploymentIdsForService =
        services.stream().collect(Collectors.toMap(Service::getServiceArn,
            service -> service.getDeployments().stream().map(Deployment::getId).collect(Collectors.toList())));

    return deploymentIdsForService.entrySet().stream().collect(
        HashMap::new, (m, v) -> v.getValue().forEach(k -> m.put(k, v.getKey())), Map::putAll);
  }

  private Map<String, List<Tag>> getServiceArnTagsMap(List<Service> services) {
    return services.stream().collect(Collectors.toMap(Service::getServiceArn, Service::getTags));
  }

  private List<String> listTaskDesiredStatus() {
    return new ArrayList<>(Arrays.asList(DesiredStatus.RUNNING.toString(), DesiredStatus.STOPPED.toString()));
  }

  private List<Service> listServices(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String clusterName, String region) {
    return awsECSHelperService.listServicesForCluster(awsCrossAccountAttributes, region, clusterName);
  }

  Map<String, Instance> listEc2Instances(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String region, Set<String> instanceIds) {
    Map<String, Instance> instanceMap = new HashMap<>();
    if (!CollectionUtils.isEmpty(instanceIds)) {
      List<Instance> instances = awsEC2HelperService.listEc2Instances(awsCrossAccountAttributes, instanceIds, region);
      instanceMap = instances.stream()
                        .filter(instance -> null != instance.getLaunchTime())
                        .collect(Collectors.toMap(Instance::getInstanceId, instance -> instance));
      log.debug("Instances {} ", instances.toString());
    }
    return instanceMap;
  }

  private Set<String> fetchEc2InstanceIds(List<ContainerInstance> containerInstances) {
    return containerInstances.stream().map(ContainerInstance::getEc2InstanceId).collect(Collectors.toSet());
  }

  private List<ContainerInstance> listContainerInstances(
      AwsCrossAccountAttributes awsCrossAccountAttributes, CECluster ceCluster) {
    return awsECSHelperService.listContainerInstancesForCluster(
        awsCrossAccountAttributes, ceCluster.getRegion(), ceCluster.getClusterArn());
  }

  private List<String> listContainerInstanceStatus() {
    return new ArrayList<>(
        Arrays.asList(ContainerInstanceStatus.ACTIVE.toString(), ContainerInstanceStatus.DRAINING.toString()));
  }

  private Map<String, AwsCrossAccountAttributes> getCrossAccountAttributes(String accountId) {
    List<SettingAttribute> ceConnectorsList =
        cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
    Map<String, AwsCrossAccountAttributes> crossAccountAttributesMap = new HashMap<>();
    if (!CollectionUtils.isEmpty(ceConnectorsList)) {
      List<CECloudAccount> ceCloudAccountList =
          ceCloudAccountDao.getBySettingId(accountId, ceConnectorsList.get(0).getUuid());
      ceCloudAccountList.forEach(ceCloudAccount
          -> crossAccountAttributesMap.put(
              ceCloudAccount.getInfraAccountId(), ceCloudAccount.getAwsCrossAccountAttributes()));
      List<SettingAttribute> ceConnectorList =
          cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
      ceConnectorList.forEach(ceConnector -> {
        CEAwsConfig ceAwsConfig = (CEAwsConfig) ceConnector.getValue();
        crossAccountAttributesMap.put(ceAwsConfig.getAwsMasterAccountId(), ceAwsConfig.getAwsCrossAccountAttributes());
      });
    }
    List<ConnectorResponseDTO> nextGenConnectors =
        ngConnectorHelper.getNextGenConnectors(accountId, Arrays.asList(ConnectorType.CE_AWS),
            Arrays.asList(CEFeatures.VISIBILITY), Arrays.asList(ConnectivityStatus.SUCCESS));
    for (ConnectorResponseDTO connector : nextGenConnectors) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
        AwsCrossAccountAttributes crossAccountAttributes =
            AwsCrossAccountAttributes.builder()
                .crossAccountRoleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                .build();
        crossAccountAttributesMap.put(ceAwsConnectorDTO.getAwsAccountId(), crossAccountAttributes);
      }
    }
    return crossAccountAttributesMap;
  }
}
