/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.EcsSetupCommandUnit.ERROR;
import static software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSwapRoutesCommandTaskHelper.BG_GREEN;
import static software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSwapRoutesCommandTaskHelper.BG_VERSION;
import static software.wings.service.impl.aws.model.AwsConstants.MAIN_ECS_CONTAINER_NAME_TAG;
import static software.wings.utils.EcsConvention.getServiceNamePrefix;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingInt;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.AwsElbConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.container.EcsContainerTaskUtils;
import software.wings.beans.dto.EcsContainerTask;
import software.wings.beans.dto.EcsServiceSpecification;
import software.wings.cloudprovider.UpdateServiceCountRequestData;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;
import software.wings.utils.EcsConvention;

import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.DeleteServiceRequest;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.SchedulingStrategy;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.ServiceEvent;
import com.amazonaws.services.ecs.model.ServiceRegistry;
import com.amazonaws.services.ecs.model.Tag;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

@OwnedBy(CDP)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsSetupCommandTaskHelper {
  @Inject private AwsClusterService awsClusterService;
  @Inject private AwsAppAutoScalingHelperServiceDelegate awsAppAutoScalingService;
  @Inject private AwsHelperService awsHelperService;
  @Inject private AwsEcsHelperServiceDelegate awsEcsHelperServiceDelegate;
  @Inject private EcsContainerService ecsContainerService;
  private static final String DELIMITER = "__";
  private static final String CONTAINER_NAME_PLACEHOLDER_REGEX = "\\$\\{CONTAINER_NAME}";

  public TaskDefinition createTaskDefinition(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      ExecutionLogCallback executionLogCallback, EcsSetupParams setupParams) {
    String dockerImageName = setupParams.getImageDetails().getName() + ":" + setupParams.getImageDetails().getTag();
    String containerName = EcsConvention.getContainerName(dockerImageName);
    String domainName = setupParams.getImageDetails().getDomainName();
    setupParams.setGeneratedContainerName(containerName);

    EcsContainerTask ecsContainerTask = (EcsContainerTask) setupParams.getContainerTask();
    ecsContainerTask = createEcsContainerTaskIfNull(ecsContainerTask);

    executionLogCallback.saveExecutionLog("Cluster Name: " + setupParams.getClusterName(), LogLevel.INFO);
    executionLogCallback.saveExecutionLog("Docker Image Name: " + dockerImageName, LogLevel.INFO);
    executionLogCallback.saveExecutionLog("Container Name: " + containerName, LogLevel.INFO);

    // create Task definition and register it with AWS
    TaskDefinition taskDefinition;
    if (setupParams.isEcsRegisterTaskDefinitionTagsEnabled()) {
      taskDefinition = createTaskDefinitionParseAsRegisterTaskDefinitionRequest(ecsContainerTask, containerName,
          dockerImageName, setupParams, awsConfig, serviceVariables, safeDisplayServiceVariables, encryptedDataDetails,
          executionLogCallback, domainName);
    } else {
      taskDefinition = createTaskDefinition(ecsContainerTask, containerName, dockerImageName, setupParams, awsConfig,
          serviceVariables, safeDisplayServiceVariables, encryptedDataDetails, executionLogCallback, domainName);
    }
    return taskDefinition;
  }

  @VisibleForTesting
  EcsContainerTask createEcsContainerTaskIfNull(EcsContainerTask ecsContainerTask) {
    if (ecsContainerTask == null) {
      software.wings.beans.container.ContainerDefinition containerDefinition =
          software.wings.beans.container.ContainerDefinition.builder()
              .memory(256)
              .cpu(1d)
              .portMappings(emptyList())
              .build();
      ecsContainerTask = EcsContainerTask.builder().containerDefinitions(newArrayList(containerDefinition)).build();
    }

    return ecsContainerTask;
  }

  /**
   * This method will create TaskDefinition and register it with AWS.
   */
  public TaskDefinition createTaskDefinition(EcsContainerTask ecsContainerTask, String containerName,
      String dockerImageName, EcsSetupParams ecsSetupParams, AwsConfig awsConfig, Map<String, String> serviceVariables,
      Map<String, String> safeDisplayServiceVariables, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback, String domainName) {
    TaskDefinition taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), containerName, dockerImageName,
        ecsSetupParams.getExecutionRoleArn(), domainName);

    // For Awsvpc mode we need to make sure NetworkConfiguration is provided
    String validationMessage = isValidateSetupParamasForECS(taskDefinition, ecsSetupParams);
    if (!isEmptyOrBlank(validationMessage)) {
      StringBuilder builder =
          new StringBuilder().append("Invalid setup params for ECS deployment: ").append(validationMessage);
      executionLogCallback.saveExecutionLog(builder.toString(), LogLevel.ERROR);
      throw new WingsException(builder.toString(), USER).addParam("message", builder.toString());
    }

    taskDefinition.setFamily(ecsSetupParams.getTaskFamily());

    // Set service variables as environment variables
    if (isNotEmpty(serviceVariables)) {
      if (isNotEmpty(safeDisplayServiceVariables)) {
        executionLogCallback.saveExecutionLog("Setting environment variables in container definition", LogLevel.INFO);
        for (Entry<String, String> entry : safeDisplayServiceVariables.entrySet()) {
          executionLogCallback.saveExecutionLog(entry.getKey() + "=" + entry.getValue(), LogLevel.INFO);
        }
      }
      Map<String, KeyValuePair> serviceValuePairs = serviceVariables.entrySet().stream().collect(Collectors.toMap(
          Entry::getKey, entry -> new KeyValuePair().withName(entry.getKey()).withValue(entry.getValue())));
      for (ContainerDefinition containerDefinition : taskDefinition.getContainerDefinitions()) {
        Map<String, KeyValuePair> valuePairsMap = new HashMap<>();
        if (containerDefinition.getEnvironment() != null) {
          containerDefinition.getEnvironment().forEach(
              keyValuePair -> valuePairsMap.put(keyValuePair.getName(), keyValuePair));
        }
        valuePairsMap.putAll(serviceValuePairs);
        containerDefinition.setEnvironment(new ArrayList<>(valuePairsMap.values()));
      }
    }

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        new RegisterTaskDefinitionRequest()
            .withContainerDefinitions(taskDefinition.getContainerDefinitions())
            .withFamily(taskDefinition.getFamily())
            .withTaskRoleArn(taskDefinition.getTaskRoleArn())
            .withNetworkMode(taskDefinition.getNetworkMode())
            .withPlacementConstraints(taskDefinition.getPlacementConstraints())
            .withVolumes(taskDefinition.getVolumes());

    if (taskDefinition.getContainerDefinitions().size() > 1) {
      registerTaskDefinitionRequest.withTags(new Tag().withKey(MAIN_ECS_CONTAINER_NAME_TAG).withValue(containerName));
    }

    if (isNotEmpty(taskDefinition.getExecutionRoleArn())) {
      registerTaskDefinitionRequest.withExecutionRoleArn(taskDefinition.getExecutionRoleArn());
    }

    // Add extra parameters for Fargate launch type
    if (isFargateTaskLauchType(ecsSetupParams)) {
      registerTaskDefinitionRequest.withNetworkMode(NetworkMode.Awsvpc);
      registerTaskDefinitionRequest.setRequiresCompatibilities(Collections.singletonList(LaunchType.FARGATE.name()));
      registerTaskDefinitionRequest.withCpu(taskDefinition.getCpu());
      registerTaskDefinitionRequest.withMemory(taskDefinition.getMemory());
    }

    executionLogCallback.saveExecutionLog(
        format("Creating task definition %s with container image %s", ecsSetupParams.getTaskFamily(), dockerImageName),
        LogLevel.INFO);
    return awsClusterService.createTask(ecsSetupParams.getRegion(), aSettingAttribute().withValue(awsConfig).build(),
        encryptedDataDetails, registerTaskDefinitionRequest);
  }

  /**
   * This method will create TaskDefinition and register it with AWS.
   */
  public TaskDefinition createTaskDefinitionParseAsRegisterTaskDefinitionRequest(EcsContainerTask ecsContainerTask,
      String containerName, String dockerImageName, EcsSetupParams ecsSetupParams, AwsConfig awsConfig,
      Map<String, String> serviceVariables, Map<String, String> safeDisplayServiceVariables,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback, String domainName) {
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), containerName, dockerImageName,
            ecsSetupParams.getExecutionRoleArn(), domainName);

    // For Awsvpc mode we need to make sure NetworkConfiguration is provided
    String validationMessage =
        isValidateSetupParamsForECSRegisterTaskDefinitionRequest(registerTaskDefinitionRequest, ecsSetupParams);
    handleValidationMessage(validationMessage, executionLogCallback);

    registerTaskDefinitionRequest.setFamily(ecsSetupParams.getTaskFamily());

    // Set service variables as environment variables
    if (isNotEmpty(serviceVariables)) {
      if (isNotEmpty(safeDisplayServiceVariables)) {
        executionLogCallback.saveExecutionLog("Setting environment variables in container definition", LogLevel.INFO);
        for (Entry<String, String> entry : safeDisplayServiceVariables.entrySet()) {
          executionLogCallback.saveExecutionLog(entry.getKey() + "=" + entry.getValue(), LogLevel.INFO);
        }
      }
      Map<String, KeyValuePair> serviceValuePairs = serviceVariables.entrySet().stream().collect(Collectors.toMap(
          Entry::getKey, entry -> new KeyValuePair().withName(entry.getKey()).withValue(entry.getValue())));
      for (ContainerDefinition containerDefinition : registerTaskDefinitionRequest.getContainerDefinitions()) {
        Map<String, KeyValuePair> valuePairsMap = new HashMap<>();
        if (containerDefinition.getEnvironment() != null) {
          containerDefinition.getEnvironment().forEach(
              keyValuePair -> valuePairsMap.put(keyValuePair.getName(), keyValuePair));
        }
        valuePairsMap.putAll(serviceValuePairs);
        containerDefinition.setEnvironment(new ArrayList<>(valuePairsMap.values()));
      }
    }

    if (registerTaskDefinitionRequest.getContainerDefinitions().size() > 1) {
      Tag mainEcsContainerNameTag = new Tag().withKey(MAIN_ECS_CONTAINER_NAME_TAG).withValue(containerName);
      if (registerTaskDefinitionRequest.getTags() == null) {
        registerTaskDefinitionRequest.withTags(mainEcsContainerNameTag);
      } else {
        registerTaskDefinitionRequest.getTags().add(mainEcsContainerNameTag);
      }
    }

    if (isEmpty(registerTaskDefinitionRequest.getExecutionRoleArn())) {
      registerTaskDefinitionRequest.withExecutionRoleArn(null);
    }

    if (isEmpty(registerTaskDefinitionRequest.getTags())) {
      ArrayList<Tag> tags = null;
      registerTaskDefinitionRequest.withTags(tags);
    }

    // Add extra parameters for Fargate launch type
    if (isFargateTaskLauchType(ecsSetupParams)) {
      registerTaskDefinitionRequest.withNetworkMode(NetworkMode.Awsvpc);
      registerTaskDefinitionRequest.setRequiresCompatibilities(Collections.singletonList(LaunchType.FARGATE.name()));
    } else {
      registerTaskDefinitionRequest.withCpu(null);
      registerTaskDefinitionRequest.withMemory(null);
    }

    executionLogCallback.saveExecutionLog(
        format("Creating task definition %s with container image %s", ecsSetupParams.getTaskFamily(), dockerImageName),
        LogLevel.INFO);
    return awsClusterService.createTask(ecsSetupParams.getRegion(), aSettingAttribute().withValue(awsConfig).build(),
        encryptedDataDetails, registerTaskDefinitionRequest);
  }

  public void handleValidationMessage(String validationMessage, ExecutionLogCallback executionLogCallback) {
    if (!isEmptyOrBlank(validationMessage)) {
      StringBuilder builder =
          new StringBuilder().append("Invalid setup params for ECS deployment: ").append(validationMessage);
      executionLogCallback.saveExecutionLog(builder.toString(), LogLevel.ERROR);
      throw new WingsException(builder.toString(), USER).addParam("message", builder.toString());
    }
  }

  /**
   * Checks for null, "" and  "    "
   *
   * @param input
   * @return
   */
  public boolean isEmptyOrBlank(String input) {
    // empty checkd for null or 0 size, blank checks for only spaces
    if (StringUtils.isEmpty(input) || isBlank(input)) {
      return true;
    }

    return false;
  }

  /**
   * Check if this task is configured as Fargate lanuch type
   *
   * @param ecsSetupParams
   * @return
   */
  public boolean isFargateTaskLauchType(EcsSetupParams ecsSetupParams) {
    return LaunchType.FARGATE.name().equals(ecsSetupParams.getLaunchType());
  }

  /**
   * For AwsVpcMode we need to make sure NetworkConfiguration i.e. (SubnetId/s, securityGroupId/s) is provided and For
   * fargate in addition to this executionRole is also required
   *
   * @param taskDefinition
   * @param ecsSetupParams
   * @return
   */
  public String isValidateSetupParamasForECS(TaskDefinition taskDefinition, EcsSetupParams ecsSetupParams) {
    StringBuilder errorMessage = new StringBuilder(128);
    if (LaunchType.FARGATE.name().equals(ecsSetupParams.getLaunchType())
        || NetworkMode.Awsvpc.name().equals(taskDefinition.getNetworkMode())) {
      if (isEmptyOrBlank(ecsSetupParams.getVpcId())) {
        errorMessage.append("VPC Id is required for fargate task");
      }

      if (ArrayUtils.isEmpty(ecsSetupParams.getSubnetIds())
          || CollectionUtils.isEmpty(Arrays.stream(ecsSetupParams.getSubnetIds())
                                         .filter(subnet -> !isEmptyOrBlank(subnet))
                                         .collect(toList()))) {
        errorMessage.append("At least 1 subnetId is required for mentioned VPC");
      }

      if (ArrayUtils.isEmpty(ecsSetupParams.getSecurityGroupIds())
          || CollectionUtils.isEmpty(Arrays.stream(ecsSetupParams.getSecurityGroupIds())
                                         .filter(securityGroup -> !isEmptyOrBlank(securityGroup))
                                         .collect(toList()))) {
        errorMessage.append("At least 1 security Group is required for mentioned VPC");
      }
    }

    if (LaunchType.FARGATE.name().equals(ecsSetupParams.getLaunchType())) {
      if (isEmptyOrBlank(taskDefinition.getExecutionRoleArn())) {
        errorMessage.append("Execution Role ARN is required for Fargate tasks");
      }
    }

    return errorMessage.toString();
  }

  /**
   * For AwsVpcMode we need to make sure NetworkConfiguration i.e. (SubnetId/s, securityGroupId/s) is provided and For
   * fargate in addition to this executionRole is also required
   *
   * @param registerTaskDefinitionRequest
   * @param ecsSetupParams
   * @return
   */
  public String isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest, EcsSetupParams ecsSetupParams) {
    StringBuilder errorMessage = new StringBuilder(128);
    if (LaunchType.FARGATE.name().equals(ecsSetupParams.getLaunchType())
        || NetworkMode.Awsvpc.name().equals(registerTaskDefinitionRequest.getNetworkMode())) {
      if (isEmptyOrBlank(ecsSetupParams.getVpcId())) {
        errorMessage.append("VPC Id is required for fargate task");
      }

      if (ArrayUtils.isEmpty(ecsSetupParams.getSubnetIds())
          || CollectionUtils.isEmpty(Arrays.stream(ecsSetupParams.getSubnetIds())
                                         .filter(subnet -> !isEmptyOrBlank(subnet))
                                         .collect(toList()))) {
        errorMessage.append("At least 1 subnetId is required for mentioned VPC");
      }

      if (ArrayUtils.isEmpty(ecsSetupParams.getSecurityGroupIds())
          || CollectionUtils.isEmpty(Arrays.stream(ecsSetupParams.getSecurityGroupIds())
                                         .filter(securityGroup -> !isEmptyOrBlank(securityGroup))
                                         .collect(toList()))) {
        errorMessage.append("At least 1 security Group is required for mentioned VPC");
      }
    }

    if (LaunchType.FARGATE.name().equals(ecsSetupParams.getLaunchType())) {
      if (isEmptyOrBlank(registerTaskDefinitionRequest.getExecutionRoleArn())) {
        errorMessage.append("Execution Role ARN is required for Fargate tasks");
      }
    }

    return errorMessage.toString();
  }

  @VisibleForTesting
  Optional<Service> getLastRunningService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      EcsSetupParams setupParams, String serviceName) {
    List<Service> sortedServiceList =
        getServicesForClusterByMatchingPrefix(awsConfig, setupParams, encryptedDataDetails, serviceName);

    if (isEmpty(sortedServiceList)) {
      return empty();
    }

    for (int i = sortedServiceList.size() - 1; i >= 0; i--) {
      Service service = sortedServiceList.get(i);
      if (service.getServiceName().equals(serviceName)) {
        continue;
      }

      if (service.getDesiredCount() > 0) {
        return of(service);
      }
    }
    return empty();
  }

  public void storeCurrentServiceNameAndCountInfo(AwsConfig awsConfig, EcsSetupParams setupParams,
      List<EncryptedDataDetail> encryptedDataDetails,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder, String serviceName) {
    Optional<Service> currentService = getLastRunningService(awsConfig, encryptedDataDetails, setupParams, serviceName);
    if (currentService.isPresent()) {
      commandExecutionDataBuilder.ecsServiceToBeDownsized(currentService.get().getServiceName());
      commandExecutionDataBuilder.countToBeDownsizedForOldService(currentService.get().getDesiredCount());
    }
  }

  public List<Service> getServicesForClusterByMatchingPrefix(AwsConfig awsConfig, EcsSetupParams setupParams,
      List<EncryptedDataDetail> encryptedDataDetails, String serviceName) {
    List<Service> services = awsEcsHelperServiceDelegate.listServicesForCluster(
        awsConfig, encryptedDataDetails, setupParams.getRegion(), setupParams.getClusterName());
    return services.stream()
        .filter(service -> matchWithRegex(service.getServiceName(), serviceName))
        .sorted(comparingInt(service -> getRevisionFromServiceName(service.getServiceName())))
        .collect(toList());
  }

  public boolean matchWithRegex(String serviceNameToMatch, String serviceNameForPattern) {
    String pattern = new StringBuilder(64)
                         .append("^")
                         .append(getServicePrefixByRemovingNumber(serviceNameForPattern))
                         .append("[0-9]+$")
                         .toString();
    return Pattern.compile(pattern).matcher(serviceNameToMatch).matches();
  }

  public String getServicePrefixByRemovingNumber(String name) {
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

  public int getRevisionFromServiceName(String name) {
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

  @VisibleForTesting
  void logLoadBalancerInfo(ExecutionLogCallback executionLogCallback, EcsSetupParams setupParams) {
    if (!setupParams.isUseRoute53DNSSwap() && setupParams.isUseLoadBalancer()) {
      executionLogCallback.saveExecutionLog("Load Balancer Name: " + setupParams.getLoadBalancerName(), LogLevel.INFO);
      executionLogCallback.saveExecutionLog("Target Group ARN: " + setupParams.getTargetGroupArn(), LogLevel.INFO);
      if (isNotBlank(setupParams.getRoleArn())) {
        executionLogCallback.saveExecutionLog("Role ARN: " + setupParams.getRoleArn(), LogLevel.INFO);
      }
    }
  }

  @VisibleForTesting
  void backupAutoScalarConfig(EcsSetupParams setupParams, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, String containerServiceName,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    Map<String, Integer> activeServiceCounts = awsClusterService.getActiveServiceCounts(setupParams.getRegion(),
        cloudProviderSetting, encryptedDataDetails, setupParams.getClusterName(), containerServiceName);

    if (isEmpty(activeServiceCounts)) {
      return;
    }

    List<String> resourceIds = activeServiceCounts.keySet()
                                   .stream()
                                   .map(serviceName
                                       -> new StringBuilder("service/")
                                              .append(setupParams.getClusterName())
                                              .append("/")
                                              .append(serviceName)
                                              .toString())
                                   .collect(toList());

    executionLogCallback.saveExecutionLog("Checking for Auto-Scalar config for existing services");
    DescribeScalableTargetsResult targetsResult = awsAppAutoScalingService.listScalableTargets(setupParams.getRegion(),
        (AwsConfig) cloudProviderSetting.getValue(), encryptedDataDetails,
        new DescribeScalableTargetsRequest().withServiceNamespace(ServiceNamespace.Ecs).withResourceIds(resourceIds));

    if (isEmpty(targetsResult.getScalableTargets())) {
      executionLogCallback.saveExecutionLog("No Auto-scalar config found for existing services");
      return;
    }

    Map<String, AwsAutoScalarConfig> scalarConfigMap = new HashMap<>();

    targetsResult.getScalableTargets().forEach(scalableTarget -> {
      scalarConfigMap.putIfAbsent(getAutoScalarMapKey(scalableTarget),
          AwsAutoScalarConfig.builder()
              .resourceId(scalableTarget.getResourceId())
              .scalableTargetJson(awsAppAutoScalingService.getJsonForAwsScalableTarget(scalableTarget))
              .build());

      DescribeScalingPoliciesResult policiesResult = awsAppAutoScalingService.listScalingPolicies(
          setupParams.getRegion(), (AwsConfig) cloudProviderSetting.getValue(), encryptedDataDetails,
          new DescribeScalingPoliciesRequest()
              .withResourceId(scalableTarget.getResourceId())
              .withScalableDimension(scalableTarget.getScalableDimension())
              .withServiceNamespace(scalableTarget.getServiceNamespace()));

      List<String> policyJsons = new ArrayList<>();
      AwsAutoScalarConfig config = scalarConfigMap.get(getAutoScalarMapKey(scalableTarget));
      if (isNotEmpty(policiesResult.getScalingPolicies())) {
        policiesResult.getScalingPolicies().forEach(
            scalingPolicy -> { policyJsons.add(awsAppAutoScalingService.getJsonForAwsScalablePolicy(scalingPolicy)); });
      }
      if (isNotEmpty(policyJsons)) {
        config.setScalingPolicyJson(policyJsons.toArray(new String[policyJsons.size()]));
      }
    });

    executionLogCallback.saveExecutionLog("Auto-Scalar Config backed up");
    commandExecutionDataBuilder.previousAwsAutoScalarConfigs(scalarConfigMap.values().stream().collect(toList()));
  }

  @VisibleForTesting
  String getAutoScalarMapKey(ScalableTarget scalableTarget) {
    return scalableTarget.getResourceId() + ":" + scalableTarget.getScalableDimension();
  }

  @VisibleForTesting
  List<String[]> integerMapToListOfStringArray(Map<String, Integer> integerMap) {
    return integerMap.entrySet()
        .stream()
        .map(entry -> new String[] {entry.getKey(), entry.getValue().toString()})
        .collect(toList());
  }

  public CreateServiceRequest getCreateServiceRequest(SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, EcsSetupParams setupParams, TaskDefinition taskDefinition,
      String containerServiceName, ExecutionLogCallback executionLogCallback, Logger logger,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      boolean isMultipleLoadBalancersFeatureFlagActive) {
    boolean isFargateTaskType = isFargateTaskLauchType(setupParams);
    CreateServiceRequest createServiceRequest =
        new CreateServiceRequest()
            .withServiceName(containerServiceName)
            .withCluster(setupParams.getClusterName())
            .withTaskDefinition(taskDefinition.getFamily() + ":" + taskDefinition.getRevision());

    if (setupParams.isBlueGreen()) {
      createServiceRequest.withTags(new Tag().withKey(BG_VERSION).withValue(BG_GREEN));
    }

    // For DAEMON scheduling Strategy, no desired count is required.
    // Its automatically calculated by ECS based on number of instances in cluster
    if (setupParams.isDaemonSchedulingStrategy()) {
      createServiceRequest.setSchedulingStrategy(SchedulingStrategy.DAEMON.name());
      createServiceRequest.withDeploymentConfiguration(
          new DeploymentConfiguration().withMaximumPercent(100).withMinimumHealthyPercent(50));
    } else {
      createServiceRequest.setDesiredCount(0);
      createServiceRequest.withDeploymentConfiguration(
          new DeploymentConfiguration().withMaximumPercent(200).withMinimumHealthyPercent(100));
      createServiceRequest.setSchedulingStrategy(SchedulingStrategy.REPLICA.name());
    }

    // Set load balancer config
    if (!setupParams.isUseRoute53DNSSwap() && setupParams.isUseLoadBalancer()) {
      executionLogCallback.saveExecutionLog("Setting load balancer to service");
      setLoadBalancerToService(setupParams, cloudProviderSetting, encryptedDataDetails, taskDefinition,
          createServiceRequest, awsClusterService, executionLogCallback, isMultipleLoadBalancersFeatureFlagActive);
    }

    // for Fargate, where network mode is "awsvpc", setting taskRole causes error.
    if (!isFargateTaskType) {
      createServiceRequest.withRole(setupParams.getRoleArn());
    } else {
      createServiceRequest.withLaunchType(LaunchType.FARGATE);
    }

    // For Awsvpc Network mode (Fargate / ECS Ec2 deployment with awsvpc mode), we need to setup
    // NetworkConfig, as it will be used by aws to create ENI
    if (isFargateTaskType || NetworkMode.Awsvpc.name().equalsIgnoreCase(taskDefinition.getNetworkMode())) {
      AssignPublicIp assignPublicIp = AssignPublicIp.DISABLED;

      if (isFargateTaskType) {
        assignPublicIp = setupParams.isAssignPublicIps() ? AssignPublicIp.ENABLED : AssignPublicIp.DISABLED;
      }

      createServiceRequest.withNetworkConfiguration(
          new NetworkConfiguration().withAwsvpcConfiguration(new AwsVpcConfiguration()
                                                                 .withSecurityGroups(setupParams.getSecurityGroupIds())
                                                                 .withSubnets(setupParams.getSubnetIds())
                                                                 .withAssignPublicIp(assignPublicIp)));
    }

    // Handle Advanced Scenario (This is ECS Service json spec provided by user)
    EcsServiceSpecification serviceSpecification = setupParams.getEcsServiceSpecification();
    List<ServiceRegistry> serviceRegistries = newArrayList();
    if (serviceSpecification != null && isNotBlank(serviceSpecification.getServiceSpecJson())) {
      // Replace $Container_NAME string if exists, with actual container name
      if (setupParams.getEcsServiceSpecification() != null
          && isNotBlank(setupParams.getEcsServiceSpecification().getServiceSpecJson())) {
        String dockerImageName = setupParams.getImageDetails().getName() + ":" + setupParams.getImageDetails().getTag();
        String containerName = EcsConvention.getContainerName(dockerImageName);
        EcsServiceSpecification specification = setupParams.getEcsServiceSpecification();
        specification.setServiceSpecJson(
            specification.getServiceSpecJson().replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName));
      }

      Service advancedServiceConfig = getAwsServiceFromJson(serviceSpecification.getServiceSpecJson(), logger);
      validateServiceRegistries(advancedServiceConfig.getServiceRegistries(), taskDefinition, executionLogCallback);

      createServiceRequest.setPlacementStrategy(advancedServiceConfig.getPlacementStrategy());
      createServiceRequest.setPlacementConstraints(advancedServiceConfig.getPlacementConstraints());
      createServiceRequest.setHealthCheckGracePeriodSeconds(advancedServiceConfig.getHealthCheckGracePeriodSeconds());
      createServiceRequest.setEnableExecuteCommand(advancedServiceConfig.getEnableExecuteCommand());

      if (isNotEmpty(advancedServiceConfig.getServiceRegistries())) {
        serviceRegistries.addAll(advancedServiceConfig.getServiceRegistries());
      }
      setDeploymentConfiguration(createServiceRequest, advancedServiceConfig);
      createServiceRequest.setTags(advancedServiceConfig.getTags());

      if (isFargateTaskType && isNotEmpty(advancedServiceConfig.getPlatformVersion())) {
        createServiceRequest.setPlatformVersion(advancedServiceConfig.getPlatformVersion());
      }

      // This will only work for Daemon service. Reason is, these tags are only propgates for tasks
      // those are created with serviceCreation. We always create service with 0 count and
      // then upsize it in all case other than daemon (where ECS launches tasks with service creation)
      createServiceRequest.setPropagateTags(advancedServiceConfig.getPropagateTags());

      if (advancedServiceConfig.getCapacityProviderStrategy() != null
          && CollectionUtils.isNotEmpty(advancedServiceConfig.getCapacityProviderStrategy())) {
        createServiceRequest.setCapacityProviderStrategy(advancedServiceConfig.getCapacityProviderStrategy());
        // If a capacityProviderStrategy is specified, the launchType parameter must be omitted.
        createServiceRequest.setLaunchType(null);
      }
    }
    setServiceRegistryForDNSSwap((AwsConfig) cloudProviderSetting.getValue(), encryptedDataDetails, setupParams,
        containerServiceName, serviceRegistries, executionLogCallback, logger, commandExecutionDataBuilder);
    createServiceRequest.setServiceRegistries(serviceRegistries);
    return createServiceRequest;
  }

  @VisibleForTesting
  void setServiceRegistryForDNSSwap(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      EcsSetupParams setupParams, String serviceName, List<ServiceRegistry> serviceRegistries,
      ExecutionLogCallback logCallback, Logger logger,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder) {
    if (!setupParams.isBlueGreen() || !setupParams.isUseRoute53DNSSwap()) {
      return;
    }
    commandExecutionDataBuilder.useRoute53Swap(true);
    commandExecutionDataBuilder.parentRecordName(setupParams.getParentRecordName());
    commandExecutionDataBuilder.parentRecordHostedZoneId(setupParams.getParentRecordHostedZoneId());

    String dockerImageName = setupParams.getImageDetails().getName() + ":" + setupParams.getImageDetails().getTag();
    String containerName = EcsConvention.getContainerName(dockerImageName);
    String registry1JSON =
        setupParams.getServiceDiscoveryService1JSON().replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName);
    String registry2JSON =
        setupParams.getServiceDiscoveryService2JSON().replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName);
    ServiceRegistry registry1 = getServiceRegistryFromJson(registry1JSON, logger);
    ServiceRegistry registry2 = getServiceRegistryFromJson(registry2JSON, logger);

    Optional<Service> currentRunningService =
        getLastRunningService(awsConfig, encryptedDataDetails, setupParams, serviceName);
    if (!currentRunningService.isPresent() || isEmpty(currentRunningService.get().getServiceRegistries())) {
      logCallback.saveExecutionLog("No currently running service found. OR no service registries found with it");
      logCallback.saveExecutionLog(format("Using: [%s] for new service.", registry1.getRegistryArn()));
      serviceRegistries.add(registry1);
      commandExecutionDataBuilder.newServiceDiscoveryArn(registry1.getRegistryArn());
      commandExecutionDataBuilder.oldServiceDiscoveryArn(registry2.getRegistryArn());
      return;
    }

    Set<String> registries = currentRunningService.get()
                                 .getServiceRegistries()
                                 .stream()
                                 .map(ServiceRegistry::getRegistryArn)
                                 .collect(toSet());

    ServiceRegistry oldRegistry;
    ServiceRegistry newRegistry;
    if (registries.contains(registry1.getRegistryArn())) {
      oldRegistry = registry1;
      newRegistry = registry2;
    } else if (registries.contains(registry2.getRegistryArn())) {
      oldRegistry = registry2;
      newRegistry = registry1;
    } else {
      logCallback.saveExecutionLog("Current Ecs Service not associated with any of the 2 registries.");
      newRegistry = registry1;
      oldRegistry = registry2;
    }
    logCallback.saveExecutionLog(format("Current Ess service uses: [%s]", oldRegistry.getRegistryArn()));
    commandExecutionDataBuilder.oldServiceDiscoveryArn(oldRegistry.getRegistryArn());
    logCallback.saveExecutionLog(format("Using: [%s] for new service.", newRegistry.getRegistryArn()));
    commandExecutionDataBuilder.newServiceDiscoveryArn(newRegistry.getRegistryArn());
    serviceRegistries.add(newRegistry);
  }

  public void setLoadBalancerToService(EcsSetupParams setupParams, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, TaskDefinition taskDefinition,
      CreateServiceRequest createServiceRequest, AwsClusterService awsClusterService,
      ExecutionLogCallback executionLogCallback, boolean isMultipleLoadBalancersFeatureFlagActive) {
    Integer containerPort = null;
    String containerName = null;

    if (isNotBlank(setupParams.getTargetContainerName())) {
      setupParams.setTargetContainerName(setupParams.getTargetContainerName().replaceAll(
          CONTAINER_NAME_PLACEHOLDER_REGEX, setupParams.getGeneratedContainerName()));
    }

    String targetContainerName = setupParams.getTargetContainerName();
    String targetPort = setupParams.getTargetPort();

    if (isNotBlank(targetContainerName) && isNotBlank(targetPort)) {
      containerName = targetContainerName;

      if (!StringUtils.isNumeric(targetPort.trim())) {
        StringBuilder builder =
            new StringBuilder().append("Invalid port : ").append(targetPort).append(". It should be a number");
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      containerPort = Integer.parseInt(targetPort);

    } else if (isBlank(targetContainerName) && isBlank(targetPort)) {
      TargetGroup targetGroup = awsClusterService.getTargetGroup(
          setupParams.getRegion(), cloudProviderSetting, encryptedDataDetails, setupParams.getTargetGroupArn());

      if (targetGroup == null) {
        StringBuilder builder = new StringBuilder()
                                    .append("Target group is null for the given ARN: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      final Integer targetGroupPort = targetGroup.getPort();

      if (targetGroupPort == null) {
        StringBuilder builder = new StringBuilder()
                                    .append("Target group port is null for the given ARN: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      List<ContainerDefinition> containerDefinitionList = taskDefinition.getContainerDefinitions();

      Multimap<ContainerDefinition, PortMapping> portMappingListWithTargetPort = HashMultimap.create();
      containerDefinitionList.forEach(containerDefinition -> {
        List<PortMapping> portMappings = containerDefinition.getPortMappings();

        if (portMappings == null) {
          return;
        }

        List<PortMapping> portMappingList =
            portMappings.stream()
                .filter(portMapping
                    -> portMapping.getContainerPort().equals(targetGroupPort)
                        || (portMapping.getHostPort() != null && portMapping.getHostPort().equals(targetGroupPort)))
                .collect(toList());
        portMappingListWithTargetPort.putAll(containerDefinition, portMappingList);
      });

      Set<ContainerDefinition> containerDefinitionSet = portMappingListWithTargetPort.keySet();
      if (isEmpty(containerDefinitionSet)) {
        StringBuilder builder = new StringBuilder()
                                    .append("No container definition has port mapping that matches the target port: ")
                                    .append(targetGroupPort)
                                    .append(" for target group: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      int portMatchCount = containerDefinitionSet.size();
      if (portMatchCount > 1) {
        StringBuilder builder = new StringBuilder()
                                    .append("Only one port mapping should match the target port: ")
                                    .append(targetGroupPort)
                                    .append(" for target group: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      ContainerDefinition containerDefinition = containerDefinitionSet.iterator().next();
      containerName = containerDefinition.getName();

      Collection<PortMapping> portMappings = portMappingListWithTargetPort.get(containerDefinition);

      if (isEmpty(portMappings)) {
        StringBuilder builder = new StringBuilder()
                                    .append("No container definition has port mapping that match the target port: ")
                                    .append(targetGroupPort)
                                    .append(" for target group: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      if (portMappings.size() > 1) {
        StringBuilder builder = new StringBuilder()
                                    .append("Only one port mapping should match the target port: ")
                                    .append(targetGroupPort)
                                    .append(" for target group: ")
                                    .append(setupParams.getTargetGroupArn());
        executionLogCallback.saveExecutionLog(ERROR + builder.toString());
        throw new WingsException(builder.toString());
      }

      PortMapping portMapping = portMappings.iterator().next();

      containerPort = portMapping.getContainerPort();
    }

    List<LoadBalancer> loadBalancers = new ArrayList<>();

    if (containerName != null && containerPort != null) {
      loadBalancers.add(new LoadBalancer()
                            .withContainerName(containerName)
                            .withContainerPort(containerPort)
                            .withTargetGroupArn(setupParams.getTargetGroupArn()));
      createServiceRequest.withLoadBalancers(loadBalancers);
    } else {
      StringBuilder builder =
          new StringBuilder()
              .append("Could not obtain container name and port to set to the target for target group: ")
              .append(setupParams.getTargetGroupArn());
      executionLogCallback.saveExecutionLog(ERROR + builder.toString());
      throw new WingsException(builder.toString());
    }
    if (isMultipleLoadBalancersFeatureFlagActive && EmptyPredicate.isNotEmpty(setupParams.getAwsElbConfigs())) {
      setAdditionalLoadBalancersToService(setupParams, cloudProviderSetting, encryptedDataDetails, taskDefinition,
          createServiceRequest, awsClusterService, executionLogCallback);
    }
  }

  public void setAdditionalLoadBalancersToService(EcsSetupParams setupParams, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, TaskDefinition taskDefinition,
      CreateServiceRequest createServiceRequest, AwsClusterService awsClusterService,
      ExecutionLogCallback executionLogCallback) {
    String generatedContainerName = setupParams.getGeneratedContainerName();
    List<LoadBalancer> loadBalancers = createServiceRequest.getLoadBalancers();

    if (EmptyPredicate.isNotEmpty(setupParams.getAwsElbConfigs())) {
      for (AwsElbConfig awsElbConfig : setupParams.getAwsElbConfigs()) {
        Integer finalTargetContainerPort = null;
        String finalTargetContainerName = null;

        if (isNotBlank(awsElbConfig.getTargetContainerName())) {
          awsElbConfig.setTargetContainerName(awsElbConfig.getTargetContainerName().replaceAll(
              CONTAINER_NAME_PLACEHOLDER_REGEX, generatedContainerName));
        }

        String targetContainerName = awsElbConfig.getTargetContainerName();
        String targetPort = awsElbConfig.getTargetPort();

        if (isNotBlank(targetContainerName) && isNotBlank(targetPort)) {
          finalTargetContainerName = targetContainerName;

          if (!StringUtils.isNumeric(targetPort.trim())) {
            StringBuilder builder =
                new StringBuilder().append("Invalid port : ").append(targetPort).append(". It should be a number");
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          finalTargetContainerPort = Integer.parseInt(targetPort);

        } else if (isBlank(targetContainerName) && isBlank(targetPort)) {
          TargetGroup targetGroup = awsClusterService.getTargetGroup(
              setupParams.getRegion(), cloudProviderSetting, encryptedDataDetails, awsElbConfig.getTargetGroupArn());

          if (targetGroup == null) {
            StringBuilder builder = new StringBuilder()
                                        .append("Target group is null for the given ARN: ")
                                        .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          final Integer targetGroupPort = targetGroup.getPort();

          if (targetGroupPort == null) {
            StringBuilder builder = new StringBuilder()
                                        .append("Target group port is null for the given ARN: ")
                                        .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          List<ContainerDefinition> containerDefinitionList = taskDefinition.getContainerDefinitions();

          Multimap<ContainerDefinition, PortMapping> portMappingListWithTargetPort = HashMultimap.create();
          containerDefinitionList.forEach(containerDefinition -> {
            List<PortMapping> portMappings = containerDefinition.getPortMappings();

            if (portMappings == null) {
              return;
            }

            List<PortMapping> portMappingList =
                portMappings.stream()
                    .filter(portMapping
                        -> portMapping.getContainerPort().equals(targetGroupPort)
                            || (portMapping.getHostPort() != null && portMapping.getHostPort().equals(targetGroupPort)))
                    .collect(toList());
            portMappingListWithTargetPort.putAll(containerDefinition, portMappingList);
          });

          Set<ContainerDefinition> containerDefinitionSet = portMappingListWithTargetPort.keySet();
          if (isEmpty(containerDefinitionSet)) {
            StringBuilder builder =
                new StringBuilder()
                    .append("No container definition has port mapping that matches the target port: ")
                    .append(targetGroupPort)
                    .append(" for target group: ")
                    .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          int portMatchCount = containerDefinitionSet.size();
          if (portMatchCount > 1) {
            StringBuilder builder = new StringBuilder()
                                        .append("Only one port mapping should match the target port: ")
                                        .append(targetGroupPort)
                                        .append(" for target group: ")
                                        .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          ContainerDefinition containerDefinition = containerDefinitionSet.iterator().next();
          finalTargetContainerName = containerDefinition.getName();

          Collection<PortMapping> portMappings = portMappingListWithTargetPort.get(containerDefinition);

          if (isEmpty(portMappings)) {
            StringBuilder builder = new StringBuilder()
                                        .append("No container definition has port mapping that match the target port: ")
                                        .append(targetGroupPort)
                                        .append(" for target group: ")
                                        .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          if (portMappings.size() > 1) {
            StringBuilder builder = new StringBuilder()
                                        .append("Only one port mapping should match the target port: ")
                                        .append(targetGroupPort)
                                        .append(" for target group: ")
                                        .append(awsElbConfig.getTargetGroupArn());
            executionLogCallback.saveExecutionLog(ERROR + builder.toString());
            throw new WingsException(builder.toString());
          }

          PortMapping portMapping = portMappings.iterator().next();

          finalTargetContainerPort = portMapping.getContainerPort();
        }

        if (finalTargetContainerName != null && finalTargetContainerPort != null) {
          loadBalancers.add(new LoadBalancer()
                                .withContainerName(finalTargetContainerName)
                                .withContainerPort(finalTargetContainerPort)
                                .withTargetGroupArn(awsElbConfig.getTargetGroupArn()));
        } else {
          StringBuilder builder =
              new StringBuilder()
                  .append("Could not obtain container name and port to set to the target for target group: ")
                  .append(awsElbConfig.getTargetGroupArn());
          executionLogCallback.saveExecutionLog(ERROR + builder.toString());
          throw new WingsException(builder.toString());
        }
      }

      createServiceRequest.withLoadBalancers(loadBalancers);
    }
  }

  @VisibleForTesting
  ServiceRegistry getServiceRegistryFromJson(String json, Logger logger) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, ServiceRegistry.class);
    } catch (IOException e) {
      String errorMsg = "Failed to Deserialize json into AWS Service object";
      log.error(errorMsg);
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  public Service getAwsServiceFromJson(String json, Logger logger) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, Service.class);
    } catch (IOException e) {
      String errorMsg = "Failed to Deserialize json into AWS Service object";
      log.error(errorMsg);
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  /**
   * Validate if ContainerName and ContianerPort if mentioned in ServiceRegistry in serviceSpec,
   * matches ones defined in TaskDefinitions
   *
   * @param serviceRegistries
   * @param taskDefinition
   * @param executionLogCallback
   * @return
   */
  public void validateServiceRegistries(List<ServiceRegistry> serviceRegistries, TaskDefinition taskDefinition,
      ExecutionLogCallback executionLogCallback) {
    // Validate containerNames in Service Registries match the ones defined in TaskDefinition
    Map<String, ContainerDefinition> nameToContainerDefinitionMap = new HashMap<>();
    taskDefinition.getContainerDefinitions().forEach(
        containerDefinition -> nameToContainerDefinitionMap.put(containerDefinition.getName(), containerDefinition));

    serviceRegistries.forEach(serviceRegistry -> {
      if (isNotBlank(serviceRegistry.getContainerName())) {
        ContainerDefinition containerDefinition = nameToContainerDefinitionMap.get(serviceRegistry.getContainerName());

        // if Container Name is not null, Validate ContainerName is mentioned in ServiceRegistry
        if (containerDefinition == null) {
          String errorMsg = new StringBuilder("Invalid Container name :")
                                .append(serviceRegistry.getContainerName())
                                .append(", mentioned in Service Registry")
                                .toString();
          executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
          throw new WingsException(errorMsg, USER).addParam("message", errorMsg);
        }

        // If containerName is mentioned, ContainerPort mapped to that name in TaskjDefinition must be used
        if (serviceRegistry.getContainerPort() == null
            || isInvalidContainerPortUsed(serviceRegistry, containerDefinition, executionLogCallback)) {
          String errorMsg = new StringBuilder("Invalid Container Port: ")
                                .append(serviceRegistry.getContainerPort())
                                .append(", mentioned in Service Registry for Container Name: ")
                                .append(serviceRegistry.getContainerName())
                                .toString();
          executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
          throw new WingsException(errorMsg, USER).addParam("message", errorMsg);
        }
      }
    });
  }

  @VisibleForTesting
  boolean isInvalidContainerPortUsed(ServiceRegistry serviceRegistry, ContainerDefinition containerDefinition,
      ExecutionLogCallback executionLogCallback) {
    List<PortMapping> portMappings = containerDefinition.getPortMappings();
    Optional<PortMapping> optionalPortMapping =
        portMappings.stream()
            .filter(portMapping -> serviceRegistry.getContainerPort().compareTo(portMapping.getContainerPort()) == 0)
            .findFirst();

    if (!optionalPortMapping.isPresent()) {
      return true;
    }

    return false;
  }

  @VisibleForTesting
  void setDeploymentConfiguration(CreateServiceRequest createServiceRequest, Service advancedServiceConfig) {
    if (advancedServiceConfig.getDeploymentConfiguration() != null
        && advancedServiceConfig.getDeploymentConfiguration().getMaximumPercent() != null
        && advancedServiceConfig.getDeploymentConfiguration().getMinimumHealthyPercent() != null) {
      createServiceRequest.setDeploymentConfiguration(advancedServiceConfig.getDeploymentConfiguration());
    }
  }

  public String createEcsService(EcsSetupParams setupParams, TaskDefinition taskDefinition,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback) {
    return createEcsService(setupParams, taskDefinition, cloudProviderSetting, encryptedDataDetails,
        commandExecutionDataBuilder, executionLogCallback, false);
  }

  public String createEcsService(EcsSetupParams setupParams, TaskDefinition taskDefinition,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      ExecutionLogCallback executionLogCallback, boolean isMultipleLoadBalancersFeatureFlagActive) {
    String containerServiceName =
        EcsConvention.getServiceName(setupParams.getTaskFamily(), taskDefinition.getRevision());

    Map<String, Integer> activeServiceCounts = awsClusterService.getActiveServiceCounts(setupParams.getRegion(),
        cloudProviderSetting, encryptedDataDetails, setupParams.getClusterName(), containerServiceName);

    Integer instanceCountForLatestVersion = Integer.valueOf(0);
    if (isNotEmpty(activeServiceCounts)) {
      List<String> existingServiceNames = new ArrayList<>(activeServiceCounts.keySet());
      Collections.sort(existingServiceNames);

      instanceCountForLatestVersion =
          activeServiceCounts.get(existingServiceNames.get(existingServiceNames.size() - 1));
    }

    commandExecutionDataBuilder.containerServiceName(containerServiceName)
        .activeServiceCounts(integerMapToListOfStringArray(activeServiceCounts))
        .instanceCountForLatestVersion(instanceCountForLatestVersion);

    CreateServiceRequest createServiceRequest = getCreateServiceRequest(cloudProviderSetting, encryptedDataDetails,
        setupParams, taskDefinition, containerServiceName, executionLogCallback, log, commandExecutionDataBuilder,
        isMultipleLoadBalancersFeatureFlagActive);

    executionLogCallback.saveExecutionLog(
        format("Creating ECS service %s in cluster %s ", containerServiceName, setupParams.getClusterName()),
        LogLevel.INFO);

    // create and register service with aws
    awsClusterService.createService(
        setupParams.getRegion(), cloudProviderSetting, encryptedDataDetails, createServiceRequest);

    return containerServiceName;
  }

  public void handleRollback(EcsSetupParams setupParams, SettingAttribute cloudProviderSetting,
      ContainerSetupCommandUnitExecutionDataBuilder commandExecutionDataBuilder,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    if (setupParams.isDaemonSchedulingStrategy()) {
      try {
        // For Daemon service, we cache service spec json for existing service before we did actual deployment,
        // as deployment is being rolled back, update service with same service spec to restore it to original state
        if (isNotEmpty(setupParams.getPreviousEcsServiceSnapshotJson())) {
          Service previousServiceSnapshot = getAwsServiceFromJson(setupParams.getPreviousEcsServiceSnapshotJson(), log);
          UpdateServiceRequest updateServiceRequest =
              new UpdateServiceRequest()
                  .withService(previousServiceSnapshot.getServiceName())
                  .withCluster(setupParams.getClusterName())
                  .withNetworkConfiguration(previousServiceSnapshot.getNetworkConfiguration())
                  .withTaskDefinition(previousServiceSnapshot.getTaskDefinition())
                  .withDeploymentConfiguration(previousServiceSnapshot.getDeploymentConfiguration())
                  .withHealthCheckGracePeriodSeconds(previousServiceSnapshot.getHealthCheckGracePeriodSeconds());

          awsHelperService.updateService(setupParams.getRegion(), (AwsConfig) cloudProviderSetting.getValue(),
              encryptedDataDetails, updateServiceRequest);
          waitForDaemonServiceToReachSteadyState(setupParams.getRegion(), cloudProviderSetting, encryptedDataDetails,
              setupParams.getClusterName(), previousServiceSnapshot.getServiceName(),
              setupParams.getServiceSteadyStateTimeout(), executionLogCallback);
          commandExecutionDataBuilder.containerServiceName(previousServiceSnapshot.getServiceName())
              .ecsTaskDefintion(previousServiceSnapshot.getTaskDefinition())
              .ecsServiceArn(previousServiceSnapshot.getServiceArn());
        } else {
          // For Daemon service, if first launch of the service fails, we delete that service, as there is no way to set
          // 0 tasks for such service. If we dont delete it, ECS will keep trying scheduling tasks on all instances with
          // cluster
          DeleteServiceRequest deleteServiceRequest = new DeleteServiceRequest()
                                                          .withService(setupParams.getEcsServiceArn())
                                                          .withCluster(setupParams.getClusterName());
          executionLogCallback.saveExecutionLog(new StringBuilder(32)
                                                    .append("Deleting Service")
                                                    .append(setupParams.getTaskFamily())
                                                    .append(" as first launch of service failed: ")
                                                    .toString());
          awsHelperService.deleteService(setupParams.getRegion(), (AwsConfig) cloudProviderSetting.getValue(),
              encryptedDataDetails, deleteServiceRequest);
        }
      } catch (TimeoutException e) {
        throw e;
      } catch (Exception e) {
        String errorMsg = "Failed while handling rollback";
        log.error(errorMsg, e);
        throw new WingsException(errorMsg, e, USER).addParam("message", errorMsg);
      }
    }
  }

  public List<ContainerInfo> waitForDaemonServiceToReachSteadyState(String region, SettingAttribute connectorConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String clusterName, String serviceName,
      int serviceSteadyStateTimeout, ExecutionLogCallback executionLogCallback) {
    AwsConfig awsConfig = awsHelperService.validateAndGetAwsConfig(connectorConfig, encryptedDataDetails, false);

    Service service = awsHelperService
                          .describeServices(region, awsConfig, encryptedDataDetails,
                              new DescribeServicesRequest().withCluster(clusterName).withServices(serviceName))
                          .getServices()
                          .get(0);

    UpdateServiceCountRequestData updateCountRequestData = UpdateServiceCountRequestData.builder()
                                                               .region(region)
                                                               .encryptedDataDetails(encryptedDataDetails)
                                                               .cluster(clusterName)
                                                               .desiredCount(service.getDesiredCount())
                                                               .executionLogCallback(executionLogCallback)
                                                               .serviceName(serviceName)
                                                               .timeOut(serviceSteadyStateTimeout)
                                                               .serviceEvents(getEventsFromService(service))
                                                               .awsConfig(awsConfig)
                                                               .build();

    ecsContainerService.waitForTasksToBeInRunningStateWithHandledExceptions(updateCountRequestData);
    ecsContainerService.waitForServiceToReachSteadyState(serviceSteadyStateTimeout, updateCountRequestData);

    return ecsContainerService.getContainerInfosAfterEcsWait(region, awsConfig, encryptedDataDetails, clusterName,
        serviceName, Collections.EMPTY_LIST, executionLogCallback);
  }

  private List<ServiceEvent> getEventsFromService(Service service) {
    List<ServiceEvent> serviceEvents = new ArrayList<>();
    if (service != null && isNotEmpty(service.getEvents())) {
      serviceEvents.addAll(service.getEvents());
    }

    return serviceEvents;
  }
  public void downsizeOldOrUnhealthy(SettingAttribute settingAttribute, EcsSetupParams setupParams,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback,
      boolean timeoutErrorSupported) {
    Map<String, Integer> activeCounts =
        awsClusterService.getActiveServiceCountsByServiceNamePrefix(setupParams.getRegion(), settingAttribute,
            encryptedDataDetails, setupParams.getClusterName(), getServiceNamePrefix(setupParams.getTaskFamily()));
    String latestHealthyController = null;
    if (activeCounts.size() > 1) {
      AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
      executionLogCallback.saveExecutionLog("\nActive tasks:");
      for (Entry<String, Integer> entry : activeCounts.entrySet()) {
        String activeServiceName = entry.getKey();
        List<String> originalTaskArns = awsHelperService
                                            .listTasks(setupParams.getRegion(), awsConfig, encryptedDataDetails,
                                                new ListTasksRequest()
                                                    .withCluster(setupParams.getClusterName())
                                                    .withServiceName(activeServiceName)
                                                    .withDesiredStatus(DesiredStatus.RUNNING),
                                                false)
                                            .getTaskArns();
        List<ContainerInfo> containerInfos =
            ecsContainerService.getContainerInfosAfterEcsWait(setupParams.getRegion(), awsConfig, encryptedDataDetails,
                setupParams.getClusterName(), activeServiceName, originalTaskArns, executionLogCallback);
        boolean allContainersSuccess =
            containerInfos.stream().allMatch(info -> info.getStatus() == ContainerInfo.Status.SUCCESS);
        if (allContainersSuccess) {
          latestHealthyController = activeServiceName;
        }
      }

      for (Entry<String, Integer> entry : activeCounts.entrySet()) {
        String serviceName = entry.getKey();
        if (!serviceName.equals(latestHealthyController)) {
          executionLogCallback.saveExecutionLog("");
          awsClusterService.resizeCluster(setupParams.getRegion(), settingAttribute, encryptedDataDetails,
              setupParams.getClusterName(), serviceName, entry.getValue(), 0,
              setupParams.getServiceSteadyStateTimeout(), executionLogCallback, timeoutErrorSupported);
        }
      }
    }
  }

  /**
   * Delete all older service with desiredCount as 0 while keeping only recent "minRevisionToKeep" no of services
   */
  public void cleanup(SettingAttribute settingAttribute, EcsSetupParams setupParams,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("\nCleaning versions with no tasks", LogLevel.INFO);
    String serviceNamePrefix = getServiceNamePrefix(setupParams.getTaskFamily());
    awsClusterService
        .getServices(setupParams.getRegion(), settingAttribute, encryptedDataDetails, setupParams.getClusterName(),
            serviceNamePrefix)
        .stream()
        .filter(service
            -> EcsConvention.getServiceNamePrefixFromServiceName(service.getServiceName()).equals(serviceNamePrefix))
        .filter(s -> s.getDesiredCount() == 0)
        .forEach(s -> {
          String oldServiceName = s.getServiceName();
          executionLogCallback.saveExecutionLog("Deleting old version: " + oldServiceName, LogLevel.INFO);
          awsClusterService.deleteService(setupParams.getRegion(), settingAttribute, encryptedDataDetails,
              setupParams.getClusterName(), oldServiceName);
        });
  }

  @VisibleForTesting
  boolean isServiceWithSamePrefix(String serviceName, String prefix) {
    if (prefix.length() >= serviceName.length()) {
      return false;
    }
    String temp = serviceName.substring(prefix.length());
    return temp.matches("[0-9]+");
  }

  public Optional<Service> getExistingServiceMetadataSnapshot(EcsSetupParams setupParams,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails, String ecsServiceName,
      AwsHelperService awsHelperService) {
    List<Service> services =
        awsHelperService
            .describeServices(setupParams.getRegion(), (AwsConfig) cloudProviderSetting.getValue(),
                encryptedDataDetails,
                new DescribeServicesRequest().withCluster(setupParams.getClusterName()).withServices(ecsServiceName))
            .getServices();

    if (isEmpty(services)) {
      return empty();
    }

    return Optional.of(services.get(0));
  }

  public String getJsonForAwsServiceConfig(Service service, Logger logger) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(service);
    } catch (JsonProcessingException e) {
      String errorMsg = "Failed to Serialize AWS Service object into json";
      log.error(errorMsg);
      throw new WingsException(ErrorCode.GENERAL_ERROR, errorMsg, USER).addParam("message", errorMsg);
    }
  }

  public String getTargetGroupForDefaultAction(Listener listener, ExecutionLogCallback executionLogCallback) {
    Optional<Action> action = listener.getDefaultActions()
                                  .stream()
                                  .filter(listenerAction
                                      -> "forward".equalsIgnoreCase(listenerAction.getType())
                                          && isNotEmpty(listenerAction.getTargetGroupArn()))
                                  .findFirst();

    if (!action.isPresent()) {
      String errorMsg = new StringBuilder(128)
                            .append("No Forward Action set for Listener: ")
                            .append("ARN: ")
                            .append(listener.getListenerArn())
                            .append("Protocol: ")
                            .append(listener.getProtocol())
                            .append("Port: ")
                            .append(listener.getPort())
                            .append(", to any TargetGroup")
                            .toString();
      executionLogCallback.saveExecutionLog(errorMsg, LogLevel.ERROR);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, errorMsg, USER).addParam("message", errorMsg);
    }

    return action.get().getTargetGroupArn();
  }

  public void deleteExistingServicesOtherThanBlueVersion(EcsSetupParams setupParams,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback) {
    List<Service> services = getServicesForClusterByMatchingPrefix((AwsConfig) cloudProviderSetting.getValue(),
        setupParams, encryptedDataDetails, trim(setupParams.getTaskFamily()) + DELIMITER);

    services = services.stream().filter(service -> isGreenVersion(service.getTags())).collect(toList());

    if (isNotEmpty(services)) {
      services.forEach(service -> {
        executionLogCallback.saveExecutionLog("Deleting Old Service  {Green Version}: " + service.getServiceName());
        awsHelperService.deleteService(setupParams.getRegion(), (AwsConfig) cloudProviderSetting.getValue(),
            encryptedDataDetails,
            new DeleteServiceRequest()
                .withService(service.getServiceArn())
                .withCluster(setupParams.getClusterName())
                .withForce(true));
        executionLogCallback.saveExecutionLog("Deletion successful");
      });
    }
  }

  private boolean isGreenVersion(List<Tag> tags) {
    if (isEmpty(tags)) {
      return false;
    }
    Optional<Tag> tag =
        tags.stream()
            .filter(serviceTag -> BG_VERSION.equals(serviceTag.getKey()) && BG_GREEN.equals(serviceTag.getValue()))
            .findFirst();
    return tag.isPresent();
  }
}
