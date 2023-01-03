/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.SAINATH;

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
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
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
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

public class EcsRunTaskCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Spy @InjectMocks EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback runTaskLogCallback;
  @Spy @InjectMocks private EcsRunTaskCommandTaskHandler ecsRunTaskCommandTaskHandler;

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void executeTaskInternalRunTaskTest() throws Exception {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().cluster("cluster").region("us-east-1").build();

    EcsRunTaskRequest ecsRunTaskRequest = EcsRunTaskRequest.builder()
                                              .timeoutIntervalInMin(10)
                                              .ecsInfraConfig(ecsInfraConfig)
                                              .ecsTaskDefinitionManifestContent("taskDef")
                                              .ecsRunTaskRequestDefinitionManifestContent("runTaskRequestDef")
                                              .skipSteadyStateCheck(false)
                                              .ecsCommandType(EcsCommandTypeNG.ECS_RUN_TASK)
                                              .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    doReturn(runTaskLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.runTask.toString(), true, commandUnitsProgress);

    doReturn(RegisterTaskDefinitionRequest.builder())
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(eq("taskDef"), any());

    doReturn(RunTaskRequest.builder()).when(ecsCommandTaskHelper).parseYamlAsObject(eq("runTaskRequestDef"), any());

    TaskDefinition taskDefinition = TaskDefinition.builder().build();
    doReturn(RegisterTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build())
        .when(ecsCommandTaskHelper)
        .createTaskDefinition(any(), any(), any());

    doReturn(RunTaskResponse.builder().build()).when(ecsCommandTaskHelper).runTask(any(), any(), any());

    doNothing().when(ecsCommandTaskHelper).waitAndDoSteadyStateCheck(any(), any(), any(), any(), any(), any());

    EcsRunTaskResponse ecsRunTaskResponse = (EcsRunTaskResponse) ecsRunTaskCommandTaskHandler.executeTaskInternal(
        ecsRunTaskRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsRunTaskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRunTaskResponse.getEcsRunTaskResult().getRegion()).isEqualTo("us-east-1");

    verify(runTaskLogCallback).saveExecutionLog("Success.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void executeTaskInternalEcsRunTaskRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsRunTaskCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}
