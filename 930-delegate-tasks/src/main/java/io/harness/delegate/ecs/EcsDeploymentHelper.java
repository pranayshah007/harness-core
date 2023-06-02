/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsDeploymentHelper {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private static final String TASK_COMPLETION_LOG = "Completed task execution for command: {}";
  private static final String DEPLOYMENT_SUCCESSFUL_LOG = "\n Deployment Successful.";

  private static final Long TIMEOUT_MULTIPLIER_IN_SECONDS = 60000L;

  public EcsRollingDeployResponse deployRollingService(LogCallback deployLogCallback,
      CreateServiceRequest createServiceRequest, EcsCommandRequest ecsCommandRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      boolean isSameAsAlreadyRunningInstances, boolean isForceNewDeployment) {
    EcsInfraConfig ecsInfraConfig = ecsCommandRequest.getEcsInfraConfig();
    long timeoutInMillis = ecsCommandRequest.getTimeoutIntervalInMin() * TIMEOUT_MULTIPLIER_IN_SECONDS;
    ecsCommandTaskHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis,
        isSameAsAlreadyRunningInstances, isForceNewDeployment);

    EcsRollingDeployResult ecsRollingDeployResult =
        EcsRollingDeployResult.builder()
            .serviceName(createServiceRequest.serviceName())
            .region(ecsInfraConfig.getRegion())
            .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion()))
            .build();

    EcsRollingDeployResponse ecsRollingDeployResponse = EcsRollingDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .ecsRollingDeployResult(ecsRollingDeployResult)
                                                            .build();

    deployLogCallback.saveExecutionLog(
        color(DEPLOYMENT_SUCCESSFUL_LOG, Green, Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    log.info(TASK_COMPLETION_LOG, ecsCommandRequest.getEcsCommandType().name());
    return ecsRollingDeployResponse;
  }

  public EcsCanaryDeployResponse deployCanaryService(LogCallback deployLogCallback,
      CreateServiceRequest createServiceRequest, EcsCommandRequest ecsCommandRequest,
      List<String> ecsScalableTargetManifestContentList, List<String> ecsScalingPolicyManifestContentList,
      Long desiredCountOverride, String ecsServiceNameSuffix) {
    EcsInfraConfig ecsInfraConfig = ecsCommandRequest.getEcsInfraConfig();
    long timeoutInMillis = ecsCommandRequest.getTimeoutIntervalInMin() * TIMEOUT_MULTIPLIER_IN_SECONDS;
    // update canary details in create service request
    String canaryServiceName = createServiceRequest.serviceName() + ecsServiceNameSuffix;
    CreateServiceRequest finalCreateServiceRequest =
        createServiceRequest.toBuilder()
            .serviceName(canaryServiceName)
            .desiredCount(Integer.parseInt(Long.toString(desiredCountOverride)))
            .build();

    ecsCommandTaskHelper.createCanaryService(finalCreateServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis);

    EcsCanaryDeployResult ecsCanaryDeployResult =
        EcsCanaryDeployResult
            .builder()

            .region(ecsInfraConfig.getRegion())
            .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                ecsInfraConfig.getCluster(), finalCreateServiceRequest.serviceName(), ecsInfraConfig.getRegion()))
            .canaryServiceName(canaryServiceName)
            .build();

    deployLogCallback.saveExecutionLog(
        color(DEPLOYMENT_SUCCESSFUL_LOG, Green, Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    log.info(TASK_COMPLETION_LOG, ecsCommandRequest.getEcsCommandType().name());
    return EcsCanaryDeployResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .ecsCanaryDeployResult(ecsCanaryDeployResult)
        .build();
  }

  public EcsBlueGreenCreateServiceResponse deployStageService(LogCallback deployLogCallback,
      EcsCommandRequest ecsCommandRequest, List<String> ecsScalableTargetManifestContentList,
      List<String> ecsScalingPolicyManifestContentList, String ecsTaskDefinitionManifestContent,
      String ecsServiceDefinitionManifestContent, String ecsTaskDefinitionArn,
      EcsLoadBalancerConfig ecsLoadBalancerConfig, String targetGroupArnKey) {
    EcsInfraConfig ecsInfraConfig = ecsCommandRequest.getEcsInfraConfig();
    long timeoutInMillis = ecsCommandRequest.getTimeoutIntervalInMin() * TIMEOUT_MULTIPLIER_IN_SECONDS;
    deployLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);

    if (EmptyPredicate.isNotEmpty(ecsTaskDefinitionArn)) {
      deployLogCallback.saveExecutionLog(
          color(format("%n ECS Task Definition ARN: %n"), White, Bold) + ecsTaskDefinitionArn);
    }

    ecsCommandTaskHelper.printEcsManifestsContent(ecsTaskDefinitionManifestContent, ecsServiceDefinitionManifestContent,
        ecsScalableTargetManifestContentList, ecsScalingPolicyManifestContentList, deployLogCallback);

    // target group arn from stage listener and stage listener rule arn
    String targetGroupArn = ecsLoadBalancerConfig.getStageTargetGroupArn();
    String finalEcsTaskDefinitionArn = ecsTaskDefinitionArn == null
        ? createTaskDefinition(ecsTaskDefinitionManifestContent, deployLogCallback, ecsInfraConfig)
        : ecsTaskDefinitionArn;

    String serviceName = ecsCommandTaskHelper.createStageService(ecsServiceDefinitionManifestContent,
        ecsScalableTargetManifestContentList, ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback,
        timeoutInMillis, targetGroupArnKey, finalEcsTaskDefinitionArn, targetGroupArn);

    EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
        EcsBlueGreenCreateServiceResult.builder()
            .region(ecsInfraConfig.getRegion())
            .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion()))
            .targetGroupArn(targetGroupArn)
            .isNewServiceCreated(true)
            .serviceName(serviceName)
            .loadBalancer(ecsLoadBalancerConfig.getLoadBalancer())
            .listenerArn(ecsLoadBalancerConfig.getStageListenerArn())
            .listenerRuleArn(ecsLoadBalancerConfig.getStageListenerRuleArn())
            .build();
    EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse =
        EcsBlueGreenCreateServiceResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsBlueGreenCreateServiceResult(ecsBlueGreenCreateServiceResult)
            .build();
    deployLogCallback.saveExecutionLog(
        color(DEPLOYMENT_SUCCESSFUL_LOG, Green, Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    log.info(TASK_COMPLETION_LOG, ecsCommandRequest.getEcsCommandType().name());
    return ecsBlueGreenCreateServiceResponse;
  }

  public CreateServiceRequest createServiceDefinitionRequest(LogCallback deployLogCallback,
      EcsInfraConfig ecsInfraConfig, String ecsTaskDefinitionManifestContent,
      String ecsServiceDefinitionManifestContent, List<String> ecsScalableTargetManifestContentList,
      List<String> ecsScalingPolicyManifestContentList, String ecsTaskDefinitionArn) {
    deployLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);

    if (EmptyPredicate.isNotEmpty(ecsTaskDefinitionArn)) {
      deployLogCallback.saveExecutionLog(
          color(format("%n ECS Task Definition ARN: %n"), White, Bold) + ecsTaskDefinitionArn);
    }
    ecsCommandTaskHelper.printEcsManifestsContent(ecsTaskDefinitionManifestContent, ecsServiceDefinitionManifestContent,
        ecsScalableTargetManifestContentList, ecsScalingPolicyManifestContentList, deployLogCallback);

    CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());
    if (ecsTaskDefinitionArn == null) {
      ecsTaskDefinitionArn = createTaskDefinition(ecsTaskDefinitionManifestContent, deployLogCallback, ecsInfraConfig);
    }
    // replace cluster and task definition
    return createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster())
        .taskDefinition(ecsTaskDefinitionArn)
        .build();
  }

  public String createTaskDefinition(
      String taskDefinitionContent, LogCallback deployLogCallback, EcsInfraConfig ecsInfraConfig) {
    // create task definition
    RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        taskDefinitionContent, RegisterTaskDefinitionRequest.serializableBuilderClass());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
    deployLogCallback.saveExecutionLog(
        format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
        registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
    String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
    deployLogCallback.saveExecutionLog(
        format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinition.taskDefinitionArn()),
        LogLevel.INFO);
    // return task definition arn
    return taskDefinition.taskDefinitionArn();
  }
}
