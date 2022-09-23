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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.Arrays;
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

public class EcsRollingDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback deployLogCallback;
  @Spy @InjectMocks private EcsRollingDeployCommandTaskHandler ecsRollingDeployCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().cluster("cluster").region("us-east-1").build();
    EcsRollingDeployRequest ecsRollingDeployRequest = EcsRollingDeployRequest.builder()
                                                          .timeoutIntervalInMin(10)
                                                          .ecsInfraConfig(ecsInfraConfig)
                                                          .ecsTaskDefinitionManifestContent("taskDef")
                                                          .ecsServiceDefinitionManifestContent("serviceDef")
                                                          .ecsScalableTargetManifestContentList(Arrays.asList("scale"))
                                                          .ecsScalingPolicyManifestContentList(Arrays.asList("policy"))
                                                          .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
        RegisterTaskDefinitionRequest.builder().family("ecs");
    CreateServiceRequest.Builder createServiceRequestBuilder = CreateServiceRequest.builder();
    doReturn(deployLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    doReturn(registerTaskDefinitionRequestBuilder).when(ecsCommandTaskHelper).parseYamlAsObject(eq("taskDef"), any());
    doReturn(createServiceRequestBuilder).when(ecsCommandTaskHelper).parseYamlAsObject(eq("serviceDef"), any());

    TaskDefinition taskDefinition = TaskDefinition.builder().family("ecs").revision(1).taskDefinitionArn("arn").build();
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse =
        RegisterTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build();
    doReturn(registerTaskDefinitionResponse).when(ecsCommandTaskHelper).createTaskDefinition(any(), any(), any());

    EcsRollingDeployResponse ecsCommandResponse =
        (EcsRollingDeployResponse) ecsRollingDeployCommandTaskHandler.executeTaskInternal(
            ecsRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsCommandResponse.getEcsRollingDeployResult().getRegion()).isEqualTo("us-east-1");
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
    ecsRollingDeployCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
