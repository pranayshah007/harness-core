/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
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
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

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

    // Get Ecs Service Name
    String ecsServiceDefinitionManifestContent = ecsPrepareRollbackRequest.getEcsServiceDefinitionManifestContent();
    CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());
    CreateServiceRequest createServiceRequest = createServiceRequestBuilder.build();
    String serviceName = createServiceRequest.serviceName();

    prepareRollbackDataLogCallback.saveExecutionLog(
        format("Fetching Service Definition Details for Service %s..", serviceName), LogLevel.INFO);
    // Describe ecs service and get service details
    Optional<Service> optionalService = ecsCommandTaskHelper.describeService(
        ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (optionalService.isPresent()
        && ecsCommandTaskHelper.isServiceActive(optionalService.get())) { // If service exists
      Service service = optionalService.get();

      // Get createServiceRequestBuilderString from service
      String createServiceRequestBuilderString = EcsMapper.createCreateServiceRequestFromService(service);
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetched Service Definition Details for Service %s", serviceName), LogLevel.INFO);

      // Get registerScalableTargetRequestBuilderStrings if present
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Scalable Target Details for Service %s..", serviceName), LogLevel.INFO);
      DescribeScalableTargetsResponse describeScalableTargetsResponse =
          ecsCommandTaskHelper.listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
              service.serviceName(), ecsInfraConfig.getRegion());

      List<String> registerScalableTargetRequestBuilderStrings = null;
      if (describeScalableTargetsResponse != null
          && CollectionUtils.isNotEmpty(describeScalableTargetsResponse.scalableTargets())) {
        registerScalableTargetRequestBuilderStrings =
            describeScalableTargetsResponse.scalableTargets()
                .stream()
                .map(scalableTarget -> {
                  try {
                    return EcsMapper.createRegisterScalableTargetRequestFromScalableTarget(scalableTarget);
                  } catch (Exception e) {
                    String message = "Error while creating register scalable target request json from scalable target";
                    log.error(message);
                    throw new InvalidRequestException(message, e);
                  }
                })
                .collect(Collectors.toList());
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetched Scalable Target Details for Service %s", serviceName), LogLevel.INFO);
      } else {
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Didn't find Scalable Target Details for Service %s", serviceName), LogLevel.INFO);
      }

      // Get putScalingPolicyRequestBuilderStrings if present
      prepareRollbackDataLogCallback.saveExecutionLog(
          format("Fetching Scaling Policy Details for Service %s..", serviceName), LogLevel.INFO);
      DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
          ecsCommandTaskHelper.listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
              service.serviceName(), ecsInfraConfig.getRegion());

      List<String> registerScalingPolicyRequestBuilderStrings = null;
      if (describeScalingPoliciesResponse != null
          && CollectionUtils.isNotEmpty(describeScalingPoliciesResponse.scalingPolicies())) {
        registerScalingPolicyRequestBuilderStrings =
            describeScalingPoliciesResponse.scalingPolicies()
                .stream()
                .map(scalingPolicy -> {
                  try {
                    return EcsMapper.createPutScalingPolicyRequestFromScalingPolicy(scalingPolicy);
                  } catch (JsonProcessingException e) {
                    String message = "Error while creating put scaling policy request json from scaling policy";
                    log.error(message);
                    throw new InvalidRequestException(message, e);
                  }
                })
                .collect(Collectors.toList());
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetched Scaling Policy Details for Service %s", serviceName), LogLevel.INFO);
      } else {
        prepareRollbackDataLogCallback.saveExecutionLog(
            format("Didn't find Scaling Policy Details for Service %s", serviceName), LogLevel.INFO);
      }

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
  }
}
