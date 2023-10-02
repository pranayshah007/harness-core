/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerraformConstants.TERRAFORM_APPLY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerragruntConstants.DESTROY_PLAN;
import static io.harness.provision.TerragruntConstants.PLAN;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.getExecutionLogCallback;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cli.CliResponse;
import io.harness.delegate.task.terraform.handlers.HarnessSMEncryptionDecryptionHandler;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.provision.TerragruntConstants;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntClient;
import io.harness.terragrunt.TerragruntDelegateTaskOutput;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.delegation.TerragruntProvisionParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
public class TerragruntApplyDestroyTaskHandler {
  @Inject private TerragruntClient terragruntClient;
  @Inject private EncryptDecryptHelper encryptDecryptHelper;
  @Inject private HarnessSMEncryptionDecryptionHandler harnessSMEncryptionDecryptionHandler;

  public TerragruntDelegateTaskOutput executeApplyTask(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService, String planName,
      String terraformConfigFileDirectoryPath) throws InterruptedException, IOException, TimeoutException {
    String targetArgs = cliCommandRequestParams.getTargetArgs();
    String varParams = cliCommandRequestParams.getVarParams();
    String uiLogs = cliCommandRequestParams.getUiLogs();
    CliResponse terragruntCliResponse = CliResponse.builder().commandExecutionStatus(SUCCESS).build();

    LogCallback applyLogCallback = getLogCallback(delegateLogService, provisionParameters, TerragruntConstants.APPLY);
    LogCallback planLogCallback = getLogCallback(delegateLogService, provisionParameters, PLAN);
    if (provisionParameters.getEncryptedTfPlan() == null) {
      terragruntCliResponse = executeRunPlanOnlyTaskInternal(
          provisionParameters, cliCommandRequestParams, planLogCallback, planName, targetArgs, varParams, uiLogs);
    } else {
      prepareInheritedPlanForApply(provisionParameters, planName, terraformConfigFileDirectoryPath, planLogCallback);
    }

    if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && !provisionParameters.isRunPlanOnly()) {
      terragruntCliResponse = executeApplyTaskInternal(cliCommandRequestParams, applyLogCallback);
    }

