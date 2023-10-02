/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class EcsRunTaskArnTask extends AbstractDelegateRunnableTask {
  @Inject private EcsDelegateTaskHelper ecsDelegateTaskHelper;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;

  public EcsRunTaskArnTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public EcsCommandResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public EcsCommandResponse run(TaskParameters parameters) {
    EcsCommandRequest ecsCommandRequest = (EcsCommandRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = ecsCommandRequest.getCommandUnitsProgress() != null
        ? ecsCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
    ecsInfraConfigHelper.decryptEcsInfraConfig(ecsCommandRequest.getEcsInfraConfig());
    try {
      return ecsDelegateTaskHelper.getEcsCommandResponse(ecsCommandRequest, getLogStreamingTaskClient());
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing ecs task [{}]",
          ecsCommandRequest.getCommandName() + ":" + ecsCommandRequest.getEcsCommandType(), sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
