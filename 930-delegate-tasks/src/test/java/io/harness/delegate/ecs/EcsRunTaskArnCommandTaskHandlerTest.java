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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRunTaskArnRequest;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

public class EcsRunTaskArnCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String taskDefinitionArn = "arn:aws:ecs:us-east-1:479370281431:task-definition/task-auto:1899";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock LogCallback runTaskLogCallback;
  @Spy @InjectMocks EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Spy @InjectMocks private EcsRunTaskArnCommandTaskHandler ecsRunTaskArnCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalRunTaskArnTest() throws Exception {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().cluster("cluster").region("us-east-1").build();

    EcsRunTaskArnRequest ecsRunTaskArnRequest = EcsRunTaskArnRequest.builder()
                                                    .timeoutIntervalInMin(10)
                                                    .ecsInfraConfig(ecsInfraConfig)
                                                    .ecsTaskDefinition(taskDefinitionArn)
                                                    .ecsRunTaskRequestDefinitionManifestContent("runTaskRequestDef")
                                                    .skipSteadyStateCheck(false)
                                                    .ecsCommandType(EcsCommandTypeNG.ECS_RUN_TASK_ARN)
                                                    .commandName("EcsRunTask")
                                                    .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    doReturn(runTaskLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.runTask.toString(), true, commandUnitsProgress);
    DescribeTaskDefinitionRequest describeTaskDefinitionRequest =
        DescribeTaskDefinitionRequest.builder().taskDefinition(taskDefinitionArn).build();
    TaskDefinition taskDefinition =
        TaskDefinition.builder().family("task-auto").revision(1899).taskDefinitionArn(taskDefinitionArn).build();
    DescribeTaskDefinitionResponse describeTaskDefinitionResponse =
        DescribeTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build();
    doReturn(describeTaskDefinitionResponse)
        .when(ecsCommandTaskHelper)
        .describeTaskDefinition(
            describeTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    doReturn(RunTaskRequest.builder()).when(ecsCommandTaskHelper).parseYamlAsObject(eq("runTaskRequestDef"), any());

    doReturn(RunTaskResponse.builder().build()).when(ecsCommandTaskHelper).runTask(any(), any(), any());

    doNothing().when(ecsCommandTaskHelper).waitAndDoSteadyStateCheck(any(), any(), any(), any(), any(), any());

    EcsRunTaskResponse ecsRunTaskResponse = (EcsRunTaskResponse) ecsRunTaskArnCommandTaskHandler.executeTaskInternal(
        ecsRunTaskArnRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsRunTaskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRunTaskResponse.getEcsRunTaskResult().getRegion()).isEqualTo("us-east-1");
    RunTaskRequest runTaskRequest =
        RunTaskRequest.builder().taskDefinition(taskDefinitionArn).cluster(ecsInfraConfig.getCluster()).build();

    verify(ecsCommandTaskHelper)
        .runTask(runTaskRequest, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getRegion());
    verify(runTaskLogCallback).saveExecutionLog("Success.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }
}
