/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsRollingDeployCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsRollingDeployRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsRollingDeployRequest"));
    }
    EcsRollingDeployRequest ecsRollingDeployRequest = (EcsRollingDeployRequest) ecsCommandRequest;

    timeoutInMillis = ecsRollingDeployRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsRollingDeployRequest.getEcsInfraConfig();

    LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    try {
      String ecsTaskDefinitionManifestContent = ecsRollingDeployRequest.getEcsTaskDefinitionManifestContent();
      String ecsServiceDefinitionManifestContent = ecsRollingDeployRequest.getEcsServiceDefinitionManifestContent();
      List<String> ecsScalableTargetManifestContentList =
          ecsRollingDeployRequest.getEcsScalableTargetManifestContentList();
      List<String> ecsScalingPolicyManifestContentList =
          ecsRollingDeployRequest.getEcsScalingPolicyManifestContentList();

      RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
          ecsCommandTaskHelper.parseYamlAsObject(
              ecsTaskDefinitionManifestContent, RegisterTaskDefinitionRequest.serializableBuilderClass());
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
      CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
          ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());

      deployLogCallback.saveExecutionLog(
          format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);

      RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
          registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
      String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
      String taskDefinitionArn = taskDefinition.taskDefinitionArn();

      deployLogCallback.saveExecutionLog(
          format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);

      // replace cluster and task definition
      CreateServiceRequest createServiceRequest =
          createServiceRequestBuilder.cluster(ecsInfraConfig.getCluster())
              .taskDefinition(registerTaskDefinitionResponse.taskDefinition().taskDefinitionArn())
              .build();

      ecsCommandTaskHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
          ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback, timeoutInMillis);
      EcsRollingDeployResult ecsRollingDeployResult =
          EcsRollingDeployResult.builder()
              .region(ecsInfraConfig.getRegion())
              .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                  ecsInfraConfig.getCluster(), createServiceRequest.serviceName(), ecsInfraConfig.getRegion()))
              .build();
      EcsRollingDeployResponse ecsRollingDeployResponse = EcsRollingDeployResponse.builder()
                                                              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                              .ecsRollingDeployResult(ecsRollingDeployResult)
                                                              .build();
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Successful."), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return ecsRollingDeployResponse;

    } catch (Exception ex) {
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }
}