    return TerragruntDelegateTaskOutput.builder()
        .cliResponse(terragruntCliResponse)
        .planJsonLogOutputStream(cliCommandRequestParams.getPlanJsonLogOutputStream())
        .build();
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeApplyTaskInternal(TerragruntCliCommandRequestParams cliCommandRequestParams,
      LogCallback applyLogCallback) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    try {
      terragruntCliResponse = terragruntClient.apply(cliCommandRequestParams, applyLogCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
        terragruntCliResponse = terragruntClient.output(
            cliCommandRequestParams, cliCommandRequestParams.getTfOutputsFile().toString(), applyLogCallback);
      }
      applyLogCallback.saveExecutionLog(
          "Finished terragrunt apply task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt apply task - [%s]", ex.getMessage());
      applyLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  private void prepareInheritedPlanForApply(TerragruntProvisionParameters provisionParameters, String planName,
      String terraformConfigFileDirectoryPath, LogCallback planLogCallback) throws IOException {
    try {
      // case when we are inheriting the approved  plan
      planLogCallback.saveExecutionLog(
          color("\nDecrypting terraform plan before applying\n", LogColor.Yellow, LogWeight.Bold), INFO,
          CommandExecutionStatus.RUNNING);
      saveTerraformPlanContentToFile(provisionParameters, terraformConfigFileDirectoryPath, planName);
      planLogCallback.saveExecutionLog(color("\nUsing approved terraform plan \n", LogColor.Yellow, LogWeight.Bold),
          INFO, CommandExecutionStatus.RUNNING);
      planLogCallback.saveExecutionLog("Finished terragrunt plan task", INFO, SUCCESS);
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt plan task - [%s]", ex.getMessage());
      planLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeRunPlanOnlyTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, LogCallback logCallback, String planName,
      String targetArgs, String varParams, String uiLogs) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;

    try {
      logCallback.saveExecutionLog(color("\nGenerating terraform plan \n", LogColor.Yellow, LogWeight.Bold), INFO,
          CommandExecutionStatus.RUNNING);
      terragruntCliResponse =
          terragruntClient.plan(cliCommandRequestParams, targetArgs, varParams, uiLogs, logCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && provisionParameters.isSaveTerragruntJson()) {
        terragruntCliResponse = executeTerragruntShowCommand(cliCommandRequestParams, APPLY, logCallback, planName);
      }
      logCallback.saveExecutionLog(
          "Finished terragrunt plan task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt plan task - [%s]", ex.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  public TerragruntDelegateTaskOutput executeDestroyTask(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService, String planName,
      String terraformConfigFileDirectoryPath) throws InterruptedException, IOException, TimeoutException {
    String targetArgs = cliCommandRequestParams.getTargetArgs();
    String varParams = cliCommandRequestParams.getVarParams();
    String uiLogs = cliCommandRequestParams.getUiLogs();
    CliResponse terragruntCliResponse = CliResponse.builder().commandExecutionStatus(SUCCESS).build();
    if (provisionParameters.isRunPlanOnly()) {
      terragruntCliResponse = executeDestroyPlanTaskInternal(
          provisionParameters, cliCommandRequestParams, delegateLogService, planName, targetArgs, varParams, uiLogs);
    } else {
      terragruntCliResponse = executeDestroyTaskInternal(provisionParameters, cliCommandRequestParams,
          delegateLogService, planName, terraformConfigFileDirectoryPath, targetArgs, varParams, uiLogs);
    }
    return TerragruntDelegateTaskOutput.builder()
        .cliResponse(terragruntCliResponse)
        .planJsonLogOutputStream(cliCommandRequestParams.getPlanJsonLogOutputStream())
        .build();
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeDestroyTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService, String planName,
      String terraformConfigFileDirectoryPath, String targetArgs, String varParams, String uiLogs)
      throws InterruptedException, TimeoutException, IOException {
    LogCallback applyDestroyLogCallback =
        getLogCallback(delegateLogService, provisionParameters, TerragruntConstants.DESTROY);
    CliResponse terragruntCliResponse;
    try {
      if (provisionParameters.getEncryptedTfPlan() == null) {
        terragruntCliResponse =
            terragruntClient.destroy(cliCommandRequestParams, targetArgs, varParams, uiLogs, applyDestroyLogCallback);
      } else {
        saveTerraformPlanContentToFile(provisionParameters, terraformConfigFileDirectoryPath, planName);
        applyDestroyLogCallback.saveExecutionLog(
            "Using approved terraform destroy plan", INFO, CommandExecutionStatus.RUNNING);
        terragruntCliResponse = terragruntClient.applyDestroyTfPlan(cliCommandRequestParams, applyDestroyLogCallback);
      }

      applyDestroyLogCallback.saveExecutionLog(
          "Finished terragrunt destroy task", INFO, terragruntCliResponse.getCommandExecutionStatus());

    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt destroy task - [%s]", ex.getMessage());
      applyDestroyLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeDestroyPlanTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService, String planName,
      String targetArgs, String varParams, String uiLogs) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    LogCallback planDestroyLogCallback = getLogCallback(delegateLogService, provisionParameters, DESTROY_PLAN);
    try {
      terragruntCliResponse =
          terragruntClient.planDestroy(cliCommandRequestParams, targetArgs, varParams, uiLogs, planDestroyLogCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && provisionParameters.isSaveTerragruntJson()) {
        terragruntCliResponse =
            executeTerragruntShowCommand(cliCommandRequestParams, DESTROY, planDestroyLogCallback, planName);
      }
      planDestroyLogCallback.saveExecutionLog(
          "Finished terragrunt destroy plan task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt destroy plan task - [%s]", ex.getMessage());
      planDestroyLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  private void saveTerraformPlanContentToFile(
      TerragruntProvisionParameters parameters, String scriptDirectory, String planName) throws IOException {
    File tfPlanFile = Paths.get(scriptDirectory, planName).toFile();

    byte[] decryptedTerraformPlan;

    if (parameters.isEncryptDecryptPlanForHarnessSMOnManager()) {
      decryptedTerraformPlan = harnessSMEncryptionDecryptionHandler.getDecryptedContent(
          parameters.getSecretManagerConfig(), parameters.getEncryptedTfPlan());
    } else {
      decryptedTerraformPlan = encryptDecryptHelper.getDecryptedContent(
          parameters.getSecretManagerConfig(), parameters.getEncryptedTfPlan());
    }

    FileUtils.copyInputStreamToFile(new ByteArrayInputStream(decryptedTerraformPlan), tfPlanFile);
  }

  private CliResponse executeTerragruntShowCommand(TerragruntCliCommandRequestParams cliCommandRequestParams,
      TerragruntCommand terragruntCommand, LogCallback logCallback, String planName)
      throws IOException, InterruptedException, TimeoutException {
    CliResponse showJsonResponse = terragruntClient.showJson(cliCommandRequestParams, planName, logCallback);

    if (showJsonResponse.getCommandExecutionStatus() == SUCCESS) {
      logCallback.saveExecutionLog(
          format("%nJson representation of %s is exported as a variable %s %n", planName,
              terragruntCommand == APPLY ? TERRAFORM_APPLY_PLAN_FILE_VAR_NAME : TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME),
          INFO, CommandExecutionStatus.RUNNING);
    }
    return showJsonResponse;
  }

  private LogCallback getLogCallback(
      DelegateLogService delegateLogService, TerragruntProvisionParameters parameters, String commandUnit) {
    return getExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), commandUnit);
  }
}
