/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerraformConstants.TERRAFORM_APPLY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerragruntConstants.DESTROY;
import static io.harness.provision.TerragruntConstants.DESTROY_PLAN;
import static io.harness.provision.TerragruntConstants.INIT;
import static io.harness.provision.TerragruntConstants.PLAN;

import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.getExecutionLogCallback;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cli.CliResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.provision.TerragruntConstants;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntClient;
import io.harness.terragrunt.TerragruntDelegateTaskOutput;

import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntRunAllTaskHandler {
  @Inject private TerragruntClient terragruntClient;
  @Inject private TerragruntProvisionTaskHelper provisionTaskHelper;

  public TerragruntDelegateTaskOutput executeRunAllTask(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService,
      TerragruntCommand terragruntCommand) throws InterruptedException, IOException, TimeoutException {
    String targetArgs = cliCommandRequestParams.getTargetArgs();
    String varParams = cliCommandRequestParams.getVarParams();
    String uiLogs = cliCommandRequestParams.getUiLogs();

    CliResponse terragruntCliResponse = null;

    terragruntCliResponse = executeRunAllInitTaskInternal(
        provisionParameters, cliCommandRequestParams, targetArgs, varParams, uiLogs, delegateLogService);

    switch (provisionParameters.getCommand()) {
      case APPLY: {
        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          if (provisionParameters.isRunPlanOnly()) {
            terragruntCliResponse = executeRunAllPlanTaskInternal(provisionParameters, cliCommandRequestParams,
                delegateLogService, terragruntCommand, targetArgs, varParams, uiLogs);
          } else {
            terragruntCliResponse = executeRunAllApplyTaskInternal(
                provisionParameters, cliCommandRequestParams, delegateLogService, targetArgs, varParams, uiLogs);
          }
        }
        break;
      }
      case DESTROY: {
        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          if (provisionParameters.isRunPlanOnly()) {
            terragruntCliResponse = executeRunAllPDestroyPlanTaskInternal(provisionParameters, cliCommandRequestParams,
                delegateLogService, terragruntCommand, targetArgs, varParams, uiLogs);
          } else {
            terragruntCliResponse = executeRunAllDestroyTaskInternal(
                provisionParameters, cliCommandRequestParams, delegateLogService, targetArgs, varParams, uiLogs);
          }
        }
        break;
      }
      default: {
        throw new IllegalArgumentException("Invalid Terragrunt Command : " + provisionParameters.getCommand().name());
      }
    }
    return TerragruntDelegateTaskOutput.builder()
        .planJsonLogOutputStream(cliCommandRequestParams.getPlanJsonLogOutputStream())
        .cliResponse(terragruntCliResponse)
        .build();
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeRunAllDestroyTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService,
      String targetArgs, String varParams, String uiLogs) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    LogCallback applyDestroyLogCallback = getLogCallback(delegateLogService, provisionParameters, DESTROY);
    try {
      terragruntCliResponse = terragruntClient.runAllDestroy(
          cliCommandRequestParams, targetArgs, varParams, uiLogs, applyDestroyLogCallback);
      applyDestroyLogCallback.saveExecutionLog(
          "Finished terragrunt destroy task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt apply destroy task - [%s]", ex.getMessage());
      applyDestroyLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeRunAllPDestroyPlanTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService,
      TerragruntCommand terragruntCommand, String targetArgs, String varParams, String uiLogs)
      throws InterruptedException, TimeoutException, IOException {
    LogCallback planDestroyLogCallback = getLogCallback(delegateLogService, provisionParameters, DESTROY_PLAN);
    CliResponse terragruntCliResponse;
    try {
      terragruntCliResponse = terragruntClient.runAllPlanDestroy(
          cliCommandRequestParams, targetArgs, varParams, uiLogs, planDestroyLogCallback);

      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && provisionParameters.isSaveTerragruntJson()) {
        String planName =
            terragruntCommand == APPLY ? TERRAFORM_PLAN_FILE_OUTPUT_NAME : TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
        terragruntCliResponse =
            terragruntClient.runAllshowJson(cliCommandRequestParams, planName, planDestroyLogCallback);
        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          planDestroyLogCallback.saveExecutionLog(
              format("%nJson representation of %s is exported as a variable %s %n", planName,
                  terragruntCommand == APPLY ? TERRAFORM_APPLY_PLAN_FILE_VAR_NAME
                                             : TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME),
              INFO, CommandExecutionStatus.RUNNING);
        }
      }
      planDestroyLogCallback.saveExecutionLog(
          "Finished terragrunt destroy plan task", INFO, terragruntCliResponse.getCommandExecutionStatus());

    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt plan destroy task - [%s]", ex.getMessage());
      planDestroyLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeRunAllApplyTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService,
      String targetArgs, String varParams, String uiLogs) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    LogCallback applyLogCallback = getLogCallback(delegateLogService, provisionParameters, TerragruntConstants.APPLY);
    try {
      terragruntCliResponse =
          terragruntClient.runAllApply(cliCommandRequestParams, targetArgs, varParams, uiLogs, applyLogCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
        terragruntCliResponse = terragruntClient.runAllOutput(cliCommandRequestParams, applyLogCallback);
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

  @NotNull
  @VisibleForTesting
  CliResponse executeRunAllPlanTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, DelegateLogService delegateLogService,
      TerragruntCommand terragruntCommand, String targetArgs, String varParams, String uiLogs)
      throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    LogCallback planLogCallback = getLogCallback(delegateLogService, provisionParameters, PLAN);
    try {
      terragruntCliResponse =
          terragruntClient.runAllplan(cliCommandRequestParams, targetArgs, varParams, uiLogs, planLogCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && provisionParameters.isSaveTerragruntJson()) {
        String planName =
            terragruntCommand == APPLY ? TERRAFORM_PLAN_FILE_OUTPUT_NAME : TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
        terragruntCliResponse = terragruntClient.runAllshowJson(cliCommandRequestParams, planName, planLogCallback);
        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          planLogCallback.saveExecutionLog(
              format("%nJson representation of %s is exported as a variable %s %n", planName,
                  terragruntCommand == APPLY ? TERRAFORM_APPLY_PLAN_FILE_VAR_NAME
                                             : TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME),
              INFO, CommandExecutionStatus.RUNNING);
        }
      }
      planLogCallback.saveExecutionLog(
          "Finished terragrunt plan task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt plan task - [%s]", ex.getMessage());
      planLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  @NotNull
  @VisibleForTesting
  CliResponse executeRunAllInitTaskInternal(TerragruntProvisionParameters provisionParameters,
      TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs, String varParams, String uiLogs,
      DelegateLogService delegateLogService) throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse;
    LogCallback initLogCallback = getLogCallback(delegateLogService, provisionParameters, INIT);
    try {
      terragruntCliResponse = terragruntClient.version(cliCommandRequestParams, initLogCallback);
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
        terragruntCliResponse = terragruntClient.runAllInit(cliCommandRequestParams, initLogCallback);
      }
      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS
          && isNotEmpty(provisionParameters.getWorkspace())) {
        terragruntCliResponse = terragruntClient.runAllNewWorkspace(
            cliCommandRequestParams, provisionParameters.getWorkspace(), initLogCallback);
        // for run-all commands, only way to know desired commands from new/select is to check output of both
        if (terragruntCliResponse.getCommandExecutionStatus() != SUCCESS) {
          terragruntCliResponse = terragruntClient.runAllSelectWorkspace(
              cliCommandRequestParams, provisionParameters.getWorkspace(), initLogCallback);
        }
        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          initLogCallback.saveExecutionLog(format("switched to workspace: %s", provisionParameters.getWorkspace()),
              INFO, CommandExecutionStatus.RUNNING);
        } else {
          initLogCallback.saveExecutionLog(
              "Error occurred while selecting workspace", ERROR, CommandExecutionStatus.RUNNING);
        }
      }

      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
        terragruntCliResponse =
            terragruntClient.runAllRefresh(cliCommandRequestParams, targetArgs, varParams, uiLogs, initLogCallback);
      }
      initLogCallback.saveExecutionLog(
          "Finished terragrunt init task", INFO, terragruntCliResponse.getCommandExecutionStatus());
    } catch (Exception ex) {
      String message = String.format("Failed to perform terragrunt init task - [%s]", ex.getMessage());
      initLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }
    return terragruntCliResponse;
  }

  private LogCallback getLogCallback(
      DelegateLogService delegateLogService, TerragruntProvisionParameters parameters, String commandUnit) {
    return getExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), commandUnit);
  }
}
