/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

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
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
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

public class EcsRollingDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock LogCallback deployLogCallback;
  @Spy @InjectMocks private EcsRollingDeployCommandTaskHandler ecsRollingDeployCommandTaskHandler;
  @Mock private EcsDeploymentHelper ecsDeploymentHelper;

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
                                                          .ecsCommandType(EcsCommandTypeNG.ECS_ROLLING_DEPLOY)
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
        .deployRollingService(eq(deployLogCallback), any(), eq(ecsRollingDeployRequest), anyList(), anyList(),
            anyBoolean(), anyBoolean());
    EcsRollingDeployResponse actualEcsCommandResponse =
        (EcsRollingDeployResponse) ecsRollingDeployCommandTaskHandler.executeTaskInternal(
            ecsRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(actualEcsCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualEcsCommandResponse.getEcsRollingDeployResult().getRegion()).isEqualTo("us-east-1");
    verify(ecsDeploymentHelper, times(1))
        .createServiceDefinitionRequest(
            eq(deployLogCallback), eq(ecsInfraConfig), anyString(), anyString(), anyList(), anyList(), eq(null));
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
