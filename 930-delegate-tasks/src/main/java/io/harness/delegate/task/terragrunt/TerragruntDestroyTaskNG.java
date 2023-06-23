/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terragrunt.TerragruntTaskService.createCliRequest;
import static io.harness.delegate.task.terragrunt.TerragruntTaskService.executeWithErrorHandling;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerragruntConstants.DESTROY;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.utils.TaskExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.terragrunt.v2.TerragruntClient;
import io.harness.terragrunt.v2.request.TerragruntApplyCliRequest;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntDestroyCliRequest;
import io.harness.terragrunt.v2.request.TerragruntWorkspaceCliRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(CDP)
public class TerragruntDestroyTaskNG extends AbstractDelegateRunnableTask {
  @Inject private TerragruntTaskService taskService;

  public TerragruntDestroyTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof TerragruntDestroyTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("parameters",
          format("Invalid task parameters type provided '%s', expected '%s'", parameters.getClass().getSimpleName(),
              TerragruntDestroyTaskParameters.class.getSimpleName())));
    }

    TerragruntDestroyTaskParameters destroyTaskParameters = (TerragruntDestroyTaskParameters) parameters;
    CommandUnitsProgress commandUnitsProgress = destroyTaskParameters.getCommandUnitsProgress() != null
        ? destroyTaskParameters.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();

    LogCallback destroyLogCallback =
        taskService.getLogCallback(getLogStreamingTaskClient(), DESTROY, commandUnitsProgress);

    String baseDir =
        TerragruntTaskService.getBaseDir(destroyTaskParameters.getAccountId(), destroyTaskParameters.getEntityId());
    TerragruntDestroyTaskResponse destroyTaskResponse;
    try {
      destroyTaskResponse = runDestroyTask(destroyTaskParameters, commandUnitsProgress, destroyLogCallback, baseDir);
      destroyTaskResponse.setUnitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Terragrunt destroy task failed", sanitizedException);
      TaskExceptionUtils.handleExceptionCommandUnits(commandUnitsProgress,
          unitName
          -> taskService.getLogCallback(getLogStreamingTaskClient(), unitName, commandUnitsProgress),
          sanitizedException);

      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
    return destroyTaskResponse;
  }

  private TerragruntDestroyTaskResponse runDestroyTask(TerragruntDestroyTaskParameters destroyTaskParameters,
      CommandUnitsProgress commandUnitsProgress, LogCallback destroyLogCallback, String baseDir)
      throws IOException, InterruptedException {
    try {
      taskService.decryptTaskParameters(destroyTaskParameters);
      LogCallback fetchFilesLogCallback =
          taskService.getLogCallback(getLogStreamingTaskClient(), FETCH_CONFIG_FILES, commandUnitsProgress);
      TerragruntContext terragruntContext =
          taskService.prepareTerragrunt(fetchFilesLogCallback, destroyTaskParameters, baseDir, destroyLogCallback);

      TerragruntClient client = terragruntContext.getClient();

      if (TerragruntTaskRunType.RUN_MODULE == destroyTaskParameters.getRunConfiguration().getRunType()
          || (TerragruntTaskRunType.RUN_ALL == destroyTaskParameters.getRunConfiguration().getRunType()
              && StringUtils.isNotBlank(terragruntContext.getBackendFile()))) {
        executeWithErrorHandling(client::init,
            createCliRequest(TerragruntCliRequest.builder(), terragruntContext, destroyTaskParameters).build(),
            destroyLogCallback);
      }

      if (isNotEmpty(destroyTaskParameters.getWorkspace())) {
        log.info("Create or select workspace {}", destroyTaskParameters.getWorkspace());
        destroyLogCallback.saveExecutionLog(
            color(format("Create or select workspace %s", destroyTaskParameters.getWorkspace()), LogColor.White,
                LogWeight.Bold));
        executeWithErrorHandling(client::workspace,
            createCliRequest(TerragruntWorkspaceCliRequest.builder(), terragruntContext, destroyTaskParameters)
                .workspace(destroyTaskParameters.getWorkspace())
                .build(),
            destroyLogCallback);
        destroyLogCallback.saveExecutionLog(
            color(format("Use workspace: %s\n", destroyTaskParameters.getWorkspace()), LogColor.White, LogWeight.Bold));
      }

      if (TerragruntTaskRunType.RUN_MODULE == destroyTaskParameters.getRunConfiguration().getRunType()) {
        if (destroyTaskParameters.getEncryptedTfPlan() != null) {
          destroyLogCallback.saveExecutionLog(
              color("\nDecrypting terraform plan before applying\n", LogColor.White, LogWeight.Bold));
          taskService.saveTerraformPlanContentToFile(destroyTaskParameters.getPlanSecretManager(),
              destroyTaskParameters.getEncryptedTfPlan(), terragruntContext.getScriptDirectory(),
              destroyTaskParameters.getAccountId(), TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME,
              destroyTaskParameters.isEncryptDecryptPlanForHarnessSMOnManager(), true);
          destroyLogCallback.saveExecutionLog(
              color("Using approved terraform plan \n", LogColor.White, LogWeight.Bold));

          String destroyPlanName = TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
          destroyLogCallback.saveExecutionLog(
              color(format("\nExecute terragrunt apply for '%s'", destroyPlanName), LogColor.White, LogWeight.Bold));
          executeWithErrorHandling(client::apply,
              createCliRequest(TerragruntApplyCliRequest.builder(), terragruntContext, destroyTaskParameters)
                  .terraformPlanName(destroyPlanName)
                  .build(),
              destroyLogCallback);

          destroyLogCallback.saveExecutionLog(
              color(format("Terragrunt Apply for '%s' successfully executed \n", destroyPlanName), LogColor.White,
                  LogWeight.Bold));
        } else {
          destroyLogCallback.saveExecutionLog(color("\nExecute terragrunt destroy", LogColor.White, LogWeight.Bold));
          executeWithErrorHandling(client::destroy,
              createCliRequest(TerragruntDestroyCliRequest.builder(), terragruntContext, destroyTaskParameters).build(),
              destroyLogCallback);

          destroyLogCallback.saveExecutionLog(
              color("Terragrunt destroy successfully executed \n", LogColor.White, LogWeight.Bold));
        }
      } else {
        destroyLogCallback.saveExecutionLog(
            color("\nExecute terragrunt run-all destroy", LogColor.White, LogWeight.Bold));
        executeWithErrorHandling(client::destroy,
            createCliRequest(TerragruntDestroyCliRequest.builder(), terragruntContext, destroyTaskParameters).build(),
            destroyLogCallback);

        destroyLogCallback.saveExecutionLog(
            color("Terragrunt run-all destroy successfully executed \n", LogColor.White, LogWeight.Bold));
      }

      String stateFileId = null;
      if (TerragruntTaskRunType.RUN_MODULE == destroyTaskParameters.getRunConfiguration().getRunType()) {
        stateFileId = taskService.uploadStateFile(terragruntContext.getTerragruntWorkingDirectory(),
            destroyTaskParameters.getWorkspace(), destroyTaskParameters.getAccountId(),
            destroyTaskParameters.getEntityId(), getDelegateId(), getTaskId(), destroyLogCallback);
      }

      return TerragruntDestroyTaskResponse.builder()
          .stateFileId(stateFileId)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } finally {
      taskService.cleanDirectoryAndSecretFromSecretManager(destroyTaskParameters.getEncryptedTfPlan(),
          destroyTaskParameters.getPlanSecretManager(), baseDir, destroyLogCallback);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
