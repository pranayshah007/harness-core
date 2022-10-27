/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.metrics.service.api.MetricService;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsPersistence;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2Handler;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncServiceV2 {
  private final MetricService metricService;
  private final CgInstanceSyncV2HandlerFactory handlerFactory;
  private final DelegateServiceGrpcClient delegateServiceClient;
  private final CgInstanceSyncTaskDetailsService taskDetailsService;
  private final InfrastructureMappingService infrastructureMappingService;
  private final SettingsServiceImpl cloudProviderService;
  private final KryoSerializer kryoSerializer;

  @Inject protected ServiceResourceService serviceResourceService;

  @Inject protected EnvironmentService environmentService;
  @Inject protected InstanceUtils instanceUtil;

  @Inject protected AppService appService;
  @Inject private WingsPersistence wingsPersistence;

  @Inject protected InstanceService instanceService;
  private final MongoTemplate mongoTemplate;
  public static final String AUTO_SCALE = "AUTO_SCALE";

  public static final String INSTANCE_SYNC_V2_DURATION_METRIC = "instance_sync_v2_duration";

  public void handleInstanceSync(DeploymentEvent event) {
    if (Objects.isNull(event)) {
      log.error("Null event sent for Instance Sync Processing. Doing nothing");
      return;
    }

    if (CollectionUtils.isEmpty(event.getDeploymentSummaries())) {
      log.error("No deployment summaries present in the deployment event. Doing nothing");
      return;
    }

    event.getDeploymentSummaries()
        .parallelStream()
        .filter(deployment -> Objects.nonNull(deployment.getDeploymentInfo()))
        .forEach(deploymentSummary -> {
          SettingAttribute cloudProvider = fetchCloudProvider(deploymentSummary);

          CgInstanceSyncV2Handler instanceSyncHandler =
              handlerFactory.getHandler(cloudProvider.getValue().getSettingType());
          if (Objects.isNull(instanceSyncHandler)) {
            log.error("No handler registered for cloud provider type: [{}]. Doing nothing",
                cloudProvider.getValue().getSettingType());
            throw new InvalidRequestException("No handler registered for cloud provider type: ["
                + cloudProvider.getValue().getSettingType() + "] with Instance Sync V2");
          }

          if (!instanceSyncHandler.isDeploymentInfoTypeSupported(deploymentSummary.getDeploymentInfo().getClass())) {
            log.error("Instance Sync V2 not enabled for deployment info type: [{}]",
                deploymentSummary.getDeploymentInfo().getClass().getName());
            throw new InvalidRequestException("Instance Sync V2 not enabled for deployment info type: "
                + deploymentSummary.getDeploymentInfo().getClass().getName());
          }

          String configuredPerpetualTaskId =
              getConfiguredPerpetualTaskId(deploymentSummary, cloudProvider.getUuid(), instanceSyncHandler);
          if (StringUtils.isEmpty(configuredPerpetualTaskId)) {
            String perpetualTaskId = createInstanceSyncPerpetualTask(cloudProvider);
            trackDeploymentRelease(cloudProvider.getUuid(), perpetualTaskId, deploymentSummary, instanceSyncHandler);
          } else {
            updateInstanceSyncPerpetualTask(cloudProvider, configuredPerpetualTaskId);
          }
        });
  }

  private SettingAttribute fetchCloudProvider(DeploymentSummary deploymentSummary) {
    InfrastructureMapping infraMapping =
        infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
    return cloudProviderService.get(infraMapping.getComputeProviderSettingId());
  }

  private void updateInstanceSyncPerpetualTask(SettingAttribute cloudProvider, String perpetualTaskId) {
    delegateServiceClient.resetPerpetualTask(AccountId.newBuilder().setId(cloudProvider.getAccountId()).build(),
        PerpetualTaskId.newBuilder().setId(perpetualTaskId).build(),
        handlerFactory.getHandler(cloudProvider.getValue().getSettingType()).fetchInfraConnectorDetails(cloudProvider));
  }

  private String createInstanceSyncPerpetualTask(SettingAttribute cloudProvider) {
    String accountId = cloudProvider.getAccountId();

    PerpetualTaskId taskId = delegateServiceClient.createPerpetualTask(AccountId.newBuilder().setId(accountId).build(),
        "CG_INSTANCE_SYNC_V2", preparePerpetualTaskSchedule(),
        PerpetualTaskClientContextDetails.newBuilder()
            .setExecutionBundle(handlerFactory.getHandler(cloudProvider.getValue().getSettingType())
                                    .fetchInfraConnectorDetails(cloudProvider))
            .build(),
        true, "CloudProvider: [" + cloudProvider.getUuid() + "] Instance Sync V2 Perpetual Task");
    log.info("Created Perpetual Task with ID: [{}], for account: [{}], and cloud provider: [{}]", taskId.getId(),
        accountId, cloudProvider.getUuid());
    return taskId.getId();
  }

  private PerpetualTaskSchedule preparePerpetualTaskSchedule() {
    return PerpetualTaskSchedule.newBuilder()
        .setInterval(Durations.fromMinutes(10))
        .setTimeout(Durations.fromMinutes(5))
        .build();
  }

  private String getConfiguredPerpetualTaskId(
      DeploymentSummary deploymentSummary, String cloudProviderId, CgInstanceSyncV2Handler instanceSyncHandler) {
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        taskDetailsService.getForInfraMapping(deploymentSummary.getAccountId(), deploymentSummary.getInfraMappingId());

    if (Objects.nonNull(instanceSyncTaskDetails)) {
      instanceSyncTaskDetails.setReleaseIdentifiers(
          instanceSyncHandler.mergeReleaseIdentifiers(instanceSyncTaskDetails.getReleaseIdentifiers(),
              instanceSyncHandler.buildReleaseIdentifiers(deploymentSummary.getDeploymentInfo())));

      taskDetailsService.save(instanceSyncTaskDetails);
      return instanceSyncTaskDetails.getPerpetualTaskId();
    }

    log.info("No Instance Sync details found for InfraMappingId: [{}]. Proceeding to handling it.",
        deploymentSummary.getInfraMappingId());
    instanceSyncTaskDetails =
        taskDetailsService.fetchForCloudProvider(deploymentSummary.getAccountId(), cloudProviderId);
    if (Objects.isNull(instanceSyncTaskDetails)) {
      log.info("No Perpetual task found for cloud providerId: [{}].", cloudProviderId);
      return StringUtils.EMPTY;
    }

    String perpetualTaskId = instanceSyncTaskDetails.getPerpetualTaskId();
    InstanceSyncTaskDetails newTaskDetails =
        instanceSyncHandler.prepareTaskDetails(deploymentSummary, cloudProviderId, perpetualTaskId);
    taskDetailsService.save(newTaskDetails);
    return perpetualTaskId;
  }

  private void trackDeploymentRelease(String cloudProviderId, String perpetualTaskId,
      DeploymentSummary deploymentSummary, CgInstanceSyncV2Handler instanceSyncHandler) {
    InstanceSyncTaskDetails newTaskDetails =
        instanceSyncHandler.prepareTaskDetails(deploymentSummary, cloudProviderId, perpetualTaskId);
    taskDetailsService.save(newTaskDetails);
  }

  public void processInstanceSyncResult(String perpetualTaskId, CgInstanceSyncResponse result) {
    log.info("Got the result. Starting to process. Perpetual Task Id: [{}]", perpetualTaskId);
    result.getInstanceDataList().forEach(instanceSyncData -> {
      log.info("[InstanceSyncV2Tracking]: for PT: [{}], and taskId: [{}], found instances: [{}]", perpetualTaskId,
          instanceSyncData.getTaskDetailsId(),
          instanceSyncData.getInstanceDataList()
              .parallelStream()
              .map(instance -> kryoSerializer.asObject(instance.toByteArray()))
              .collect(Collectors.toList()));
    });

    Criteria criteriaInstanceSyncTaskDetails = new Criteria();
    criteriaInstanceSyncTaskDetails.and(InstanceSyncTaskDetails.InstanceSyncTaskDetailsKeys.perpetualTaskId)
        .is(perpetualTaskId);
    final InstanceSyncTaskDetails instanceSyncTaskDetails =
        mongoTemplate.findOne(new Query(criteriaInstanceSyncTaskDetails), InstanceSyncTaskDetails.class);

    InfrastructureMapping infrastructureMapping =
        mongoTemplate.findById(instanceSyncTaskDetails.getInfraMappingId(), InfrastructureMapping.class);

    for (InstanceSyncData instanceSyncData : result.getInstanceDataList()) {
      List<K8sPodInfo> instances = instanceSyncData.getInstanceDataList()
                                       .parallelStream()
                                       .map(instance -> (K8sPodInfo) kryoSerializer.asObject(instance.toByteArray()))
                                       .collect(Collectors.toList());

      Criteria criteria = new Criteria();
      criteria.and(Instance.InstanceKeys.infraMappingId).is(instanceSyncTaskDetails.getInfraMappingId());
      List<Instance> instancesInDB = mongoTemplate.find(new Query(criteria), Instance.class);
      Map<String, Instance> dbPodMap = new HashMap<>();
      Map<String, K8sPodInfo> currentPodsMap = new HashMap<>();

      instances.forEach(podInfo
          -> currentPodsMap.put(
              podInfo.getPodName() + podInfo.getNamespace() + getImageInStringFormat(podInfo), podInfo));
      instancesInDB.forEach(podInstance
          -> dbPodMap.put(podInstance.getPodInstanceKey().getPodName() + podInstance.getPodInstanceKey().getNamespace()
                  + getImageInStringFormat(podInstance),
              podInstance));

      Sets.SetView<String> instancesToBeAddedorUpdated = (Sets.SetView<String>) currentPodsMap.keySet();
      Sets.SetView<String> instancesToBeDeleted = Sets.difference(dbPodMap.keySet(), currentPodsMap.keySet());
      Sets.SetView<String> instancesToBeUpdated = Sets.intersection(currentPodsMap.keySet(), dbPodMap.keySet());

      Set<String> instanceIdsToBeDeleted = instancesToBeDeleted.stream()
                                               .map(instancePodName -> dbPodMap.get(instancePodName).getUuid())
                                               .collect(toSet());

      if (isNotEmpty(instanceIdsToBeDeleted)) {
        instanceService.delete(instanceIdsToBeDeleted);
      }

      for (String podName : instancesToBeAddedorUpdated) {
        DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();

        InstanceInfo info = null;
        if (!instancesInDB.isEmpty()) {
          generateDeploymentSummaryFromInstance(instancesInDB.stream().findFirst().get(), deploymentSummary);
          if (instancesToBeUpdated.contains(podName)) {
            deploymentSummary.setDeployedAt(instancesInDB.stream().findFirst().get().getDeletedAt());
          } else {
            deploymentSummary.setDeployedAt(System.currentTimeMillis());
          }

          info = instancesInDB.stream()
                     .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
                     .findFirst()
                     .get()
                     .getInstanceInfo();
        } else {
          Instance lastDiscoveredInstance = instanceService.getLastDiscoveredInstance(
              infrastructureMapping.getAppId(), infrastructureMapping.getUuid());

          if (Objects.nonNull(lastDiscoveredInstance)) {
            generateDeploymentSummaryFromInstance(lastDiscoveredInstance, deploymentSummary);
            if (instancesToBeUpdated.contains(podName)) {
              deploymentSummary.setDeployedAt(lastDiscoveredInstance.getDeletedAt());
            } else {
              deploymentSummary.setDeployedAt(System.currentTimeMillis());
            }
            info = lastDiscoveredInstance.getInstanceInfo();
          } else {
            deploymentSummary.setDeployedByName(AUTO_SCALE);
            deploymentSummary.setDeployedById(AUTO_SCALE);
            deploymentSummary.setDeployedAt(System.currentTimeMillis());
          }
        }

        if (Objects.nonNull(info) && info instanceof K8sPodInfo) {
          K8sPodInfo instanceInfo = (K8sPodInfo) info;
          if (Objects.isNull(deploymentSummary.getDeploymentInfo())) {
            deploymentSummary.setDeploymentInfo(K8sDeploymentInfo.builder()
                                                    .clusterName(instanceInfo.getClusterName())
                                                    .releaseName(instanceInfo.getReleaseName())
                                                    .namespace(instanceInfo.getNamespace())
                                                    .blueGreenStageColor(instanceInfo.getBlueGreenColor())
                                                    .helmChartInfo(instanceInfo.getHelmChartInfo())
                                                    .build());
          }
        }
        HelmChartInfo helmChartInfo =
            getK8sPodHelmChartInfo(deploymentSummary, currentPodsMap.get(podName), instancesInDB);
        Instance instance =
            buildInstanceFromPodInfo(infrastructureMapping, currentPodsMap.get(podName), deploymentSummary);
        ContainerInfo containerInfo = (ContainerInfo) instance.getInstanceInfo();
        setHelmChartInfoToContainerInfo(helmChartInfo, containerInfo);
        instanceService.saveOrUpdate(instance);
      }
    }
  }

  private Instance buildInstanceFromPodInfo(
      InfrastructureMapping infraMapping, K8sPodInfo pod, DeploymentSummary deploymentSummary) {
    Instance.InstanceBuilder builder = buildInstanceBase(null, infraMapping, deploymentSummary, null);
    builder.podInstanceKey(PodInstanceKey.builder().podName(pod.getPodName()).namespace(pod.getNamespace()).build());
    builder.instanceInfo(pod);

    if (deploymentSummary != null && deploymentSummary.getArtifactStreamId() != null) {
      return populateArtifactInInstanceBuilder(builder, deploymentSummary, infraMapping, pod).build();
    }

    return builder.build();
  }

  private Instance.InstanceBuilder populateArtifactInInstanceBuilder(Instance.InstanceBuilder builder,
      DeploymentSummary deploymentSummary, InfrastructureMapping infraMapping, K8sPodInfo pod) {
    boolean instanceBuilderUpdated = false;
    Artifact firstValidArtifact = null;
    String firstValidImage = "";
    for (K8sContainerInfo k8sContainer : pod.getContainers()) {
      String image = k8sContainer.getImage();
      Artifact artifact = findArtifactForImage(deploymentSummary.getArtifactStreamId(), infraMapping.getAppId(), image);

      if (artifact != null) {
        if (firstValidArtifact == null) {
          firstValidArtifact = artifact;
          firstValidImage = image;
        }
        // update only if buildNumber also matches
        if (isBuildNumSame(deploymentSummary.getArtifactBuildNum(), artifact.getBuildNo())) {
          builder.lastArtifactId(artifact.getUuid());
          updateInstanceWithArtifactSourceAndBuildNum(builder, image);
          instanceBuilderUpdated = true;
          break;
        }
      }
    }

    if (!instanceBuilderUpdated && firstValidArtifact != null) {
      builder.lastArtifactId(firstValidArtifact.getUuid());
      updateInstanceWithArtifactSourceAndBuildNum(builder, firstValidImage);
      instanceBuilderUpdated = true;
    }

    if (!instanceBuilderUpdated) {
      updateInstanceWithArtifactSourceAndBuildNum(builder, pod.getContainers().get(0).getImage());
    }

    return builder;
  }

  private Artifact findArtifactForImage(String artifactStreamId, String appId, String image) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(Artifact.ArtifactKeys.artifactStreamId, artifactStreamId)
        .filter(Artifact.ArtifactKeys.appId, appId)
        .filter("metadata.image", image)
        .disableValidation()
        .get();
  }

  private void updateInstanceWithArtifactSourceAndBuildNum(Instance.InstanceBuilder builder, String image) {
    String artifactSource;
    String tag;
    String[] splitArray = image.split(":");
    if (splitArray.length == 2) {
      artifactSource = splitArray[0];
      tag = splitArray[1];
    } else if (splitArray.length == 1) {
      artifactSource = splitArray[0];
      tag = "latest";
    } else {
      artifactSource = image;
      tag = image;
    }

    builder.lastArtifactName(image);
    builder.lastArtifactSourceName(artifactSource);
    builder.lastArtifactBuildNum(tag);
  }

  private boolean isBuildNumSame(String build1, String build2) {
    if (isEmpty(build1) || isEmpty(build2)) {
      return false;
    }
    return build1.equals(build2);
  }

  protected Instance.InstanceBuilder buildInstanceBase(
      String instanceId, InfrastructureMapping infraMapping, DeploymentSummary deploymentSummary, Artifact artifact) {
    String appId = infraMapping.getAppId();
    Application application = appService.get(appId);
    notNullCheck("Application is null for the given appId: " + appId, application);
    Environment environment = environmentService.get(appId, infraMapping.getEnvId(), false);
    notNullCheck("Environment is null for the given id: " + infraMapping.getEnvId(), environment);
    Service service = serviceResourceService.getWithDetails(appId, infraMapping.getServiceId());
    notNullCheck("Service is null for the given id: " + infraMapping.getServiceId(), service);
    String infraMappingType = infraMapping.getInfraMappingType();

    if (instanceId == null) {
      instanceId = generateUuid();
    }

    Instance.InstanceBuilder builder = Instance.builder()
                                           .uuid(instanceId)
                                           .accountId(application.getAccountId())
                                           .appId(appId)
                                           .appName(application.getName())
                                           .envName(environment.getName())
                                           .envId(infraMapping.getEnvId())
                                           .envType(environment.getEnvironmentType())
                                           .computeProviderId(infraMapping.getComputeProviderSettingId())
                                           .computeProviderName(infraMapping.getComputeProviderName())
                                           .infraMappingId(infraMapping.getUuid())
                                           .infraMappingName(infraMapping.getDisplayName())
                                           .infraMappingType(infraMappingType)
                                           .serviceId(infraMapping.getServiceId())
                                           .serviceName(service.getName());
    instanceUtil.setInstanceType(builder, infraMappingType);

    if (deploymentSummary != null) {
      builder.lastDeployedAt(deploymentSummary.getDeployedAt())
          .lastDeployedById(deploymentSummary.getDeployedById())
          .lastDeployedByName(deploymentSummary.getDeployedByName())
          .lastWorkflowExecutionId(deploymentSummary.getWorkflowExecutionId())
          .lastWorkflowExecutionName(deploymentSummary.getWorkflowExecutionName())
          .lastPipelineExecutionId(deploymentSummary.getPipelineExecutionId())
          .lastPipelineExecutionName(deploymentSummary.getPipelineExecutionName());

      if (artifact != null) {
        builder.lastArtifactId(artifact.getUuid())
            .lastArtifactName(artifact.getDisplayName())
            .lastArtifactStreamId(artifact.getArtifactStreamId())
            .lastArtifactSourceName(artifact.getArtifactSourceName())
            .lastArtifactBuildNum(artifact.getBuildNo());
      } else {
        builder.lastArtifactId(deploymentSummary.getArtifactId())
            .lastArtifactName(deploymentSummary.getArtifactName())
            .lastArtifactStreamId(deploymentSummary.getArtifactStreamId())
            .lastArtifactSourceName(deploymentSummary.getArtifactSourceName())
            .lastArtifactBuildNum(deploymentSummary.getArtifactBuildNum());
      }
    }

    return builder;
  }

  private HelmChartInfo getK8sPodHelmChartInfo(
      DeploymentSummary deploymentSummary, K8sPodInfo pod, Collection<Instance> instances) {
    if (deploymentSummary != null && deploymentSummary.getDeploymentInfo() instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo deploymentInfo = (K8sDeploymentInfo) deploymentSummary.getDeploymentInfo();
      if (StringUtils.equals(pod.getBlueGreenColor(), deploymentInfo.getBlueGreenStageColor())) {
        return deploymentInfo.getHelmChartInfo();
      }
    }

    return instances.stream()
        .sorted(Comparator.comparingLong(Instance::getLastDeployedAt).reversed())
        .map(Instance::getInstanceInfo)
        .filter(K8sPodInfo.class ::isInstance)
        .map(K8sPodInfo.class ::cast)
        .filter(podInfo -> StringUtils.equals(podInfo.getBlueGreenColor(), pod.getBlueGreenColor()))
        .findFirst()
        .map(K8sPodInfo::getHelmChartInfo)
        .orElse(null);
  }
  @VisibleForTesting
  void setHelmChartInfoToContainerInfo(HelmChartInfo helmChartInfo, ContainerInfo k8sInfo) {
    Optional.ofNullable(helmChartInfo).ifPresent(chartInfo -> {
      if (KubernetesContainerInfo.class == k8sInfo.getClass()) {
        ((KubernetesContainerInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      } else if (K8sPodInfo.class == k8sInfo.getClass()) {
        ((K8sPodInfo) k8sInfo).setHelmChartInfo(helmChartInfo);
      }
    });
  }

  protected DeploymentSummary generateDeploymentSummaryFromInstance(
      Instance instance, DeploymentSummary deploymentSummary) {
    deploymentSummary.setAppId(instance.getAppId());
    deploymentSummary.setAccountId(instance.getAccountId());
    deploymentSummary.setInfraMappingId(instance.getInfraMappingId());
    deploymentSummary.setInfraMappingId(instance.getInfraMappingId());
    deploymentSummary.setWorkflowExecutionId(instance.getLastWorkflowExecutionId());
    deploymentSummary.setWorkflowExecutionName(instance.getLastWorkflowExecutionName());
    deploymentSummary.setWorkflowId(instance.getLastWorkflowExecutionId());

    deploymentSummary.setArtifactId(instance.getLastArtifactId());
    deploymentSummary.setArtifactName(instance.getLastArtifactName());
    deploymentSummary.setArtifactStreamId(instance.getLastArtifactStreamId());
    deploymentSummary.setArtifactSourceName(instance.getLastArtifactSourceName());
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    deploymentSummary.setPipelineExecutionId(instance.getLastPipelineExecutionId());
    deploymentSummary.setPipelineExecutionName(instance.getLastPipelineExecutionName());

    // Commented this out, so we can distinguish between autoscales instances and instances we deployed
    deploymentSummary.setDeployedById(AUTO_SCALE);
    deploymentSummary.setDeployedByName(AUTO_SCALE);
    deploymentSummary.setArtifactBuildNum(instance.getLastArtifactBuildNum());

    return deploymentSummary;
  }

  private String getImageInStringFormat(K8sPodInfo pod) {
    return emptyIfNull(pod.getContainers()).stream().map(K8sContainerInfo::getImage).collect(Collectors.joining());
  }

  private String getImageInStringFormat(Instance instance) {
    if (instance.getInstanceInfo() instanceof K8sPodInfo) {
      return emptyIfNull(((K8sPodInfo) instance.getInstanceInfo()).getContainers())
          .stream()
          .map(K8sContainerInfo::getImage)
          .collect(Collectors.joining());
    }
    return EMPTY;
  }
  public InstanceSyncTrackedDeploymentDetails fetchTaskDetails(String perpetualTaskId, String accountId) {
    List<InstanceSyncTaskDetails> instanceSyncTaskDetails =
        taskDetailsService.fetchAllForPerpetualTask(accountId, perpetualTaskId);
    Map<String, SettingAttribute> cloudProviders = new ConcurrentHashMap<>();

    List<CgDeploymentReleaseDetails> deploymentReleaseDetails = new ArrayList<>();
    instanceSyncTaskDetails.parallelStream().forEach(taskDetails -> {
      SettingAttribute cloudProvider =
          cloudProviders.computeIfAbsent(taskDetails.getCloudProviderId(), cloudProviderService::get);
      CgInstanceSyncV2Handler instanceSyncHandler =
          handlerFactory.getHandler(cloudProvider.getValue().getSettingType());
      deploymentReleaseDetails.addAll(instanceSyncHandler.getDeploymentReleaseDetails(taskDetails));
    });

    if (cloudProviders.size() > 1) {
      log.warn("Multiple cloud providers are being tracked by perpetual task: [{}]. This should not happen.",
          perpetualTaskId);
    }

    return InstanceSyncTrackedDeploymentDetails.newBuilder()
        .setAccountId(accountId)
        .setPerpetualTaskId(perpetualTaskId)
        .addAllDeploymentDetails(deploymentReleaseDetails)
        .build();
  }
}
