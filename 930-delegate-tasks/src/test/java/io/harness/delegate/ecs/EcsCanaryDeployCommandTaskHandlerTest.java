/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCanaryDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

public class EcsCanaryDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String taskDefinitionName = "family:1";
  private final String taskDefinitionArn = "arn";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback deployLogCallback;

  @Spy @InjectMocks private EcsCanaryDeployCommandTaskHandler ecsCanaryDeployCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().region("us-east-1").ecsInfraType(EcsInfraType.ECS).build();
    EcsCanaryDeployRequest ecsCanaryDeployRequest = EcsCanaryDeployRequest.builder()
                                                        .timeoutIntervalInMin(10)
                                                        .commandUnitsProgress(commandUnitsProgress)
                                                        .ecsInfraConfig(ecsInfraConfig)
                                                        .ecsTaskDefinitionManifestContent("taskDef")
                                                        .ecsServiceDefinitionManifestContent("serviceDef")
                                                        .desiredCountOverride(1L)
                                                        .ecsServiceNameSuffix("canary")
                                                        .build();

    RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
        RegisterTaskDefinitionRequest.builder().family("ecs").taskRoleArn("arn");
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster("cluster");
    doReturn(deployLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    doReturn(registerTaskDefinitionRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("taskDef", RegisterTaskDefinitionRequest.serializableBuilderClass());
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("serviceDef", CreateServiceRequest.serializableBuilderClass());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
    TaskDefinition taskDefinition =
        TaskDefinition.builder().taskDefinitionArn("arn").revision(1).family("family").build();
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse =
        RegisterTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build();
    doReturn(registerTaskDefinitionResponse)
        .when(ecsCommandTaskHelper)
        .createTaskDefinition(
            registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    EcsCanaryDeployResponse ecsCanaryDeployResponse =
        (EcsCanaryDeployResponse) ecsCanaryDeployCommandTaskHandler.executeTaskInternal(
            ecsCanaryDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsCanaryDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsCanaryDeployResponse.getEcsCanaryDeployResult().getCanaryServiceName()).isEqualTo("ecscanary");
    assertThat(ecsCanaryDeployResponse.getEcsCanaryDeployResult().getRegion()).isEqualTo("us-east-1");
    verify(deployLogCallback)
        .saveExecutionLog(format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()),
            LogLevel.INFO);
    verify(deployLogCallback)
        .saveExecutionLog(
            format("Created Task Definition %s with Arn %s..%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);
    verify(deployLogCallback)
        .saveExecutionLog(color(format("%n Deployment Successful."), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalEcsRollingDeployRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsCanaryDeployCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
