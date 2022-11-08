/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.logging.CommandExecutionStatus;
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
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2Handler;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncServiceV2 {
  private final CgInstanceSyncV2HandlerFactory handlerFactory;
  private final DelegateServiceGrpcClient delegateServiceClient;
  private final CgInstanceSyncTaskDetailsService taskDetailsService;
  private final InfrastructureMappingService infrastructureMappingService;
  private final SettingsServiceImpl cloudProviderService;
  private final KryoSerializer kryoSerializer;
  private final InstanceService instanceService;

  public static final String AUTO_SCALE = "AUTO_SCALE";
  public static final int PERPETUAL_TASK_INTERVAL = 10;
  public static final int PERPETUAL_TASK_TIMEOUT = 5;

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

          // handle current instances
          List<Instance> deployedInstances = instanceSyncHandler.getDeployedInstances(deploymentSummary);
          if (CollectionUtils.isNotEmpty(deployedInstances)) {
            handleInstances(deployedInstances, instanceSyncHandler);
          }
        });
  }

  private void handleInstances(List<Instance> instances, CgInstanceSyncV2Handler instanceSyncHandler) {
    if (CollectionUtils.isEmpty(instances)) {
      return;
    }

    Instance newInstance = instances.get(0);
    List<Instance> instancesInDb =
        instanceService.getInstancesForAppAndInframapping(newInstance.getAppId(), newInstance.getInfraMappingId());

    List<Instance> instancesToDelete = instanceSyncHandler.difference(instancesInDb, instances);
    Set<String> instanceIdsToDelete = instancesToDelete.parallelStream().map(Instance::getUuid).collect(toSet());
    log.info("Instances to delete: [{}]", instanceIdsToDelete);
    instanceService.delete(instanceIdsToDelete);

    List<Instance> instancesToAdd = instanceSyncHandler.difference(instances, instancesInDb);
    log.info("Instances to add: [{}]", instancesToAdd);
    instanceService.saveOrUpdate(instancesToAdd);

    List<Instance> instancesToUpdate = instanceSyncHandler.instancesToUpdate(instances, instancesInDb);
    log.info("Instances to update: [{}]", instancesToUpdate);
    instanceService.saveOrUpdate(instancesToUpdate);
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
        .setInterval(Durations.fromMinutes(PERPETUAL_TASK_INTERVAL))
        .setTimeout(Durations.fromMinutes(PERPETUAL_TASK_TIMEOUT))
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

    if (!result.getExecutionStatus().equals(CommandExecutionStatus.SUCCESS.name())) {
      log.error(
          "Instance Sync failed for perpetual task: [{}], with error: [{}]", perpetualTaskId, result.getErrorMessage());
      return;
    }

    Map<String, List<InstanceInfo>> instancesPerTask = new HashMap<>();
    for (InstanceSyncData instanceSyncData : result.getInstanceDataList()) {
      if (!instanceSyncData.getExecutionStatus().equals(CommandExecutionStatus.SUCCESS.name())) {
        log.error("Instance Sync failed for perpetual task: [{}], for task details: [{}], with error: [{}]",
            perpetualTaskId, instanceSyncData.getTaskDetailsId(), instanceSyncData.getErrorMessage());
        continue;
      }

      if (!instancesPerTask.containsKey(instanceSyncData.getTaskDetailsId())) {
        instancesPerTask.put(instanceSyncData.getTaskDetailsId(), new ArrayList<>());
      }

      instancesPerTask.get(instanceSyncData.getTaskDetailsId())
          .addAll(instanceSyncData.getInstanceDataList()
                      .parallelStream()
                      .map(instance -> (InstanceInfo) kryoSerializer.asObject(instance.toByteArray()))
                      .collect(Collectors.toList()));
    }

    Map<String, SettingAttribute> cloudProviders = new ConcurrentHashMap<>();
    for (String taskDetailsId : instancesPerTask.keySet()) {
      InstanceSyncTaskDetails taskDetails = taskDetailsService.getForId(taskDetailsId);
      SettingAttribute cloudProvider =
          cloudProviders.computeIfAbsent(taskDetails.getCloudProviderId(), cloudProviderService::get);
      CgInstanceSyncV2Handler instanceSyncHandler =
          handlerFactory.getHandler(cloudProvider.getValue().getSettingType());

      Instance lastDiscoveredInstance =
          instanceService.getLastDiscoveredInstance(taskDetails.getAppId(), taskDetails.getInfraMappingId());
      List<Instance> instancesInDb =
          instanceService.getInstancesForAppAndInframapping(taskDetails.getAppId(), taskDetails.getInfraMappingId());

      List<Instance> instances = instanceSyncHandler.getDeployedInstances(
          instancesPerTask.get(taskDetailsId), instancesInDb, lastDiscoveredInstance);
      handleInstances(instances, instanceSyncHandler);
      taskDetailsService.updateLastRun(taskDetailsId);
    }
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
