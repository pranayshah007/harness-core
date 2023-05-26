/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;

@OwnedBy(CDP)
@UtilityClass
public class EcsMapper {
  public UpdateServiceRequest createServiceRequestToUpdateServiceRequest(
      CreateServiceRequest createServiceRequest, boolean forceNewDeployment) {
    return UpdateServiceRequest.builder()
        .service(createServiceRequest.serviceName())
        .serviceRegistries(createServiceRequest.serviceRegistries())
        .capacityProviderStrategy(createServiceRequest.capacityProviderStrategy())
        .cluster(createServiceRequest.cluster())
        .deploymentConfiguration(createServiceRequest.deploymentConfiguration())
        .desiredCount(createServiceRequest.desiredCount())
        .enableECSManagedTags(createServiceRequest.enableECSManagedTags())
        .healthCheckGracePeriodSeconds(createServiceRequest.healthCheckGracePeriodSeconds())
        .loadBalancers(createServiceRequest.loadBalancers())
        .enableExecuteCommand(createServiceRequest.enableExecuteCommand())
        .networkConfiguration(createServiceRequest.networkConfiguration())
        .overrideConfiguration(createServiceRequest.overrideConfiguration().isPresent()
                ? createServiceRequest.overrideConfiguration().get()
                : null)
        .placementConstraints(createServiceRequest.placementConstraints())
        .placementStrategy(createServiceRequest.placementStrategy())
        .platformVersion(createServiceRequest.platformVersion())
        .propagateTags(createServiceRequest.propagateTags())
        .taskDefinition(createServiceRequest.taskDefinition())
        .forceNewDeployment(forceNewDeployment)
        .build();
  }

  public EcsTask toEcsTask(Task task, String service) {
    return EcsTask.builder()
        .clusterArn(task.clusterArn())
        .serviceName(service)
        .launchType(task.launchTypeAsString())
        .taskArn(task.taskArn())
        .taskDefinitionArn(task.taskDefinitionArn())
        .startedAt(task.startedAt() != null ? task.startedAt().getEpochSecond() : Instant.now().getEpochSecond())
        .startedBy(task.startedBy())
        .version(task.version())
        .containers(CollectionUtils.isNotEmpty(task.containers())
                ? task.containers().stream().map(EcsMapper::toEcsContainer).collect(Collectors.toList())
                : null)
        .build();
  }

  public EcsContainer toEcsContainer(Container container) {
    return EcsContainer.builder()
        .containerArn(container.containerArn())
        .image(container.image())
        .name(container.name())
        .runtimeId(container.runtimeId())
        .build();
  }

  public CreateServiceRequest.Builder createCreateServiceRequestBuilderFromService(Service service) {
    return CreateServiceRequest.builder()
        .serviceName(service.serviceName())
        .taskDefinition(service.taskDefinition())
        .capacityProviderStrategy(service.capacityProviderStrategy())
        .serviceRegistries(service.serviceRegistries())
        .deploymentConfiguration(service.deploymentConfiguration())
        .deploymentController(service.deploymentController())
        .desiredCount(service.desiredCount())
        .enableECSManagedTags(service.enableECSManagedTags())
        .enableExecuteCommand(service.enableExecuteCommand())
        .healthCheckGracePeriodSeconds(service.healthCheckGracePeriodSeconds())
        .launchType(service.launchType())
        .networkConfiguration(service.networkConfiguration())
        .loadBalancers(service.loadBalancers())
        .placementConstraints(service.placementConstraints())
        .placementStrategy(service.placementStrategy())
        .platformVersion(service.platformVersion())
        .propagateTags(service.propagateTags())
        .role(service.roleArn())
        .tags(service.tags());
  }

  public String createRegisterScalableTargetRequestFromScalableTarget(ScalableTarget scalableTarget)
      throws JsonProcessingException {
    RegisterScalableTargetRequest.Builder registerScalableTargetRequestBuilder =
        RegisterScalableTargetRequest.builder()
            .maxCapacity(scalableTarget.maxCapacity())
            .minCapacity(scalableTarget.minCapacity())
            .roleARN(scalableTarget.roleARN())
            .scalableDimension(scalableTarget.scalableDimension())
            .serviceNamespace(scalableTarget.serviceNamespace())
            .suspendedState(scalableTarget.suspendedState());
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(registerScalableTargetRequestBuilder);
  }

  public String createPutScalingPolicyRequestFromScalingPolicy(ScalingPolicy scalingPolicy)
      throws JsonProcessingException {
    PutScalingPolicyRequest.Builder putScalingPolicyRequestRequestBuilder =
        PutScalingPolicyRequest.builder()
            .stepScalingPolicyConfiguration(scalingPolicy.stepScalingPolicyConfiguration())
            .targetTrackingScalingPolicyConfiguration(scalingPolicy.targetTrackingScalingPolicyConfiguration())
            .policyName(scalingPolicy.policyName())
            .policyType(scalingPolicy.policyType())
            .scalableDimension(scalingPolicy.scalableDimension())
            .serviceNamespace(scalingPolicy.serviceNamespace());

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return objectMapper.writeValueAsString(putScalingPolicyRequestRequestBuilder);
  }
}
