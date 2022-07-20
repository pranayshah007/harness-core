package io.harness.aws.awsv2;

import io.harness.aws.beans.AwsInternalConfig;

import java.util.Optional;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetResponse;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

public interface AwsApiV2Client {
  CreateServiceResponse createService(
      AwsInternalConfig awsConfig, CreateServiceRequest createServiceRequest, String region);

  UpdateServiceResponse updateService(
      AwsInternalConfig awsConfig, UpdateServiceRequest updateServiceRequest, String region);

  RegisterTaskDefinitionResponse createTask(
      AwsInternalConfig awsConfig, RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region);

  WaiterResponse<DescribeServicesResponse> ecsServiceSteadyStateCheck(AwsInternalConfig awsConfig,
      DescribeServicesRequest describeServicesRequest, String region, int serviceSteadyStateTimeout);

  RegisterScalableTargetResponse registerScalableTarget(
      AwsInternalConfig awsConfig, RegisterScalableTargetRequest registerScalableTargetRequest, String region);

  DeregisterScalableTargetResponse deregisterScalableTarget(
      AwsInternalConfig awsConfig, DeregisterScalableTargetRequest deregisterScalableTargetRequest, String region);

  PutScalingPolicyResponse attachScalingPolicy(
      AwsInternalConfig awsConfig, PutScalingPolicyRequest putScalingPolicyRequest, String region);

  DeleteScalingPolicyResponse deleteScalingPolicy(
      AwsInternalConfig awsConfig, DeleteScalingPolicyRequest deleteScalingPolicyRequest, String region);

  DescribeScalableTargetsResponse listScalableTargets(
      AwsInternalConfig awsConfig, DescribeScalableTargetsRequest describeScalableTargetsRequest, String region);

  DescribeScalingPoliciesResponse listScalingPolicies(
      AwsInternalConfig awsConfig, DescribeScalingPoliciesRequest describeScalingPoliciesRequest, String region);

  Optional<Service> getService(AwsInternalConfig awsConfig, String clusterName, String serviceName, String region);

  TaskDefinition getTaskDefinitionForService(AwsInternalConfig awsConfig, Service service, String region);
}
