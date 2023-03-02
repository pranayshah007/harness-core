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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
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
import io.harness.rule.Owner;

import com.google.api.client.util.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsCanaryDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String taskDefinitionName = "family:1";
  private final String taskDefinitionArn = "arn";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock LogCallback deployLogCallback;
  @Mock private EcsDeploymentHelper ecsDeploymentHelper;

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
                                                        .ecsScalableTargetManifestContentList(Lists.newArrayList())
                                                        .ecsScalingPolicyManifestContentList(Lists.newArrayList())
                                                        .ecsServiceNameSuffix("canary")
                                                        .ecsCommandType(EcsCommandTypeNG.ECS_CANARY_DEPLOY)
                                                        .build();
    EcsCanaryDeployResponse ecsCanaryDeployResponse =
        EcsCanaryDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsCanaryDeployResult(
                EcsCanaryDeployResult.builder().region("us-east-1").canaryServiceName("ecscanary").build())
            .build();

    doReturn(deployLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    doReturn(ecsCanaryDeployResponse)
        .when(ecsDeploymentHelper)
        .deployCanaryService(
            eq(deployLogCallback), any(), eq(ecsCanaryDeployRequest), anyList(), anyList(), anyLong(), anyString());

    EcsCanaryDeployResponse actualEcsCanaryDeployResponse =
        (EcsCanaryDeployResponse) ecsCanaryDeployCommandTaskHandler.executeTaskInternal(
            ecsCanaryDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(actualEcsCanaryDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualEcsCanaryDeployResponse.getEcsCanaryDeployResult().getCanaryServiceName()).isEqualTo("ecscanary");
    assertThat(actualEcsCanaryDeployResponse.getEcsCanaryDeployResult().getRegion()).isEqualTo("us-east-1");
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
    ecsCanaryDeployCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
