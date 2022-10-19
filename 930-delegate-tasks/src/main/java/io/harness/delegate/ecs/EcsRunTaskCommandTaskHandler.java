/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsRunTaskResult;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsRunTaskCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;
  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsRunTaskRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsRollingDeployRequest"));
    }

    EcsRunTaskRequest ecsRunTaskRequest = (EcsRunTaskRequest) ecsCommandRequest;

    timeoutInMillis = ecsRunTaskRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsRunTaskRequest.getEcsInfraConfig();

    LogCallback runTaskLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.runTask.toString(), true, commandUnitsProgress);

    String ecsTaskDefinitionManifestContent = ecsRunTaskRequest.getEcsTaskDefinitionManifestContent();
    String ecsRunTaskRequestDefinitionManifestContent =
        ecsRunTaskRequest.getEcsRunTaskRequestDefinitionManifestContent();

    RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        ecsTaskDefinitionManifestContent, RegisterTaskDefinitionRequest.serializableBuilderClass());

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();

    runTaskLogCallback.saveExecutionLog(
        format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);

    RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
        registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
    String taskDefinitionArn = taskDefinition.taskDefinitionArn();
    String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();

    runTaskLogCallback.saveExecutionLog(format("Created Task Definition %s %n", taskDefinitionName), LogLevel.INFO);

    RunTaskRequest.Builder runTaskRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
        ecsRunTaskRequestDefinitionManifestContent, RunTaskRequest.serializableBuilderClass());

    RunTaskRequest runTaskRequest =
        runTaskRequestBuilder.taskDefinition(taskDefinitionArn).cluster(ecsInfraConfig.getCluster()).build();

    runTaskLogCallback.saveExecutionLog(
        format("Triggering %s tasks with task definition %s",
            runTaskRequest.count() != null ? runTaskRequest.count() : 1, taskDefinitionName),
        LogLevel.INFO);

    RunTaskResponse runTaskResponse =
        ecsCommandTaskHelper.runTask(runTaskRequest, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getRegion());

    runTaskLogCallback.saveExecutionLog(format("%d Tasks were triggered successfully and %d failures were received.",
                                            runTaskResponse.tasks().size(), runTaskResponse.failures().size()),
        LogLevel.INFO);

    runTaskResponse.tasks().forEach(
        t -> runTaskLogCallback.saveExecutionLog(format("Task => %s succeeded", t.taskArn())));

    runTaskResponse.failures().forEach(f -> {
      runTaskLogCallback.saveExecutionLog(
          format("%s failed with reason => %s \nDetails: %s", f.arn(), f.reason(), f.detail()), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
    });

    List<Task> triggeredTasks = runTaskResponse.tasks();

    List<String> triggeredTaskARNs = triggeredTasks.stream().map(task -> task.taskArn()).collect(Collectors.toList());

    if (!ecsRunTaskRequest.isSkipSteadyStateCheck()) {
      ecsCommandTaskHelper.waitAndDoSteadyStateCheck(triggeredTaskARNs, timeoutInMillis,
          ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getRegion(), ecsInfraConfig.getCluster(),
          runTaskLogCallback);
    } else {
      runTaskLogCallback.saveExecutionLog(format("Skipped Steady State Check"), LogLevel.INFO);
    }

    runTaskLogCallback.saveExecutionLog("Success.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    List<EcsTask> triggeredEcsTasks =
        triggeredTasks.stream().map(task -> EcsMapper.toEcsTask(task, null)).collect(Collectors.toList());

    EcsRunTaskResult ecsRunTaskResult = EcsRunTaskResult.builder().ecsTasks(triggeredEcsTasks).build();

    return EcsRunTaskResponse.builder()
        .ecsRunTaskResult(ecsRunTaskResult)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .build();
  }
}
