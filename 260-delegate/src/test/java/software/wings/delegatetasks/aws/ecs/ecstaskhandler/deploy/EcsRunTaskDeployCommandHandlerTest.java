/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SAINATH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsRunTaskDeployResponse;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsRunTaskDeployCommandHandlerTest extends WingsBaseTest {
  @Mock private EcsDeployCommandTaskHelper mockEcsDeployCommandTaskHelper;
  @Mock private AwsHelperService mockAwsHelperService;

  @InjectMocks @Inject private EcsRunTaskDeployCommandHandler ecsRunTaskDeployCommandHandler;

  private String runTaskFamilyName = "RUN_TASK_FAMILY_NAME";
  private String runTaskDefinition = "{\n"
      + "  \"family\": \"raghav_test_run_task\",\n"
      + "  \"containerDefinitions\": [\n"
      + "    {\n"
      + "      \"name\": \"web\",\n"
      + "      \"image\": \"tongueroo/sinatra:latest\",\n"
      + "      \"cpu\": 128,\n"
      + "      \"memoryReservation\": 128,\n"
      + "      \"portMappings\": [\n"
      + "        {\n"
      + "          \"containerPort\": 4567,\n"
      + "          \"protocol\": \"tcp\"\n"
      + "        }\n"
      + "      ],\n"
      + "      \"command\": [\n"
      + "        \"ruby\", \"hi.rb\"\n"
      + "      ],\n"
      + "      \"essential\": true\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNoSteadyStateCheck() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = EcsRunTaskDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsRunTaskDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyRunTaskDeployResponse();

    when(mockEcsDeployCommandTaskHelper.createRunTaskDefinition(runTaskDefinition, runTaskFamilyName))
        .thenCallRealMethod();

    TaskDefinition registeredRunTaskDefinition = new TaskDefinition();
    registeredRunTaskDefinition.setTaskDefinitionArn("taskArn1");
    doReturn(registeredRunTaskDefinition)
        .when(mockEcsDeployCommandTaskHelper)
        .registerRunTaskDefinition(any(), any(), any(), any(), any(), any());

    EcsRunTaskDeployRequest ecsCommandRequest = EcsRunTaskDeployRequest.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .runTaskFamilyName(runTaskFamilyName)
                                                    .serviceSteadyStateTimeout(1l)
                                                    .skipSteadyStateCheck(true)
                                                    .launchType("EC2")
                                                    .cluster("RUN_TASK_CLUSTER")
                                                    .listTaskDefinitionJson(singletonList(runTaskDefinition))
                                                    .build();

    when(mockEcsDeployCommandTaskHelper.createAwsRunTaskRequest(registeredRunTaskDefinition, ecsCommandRequest))
        .thenCallRealMethod();

    RunTaskResult runTaskResult = new RunTaskResult();
    Task task = new Task();
    task.setTaskArn("taskArn1");
    runTaskResult.setTasks(singletonList(task));

    doReturn(runTaskResult)
        .when(mockEcsDeployCommandTaskHelper)
        .triggerRunTask(eq(ecsCommandRequest.getRegion()), any(), any(), any());

    AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(awsConfig).when(mockAwsHelperService).validateAndGetAwsConfig(any(), any(), anyBoolean());

    EcsCommandExecutionResponse response =
        ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
    verify(mockEcsDeployCommandTaskHelper).triggerRunTask(any(), any(), any(), captor.capture());

    verify(mockEcsDeployCommandTaskHelper, times(0)).getTasksFromTaskArn(any(), any(), any(), any(), any(), any());

    RunTaskRequest triggeredRunTaskRequest = captor.getValue();
    assertThat(triggeredRunTaskRequest.getTaskDefinition()).isEqualTo("taskArn1");
    assertThat(triggeredRunTaskRequest.getLaunchType()).isEqualTo("EC2");
    assertThat(triggeredRunTaskRequest.getCluster()).isEqualTo("RUN_TASK_CLUSTER");
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo(null);
    assertThat(ecsRunTaskDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRunTaskDeployResponse.getOutput()).isEqualTo("");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithSteadyStateCheck() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = EcsRunTaskDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsRunTaskDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyRunTaskDeployResponse();

    when(mockEcsDeployCommandTaskHelper.createRunTaskDefinition(runTaskDefinition, runTaskFamilyName))
        .thenCallRealMethod();

    TaskDefinition registeredRunTaskDefinition = new TaskDefinition();
    registeredRunTaskDefinition.setTaskDefinitionArn("taskArn1");
    doReturn(registeredRunTaskDefinition)
        .when(mockEcsDeployCommandTaskHelper)
        .registerRunTaskDefinition(any(), any(), any(), any(), any(), any());

    EcsRunTaskDeployRequest ecsCommandRequest = EcsRunTaskDeployRequest.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .runTaskFamilyName(runTaskFamilyName)
                                                    .serviceSteadyStateTimeout(1l)
                                                    .launchType("EC2")
                                                    .cluster("RUN_TASK_CLUSTER")
                                                    .listTaskDefinitionJson(singletonList(runTaskDefinition))
                                                    .build();

    when(mockEcsDeployCommandTaskHelper.createAwsRunTaskRequest(registeredRunTaskDefinition, ecsCommandRequest))
        .thenCallRealMethod();

    RunTaskResult runTaskResult = new RunTaskResult();
    Task task = new Task();
    task.setTaskArn("taskArn1");
    runTaskResult.setTasks(singletonList(task));

    doReturn(runTaskResult)
        .when(mockEcsDeployCommandTaskHelper)
        .triggerRunTask(eq(ecsCommandRequest.getRegion()), any(), any(), any());

    AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(awsConfig).when(mockAwsHelperService).validateAndGetAwsConfig(any(), any(), anyBoolean());

    task.setLastStatus(DesiredStatus.STOPPED.name());

    Task taskWithStatusStopped = new Task();
    taskWithStatusStopped.setTaskArn("taskArn1");
    taskWithStatusStopped.setLastStatus(DesiredStatus.RUNNING.name());
    doReturn(singletonList(taskWithStatusStopped))
        .doReturn(singletonList(task))
        .when(mockEcsDeployCommandTaskHelper)
        .getTasksFromTaskArn(eq(awsConfig), any(), any(), eq(singletonList("taskArn1")), any(), any());

    EcsCommandExecutionResponse response =
        ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
    verify(mockEcsDeployCommandTaskHelper, times(1)).triggerRunTask(any(), any(), any(), captor.capture());

    verify(mockEcsDeployCommandTaskHelper, times(2)).getTasksFromTaskArn(any(), any(), any(), any(), any(), any());

    RunTaskRequest triggeredRunTaskRequest = captor.getValue();
    assertThat(triggeredRunTaskRequest.getTaskDefinition()).isEqualTo("taskArn1");
    assertThat(triggeredRunTaskRequest.getLaunchType()).isEqualTo("EC2");
    assertThat(triggeredRunTaskRequest.getCluster()).isEqualTo("RUN_TASK_CLUSTER");
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo(null);
    assertThat(ecsRunTaskDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRunTaskDeployResponse.getOutput()).isEqualTo("");

    // task with container with STOPPED status and exit code as null
    doReturn(singletonList(taskWithStatusStopped))
        .doReturn(singletonList(task))
        .when(mockEcsDeployCommandTaskHelper)
        .getTasksFromTaskArn(eq(awsConfig), any(), any(), eq(singletonList("taskArn1")), any(), any());

    Container container = new Container();
    container.setLastStatus("STOPPED");
    container.setExitCode(null);
    task.setContainers(singletonList(container));

    response = ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    // task with container with STOPPED status and exit code as 0
    doReturn(singletonList(taskWithStatusStopped))
        .doReturn(singletonList(task))
        .when(mockEcsDeployCommandTaskHelper)
        .getTasksFromTaskArn(eq(awsConfig), any(), any(), eq(singletonList("taskArn1")), any(), any());

    container = new Container();
    container.setLastStatus("STOPPED");
    container.setExitCode(0);
    task.setContainers(singletonList(container));

    response = ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // task with container with STOPPED status and exit code as 1
    doReturn(singletonList(taskWithStatusStopped))
        .doReturn(singletonList(task))
        .when(mockEcsDeployCommandTaskHelper)
        .getTasksFromTaskArn(eq(awsConfig), any(), any(), eq(singletonList("taskArn1")), any(), any());

    container = new Container();
    container.setLastStatus("STOPPED");
    container.setExitCode(1);
    task.setContainers(singletonList(container));

    response = ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithSteadyStateCheckNonZeroExitCodeContainers() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = EcsRunTaskDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsRunTaskDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyRunTaskDeployResponse();

    when(mockEcsDeployCommandTaskHelper.createRunTaskDefinition(runTaskDefinition, runTaskFamilyName))
        .thenCallRealMethod();

    TaskDefinition registeredRunTaskDefinition = new TaskDefinition();
    registeredRunTaskDefinition.setTaskDefinitionArn("taskArn1");
    doReturn(registeredRunTaskDefinition)
        .when(mockEcsDeployCommandTaskHelper)
        .registerRunTaskDefinition(any(), any(), any(), any(), any(), any());

    EcsRunTaskDeployRequest ecsCommandRequest = EcsRunTaskDeployRequest.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .runTaskFamilyName(runTaskFamilyName)
                                                    .serviceSteadyStateTimeout(1l)
                                                    .launchType("EC2")
                                                    .cluster("RUN_TASK_CLUSTER")
                                                    .listTaskDefinitionJson(singletonList(runTaskDefinition))
                                                    .build();

    when(mockEcsDeployCommandTaskHelper.createAwsRunTaskRequest(registeredRunTaskDefinition, ecsCommandRequest))
        .thenCallRealMethod();

    RunTaskResult runTaskResult = new RunTaskResult();
    Task task = new Task();
    Container containerWithNonZeroExitCode = new Container();
    containerWithNonZeroExitCode.setExitCode(1);
    containerWithNonZeroExitCode.setContainerArn("containerArn");
    containerWithNonZeroExitCode.setTaskArn("taskArn1");
    task.setTaskArn("taskArn1");
    task.setContainers(singletonList(containerWithNonZeroExitCode));
    runTaskResult.setTasks(singletonList(task));

    doReturn(runTaskResult)
        .when(mockEcsDeployCommandTaskHelper)
        .triggerRunTask(eq(ecsCommandRequest.getRegion()), any(), any(), any());

    AwsConfig awsConfig = AwsConfig.builder().build();
    doReturn(awsConfig).when(mockAwsHelperService).validateAndGetAwsConfig(any(), any(), anyBoolean());

    task.setLastStatus(DesiredStatus.STOPPED.name());
    Container stillRunningContainers = new Container();
    stillRunningContainers.setExitCode(null);
    stillRunningContainers.setContainerArn("containerArn");
    stillRunningContainers.setTaskArn("taskArn1");
    Task taskWithStatusStopped = new Task();
    taskWithStatusStopped.setTaskArn("taskArn1");
    taskWithStatusStopped.setLastStatus(DesiredStatus.RUNNING.name());
    taskWithStatusStopped.setContainers(singletonList(stillRunningContainers));
    doReturn(singletonList(taskWithStatusStopped))
        .doReturn(singletonList(task))
        .when(mockEcsDeployCommandTaskHelper)
        .getTasksFromTaskArn(eq(awsConfig), any(), any(), eq(singletonList("taskArn1")), any(), any());

    EcsCommandExecutionResponse response =
        ecsRunTaskDeployCommandHandler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    ArgumentCaptor<RunTaskRequest> captor = ArgumentCaptor.forClass(RunTaskRequest.class);
    verify(mockEcsDeployCommandTaskHelper, times(1)).triggerRunTask(any(), any(), any(), captor.capture());

    verify(mockEcsDeployCommandTaskHelper, times(2)).getTasksFromTaskArn(any(), any(), any(), any(), any(), any());

    RunTaskRequest triggeredRunTaskRequest = captor.getValue();
    assertThat(triggeredRunTaskRequest.getTaskDefinition()).isEqualTo("taskArn1");
    assertThat(triggeredRunTaskRequest.getLaunchType()).isEqualTo("EC2");
    assertThat(triggeredRunTaskRequest.getCluster()).isEqualTo("RUN_TASK_CLUSTER");
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo(null);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getEcsCommandResponse().getOutput())
        .isEqualTo("Failed to execute command: Containers in some tasks failed and are showing non zero exit code\n"
            + " taskArn1 => containerArn => exit code : 1");
    assertThat(ecsRunTaskDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(ecsRunTaskDeployResponse.getOutput())
        .isEqualTo("Failed to execute command: Containers in some tasks failed and are showing non zero exit code\n"
            + " taskArn1 => containerArn => exit code : 1");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testIsEcsTaskContainerFailed() {
    Container container = new Container();

    container.setExitCode(null);
    container.setLastStatus("RUNNING");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(false);

    container.setExitCode(0);
    container.setLastStatus("RUNNING");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(false);

    container.setExitCode(1);
    container.setLastStatus("RUNNING");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(true);

    container.setExitCode(null);
    container.setLastStatus("STOPPED");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(true);

    container.setExitCode(0);
    container.setLastStatus("STOPPED");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(false);

    container.setExitCode(1);
    container.setLastStatus("STOPPED");
    assertThat(ecsRunTaskDeployCommandHandler.isEcsTaskContainerFailed(container)).isEqualTo(true);
  }
}
