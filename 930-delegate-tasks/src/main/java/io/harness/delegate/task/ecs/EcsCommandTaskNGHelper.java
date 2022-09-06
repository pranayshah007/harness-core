/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidYamlException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsCommandTaskNGHelper {
  @Inject private EcsV2Client ecsV2Client;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private YamlUtils yamlUtils = new YamlUtils();

  public RegisterTaskDefinitionResponse createTaskDefinition(
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createTask(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), registerTaskDefinitionRequest, region);
  }

  public CreateServiceResponse createService(
      CreateServiceRequest createServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), createServiceRequest, region);
  }

  public UpdateServiceResponse updateService(
      UpdateServiceRequest updateServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.updateService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), updateServiceRequest, region);
  }

  public DeleteServiceResponse deleteService(
      String serviceName, String cluster, String region, AwsConnectorDTO awsConnectorDTO) {
    DeleteServiceRequest deleteServiceRequest =
        DeleteServiceRequest.builder().service(serviceName).cluster(cluster).force(true).build();

    return ecsV2Client.deleteService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), deleteServiceRequest, region);
  }

  public Optional<Service> describeService(
      String cluster, String serviceName, String region, AwsConnectorDTO awsConnectorDTO) {
    DescribeServicesResponse describeServicesResponse = ecsV2Client.describeService(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, serviceName, region);
    return CollectionUtils.isNotEmpty(describeServicesResponse.services())
        ? Optional.of(describeServicesResponse.services().get(0))
        : Optional.empty();
  }

  public WaiterResponse<DescribeServicesResponse> ecsServiceSteadyStateCheck(LogCallback deployLogCallback,
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region,
      int serviceSteadyStateTimeout) {
    deployLogCallback.saveExecutionLog(
        format("Waiting for Service %s to reach steady state %n", serviceName), LogLevel.INFO);

    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();

    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsV2Client.ecsServiceSteadyStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
            describeServicesRequest, region, serviceSteadyStateTimeout);

    if (describeServicesResponseWaiterResponse.matched().exception().isPresent()) {
      Throwable throwable = describeServicesResponseWaiterResponse.matched().exception().get();
      deployLogCallback.saveExecutionLog(
          format("Service %s failed to reach steady state %n", serviceName), LogLevel.ERROR);
      throw new RuntimeException(format("Service %s failed to reach steady state %n", serviceName), throwable);
    }

    deployLogCallback.saveExecutionLog(format("Service %s reached steady state %n", serviceName), LogLevel.INFO);
    return describeServicesResponseWaiterResponse;
  }

  public WaiterResponse<DescribeServicesResponse> ecsServiceInactiveStateCheck(LogCallback deployLogCallback,
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region,
      int serviceInactiveStateTimeout) {
    deployLogCallback.saveExecutionLog(
        format("Waiting for existing Service %s to reach inactive state %n", serviceName), LogLevel.INFO);

    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();

    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsV2Client.ecsServiceInactiveStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
            describeServicesRequest, region, serviceInactiveStateTimeout);

    if (describeServicesResponseWaiterResponse.matched().exception().isPresent()) {
      Throwable throwable = describeServicesResponseWaiterResponse.matched().exception().get();
      deployLogCallback.saveExecutionLog(
          format("Existing Service %s failed to reach inactive state %n", serviceName), LogLevel.ERROR);
      throw new RuntimeException(
          format("Existing Service %s failed to reach inactive state %n", serviceName), throwable);
    }

    deployLogCallback.saveExecutionLog(
        format("Existing Service %s reached inactive state %n", serviceName), LogLevel.INFO);
    return describeServicesResponseWaiterResponse;
  }

  public DescribeScalableTargetsResponse listScalableTargets(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(Collections.singletonList(format("service/%s/%s", cluster, serviceName)))
            .build();
    return ecsV2Client.listScalableTargets(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalableTargetsRequest, region);
  }

  public DescribeScalingPoliciesResponse listScalingPolicies(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();
    return ecsV2Client.listScalingPolicies(
        awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalingPoliciesRequest, region);
  }

  public void deleteScalingPolicies(
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("%n%n"
                                         + "Deleting Scaling Policies from service %s..%n%n%n",
                                     serviceName),
        LogLevel.INFO);

    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        ecsV2Client.listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);

    if (describeScalingPoliciesResponse != null
        && CollectionUtils.isNotEmpty(describeScalingPoliciesResponse.scalingPolicies())) {
      describeScalingPoliciesResponse.scalingPolicies().forEach(scalingPolicy -> {
        DeleteScalingPolicyRequest deleteScalingPolicyRequest =
            DeleteScalingPolicyRequest.builder()
                .policyName(scalingPolicy.policyName())
                .resourceId(format("service/%s/%s", cluster, serviceName))
                .scalableDimension(scalingPolicy.scalableDimension())
                .serviceNamespace(scalingPolicy.serviceNamespace())
                .build();
        ecsV2Client.deleteScalingPolicy(awsInternalConfig, deleteScalingPolicyRequest, region);
        logCallback.saveExecutionLog(
            format("Deleted Scaling Policy %s from service %s %n..", scalingPolicy.policyName(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(
          format("%n%nDeleted Scaling Policies from service %s %n%n%n", serviceName), LogLevel.INFO);
    } else {
      logCallback.saveExecutionLog(
          format("Didn't find any Scaling Policies attached to service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public List<EcsTask> getRunningEcsTasks(
      AwsConnectorDTO awsConnectorDTO, String cluster, String serviceName, String region) {
    String nextToken = null;
    List<EcsTask> response = new ArrayList<>();
    do {
      ListTasksRequest.Builder listTasksRequestBuilder =
          ListTasksRequest.builder().cluster(cluster).serviceName(serviceName).desiredStatus(DesiredStatus.RUNNING);

      if (nextToken != null) {
        listTasksRequestBuilder.nextToken(nextToken);
      }

      ListTasksResponse listTasksResponse = ecsV2Client.listTaskArns(
          awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), listTasksRequestBuilder.build(), region);
      nextToken = listTasksResponse.nextToken();
      if (CollectionUtils.isNotEmpty(listTasksResponse.taskArns())) {
        DescribeTasksResponse describeTasksResponse = ecsV2Client.getTasks(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, listTasksResponse.taskArns(), region);
        response.addAll(describeTasksResponse.tasks()
                            .stream()
                            .map(task -> EcsMapper.toEcsTask(task, serviceName))
                            .collect(Collectors.toList()));
      }
    } while (nextToken != null);
    return response;
  }

  public void deregisterScalableTargets(
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format("%n%nDeregistering Scalable Targets from service %s..%n%n%n", serviceName), LogLevel.INFO);

    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(format("service/%s/%s", cluster, serviceName))
            .build();

    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        ecsV2Client.listScalableTargets(awsInternalConfig, describeScalableTargetsRequest, region);

    if (describeScalableTargetsResponse != null
        && CollectionUtils.isNotEmpty(describeScalableTargetsResponse.scalableTargets())) {
      describeScalableTargetsResponse.scalableTargets().forEach(scalableTarget -> {
        DeregisterScalableTargetRequest deregisterScalableTargetRequest =
            DeregisterScalableTargetRequest.builder()
                .scalableDimension(scalableTarget.scalableDimension())
                .serviceNamespace(scalableTarget.serviceNamespace())
                .resourceId(format("service/%s/%s", cluster, serviceName))
                .build();

        ecsV2Client.deregisterScalableTarget(awsInternalConfig, deregisterScalableTargetRequest, region);
        logCallback.saveExecutionLog(
            format("Deregistered Scalable Target with Scalable Dimension %s from service %s %n..",
                scalableTarget.scalableDimension().toString(), serviceName),
            LogLevel.INFO);
      });
      logCallback.saveExecutionLog(
          format("%n%nDeregistered Scalable Targets from service %s %n%n%n", serviceName), LogLevel.INFO);
    } else {
      logCallback.saveExecutionLog(
          format("Didn't find any Scalable Targets on service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public void attachScalingPolicies(List<String> ecsScalingPolicyManifestContentList, AwsConnectorDTO awsConnectorDTO,
      String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%n%nAttaching Scaling Policies to service %s.. %n%n%n", serviceName), LogLevel.INFO);

      ecsScalingPolicyManifestContentList.forEach(ecsScalingPolicyManifestContent -> {
        AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

        PutScalingPolicyRequest.Builder putScalingPolicyRequestBuilder =
            parseYamlAsObject(ecsScalingPolicyManifestContent, PutScalingPolicyRequest.serializableBuilderClass());
        PutScalingPolicyRequest putScalingPolicyRequest =
            putScalingPolicyRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();

        ecsV2Client.attachScalingPolicy(awsInternalConfig, putScalingPolicyRequest, region);
        logCallback.saveExecutionLog(
            format("Attached Scaling Policy %s to service %s %n", putScalingPolicyRequest.policyName(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(
          format("%n%nAttached Scaling Policies to service %s %n%n%n", serviceName), LogLevel.INFO);
    }
  }

  public void registerScalableTargets(List<String> ecsScalableTargetManifestContentList,
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%n%nRegistering Scalable Targets to service %s.. %n%n%n", serviceName), LogLevel.INFO);

      ecsScalableTargetManifestContentList.forEach(ecsScalableTargetManifestContent -> {
        AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);

        RegisterScalableTargetRequest.Builder registerScalableTargetRequestBuilder = parseYamlAsObject(
            ecsScalableTargetManifestContent, RegisterScalableTargetRequest.serializableBuilderClass());

        RegisterScalableTargetRequest registerScalableTargetRequest =
            registerScalableTargetRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();

        ecsV2Client.registerScalableTarget(awsInternalConfig, registerScalableTargetRequest, region);
        logCallback.saveExecutionLog(format("Registered Scalable Target with Scalable Dimension %s to service %s %n",
                                         registerScalableTargetRequest.scalableDimension(), serviceName),
            LogLevel.INFO);
      });

      logCallback.saveExecutionLog(
          format("%n%nRegistered Scalable Targets to service %s %n%n%n", serviceName), LogLevel.INFO);
    }
  }

  public <T> T parseYamlAsObject(String yaml, Class<T> tClass) {
    T object;
    try {
      object = yamlUtils.read(yaml, tClass);
    } catch (Exception e) {
      throw new InvalidYamlException(format("Error while parsing yaml to class %s", tClass.getName()), e);
    }
    return object;
  }

  public void createOrUpdateService(CreateServiceRequest createServiceRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis) {
    // if service exists create service, otherwise update service
    Optional<Service> optionalService = describeService(createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (!(optionalService.isPresent() && isServiceActive(optionalService.get()))) {
      logCallback.saveExecutionLog(
          format("Creating Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);
      CreateServiceResponse createServiceResponse =
          createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

      logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                                       createServiceResponse.service().serviceArn()),
          LogLevel.INFO);

      registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
          logCallback);

      attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
          logCallback);

    } else {
      Service service = optionalService.get();
      deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);
      deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
          ecsInfraConfig.getRegion(), logCallback);

      UpdateServiceRequest updateServiceRequest =
          EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest);
      logCallback.saveExecutionLog(
          format("Updating Service with name %s %n", updateServiceRequest.service()), LogLevel.INFO);
      UpdateServiceResponse updateServiceResponse =
          updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

      logCallback.saveExecutionLog(format("Updated Service %s with Arn %s %n", updateServiceRequest.service(),
                                       updateServiceResponse.service().serviceArn()),
          LogLevel.INFO);

      registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

      attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    }
  }

  public void createCanaryService(CreateServiceRequest createServiceRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis) {
    // if service exists create service, otherwise update service
    Optional<Service> optionalService = describeService(createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (optionalService.isPresent() && isServiceActive(optionalService.get())) { // if service exists delete it

      Service service = optionalService.get();

      logCallback.saveExecutionLog(
          format("Deleting existing Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);

      deleteService(
          service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

      logCallback.saveExecutionLog(
          format("Deleted existing Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);
    }

    logCallback.saveExecutionLog(
        format("Creating Service with name %s %n", createServiceRequest.serviceName()), LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
        createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
        (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

    logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                                     createServiceResponse.service().serviceArn()),
        LogLevel.INFO);

    registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
        logCallback);

    attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
        createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
        logCallback);
  }

  public boolean isServiceActive(Service service) {
    return service != null && service.status().equals("ACTIVE");
  }
}
