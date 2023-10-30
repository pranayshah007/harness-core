/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ecs.EcsBasicRollbackData;
import io.harness.delegate.beans.ecs.EcsServiceData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBasicRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBasicRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceNotFoundException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBasicRollbackTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  private EcsInfraConfig ecsInfraConfig;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBasicRollbackRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsBasicRollbackRequest"));
    }

    EcsBasicRollbackRequest basicRollbackRequest = (EcsBasicRollbackRequest) ecsCommandRequest;
    ecsInfraConfig = basicRollbackRequest.getInfraConfig();

    LogCallback logCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    try {
      long timeoutInMs = basicRollbackRequest.getTimeoutIntervalInMillis();
      logCallback.saveExecutionLog(format("Rolling back..%n%n"), LogLevel.INFO);
      if (basicRollbackRequest.isFirstDeployment()) {
        String serviceName = basicRollbackRequest.getNewServiceName();
        logCallback.saveExecutionLog(format("Deleting service %s..", serviceName), LogLevel.INFO);
        try {
          // delete new service in first time deployment
          ecsCommandTaskHelper.deleteService(serviceName, ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
              ecsInfraConfig.getAwsConnectorDTO());
          rollbackSuccessfulLogs(logCallback, basicRollbackRequest);
          return getResponseForFirstTimeRollback();
        } catch (Exception e) {
          if (e.getCause() instanceof ServiceNotFoundException) {
            logCallback.saveExecutionLog(format("service %s doesn't exist, so "
                                                 + "skipping deletion of service",
                                             serviceName),
                LogLevel.INFO);
            rollbackSuccessfulLogs(logCallback, basicRollbackRequest);
            return getResponseForFirstTimeRollback();
          }
          throw e;
        }
      } else {
        CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
            basicRollbackRequest.getCreateServiceRequestYaml(), CreateServiceRequest.serializableBuilderClass());
        createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster());
        logCallback.saveExecutionLog(format("Rolling back old service: %s to its previous state..%n%n",
                                         basicRollbackRequest.getOldServiceName()),
            LogLevel.INFO);

        // rollback old service
        ecsCommandTaskHelper.createOrUpdateService(createServiceRequestBuilder.build(),
            basicRollbackRequest.getRegisterScalableTargetRequestYaml(),
            basicRollbackRequest.getRegisterScalingPolicyRequestYaml(), ecsInfraConfig, logCallback, timeoutInMs, false,
            false);

        Optional<Service> serviceOptional = ecsCommandTaskHelper.describeService(ecsInfraConfig.getCluster(),
            basicRollbackRequest.getNewServiceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
        logCallback.saveExecutionLog(
            format("Scaling down new service: %s with zero instances..%n%n", basicRollbackRequest.getNewServiceName()),
            LogLevel.INFO);
        if (serviceOptional.isPresent()) {
          // remove scaling policies from new service
          ecsCommandTaskHelper.deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(),
              basicRollbackRequest.getNewServiceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
              logCallback);

          // de-registering scalable targets from new service
          ecsCommandTaskHelper.deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(),
              basicRollbackRequest.getNewServiceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
              logCallback);

          // downsize new service
          ecsCommandTaskHelper.downsizeService(serviceOptional.get(), ecsInfraConfig, logCallback, timeoutInMs);
        }
        EcsBasicRollbackData ecsBasicRollbackData =
            EcsBasicRollbackData.builder()
                .isFirstDeployment(false)
                .newServiceData(EcsServiceData.builder()
                                    .region(ecsInfraConfig.getRegion())
                                    .serviceName(basicRollbackRequest.getNewServiceName())
                                    .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                                        ecsInfraConfig.getCluster(), basicRollbackRequest.getNewServiceName(),
                                        ecsInfraConfig.getRegion()))
                                    .build())
                .oldServiceData(EcsServiceData.builder()
                                    .region(ecsInfraConfig.getRegion())
                                    .serviceName(basicRollbackRequest.getOldServiceName())
                                    .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                                        ecsInfraConfig.getCluster(), basicRollbackRequest.getOldServiceName(),
                                        ecsInfraConfig.getRegion()))
                                    .build())
                .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
                .build();
        rollbackSuccessfulLogs(logCallback, basicRollbackRequest);
        return EcsBasicRollbackResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .rollbackData(ecsBasicRollbackData)
            .build();
      }

    } catch (Exception ex) {
      logCallback.saveExecutionLog(color(format("%n Rollback Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }

  private EcsBasicRollbackResponse getResponseForFirstTimeRollback() {
    EcsBasicRollbackData ecsBasicRollbackData =
        EcsBasicRollbackData.builder()
            .isFirstDeployment(true)
            .newServiceData(EcsServiceData.builder().region(ecsInfraConfig.getRegion()).build())
            .oldServiceData(EcsServiceData.builder().build())
            .infrastructureKey(ecsInfraConfig.getInfraStructureKey())
            .build();

    return EcsBasicRollbackResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .rollbackData(ecsBasicRollbackData)
        .build();
  }

  private void rollbackSuccessfulLogs(LogCallback logCallback, EcsBasicRollbackRequest ecsBasicRollbackRequest) {
    logCallback.saveExecutionLog(color(format("%n Rollback Successful."), LogColor.Green, LogWeight.Bold),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    log.info("Completed task execution for command: {}", ecsBasicRollbackRequest.getEcsCommandType().name());
  }
}
