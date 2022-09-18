/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static com.google.common.collect.Lists.newArrayList;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static java.lang.String.format;
import static java.util.Comparator.comparingInt;
import static org.apache.commons.lang3.StringUtils.trim;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.data.structure.EmptyPredicate;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.YamlUtils;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceField;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.TagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UntagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsCommandTaskNGHelper {
  @Inject private EcsV2Client ecsV2Client;
  @Inject private ElbV2Client elbV2Client;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private TimeLimiter timeLimiter;

  private YamlUtils yamlUtils = new YamlUtils();
  public static final String DELIMITER = "__";
  public static final String BG_VERSION = "BG_VERSION";
  public static final String BG_GREEN = "GREEN";
  public static final String BG_BLUE = "BLUE";
  private static final String TARGET_GROUP_ARN_EXPRESSION = "<+targetGroupArn>";


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
      long serviceSteadyStateTimeout, List<ServiceEvent> eventsAlreadyProcessed) {
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

    Optional<Service> serviceOptional = describeService(cluster, serviceName, region, awsConnectorDTO);

    printAwsEvent(serviceOptional.get(), eventsAlreadyProcessed, deployLogCallback);

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
    logCallback.saveExecutionLog(format("%n"
                                         + "Deleting Scaling Policies from service %s..%n",
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

      logCallback.saveExecutionLog(format("Deleted Scaling Policies from service %s %n", serviceName), LogLevel.INFO);
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
        format("%nDeregistering Scalable Targets from service %s..%n", serviceName), LogLevel.INFO);

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
            format("Deregistered Scalable Target with Scalable Dimension %s from service %s.. %n",
                scalableTarget.scalableDimension().toString(), serviceName),
            LogLevel.INFO);
      });
      logCallback.saveExecutionLog(
          format("Deregistered Scalable Targets from service %s %n", serviceName), LogLevel.INFO);
    } else {
      logCallback.saveExecutionLog(
          format("Didn't find any Scalable Targets on service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public void attachScalingPolicies(List<String> ecsScalingPolicyManifestContentList, AwsConnectorDTO awsConnectorDTO,
      String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%nAttaching Scaling Policies to service %s.. %n", serviceName), LogLevel.INFO);

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

      logCallback.saveExecutionLog(format("Attached Scaling Policies to service %s %n", serviceName), LogLevel.INFO);
    }
  }

  public void registerScalableTargets(List<String> ecsScalableTargetManifestContentList,
      AwsConnectorDTO awsConnectorDTO, String serviceName, String cluster, String region, LogCallback logCallback) {
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestContentList)) {
      logCallback.saveExecutionLog(
          format("%nRegistering Scalable Targets to service %s..%n", serviceName), LogLevel.INFO);

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

      logCallback.saveExecutionLog(format("Registered Scalable Targets to service %s %n", serviceName), LogLevel.INFO);
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
      EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis,
      boolean sameAsAlreadyRunningInstances, boolean forceNewDeployment) {
    // if service exists create service, otherwise update service
    Optional<Service> optionalService = describeService(createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    if (!(optionalService.isPresent() && isServiceActive(optionalService.get()))) {
      logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);
      CreateServiceResponse createServiceResponse =
          createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

      waitForTasksToBeInRunningState(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
          ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
          eventsAlreadyProcessed, logCallback, timeoutInMillis);

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

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
          EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, forceNewDeployment);

      // same as already running instances
      if (sameAsAlreadyRunningInstances) {
        updateServiceRequest = updateServiceRequest.toBuilder().desiredCount(null).build();
      }

      logCallback.saveExecutionLog(
          format("Updating Service %s with task definition %s and desired count %s %n", updateServiceRequest.service(),
              updateServiceRequest.taskDefinition(),
              updateServiceRequest.desiredCount() == null ? "same as existing" : updateServiceRequest.desiredCount()),
          LogLevel.INFO);
      UpdateServiceResponse updateServiceResponse =
          updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

      waitForTasksToBeInRunningState(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
          ecsInfraConfig.getCluster(), updateServiceRequest.service(), ecsInfraConfig.getRegion(),
          eventsAlreadyProcessed, logCallback, timeoutInMillis);

      ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
          createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

      logCallback.saveExecutionLog(format("Updated Service %s with Arn %s %n", updateServiceRequest.service(),
                                       updateServiceResponse.service().serviceArn()),
          LogLevel.INFO);

      registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

      attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
          service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    }
  }

  private void waitForTasksToBeInRunningState(AwsInternalConfig awsConfig, String clusterName, String serviceName,
      String region, List<ServiceEvent> eventsAlreadyProcessed, LogCallback logCallback, long timeOut) {
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeOut), () -> {
        while (notAllDesiredTasksRunning(
            awsConfig, clusterName, serviceName, region, eventsAlreadyProcessed, logCallback)) {
          sleep(ofSeconds(10));
        }
        return true;
      });
    } catch (UncheckedTimeoutException e) {
      throw new InvalidRequestException("Timed out waiting for tasks to be in running state", e);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for tasks to be in running state", e);
    }
  }

  private boolean notAllDesiredTasksRunning(AwsInternalConfig awsConfig, String clusterName, String serviceName,
      String region, List<ServiceEvent> eventsAlreadyProcessed, LogCallback logCallback) {
    DescribeServicesResponse describeServicesResponse =
        ecsV2Client.describeService(awsConfig, clusterName, serviceName, region);
    Service service = describeServicesResponse.services().get(0);

    log.info("Waiting for pending tasks to finish. {}/{} running ...", service.runningCount(), service.desiredCount());

    logCallback.saveExecutionLog(format("Waiting for pending tasks to finish. %s/%s running ...",
                                     service.runningCount(), service.desiredCount()),
        LogLevel.INFO);

    printAwsEvent(service, eventsAlreadyProcessed, logCallback);

    return !Integer.valueOf(service.desiredCount()).equals(service.runningCount());
  }

  private void printAwsEvent(Service service, List<ServiceEvent> eventsAlreadyProcessed, LogCallback logCallback) {
    List<ServiceEvent> events = new ArrayList<>();
    events.addAll(service.events());

    Set<String> eventIdsProcessed = eventsAlreadyProcessed.stream().map(ServiceEvent::id).collect(toSet());
    events = events.stream().filter(event -> !eventIdsProcessed.contains(event.id())).collect(toList());

    for (int index = events.size() - 1; index >= 0; index--) {
      logCallback.saveExecutionLog(color(new StringBuilder()
                                             .append("# AWS Event: ")
                                             .append(Timestamp.from(events.get(index).createdAt()))
                                             .append(" ")
                                             .append(events.get(index).message())
                                             .toString(),
          Yellow, Bold));
    }

    eventsAlreadyProcessed.addAll(events);
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

    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                     createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                     createServiceRequest.desiredCount()),
        LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
        createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());
    waitForTasksToBeInRunningState(awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO()),
        ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
        eventsAlreadyProcessed, logCallback, timeoutInMillis);

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
        createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

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

  public List<String> getScalableTargetsAsString(LogCallback prepareRollbackDataLogCallback, String serviceName,
                                                Service service, EcsInfraConfig ecsInfraConfig ) {
    List<String> registerScalableTargetRequestBuilderStrings = null;
    prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetching Scalable Target Details for Service %s..", serviceName), LogLevel.INFO);
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
            listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
                    service.serviceName(), ecsInfraConfig.getRegion());
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
    return registerScalableTargetRequestBuilderStrings;
  }

  public List<String> getScalingPoliciesAsString(LogCallback prepareRollbackDataLogCallback, String serviceName,
                                                 Service service, EcsInfraConfig ecsInfraConfig) {
    List<String> registerScalingPolicyRequestBuilderStrings = null;
    prepareRollbackDataLogCallback.saveExecutionLog(
            format("Fetching Scaling Policy Details for Service %s..", serviceName), LogLevel.INFO);
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
            listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
                    service.serviceName(), ecsInfraConfig.getRegion());

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
    return registerScalingPolicyRequestBuilderStrings;
  }



  public String createStageService(String ecsServiceDefinitionManifestContent,
                                 List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
                                 EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis,
                                 EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest,
                                 String taskDefinitionArn, String targetGroupArn) {

    // render target group arn value in its expression in ecs service definition yaml
    ecsServiceDefinitionManifestContent = updateTargetGroupArn(ecsServiceDefinitionManifestContent, targetGroupArn,
            ecsBlueGreenCreateServiceRequest.getTargetGroupArnKey());

    CreateServiceRequest createServiceRequest = parseYamlAsObject(
            ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass()).build();

    // get stage service name
    String stageServiceName = getNonBlueVersionServiceName(trim(createServiceRequest.serviceName()+DELIMITER), ecsInfraConfig);

    Optional<Service> optionalService = describeService(ecsInfraConfig.getCluster(),
            stageServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    //delete the existing non-blue version
    if (optionalService.isPresent() && isServiceActive(optionalService.get())) {
      Service service = optionalService.get();
      deleteService(
              service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
              createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
              (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
    }

    // add green tag in create service request
    CreateServiceRequest.Builder createServiceRequestBuilder = addGreenTagInCreateServiceRequest(createServiceRequest);

    // update service name, cluster and task definition
     createServiceRequest = createServiceRequestBuilder
            .serviceName(stageServiceName)
            .cluster(ecsInfraConfig.getCluster())
            .taskDefinition(taskDefinitionArn)
            .build();

    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                    createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                    createServiceRequest.desiredCount()),
            LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
            createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
         timeoutInMillis, eventsAlreadyProcessed);

    logCallback.saveExecutionLog(format("Created Service %s with Arn %s %n", createServiceRequest.serviceName(),
                    createServiceResponse.service().serviceArn()),
            LogLevel.INFO);

    registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);
    return  createServiceResponse.service().serviceName();
  }

  public void swapTargetGroups(EcsInfraConfig ecsInfraConfig, LogCallback logCallback,
                               EcsLoadBalancerConfig ecsLoadBalancerConfig,
                               AwsInternalConfig awsInternalConfig) {

    // modify prod listener rule with stage target group
    modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getProdListenerArn(),
            ecsLoadBalancerConfig.getProdListenerRuleArn(),
            ecsLoadBalancerConfig.getStageTargetGroupArn(), awsInternalConfig);

    // modify stage listener rule with prod target group
    modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getStageListenerArn(),
            ecsLoadBalancerConfig.getStageListenerRuleArn(),
            ecsLoadBalancerConfig.getProdTargetGroupArn(), awsInternalConfig);

  }

  public void updateTag(String serviceName, EcsInfraConfig ecsInfraConfig, String value, AwsInternalConfig awsInternalConfig) {
    // Describe ecs service and get service details
    Optional<Service> optionalService = describeService(
            ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    if(optionalService.isPresent() && isServiceActive(optionalService.get())) {
      UntagResourceRequest untagResourceRequest = UntagResourceRequest.builder()
              .resourceArn(optionalService.get().serviceArn())
              .tagKeys(BG_VERSION)
              .build();

      // remove BG tag from service
      ecsV2Client.untagService(awsInternalConfig, untagResourceRequest, ecsInfraConfig.getRegion());
      TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
              .resourceArn(optionalService.get().serviceArn())
              .tags(Tag.builder().key(BG_VERSION).value(value).build())
              .build();

      // update BG tag to service
      ecsV2Client.tagService(awsInternalConfig, tagResourceRequest, ecsInfraConfig.getRegion());
    }
    else{

    }
  }

  public UpdateServiceResponse updateDesiredCount(String serviceName, EcsInfraConfig ecsInfraConfig,AwsInternalConfig awsInternalConfig,
                                 int desiredCount) {
    // Describe ecs service and get service details
    Optional<Service> optionalService = describeService(
            ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    if(optionalService.isPresent() && isServiceActive(optionalService.get())) {
      UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder()
              .service(optionalService.get().serviceName())
              .cluster(ecsInfraConfig.getCluster())
              .desiredCount(desiredCount)
              .build();
      // updating desired count
      return ecsV2Client.updateService(awsInternalConfig, updateServiceRequest, ecsInfraConfig.getRegion());
    }
    //todo modify it
    throw new InvalidRequestException("service is not active, not able to update it.");
  }



  private void modifyListenerRule(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn,
                                  String targetGroupArn, AwsInternalConfig awsInternalConfig) {
    // check if listener rule is default one in listener
    if(checkForDefaultRule(ecsInfraConfig, listenerArn, listenerRuleArn, awsInternalConfig)){
      // update listener with target group
      modifyDefaultListenerRule(ecsInfraConfig, listenerArn, targetGroupArn, awsInternalConfig);
    }
    else {
      // update listener rule with target group
      modifySpecificListenerRule(ecsInfraConfig, listenerRuleArn, targetGroupArn, awsInternalConfig);
    }
  }

  private void modifyDefaultListenerRule(EcsInfraConfig ecsInfraConfig, String listenerArn,
                                         String targetGroupArn, AwsInternalConfig awsInternalConfig) {
    ModifyListenerRequest modifyListenerRequest = ModifyListenerRequest.builder()
            .listenerArn(listenerArn)
            .defaultActions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    elbV2Client.modifyListener(awsInternalConfig, modifyListenerRequest, ecsInfraConfig.getRegion());
  }

  private void modifySpecificListenerRule(EcsInfraConfig ecsInfraConfig, String listenerRuleArn,
                                          String targetGroupArn, AwsInternalConfig awsInternalConfig) {
    ModifyRuleRequest modifyRuleRequest = ModifyRuleRequest.builder()
            .ruleArn(listenerRuleArn)
            .actions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    elbV2Client.modifyRule(awsInternalConfig, modifyRuleRequest, ecsInfraConfig.getRegion());
  }

  private boolean checkForDefaultRule(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn,
                                      AwsInternalConfig awsInternalConfig) {
    String nextToken = null;
    do {
      DescribeRulesRequest describeRulesRequest = DescribeRulesRequest.builder()
              .listenerArn(listenerArn)
              .marker(nextToken)
              .pageSize(10)
              .build();
      DescribeRulesResponse describeRulesResponse = elbV2Client.describeRules(awsInternalConfig,describeRulesRequest,
              ecsInfraConfig.getRegion());
      List<Rule> currentRules = describeRulesResponse.rules();
      if (EmptyPredicate.isNotEmpty(currentRules)) {
        Optional<Rule> defaultRule = currentRules.stream().filter(Rule::isDefault).findFirst();
        if (defaultRule.isPresent() && listenerRuleArn.equalsIgnoreCase(defaultRule.get().ruleArn())) {
          return true;
        }
      }
      nextToken = describeRulesResponse.nextMarker();
    } while (nextToken != null);
    return false;
  }

  public Optional<String> getBlueVersionServiceName(String servicePrefix, EcsInfraConfig ecsInfraConfig) {
    // check for service suffix 1
    String firstVersionServiceName = servicePrefix + 1;

    Optional<Service> optionalService = describeService(ecsInfraConfig.getCluster(),
            firstVersionServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    // if service with suffix 1 is active and has blue tag, then its blue version
    if(optionalService.isPresent() && isServiceActive(optionalService.get()) &&
            isServiceBGVersion(optionalService.get().tags(),BG_BLUE)) {
      return Optional.of(firstVersionServiceName);
    }

    // check for service suffix 2
    String secondVersionServiceName = servicePrefix + 2;
    optionalService = describeService(ecsInfraConfig.getCluster(),
            secondVersionServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    // if service with suffix 2 is active and has blue tag, then its blue version
    if(optionalService.isPresent() && isServiceActive(optionalService.get()) &&
            isServiceBGVersion(optionalService.get().tags(),BG_BLUE)) {
      return Optional.of(secondVersionServiceName);
    }
    return Optional.empty();
  }

  public String getNonBlueVersionServiceName(String servicePrefix, EcsInfraConfig ecsInfraConfig) {
    // check for service suffix 1
    String firstVersionServiceName = servicePrefix + 1;

    Optional<Service> optionalService = describeService(ecsInfraConfig.getCluster(),
            firstVersionServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    // if service with suffix 1 is active and has not blue tag, then its green version
    if(optionalService.isPresent() && isServiceActive(optionalService.get()) &&
            !isServiceBGVersion(optionalService.get().tags(),BG_BLUE)) {
      return firstVersionServiceName;
    }

    // check for service suffix 2
    String secondVersionServiceName = servicePrefix + 2;
    optionalService = describeService(ecsInfraConfig.getCluster(),
            secondVersionServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    // if service with suffix 2 is active and has not blue tag, then its green version
    if(optionalService.isPresent() && isServiceActive(optionalService.get()) &&
            !isServiceBGVersion(optionalService.get().tags(),BG_BLUE)) {
      return secondVersionServiceName;
    }
    return firstVersionServiceName;
  }

  private CreateServiceRequest.Builder addGreenTagInCreateServiceRequest(CreateServiceRequest serviceRequest) {
    List<Tag> tags = newArrayList();
    tags.addAll(serviceRequest.tags());

    Tag greenTag = Tag.builder().key(BG_VERSION).value(BG_GREEN).build();
    tags.add(greenTag);
    return serviceRequest.toBuilder().tags(tags);
  }

  private String updateTargetGroupArn(String ecsServiceDefinitionManifestContent, String targetGroupArn,
                                      String targetGroupArnKey) {
    if(ecsServiceDefinitionManifestContent.contains(targetGroupArnKey)) {
      ecsServiceDefinitionManifestContent =
              ecsServiceDefinitionManifestContent.replace(targetGroupArnKey, targetGroupArn);
    }
    //todo: fail it before target group expression not found
    return ecsServiceDefinitionManifestContent;
  }

  public String getTargetGroupArnFromLoadBalancer(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn,
                                              String loadBalancer, AwsInternalConfig awsInternalConfig) {
    DescribeLoadBalancersRequest describeLoadBalancersRequest = DescribeLoadBalancersRequest.builder()
            .names(loadBalancer)
            .build();
    DescribeLoadBalancersResponse describeLoadBalancersResponse = elbV2Client.describeLoadBalancer(awsInternalConfig,
            describeLoadBalancersRequest, ecsInfraConfig.getRegion());
    if(EmptyPredicate.isEmpty(describeLoadBalancersResponse.loadBalancers())) {
      throw new InvalidRequestException("load balancer with name:" + loadBalancer+ "is not present in this aws account");
    }
    String loadBalancerArn = describeLoadBalancersResponse.loadBalancers().get(0).loadBalancerArn();
    List<Listener> listeners = newArrayList();
    String nextToken=null;
    do{
      DescribeListenersRequest describeListenersRequest = DescribeListenersRequest.builder()
              .loadBalancerArn(loadBalancerArn)
              .marker(nextToken)
              .pageSize(10)
              .build();
      DescribeListenersResponse describeListenersResponse = elbV2Client.describeListener(awsInternalConfig,
              describeListenersRequest, ecsInfraConfig.getRegion());
      listeners.addAll(describeListenersResponse.listeners());
      nextToken=describeLoadBalancersResponse.nextMarker();
    }
    while(nextToken!=null);
    if(EmptyPredicate.isNotEmpty(listeners)) {
      for(Listener listener: listeners) {
        if(isListenerArnMatching(listenerArn, listener))
          return getFirstTargetGroupFromListener(awsInternalConfig, ecsInfraConfig, listenerArn, listenerRuleArn);
      }
    }
    throw new InvalidRequestException("listener with arn:" + listenerArn+ "is not present in load balancer: "+loadBalancer);
  }

  private String getFirstTargetGroupFromListener(AwsInternalConfig awsInternalConfig,
                                                 EcsInfraConfig ecsInfraConfig, String listenerArn,
                                                 String listenerRuleArn) {
    List<Rule> rules = newArrayList();
    String nextToken=null;
    do{
      DescribeRulesRequest describeRulesRequest = DescribeRulesRequest.builder()
              .listenerArn(listenerArn)
              .marker(nextToken)
              .pageSize(10)
              .build();
      DescribeRulesResponse describeRulesResponse = elbV2Client.describeRules(awsInternalConfig, describeRulesRequest,
              ecsInfraConfig.getRegion());
      rules.addAll(describeRulesResponse.rules());
      nextToken=describeRulesResponse.nextMarker();
    }
    while(nextToken!=null);

    if(EmptyPredicate.isNotEmpty(rules)) {
      for (Rule rule : rules) {
        if (isListenerRuleArnMatching(listenerRuleArn, rule)) {
          return getFirstTargetGroupFromListenerRule(rule);
        }
      }
    }
    throw new InvalidRequestException("listener rule with arn: "+listenerRuleArn+" is not present in listener: "+listenerArn);
  }

  private String getFirstTargetGroupFromListenerRule(Rule rule) {
    if(EmptyPredicate.isNotEmpty(rule.actions())){
      Action action = rule.actions().stream().findFirst().orElse(null);
      if(action== null || EmptyPredicate.isEmpty(action.targetGroupArn())) {
        throw new InvalidRequestException("No action is present in listener rule:"+
                rule.ruleArn()+" or there is no target group attached");
      }
      return action.targetGroupArn();
    }
    throw new InvalidRequestException("No action is present in listener rule: "+ rule.ruleArn());
  }

  private boolean isListenerRuleArnMatching(String listenerRuleArn, Rule rule) {
    if(EmptyPredicate.isNotEmpty(rule.ruleArn()) && listenerRuleArn.equalsIgnoreCase(rule.ruleArn())){
      return true;
    }
    return false;
  }

  private boolean isListenerArnMatching(String listenerArn, Listener listener) {
    if(EmptyPredicate.isNotEmpty(listener.listenerArn()) && listenerArn.equalsIgnoreCase(listener.listenerArn())){
      return true;
    }
    return false;
  }

  public void deleteServices(List<Service> services, EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long
                             timeoutInMillis) {
    if(EmptyPredicate.isNotEmpty(services)) {
      services.forEach(service -> {
        deleteService(
                service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

        ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
                service.serviceName(), ecsInfraConfig.getRegion(),
                (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
      });
    }
  }

  private boolean isServiceBGVersion(List<Tag> tags, String version) {
    if (isEmpty(tags)) {
      return false;
    }
    Optional<Tag> tag =
            tags.stream()
                    .filter(serviceTag -> BG_VERSION.equals(serviceTag.key()) && version.equals(serviceTag.value()))
                    .findFirst();
    return tag.isPresent();
  }

  public boolean isServiceActive(Service service) {
    return service != null && service.status().equals("ACTIVE");
  }

}
