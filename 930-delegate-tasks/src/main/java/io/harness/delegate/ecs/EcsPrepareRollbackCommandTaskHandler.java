/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsPrepareRollbackDataRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsPrepareRollbackCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;

  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsPrepareRollbackRequest"));
    }

    EcsPrepareRollbackDataRequest ecsPrepareRollbackRequest = (EcsPrepareRollbackDataRequest) ecsCommandRequest;

    timeoutInMillis = ecsPrepareRollbackRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsPrepareRollbackRequest.getEcsInfraConfig();

    LogCallback prepareRollbackDataLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      prepareRollbackDataLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"), LogLevel.INFO);
      // Get Ecs Service Name
      String ecsServiceDefinitionManifestContent = ecsPrepareRollbackRequest.getEcsServiceDefinitionManifestContent();
      CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
          ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());
      CreateServiceRequest createServiceRequest = createServiceRequestBuilder.build();

      if (StringUtils.isEmpty(createServiceRequest.serviceName())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            format(
                "Please check if ECS service name is configured properly in ECS Service Definition Manifest in Harness Service."),
            format("ECS service name is not configured properly in ECS Service Definition. It is found to be empty."),
            new InvalidRequestException("ECS service name is empty."));
      }

      String serviceName = createServiceRequest.serviceName();

      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Service Definition Details for Service %s..", serviceName), LogLevel.INFO);
      // Describe ecs service and get service details
      Optional<Service> optionalService = ecsCommandTaskHelper.describeService(
          ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      if (optionalService.isPresent()
          && ecsCommandTaskHelper.isServiceActive(optionalService.get())) { // If service exists
        Service service = optionalService.get();

        Integer maxDesiredCount = service.desiredCount();
        // compare max desired count with current desired count
        if (createServiceRequest.desiredCount() != null) {
          maxDesiredCount = Math.max(maxDesiredCount, createServiceRequest.desiredCount());
        }
        Service updatedService = service.toBuilder().desiredCount(maxDesiredCount).build();

        // Get createServiceRequestBuilderString from service
        String createServiceRequestBuilderString =
            ecsCommandTaskHelper.toYaml(EcsMapper.createCreateServiceRequestBuilderFromService(updatedService));
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetched Service Definition Details for Service %s", serviceName), LogLevel.INFO);

        // Get registerScalableTargetRequestBuilderStrings if present
        List<String> registerScalableTargetRequestBuilderStrings = ecsCommandTaskHelper.getScalableTargetsAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        // Get putScalingPolicyRequestBuilderStrings if present
        List<String> registerScalingPolicyRequestBuilderStrings = ecsCommandTaskHelper.getScalingPoliciesAsString(
            prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

        EcsPrepareRollbackDataResult ecsPrepareRollbackDataResult =
            EcsPrepareRollbackDataResult.builder()
                .isFirstDeployment(false)
                .serviceName(serviceName)
                .createServiceRequestBuilderString(createServiceRequestBuilderString)
                .registerScalableTargetRequestBuilderStrings(registerScalableTargetRequestBuilderStrings)
                .registerScalingPolicyRequestBuilderStrings(registerScalingPolicyRequestBuilderStrings)
                .build();

        EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse =
            EcsPrepareRollbackDataResponse.builder()
                .ecsPrepareRollbackDataResult(ecsPrepareRollbackDataResult)
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .build();

        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Preparing Rollback Data complete"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        return ecsPrepareRollbackDataResponse;

      } else { // If service doesn't exist
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Service %s doesn't exist. Skipping Prepare Rollback Data..", serviceName), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);

        // Send EcsPrepareRollbackDataResult with isFirstDeployment as true
        EcsPrepareRollbackDataResult ecsPrepareRollbackDataResult =
            EcsPrepareRollbackDataResult.builder().isFirstDeployment(true).serviceName(serviceName).build();

        return EcsPrepareRollbackDataResponse.builder()
            .ecsPrepareRollbackDataResult(ecsPrepareRollbackDataResult)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
      }
    } catch (Exception ex) {
      prepareRollbackDataLogCallback.saveExecutionLog(
          color(format("%n PrepareRollback Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }
}
