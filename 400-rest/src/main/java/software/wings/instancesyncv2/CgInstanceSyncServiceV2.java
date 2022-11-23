/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

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
import software.wings.instancesyncv2.handler.CgInstanceSyncV2Handler;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.instancesyncv2.service.CgInstanceSyncTaskDetailsService;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncServiceV2 {
  @NonNull private CgInstanceSyncV2HandlerFactory handlerFactory;
  @NonNull private DelegateServiceGrpcClient delegateServiceClient;
  @NonNull private CgInstanceSyncTaskDetailsService taskDetailsService;
  @NonNull private InfrastructureMappingService infrastructureMappingService;
  @NonNull private SettingsServiceImpl cloudProviderService;
  @NonNull private KryoSerializer kryoSerializer;
  @NonNull private InstanceService instanceService;
  @NonNull private DeploymentService deploymentService;

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

    List<DeploymentSummary> deploymentSummaries = event.getDeploymentSummaries();

    deploymentSummaries = deploymentSummaries.stream().filter(this::hasDeploymentKey).collect(Collectors.toList());

    event.getDeploymentSummaries()
        .parallelStream()
        .filter(deployment -> Objects.nonNull(deployment.getDeploymentInfo()))
        .filter(this::hasDeploymentKey)
        .forEach(deploymentSummary -> {
          if (event.isRollback()) {
            deploymentSummary = getDeploymentSummaryForRollback(deploymentSummary);
          }

          saveDeploymentSummary(deploymentSummary, event.isRollback());

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

          Set<CgReleaseIdentifiers> cgReleaseIdentifiers =
              instanceSyncHandler.createReleaseIdentifiers(deploymentSummary);

          Map<CgReleaseIdentifiers, List<Instance>> deployedInstancesMap =
              instanceSyncHandler.getDeployedInstances(cgReleaseIdentifiers, deploymentSummary);

          Map<CgReleaseIdentifiers, List<Instance>> instancesInDbMap = instanceSyncHandler.fetchInstancesFromDb(
              cgReleaseIdentifiers, deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());

          for (CgReleaseIdentifiers cgReleaseIdentifier : cgReleaseIdentifiers) {
            handleInstances(deployedInstancesMap.get(cgReleaseIdentifier), instancesInDbMap.get(cgReleaseIdentifier),
                instanceSyncHandler);
          }
        });
  }

  @VisibleForTesting
  boolean hasDeploymentKey(DeploymentSummary deploymentSummary) {
    return deploymentSummary.getPcfDeploymentKey() != null || deploymentSummary.getK8sDeploymentKey() != null
        || deploymentSummary.getContainerDeploymentKey() != null || deploymentSummary.getAwsAmiDeploymentKey() != null
        || deploymentSummary.getAwsCodeDeployDeploymentKey() != null
        || deploymentSummary.getSpotinstAmiDeploymentKey() != null
        || deploymentSummary.getAwsLambdaDeploymentKey() != null
        || deploymentSummary.getAzureVMSSDeploymentKey() != null
        || deploymentSummary.getAzureWebAppDeploymentKey() != null
        || deploymentSummary.getCustomDeploymentKey() != null;
  }

  @VisibleForTesting
  DeploymentSummary saveDeploymentSummary(DeploymentSummary deploymentSummary, boolean rollback) {
    if (shouldSaveDeploymentSummary(deploymentSummary, rollback)) {
      return deploymentService.save(deploymentSummary);
    }
    return deploymentSummary;
  }

  @VisibleForTesting
  boolean shouldSaveDeploymentSummary(DeploymentSummary summary, boolean isRollback) {
    if (summary == null) {
      return false;
    }
    if (!isRollback) {
      return true;
    }
    // save rollback for lambda deployments
    return summary.getAwsLambdaDeploymentKey() != null;
  }
  protected DeploymentSummary getDeploymentSummaryForRollback(DeploymentSummary deploymentSummary) {
    Optional<DeploymentSummary> summaryOptional = deploymentService.get(deploymentSummary);
    if (summaryOptional != null && summaryOptional.isPresent()) {
      DeploymentSummary deploymentSummaryFromDB = summaryOptional.get();
      deploymentSummary.setUuid(deploymentSummaryFromDB.getUuid());
      // Copy Artifact Information for rollback version for previous deployment summary
      deploymentSummary.setArtifactBuildNum(deploymentSummaryFromDB.getArtifactBuildNum());
      deploymentSummary.setArtifactName(deploymentSummaryFromDB.getArtifactName());
      deploymentSummary.setArtifactId(deploymentSummaryFromDB.getArtifactId());
      deploymentSummary.setArtifactSourceName(deploymentSummaryFromDB.getArtifactSourceName());
      deploymentSummary.setArtifactStreamId(deploymentSummaryFromDB.getArtifactStreamId());
    } else {
      log.info("Unable to find DeploymentSummary while rolling back " + deploymentSummary);
    }
    return deploymentSummary;
  }

  private void handleInstances(
      List<Instance> instances, List<Instance> instancesInDb, CgInstanceSyncV2Handler instanceSyncHandler) {
    List<Instance> instancesToDelete = instanceSyncHandler.instancesToDelete(instancesInDb, instances);
    Set<String> instanceIdsToDelete = instancesToDelete.parallelStream().map(Instance::getUuid).collect(toSet());
    log.info("Instances to delete: [{}]", instanceIdsToDelete);
    instanceService.delete(instanceIdsToDelete);

    List<Instance> instancesToSaveAndUpdate = instanceSyncHandler.instancesToSaveAndUpdate(instances, instancesInDb);
    log.info("Instances to add: [{}]", instancesToSaveAndUpdate);
    instanceService.saveOrUpdate(instancesToSaveAndUpdate);
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
              instanceSyncHandler.createReleaseIdentifiers(deploymentSummary)));

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

    Map<String, List<InstanceSyncData>> InstanceSyncDataListPerTask = new HashMap<>();
    for (InstanceSyncData instanceSyncData : result.getInstanceDataList()) {
      if (!instanceSyncData.getExecutionStatus().equals(CommandExecutionStatus.SUCCESS.name())) {
        log.error("Instance Sync failed for perpetual task: [{}], for task details: [{}], with error: [{}]",
            perpetualTaskId, instanceSyncData.getTaskDetailsId(), instanceSyncData.getErrorMessage());
        continue;
      }

      if (!InstanceSyncDataListPerTask.containsKey(instanceSyncData.getTaskDetailsId())) {
        InstanceSyncDataListPerTask.put(instanceSyncData.getTaskDetailsId(), new ArrayList<>());
      }

      InstanceSyncDataListPerTask.get(instanceSyncData.getTaskDetailsId()).add(instanceSyncData);
    }

    Map<String, SettingAttribute> cloudProviders = new ConcurrentHashMap<>();
    for (String taskDetailsId : InstanceSyncDataListPerTask.keySet()) {
      Map<CgReleaseIdentifiers, DeploymentSummary> deploymentSummaries = new HashMap<>();
      Map<CgReleaseIdentifiers, List<Instance>> deployedInstances = new HashMap<>();
      Map<CgReleaseIdentifiers, List<Instance>> instancesInDbMap = new HashMap<>();

      List<InstanceSyncData> instanceSyncDataList = InstanceSyncDataListPerTask.get(taskDetailsId);
      InstanceSyncTaskDetails taskDetails = taskDetailsService.getForId(taskDetailsId);
      SettingAttribute cloudProvider =
          cloudProviders.computeIfAbsent(taskDetails.getCloudProviderId(), cloudProviderService::get);
      CgInstanceSyncV2Handler instanceSyncHandler =
          handlerFactory.getHandler(cloudProvider.getValue().getSettingType());

      Map<CgReleaseIdentifiers, InstanceSyncData> cgReleaseIdentifiersInstanceSyncDataMap =
          instanceSyncHandler.getCgReleaseIdentifiersList(instanceSyncDataList);

      Set<CgReleaseIdentifiers> cgReleaseIdentifiersResult =
          Sets.intersection(taskDetails.getReleaseIdentifiers(), cgReleaseIdentifiersInstanceSyncDataMap.keySet());

      for (CgReleaseIdentifiers cgReleaseIdentifiers : cgReleaseIdentifiersResult) {
        deploymentSummaries.put(
            cgReleaseIdentifiers, deploymentService.get(cgReleaseIdentifiers.getLastDeploymentSummaryId()));
      }

      instancesInDbMap = instanceSyncHandler.fetchInstancesFromDb(
          cgReleaseIdentifiersInstanceSyncDataMap.keySet(), taskDetails.getAppId(), taskDetails.getInfraMappingId());

      deployedInstances =
          instanceSyncHandler.groupInstanceSyncData(deploymentSummaries, cgReleaseIdentifiersInstanceSyncDataMap);

      for (CgReleaseIdentifiers cgReleaseIdentifiers : cgReleaseIdentifiersInstanceSyncDataMap.keySet()) {
        List<Instance> instances = deployedInstances.get(cgReleaseIdentifiers);
        List<Instance> instancesInDb = instancesInDbMap.get(cgReleaseIdentifiers);
        handleInstances(instances, instancesInDb, instanceSyncHandler);
      }
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
