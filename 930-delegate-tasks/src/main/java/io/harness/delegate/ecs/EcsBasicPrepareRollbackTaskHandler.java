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
import io.harness.delegate.beans.ecs.EcsBasicPrepareRollbackData;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBasicPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBasicPrepareRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBasicPrepareRollbackTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  private EcsInfraConfig ecsInfraConfig;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBasicPrepareRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBasicPrepareRollbackRequest"));
    }

    EcsBasicPrepareRollbackRequest prepareRollbackRequest = (EcsBasicPrepareRollbackRequest) ecsCommandRequest;
    ecsInfraConfig = prepareRollbackRequest.getInfraConfig();

    LogCallback logCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      // Get Ecs service prefix
      String ecsServiceDefinitionManifestContent = prepareRollbackRequest.getServiceDefinitionManifestContent();
      CreateServiceRequest createServiceRequest =
          ecsCommandTaskHelper
              .parseYamlAsObject(ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass())
              .build();
      if (StringUtils.isEmpty(createServiceRequest.serviceName())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Please check if ECS service name is configured properly in ECS Service Definition Manifest in Harness Service.",
            "ECS service name is not configured properly in ECS Service Definition. It is found to be empty.",
            new InvalidRequestException("ECS service name is empty."));
      }

      String servicePrefix = createServiceRequest.serviceName() + EcsCommandTaskNGHelper.DELIMITER;

      Integer instanceCount = createServiceRequest.desiredCount();

      // fetch all services in cluster
      List<Service> services = ecsCommandTaskHelper.fetchServicesOfCluster(
          ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion());

      // filter services with prefix
      List<Service> eligibleServices = ecsCommandTaskHelper.fetchServicesWithPrefix(services, servicePrefix);

      if (CollectionUtils.isEmpty(eligibleServices)) {
        // first time deployment
        logCallback.saveExecutionLog(
            format("Service doesn't exist with prefix %s. Skipping Prepare Rollback Data..", servicePrefix),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
        return getFirstTimeDeploymentRollbackData(servicePrefix, instanceCount);
      }

      // get latest service with desired count greater than zero
      Optional<Service> activeServiceOptional = ecsCommandTaskHelper.getLatestRunningService(eligibleServices);

      int nextServiceRevision = ecsCommandTaskHelper.getNextRevisionForDeployment(eligibleServices);

      if (activeServiceOptional.isEmpty()) {
        logCallback.saveExecutionLog(
            format("There is no running service with prefix %s. Skipping Prepare Rollback Data..", servicePrefix),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
        return getInactiveServiceRollbackData(servicePrefix, instanceCount, nextServiceRevision);
      }

      Service activeService = activeServiceOptional.get();

      // Get createServiceRequestYaml from service
      logCallback.saveExecutionLog(
          format("Fetching Service Definition Details for Service %s..", activeService.serviceName()), LogLevel.INFO);
      String createServiceRequestYaml =
          ecsCommandTaskHelper.toYaml(EcsMapper.createCreateServiceRequestBuilderFromService(activeService));
      logCallback.saveExecutionLog(
          format("Fetched Service Definition Details for Service %s", activeService.serviceName()), LogLevel.INFO);

      // Get registerScalableTargetRequestYaml if present
      List<String> registerScalableTargetRequestYaml = ecsCommandTaskHelper.getScalableTargetsAsString(
          logCallback, activeService.serviceName(), activeService, ecsInfraConfig);

      // Get registerScalingPolicyRequestYaml if present
      List<String> registerScalingPolicyRequestYaml = ecsCommandTaskHelper.getScalingPoliciesAsString(
          logCallback, activeService.serviceName(), activeService, ecsInfraConfig);

      EcsBasicPrepareRollbackData prepareRollbackData =
          EcsBasicPrepareRollbackData.builder()
              .isFirstDeployment(false)
              .servicePrefix(servicePrefix)
              .createServiceRequestYaml(createServiceRequestYaml)
              .registerScalableTargetRequestYaml(registerScalableTargetRequestYaml)
              .registerScalingPolicyRequestYaml(registerScalingPolicyRequestYaml)
              .currentServiceName(activeService.serviceName())
              .currentServiceInstanceCount(activeService.desiredCount())
              .nextRevision(nextServiceRevision)
              .newServiceThresholdInstanceCount(instanceCount)
              .build();

      logCallback.saveExecutionLog("Preparing Rollback Data complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return EcsBasicPrepareRollbackResponse.builder()
          .prepareRollbackData(prepareRollbackData)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    }

    catch (Exception e) {
      logCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }

  private EcsBasicPrepareRollbackResponse getFirstTimeDeploymentRollbackData(
      String servicePrefix, Integer instanceCount) {
    EcsBasicPrepareRollbackData prepareRollbackData = EcsBasicPrepareRollbackData.builder()
                                                          .isFirstDeployment(true)
                                                          .servicePrefix(servicePrefix)
                                                          .nextRevision(1)
                                                          .newServiceThresholdInstanceCount(instanceCount)
                                                          .build();
    return EcsBasicPrepareRollbackResponse.builder()
        .prepareRollbackData(prepareRollbackData)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }

  private EcsBasicPrepareRollbackResponse getInactiveServiceRollbackData(
      String servicePrefix, Integer instanceCount, int nextServiceRevision) {
    EcsBasicPrepareRollbackData prepareRollbackData = EcsBasicPrepareRollbackData.builder()
                                                          .isFirstDeployment(true)
                                                          .servicePrefix(servicePrefix)
                                                          .newServiceThresholdInstanceCount(instanceCount)
                                                          .nextRevision(nextServiceRevision)
                                                          .build();
    return EcsBasicPrepareRollbackResponse.builder()
        .prepareRollbackData(prepareRollbackData)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
