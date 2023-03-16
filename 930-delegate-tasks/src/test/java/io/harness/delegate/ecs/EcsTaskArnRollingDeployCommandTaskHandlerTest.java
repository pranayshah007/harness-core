/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsTaskArnRollingDeployRequest;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsTaskArnRollingDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock LogCallback deployLogCallback;
  @Spy @InjectMocks private EcsTaskArnRollingDeployCommandTaskHandler ecsTaskArnRollingDeployCommandTaskHandler;
  @Mock private EcsDeploymentHelper ecsDeploymentHelper;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().cluster("cluster").region("us-east-1").build();
    EcsTaskArnRollingDeployRequest ecsTaskArnRollingDeployRequest =
        EcsTaskArnRollingDeployRequest.builder()
            .timeoutIntervalInMin(10)
            .ecsInfraConfig(ecsInfraConfig)
            .ecsTaskDefinitionArn("taskArn")
            .ecsServiceDefinitionManifestContent("serviceDef")
            .ecsScalableTargetManifestContentList(Arrays.asList("scale"))
            .ecsScalingPolicyManifestContentList(Arrays.asList("policy"))
            .ecsCommandType(EcsCommandTypeNG.ECS_TASK_ARN_ROLLING_DEPLOY)
            .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsRollingDeployResponse ecsRollingDeployResponse =
        EcsRollingDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsRollingDeployResult(EcsRollingDeployResult.builder().region("us-east-1").build())
            .build();
    doReturn(deployLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    doReturn(ecsRollingDeployResponse)
        .when(ecsDeploymentHelper)
        .deployRollingService(eq(deployLogCallback), any(), eq(ecsTaskArnRollingDeployRequest), anyList(), anyList(),
            anyBoolean(), anyBoolean());
    EcsRollingDeployResponse actualEcsCommandResponse =
        (EcsRollingDeployResponse) ecsTaskArnRollingDeployCommandTaskHandler.executeTaskInternal(
            ecsTaskArnRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(actualEcsCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualEcsCommandResponse.getEcsRollingDeployResult().getRegion()).isEqualTo("us-east-1");
    verify(ecsDeploymentHelper, times(1))
        .createServiceDefinitionRequest(
            eq(deployLogCallback), eq(ecsInfraConfig), eq(null), anyString(), anyList(), anyList(), eq("taskArn"));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalEcsTaskArnRollingDeployRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsTaskArnRollingDeployCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
