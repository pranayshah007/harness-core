/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRunTaskArnRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsRunTaskArnCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;
  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsRunTaskArnRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsRunTaskArnRequest"));
    }

    EcsRunTaskArnRequest ecsRunTaskArnRequest = (EcsRunTaskArnRequest) ecsCommandRequest;

    timeoutInMillis = ecsRunTaskArnRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsRunTaskArnRequest.getEcsInfraConfig();

    LogCallback runTaskLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.runTask.toString(), true, commandUnitsProgress);
    try {
      runTaskLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);
      String ecsTaskDefinition = ecsRunTaskArnRequest.getEcsTaskDefinition();
      runTaskLogCallback.saveExecutionLog(format("TaskDefinition: %s %n", ecsTaskDefinition), LogLevel.INFO);
      DescribeTaskDefinitionResponse describeTaskDefinitionResponse =
          ecsCommandTaskHelper.validateEcsTaskDefinition(ecsTaskDefinition, ecsInfraConfig, runTaskLogCallback);

      TaskDefinition taskDefinition = describeTaskDefinitionResponse.taskDefinition();

      EcsRunTaskResponse ecsRunTaskResponse = ecsCommandTaskHelper.getEcsRunTaskResponse(taskDefinition,
          ecsRunTaskArnRequest.getEcsRunTaskRequestDefinitionManifestContent(),
          ecsRunTaskArnRequest.isSkipSteadyStateCheck(), timeoutInMillis, ecsInfraConfig, runTaskLogCallback);

      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return ecsRunTaskResponse;
    } catch (Exception e) {
      runTaskLogCallback.saveExecutionLog("Run Task Failed .", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
}
