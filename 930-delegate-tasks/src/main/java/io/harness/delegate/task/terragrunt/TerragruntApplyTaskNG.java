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
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_VARIABLES_FILE_NAME;
import static io.harness.provision.TerragruntConstants.APPLY;
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
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntApplyTaskResponse;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.utils.TaskExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanLogOutputStream;
import io.harness.terragrunt.v2.TerragruntClient;
import io.harness.terragrunt.v2.request.TerragruntApplyCliRequest;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntOutputCliRequest;
import io.harness.terragrunt.v2.request.TerragruntPlanCliRequest;
import io.harness.terragrunt.v2.request.TerragruntWorkspaceCliRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(CDP)
public class TerragruntApplyTaskNG extends AbstractDelegateRunnableTask {
  @Inject private TerragruntTaskService taskService;

  public TerragruntApplyTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof TerragruntApplyTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("parameters",
          format("Invalid task parameters type provided '%s', expected '%s'", parameters.getClass().getSimpleName(),
              TerragruntApplyTaskParameters.class.getSimpleName())));
    }

    TerragruntApplyTaskParameters applyTaskParameters = (TerragruntApplyTaskParameters) parameters;
    CommandUnitsProgress commandUnitsProgress = applyTaskParameters.getCommandUnitsProgress() != null
        ? applyTaskParameters.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    LogCallback applyLogCallback = taskService.getLogCallback(getLogStreamingTaskClient(), APPLY, commandUnitsProgress);
    String baseDir =
        TerragruntTaskService.getBaseDir(applyTaskParameters.getAccountId(), applyTaskParameters.getEntityId());
    TerragruntApplyTaskResponse applyTaskResponse;

    try {
      applyTaskResponse = runApplyTask(applyTaskParameters, commandUnitsProgress, applyLogCallback, baseDir);
      applyTaskResponse.setUnitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Terragrunt apply task failed", sanitizedException);
      TaskExceptionUtils.handleExceptionCommandUnits(commandUnitsProgress,
          unitName
          -> taskService.getLogCallback(getLogStreamingTaskClient(), unitName, commandUnitsProgress),
          sanitizedException);

      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
    return applyTaskResponse;
  }

  private TerragruntApplyTaskResponse runApplyTask(TerragruntApplyTaskParameters applyTaskParameters,
      CommandUnitsProgress commandUnitsProgress, LogCallback applyLogCallback, String baseDir)
      throws IOException, InterruptedException {
    try (PlanLogOutputStream planLogOutputStream = new PlanLogOutputStream()) {
      taskService.decryptTaskParameters(applyTaskParameters);

      LogCallback fetchFilesLogCallback =
          taskService.getLogCallback(getLogStreamingTaskClient(), FETCH_CONFIG_FILES, commandUnitsProgress);
      TerragruntContext terragruntContext =
          taskService.prepareTerragrunt(fetchFilesLogCallback, applyTaskParameters, baseDir, applyLogCallback);

      TerragruntClient client = terragruntContext.getClient();

      if (TerragruntTaskRunType.RUN_MODULE == applyTaskParameters.getRunConfiguration().getRunType()
          || (TerragruntTaskRunType.RUN_ALL == applyTaskParameters.getRunConfiguration().getRunType()
              && StringUtils.isNotBlank(terragruntContext.getBackendFile()))) {
        executeWithErrorHandling(client::init,
            createCliRequest(TerragruntCliRequest.builder(), terragruntContext, applyTaskParameters).build(),
            applyLogCallback);
      }

      if (isNotEmpty(applyTaskParameters.getWorkspace())) {
        log.info("Create or select workspace {}", applyTaskParameters.getWorkspace());
        applyLogCallback.saveExecutionLog(
            color(format("Create or select workspace %s", applyTaskParameters.getWorkspace()), LogColor.White,
                LogWeight.Bold));
        executeWithErrorHandling(client::workspace,
            createCliRequest(TerragruntWorkspaceCliRequest.builder(), terragruntContext, applyTaskParameters)
                .workspace(applyTaskParameters.getWorkspace())
                .build(),
            applyLogCallback);
        applyLogCallback.saveExecutionLog(
            color(format("Use workspace: %s\n", applyTaskParameters.getWorkspace()), LogColor.White, LogWeight.Bold));
      }

      if (TerragruntTaskRunType.RUN_MODULE == applyTaskParameters.getRunConfiguration().getRunType()) {
        if (applyTaskParameters.getEncryptedTfPlan() != null) {
          applyLogCallback.saveExecutionLog(
              color("\nDecrypting terraform plan before applying\n", LogColor.White, LogWeight.Bold));
          taskService.saveTerraformPlanContentToFile(applyTaskParameters.getPlanSecretManager(),
              applyTaskParameters.getEncryptedTfPlan(), terragruntContext.getScriptDirectory(),
              applyTaskParameters.getAccountId(), TERRAFORM_PLAN_FILE_OUTPUT_NAME,
              applyTaskParameters.isEncryptDecryptPlanForHarnessSMOnManager(), true);
          applyLogCallback.saveExecutionLog(color("Using approved terraform plan \n", LogColor.White, LogWeight.Bold));
        } else {
          String planName = TERRAFORM_PLAN_FILE_OUTPUT_NAME;
          applyLogCallback.saveExecutionLog(
              color(format("\nCreate terragrunt plan '%s'", planName), LogColor.White, LogWeight.Bold));
          executeWithErrorHandling(client::plan,
              createCliRequest(TerragruntPlanCliRequest.builder(), terragruntContext, applyTaskParameters)
                  .planOutputStream(planLogOutputStream)
                  .tfPlanName(planName)
                  .destroy(false)
                  .build(),
              applyLogCallback);
          applyLogCallback.saveExecutionLog(
              color(format("\nTerragrunt plan '%s' successfully created\n", planName), LogColor.White, LogWeight.Bold));
        }
      }

      String planName = TERRAFORM_PLAN_FILE_OUTPUT_NAME;
      applyLogCallback.saveExecutionLog(color("\nExecute terragrunt apply", LogColor.White, LogWeight.Bold));
      executeWithErrorHandling(client::apply,
          createCliRequest(TerragruntApplyCliRequest.builder(), terragruntContext, applyTaskParameters)
              .terraformPlanName(planName)
              .build(),
          applyLogCallback);

      applyLogCallback.saveExecutionLog(
          color("Terragrunt Apply successfully executed \n", LogColor.White, LogWeight.Bold));

      File tfOutputsFile =
          Paths.get(terragruntContext.getScriptDirectory(), format(TERRAFORM_VARIABLES_FILE_NAME, "output")).toFile();

      applyLogCallback.saveExecutionLog(color("\nExecute terragrunt output", LogColor.White, LogWeight.Bold));
      executeWithErrorHandling(client::output,
          createCliRequest(TerragruntOutputCliRequest.builder(), terragruntContext, applyTaskParameters)
              .terraformOutputsFile(tfOutputsFile.getAbsolutePath())
              .build(),
          applyLogCallback);
      applyLogCallback.saveExecutionLog(
          color("Terragrunt output successfully executed \n", LogColor.White, LogWeight.Bold));

      String stateFileId = null;
      if (TerragruntTaskRunType.RUN_MODULE == applyTaskParameters.getRunConfiguration().getRunType()) {
        stateFileId = taskService.uploadStateFile(terragruntContext.getTerragruntWorkingDirectory(),
            applyTaskParameters.getWorkspace(), applyTaskParameters.getAccountId(), applyTaskParameters.getEntityId(),
            getDelegateId(), getTaskId(), applyLogCallback);
      }

      return TerragruntApplyTaskResponse.builder()
          .stateFileId(stateFileId)
          .outputs(new String(Files.readAllBytes(tfOutputsFile.toPath()), Charsets.UTF_8))
          .configFilesSourceReference(terragruntContext.getConfigFilesSourceReference())
          .backendFileSourceReference(terragruntContext.getBackendFileSourceReference())
          .varFilesSourceReference(terragruntContext.getVarFilesSourceReference())
          .build();
    } finally {
      taskService.cleanDirectoryAndSecretFromSecretManager(applyTaskParameters.getEncryptedTfPlan(),
          applyTaskParameters.getPlanSecretManager(), baseDir, applyLogCallback);
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
