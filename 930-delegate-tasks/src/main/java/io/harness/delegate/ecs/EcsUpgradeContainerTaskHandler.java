/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.delegate.task.ecs.EcsInstanceUnitType.COUNT;
import static io.harness.delegate.task.ecs.EcsInstanceUnitType.PERCENTAGE;
import static io.harness.delegate.task.ecs.EcsResizeStrategy.RESIZE_NEW_FIRST;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ecs.EcsBasicDeployData;
import io.harness.delegate.beans.ecs.EcsBasicDeployData.EcsBasicDeployDataBuilder;
import io.harness.delegate.beans.ecs.EcsServiceData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.EcsUpgradeContainerServiceData;
import io.harness.delegate.task.ecs.EcsUtils;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsUpgradeContainerRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsUpgradeContainerResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsUpgradeContainerTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  private EcsInfraConfig ecsInfraConfig;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsUpgradeContainerRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsUpgradeContainerRequest"));
    }

    EcsUpgradeContainerRequest upgradeContainerRequest = (EcsUpgradeContainerRequest) ecsCommandRequest;
    ecsInfraConfig = upgradeContainerRequest.getInfraConfig();

    LogCallback logCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.upgradeContainer.toString(), true, commandUnitsProgress);

    try {
      long timeoutInMs = upgradeContainerRequest.getTimeoutIntervalInMillis();
      EcsUpgradeContainerServiceData newServiceData = upgradeContainerRequest.getNewServiceData();
      EcsUpgradeContainerServiceData oldServiceData = upgradeContainerRequest.getOldServiceData();
      int oldServiceThresholdInstanceCount =
          oldServiceData.getThresholdInstanceCount() != null ? oldServiceData.getThresholdInstanceCount() : 1;
      int newServiceDesiredCount = 0;
      int oldServiceDesiredCount = oldServiceThresholdInstanceCount;

      if (PERCENTAGE.equals(newServiceData.getInstanceUnitType())) {
        newServiceDesiredCount =
            EcsUtils.getPercentCount(newServiceData.getInstanceCount(), newServiceData.getThresholdInstanceCount());
        if (oldServiceData.getInstanceUnitType() == null) {
          oldServiceDesiredCount =
              EcsUtils.getPercentCount(100 - newServiceData.getInstanceCount(), oldServiceThresholdInstanceCount);
        }
      } else {
        newServiceDesiredCount =
            Integer.min(newServiceData.getInstanceCount(), newServiceData.getThresholdInstanceCount());
        if (oldServiceData.getInstanceUnitType() == null) {
          oldServiceDesiredCount = Integer.max(0, oldServiceThresholdInstanceCount - newServiceData.getInstanceCount());
        }
      }

      if (PERCENTAGE.equals(oldServiceData.getInstanceUnitType())) {
        oldServiceDesiredCount =
            EcsUtils.getPercentCount(oldServiceData.getInstanceCount(), oldServiceThresholdInstanceCount);
      } else if (COUNT.equals(oldServiceData.getInstanceUnitType())) {
        oldServiceDesiredCount = Integer.min(oldServiceThresholdInstanceCount, oldServiceData.getInstanceCount());
      }

      boolean upsizeFirst = RESIZE_NEW_FIRST.equals(upgradeContainerRequest.getResizeStrategy());

      // fetch all services in cluster
      List<Service> services = ecsCommandTaskHelper.fetchServicesOfCluster(
          ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion());

      String servicePrefix =
          EcsUtils.getServicePrefixByRemovingNumber(upgradeContainerRequest.getNewServiceData().getServiceName());

      // filter services with prefix
      List<Service> eligibleServices = ecsCommandTaskHelper.fetchServicesWithPrefix(services, servicePrefix);

      Service newService = ecsCommandTaskHelper.fetchServiceWithName(
          eligibleServices, upgradeContainerRequest.getNewServiceData().getServiceName());

      if (upsizeFirst) {
        upsizeNewService(upgradeContainerRequest, logCallback, timeoutInMs, newServiceDesiredCount, newService);
        downsizeOldServices(
            upgradeContainerRequest, logCallback, timeoutInMs, oldServiceDesiredCount, eligibleServices);
      } else {
        downsizeOldServices(
            upgradeContainerRequest, logCallback, timeoutInMs, oldServiceDesiredCount, eligibleServices);
        upsizeNewService(upgradeContainerRequest, logCallback, timeoutInMs, newServiceDesiredCount, newService);
      }

      EcsBasicDeployDataBuilder basicDeployDataBuilder =
          EcsBasicDeployData.builder()
              .newServiceData(
                  EcsServiceData.builder()
                      .region(ecsInfraConfig.getRegion())
                      .serviceName(upgradeContainerRequest.getNewServiceData().getServiceName())
                      .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                          ecsInfraConfig.getCluster(), upgradeContainerRequest.getNewServiceData().getServiceName(),
                          ecsInfraConfig.getRegion()))
                      .build())
              .oldServiceData(EcsServiceData.builder().build());

      if (!upgradeContainerRequest.isFirstTimeDeployment()) {
        basicDeployDataBuilder.oldServiceData(
            EcsServiceData.builder()
                .region(ecsInfraConfig.getRegion())
                .serviceName(upgradeContainerRequest.getOldServiceData().getServiceName())
                .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                    ecsInfraConfig.getCluster(), upgradeContainerRequest.getOldServiceData().getServiceName(),
                    ecsInfraConfig.getRegion()))
                .build());
      }

      EcsUpgradeContainerResponse ecsUpgradeContainerResponse =
          EcsUpgradeContainerResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
              .deployData(basicDeployDataBuilder.build())
              .build();
      logCallback.saveExecutionLog("Upgrade Container complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return ecsUpgradeContainerResponse;

    } catch (Exception ex) {
      logCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }

  private void downsizeOldServices(EcsUpgradeContainerRequest upgradeContainerRequest, LogCallback logCallback,
      long timeoutInMs, int desiredCount, List<Service> eligibleServices) {
    // get stale services
    List<Service> staleServices = ecsCommandTaskHelper.fetchStaleServices(eligibleServices,
        Lists.newArrayList(upgradeContainerRequest.getNewServiceData().getServiceName(),
            upgradeContainerRequest.getOldServiceData().getServiceName()));

    // downsize stale services which are having running instances
    ecsCommandTaskHelper.downsizeStaleServicesHavingRunningInstances(
        staleServices, logCallback, ecsInfraConfig, timeoutInMs);

    if (!upgradeContainerRequest.isFirstTimeDeployment()) {
      // remove scaling policies from old service
      ecsCommandTaskHelper.deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(),
          upgradeContainerRequest.getOldServiceData().getServiceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);

      // de-registering scalable targets from old service
      ecsCommandTaskHelper.deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(),
          upgradeContainerRequest.getOldServiceData().getServiceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);

      updateDesiredCount(upgradeContainerRequest.getOldServiceData().getServiceName(), desiredCount, logCallback,
          timeoutInMs,
          ecsCommandTaskHelper.fetchServiceWithName(
              eligibleServices, upgradeContainerRequest.getOldServiceData().getServiceName()));
    }
  }

  private void upsizeNewService(EcsUpgradeContainerRequest upgradeContainerRequest, LogCallback logCallback,
      long timeoutInMs, int desiredCount, Service service) {
    updateDesiredCount(
        upgradeContainerRequest.getNewServiceData().getServiceName(), desiredCount, logCallback, timeoutInMs, service);
    // checking if it is final step
    if (desiredCount >= upgradeContainerRequest.getNewServiceData().getThresholdInstanceCount()) {
      // register scalable targets
      ecsCommandTaskHelper.registerScalableTargets(upgradeContainerRequest.getScalableTargetManifestContentList(),
          ecsInfraConfig.getAwsConnectorDTO(), upgradeContainerRequest.getNewServiceData().getServiceName(),
          ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

      // attach scaling policies
      ecsCommandTaskHelper.attachScalingPolicies(upgradeContainerRequest.getScalingPolicyManifestContentList(),
          ecsInfraConfig.getAwsConnectorDTO(), upgradeContainerRequest.getNewServiceData().getServiceName(),
          ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    }
  }

  private void updateDesiredCount(
      String serviceName, int desiredCount, LogCallback logCallback, long timeoutInMs, Service service) {
    if (service.desiredCount() == desiredCount) {
      logCallback.saveExecutionLog(
          color(format("service: %s already stays at %s desired count. skipping service update", serviceName,
                    service.desiredCount()),
              LogColor.White, LogWeight.Bold),
          LogLevel.INFO);
      return;
    }
    logCallback.saveExecutionLog(color(format("%n Updating service: %s from desired count: %s to %s.", serviceName,
                                           service.desiredCount(), desiredCount),
                                     LogColor.White, LogWeight.Bold),
        LogLevel.INFO);

    // update desired count
    UpdateServiceResponse updateServiceResponse = ecsCommandTaskHelper.updateDesiredCount(serviceName, ecsInfraConfig,
        awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()), desiredCount);

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

    // steady state check to reach stable state
    ecsCommandTaskHelper.ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(),
        ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), timeoutInMs, eventsAlreadyProcessed);

    logCallback.saveExecutionLog(
        color(format("%n Updated service: %s with desired count: %s.", serviceName, desiredCount), LogColor.White,
            LogWeight.Bold),
        LogLevel.INFO);
  }
}
