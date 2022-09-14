package io.harness.delegate.task.ecs;

import static com.amazonaws.services.ecs.model.ServiceField.TAGS;
import static com.google.common.collect.Lists.newArrayList;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static java.lang.String.format;
import static java.util.Comparator.comparingInt;
import static org.apache.commons.lang3.StringUtils.trim;

import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenSwapTargetGroupsRequest;
import io.harness.exception.InvalidRequestException;
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
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceField;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
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
  private YamlUtils yamlUtils = new YamlUtils();
  private static final String DELIMITER = "__";
  private static final String BG_VERSION = "BG_VERSION";
  private static final String BG_GREEN = "GREEN";
  private static final String BG_BLUE = "BLUE";
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
      logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                       createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                       createServiceRequest.desiredCount()),
          LogLevel.INFO);
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
          format("Updating Service %s with task definition %s and desired count %s %n", updateServiceRequest.service(),
              updateServiceRequest.taskDefinition(), updateServiceRequest.desiredCount()),
          LogLevel.INFO);
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

    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                                     createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                                     createServiceRequest.desiredCount()),
        LogLevel.INFO);
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



  public void createStageService(String ecsServiceDefinitionManifestContent,
                                 List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
                                 EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis,
                                 EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest,
                                 String taskDefinitionArn) {

    // find target group arn from stage listener and stage listener rule arn
    String targetGroupArn = getTargetGroupArnFromListener(ecsInfraConfig, ecsBlueGreenCreateServiceRequest.getStageListener(),
            ecsBlueGreenCreateServiceRequest.getStageListenerRuleArn());

    // render target group arn value in its expression in ecs service definition yaml
    ecsServiceDefinitionManifestContent = updateTargetGroupArn(ecsServiceDefinitionManifestContent, targetGroupArn);

    CreateServiceRequest tempServiceRequest = parseYamlAsObject(
            ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass()).build();
    //todo: change into builder to crate new req

    // get list of all available prefix matching services in cluster
    List<Service> existingBGServices = getMatchingServicesInCluster(ecsInfraConfig, trim(tempServiceRequest.serviceName()+DELIMITER));

    // get green version services using tags
    List<Service> greenVersionServices = existingBGServices.stream()
            .filter(service -> isServiceBGVersion(service.tags(), BG_GREEN))
            .collect(Collectors.toList());
    //todo: delete services other than blue and check regex to handle corner cases

    //deleting all existing green versions of service
    deleteServices(greenVersionServices, ecsInfraConfig, logCallback, timeoutInMillis);

    // add green tag in create service request
    CreateServiceRequest.Builder createServiceRequestBuilder = addGreenTagInCreateServiceRequest(tempServiceRequest);

    // get stage service name
    String stageServiceName = evaluateNewStageServiceName(existingBGServices,trim(tempServiceRequest.serviceName()+DELIMITER));

    // update service name, cluster and task definition
    CreateServiceRequest createServiceRequest = createServiceRequestBuilder.serviceName(stageServiceName)
            .cluster(ecsInfraConfig.getCluster())
            .taskDefinition(taskDefinitionArn)
            .build();

    logCallback.saveExecutionLog(format("Creating Service %s with task definition %s and desired count %s %n",
                    createServiceRequest.serviceName(), createServiceRequest.taskDefinition(),
                    createServiceRequest.desiredCount()),
            LogLevel.INFO);
    CreateServiceResponse createServiceResponse =
            createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
    //todo: take develop pull and correct service steady check

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

  public void swapTargetGroups(EcsInfraConfig ecsInfraConfig, LogCallback logCallback, long timeoutInMillis,
                          EcsBlueGreenSwapTargetGroupsRequest ecsBlueGreenSwapTargetGroupsRequest) {

    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    //todo: refactor awsInternalConfig

    // get prod target group arn
    String prodTargetGroup = getTargetGroupArnFromListener(ecsInfraConfig, ecsBlueGreenSwapTargetGroupsRequest.getProdListener(),
            ecsBlueGreenSwapTargetGroupsRequest.getProdListenerRuleArn());
    //todo: get target group from previous step outcome

    // get stage target group arn
    String stageTargetGroup = getTargetGroupArnFromListener(ecsInfraConfig, ecsBlueGreenSwapTargetGroupsRequest.getStageListener(),
            ecsBlueGreenSwapTargetGroupsRequest.getStageListenerRuleArn());
    //todo: get target group from previous step outcome

    // modify prod listener rule with stage target group
    modifyListenerRule(ecsInfraConfig, ecsBlueGreenSwapTargetGroupsRequest.getProdListener(),
            ecsBlueGreenSwapTargetGroupsRequest.getProdListenerRuleArn(),
            stageTargetGroup, awsInternalConfig);

    // modify stage listener rule with prod target group
    modifyListenerRule(ecsInfraConfig, ecsBlueGreenSwapTargetGroupsRequest.getStageListener(),
            ecsBlueGreenSwapTargetGroupsRequest.getStageListenerRuleArn(),
            prodTargetGroup, awsInternalConfig);

    // update tags
    // downsize old service

  }

  private void modifyListenerRule(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn,
                                  String targetGroupArn, AwsInternalConfig awsInternalConfig) {
    // check if listener rule is default one in listener
    if(checkForDefaultRule(ecsInfraConfig, listenerArn, listenerRuleArn, awsInternalConfig)){
      // update listener with target group
      modifyDefaultListenerRule(ecsInfraConfig, listenerArn, targetGroupArn, awsInternalConfig);
    }
    // update listener rule with target group
    modifySpecificListenerRule(ecsInfraConfig, listenerRuleArn, targetGroupArn, awsInternalConfig);
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

  private String evaluateNewStageServiceName(List<Service> services, String servicePrefix) {
    // get blue version service using tags
    Service blueVersionService = services.stream()
            .filter(service -> isServiceBGVersion(service.tags(), BG_BLUE))
            .findFirst()
            .orElse(null);
    if(blueVersionService!=null && blueVersionService.serviceName().contains(servicePrefix)) {
      int newVersion =  getVersionFromServiceName(blueVersionService.serviceName())+1;
      return servicePrefix+newVersion;
    }
    return servicePrefix+1;
  }

  private CreateServiceRequest.Builder addGreenTagInCreateServiceRequest(CreateServiceRequest serviceRequest) {
    List<Tag> tags = newArrayList();
    tags.addAll(serviceRequest.tags());

    Tag greenTag = Tag.builder().key(BG_VERSION).value(BG_GREEN).build();
    tags.add(greenTag);
    return CreateServiceRequest.builder()
            .cluster(serviceRequest.cluster())
            .serviceName(serviceRequest.serviceName())
            .taskDefinition(serviceRequest.taskDefinition())
            .loadBalancers(serviceRequest.loadBalancers())
            .serviceRegistries(serviceRequest.serviceRegistries())
            .desiredCount(serviceRequest.desiredCount())
            .clientToken(serviceRequest.clientToken())
            .launchType(serviceRequest.launchType())
            .capacityProviderStrategy(serviceRequest.capacityProviderStrategy())
            .platformVersion(serviceRequest.platformVersion())
            .role(serviceRequest.role())
            .deploymentConfiguration(serviceRequest.deploymentConfiguration())
            .placementConstraints(serviceRequest.placementConstraints())
            .placementStrategy(serviceRequest.placementStrategy())
            .networkConfiguration(serviceRequest.networkConfiguration())
            .healthCheckGracePeriodSeconds(serviceRequest.healthCheckGracePeriodSeconds())
            .schedulingStrategy(serviceRequest.schedulingStrategy())
            .deploymentController(serviceRequest.deploymentController())
            .tags(tags)
            .enableECSManagedTags(serviceRequest.enableECSManagedTags())
            .propagateTags(serviceRequest.propagateTags())
            .enableExecuteCommand(serviceRequest.enableExecuteCommand());
  }

  private String updateTargetGroupArn(String ecsServiceDefinitionManifestContent, String targetGroupArn) {
    if(ecsServiceDefinitionManifestContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
      ecsServiceDefinitionManifestContent =
              ecsServiceDefinitionManifestContent.replaceAll(TARGET_GROUP_ARN_EXPRESSION, targetGroupArn);
    }
    return ecsServiceDefinitionManifestContent;
  }

  public String getTargetGroupArnFromListener(EcsInfraConfig ecsInfraConfig, String listenerArn, String listenerRuleArn ) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    String nextToken=null;
    List<Rule> rules = newArrayList();
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
    else {
      throw new InvalidRequestException("listener rule is not present in listener: "+listenerArn);
    }
    throw new InvalidRequestException("listener rule arn: "+listenerRuleArn+" is not present in listener: "+listenerArn);
  }

  private String getFirstTargetGroupFromListenerRule(Rule rule) {
    if(EmptyPredicate.isNotEmpty(rule.actions())){
      Action action = rule.actions().stream().findFirst().orElse(null);
      if(action== null || EmptyPredicate.isEmpty(action.targetGroupArn())) {
        throw new InvalidRequestException("action is not present in listener rule:"+
                rule.ruleArn()+" or there is no target group attached");
      }
      return action.targetGroupArn();
    }
    throw new InvalidRequestException("action is not present in listener rule: "+ rule.ruleArn());
  }

  private boolean isListenerRuleArnMatching(String listenerRuleArn, Rule rule) {
    if(EmptyPredicate.isNotEmpty(rule.ruleArn()) && listenerRuleArn.equalsIgnoreCase(rule.ruleArn())){
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

  private List<Service> getMatchingServicesInCluster(EcsInfraConfig ecsInfraConfig, String serviceNamePrefix){
    // get all available services in cluster
    List<Service> allAvailableServicesInCluster = getAvailableServicesInCluster(ecsInfraConfig);

    // filtering by service name prefix
    if(EmptyPredicate.isNotEmpty(allAvailableServicesInCluster)) {
      return allAvailableServicesInCluster.stream()
              .filter(service -> matchWithServiceRegex(service.serviceName(), serviceNamePrefix))
              .sorted(comparingInt(service-> getVersionFromServiceName(service.serviceName())))
              .collect(Collectors.toList());
    }
    return newArrayList();
  }

  public List<Service> getAvailableServicesInCluster(EcsInfraConfig ecsInfraConfig) {
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    List<String> serviceArns = newArrayList();
    String nextToken=null;
    do {
      ListServicesRequest listServicesRequest = ListServicesRequest.builder()
              .cluster(ecsInfraConfig.getCluster())
              .nextToken(nextToken)
              .build();
      ListServicesResponse listServicesResponse = ecsV2Client.listServices(awsInternalConfig, listServicesRequest, ecsInfraConfig.getRegion());
      if(EmptyPredicate.isNotEmpty(listServicesResponse.serviceArns())){
        serviceArns.addAll(listServicesResponse.serviceArns());
      }
      nextToken = listServicesResponse.nextToken();
    }
    while(nextToken!=null);
    int counter = 0;
    List<Service> allServices = newArrayList();
    while (counter < serviceArns.size()) {
      // Describing 10 services at a time.
      List<String> arnsBatch = newArrayList();
      for (int i = 0; i < 10 && counter < serviceArns.size(); i++) {
        arnsBatch.add(serviceArns.get(counter));
        counter++;
      }
      DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
              .cluster(ecsInfraConfig.getCluster())
              .services(arnsBatch)
              .include(ServiceField.TAGS)
              .build();
      DescribeServicesResponse describeServicesResponse =
              ecsV2Client.describeServices(awsInternalConfig, describeServicesRequest, ecsInfraConfig.getRegion());
      allServices.addAll(describeServicesResponse.services());
    }
    return allServices;
  }

  private int getVersionFromServiceName(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        try {
          return Integer.parseInt(name.substring(index + DELIMITER.length()));
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    return -1;
  }

  private boolean matchWithServiceRegex(String serviceNameToMatch, String serviceNameForPattern) {
    String pattern = new StringBuilder(64)
            .append("^")
            .append(getServicePrefixByRemovingNumber(serviceNameForPattern))
            .append("[0-9]+$")
            .toString();
    return Pattern.compile(pattern).matcher(serviceNameToMatch).matches();
  }

  private String getServicePrefixByRemovingNumber(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        try {
          return name.substring(0, index + DELIMITER.length());
        } catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    return name;
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
