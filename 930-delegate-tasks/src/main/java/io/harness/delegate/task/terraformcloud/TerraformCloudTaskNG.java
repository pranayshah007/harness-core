/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudDelegateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunStatus;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformCloudTaskNG extends AbstractDelegateRunnableTask {
  @Inject private TerraformCloudValidationHandler terraformCloudValidationHandler;
  @Inject private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Inject private TerraformCloudTaskHelper terraformCloudTaskHelper;

  public TerraformCloudTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Object Array parameters not supported");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException {
    TerraformCloudTaskParams taskParameters = (TerraformCloudTaskParams) parameters;

    TerraformCloudConnectorDTO terraformCloudConnectorDTO = taskParameters.getTerraformCloudConnectorDTO();
    TerraformCloudConfig terraformCloudConfig = terraformCloudConfigMapper.mapTerraformCloudConfigWithDecryption(
        terraformCloudConnectorDTO, taskParameters.getEncryptionDetails());
    TerraformCloudDelegateTaskResponse taskResponse;
    switch (taskParameters.getTerraformCloudTaskType()) {
      case VALIDATE:
        ConnectorValidationResult connectorValidationResult =
            terraformCloudValidationHandler.validate(terraformCloudConfig);
        connectorValidationResult.setDelegateId(getDelegateId());
        taskResponse =
            TerraformCloudValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
        break;
      case GET_ORGANIZATIONS:
        taskResponse = TerraformCloudOrganizationsTaskResponse.builder()
                           .organizations(terraformCloudTaskHelper.getOrganizationsMap(terraformCloudConfig))
                           .build();
        break;
      case GET_WORKSPACES:
        taskResponse = TerraformCloudWorkspacesTaskResponse.builder()
                           .workspaces(terraformCloudTaskHelper.getWorkspacesMap(
                               terraformCloudConfig, taskParameters.getOrganization()))
                           .build();
        break;
      case RUN_REFRESH_STATE:
        refreshState(
            (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials(), taskParameters);
        taskResponse = TerraformCloudRunTaskResponse.builder().build();
        break;
      case RUN_PLAN_ONLY:
      case RUN_PLAN:
        taskResponse = plan((TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials(),
            taskParameters, getDelegateId(), getTaskId());
        break;
      case RUN_PLAN_AND_APPLY:
      case RUN_PLAN_AND_DESTROY:
        taskResponse = autoApply(
            (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials(), taskParameters);
        break;
      case RUN_APPLY:
        taskResponse = apply(
            (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials(), taskParameters);
        break;
      default:
        throw new InvalidRequestException("Task type not identified");
    }
    taskResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    return taskResponse;
  }

  private void refreshState(TerraformCloudApiTokenCredentials credentials, TerraformCloudTaskParams taskParameters)
      throws IOException {
    LogCallback logCallback =
        getLogCallback(TerraformCloudCommandUnit.RUN.name(), CommandUnitsProgress.builder().build());
    terraformCloudTaskHelper.createRun(credentials.getUrl(), credentials.getToken(), taskParameters, logCallback);
    logCallback.saveExecutionLog("Refresh state completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private TerraformCloudRunTaskResponse plan(TerraformCloudApiTokenCredentials credentials,
      TerraformCloudTaskParams taskParameters, String delegateId, String taskId) throws IOException {
    LogCallback logCallback =
        getLogCallback(TerraformCloudCommandUnit.RUN.name(), CommandUnitsProgress.builder().build());

    RunData runData =
        terraformCloudTaskHelper.createRun(credentials.getUrl(), credentials.getToken(), taskParameters, logCallback);
    String tfPlanJsonFileId = null;
    if (taskParameters.isExportJsonTfPlan()) {
      String jsonPlan = terraformCloudTaskHelper.getJsonPlan(credentials.getUrl(), credentials.getToken(), runData);
      if (jsonPlan != null) {
        tfPlanJsonFileId = terraformCloudTaskHelper.uploadTfPlanJson(taskParameters.getAccountId(), delegateId, taskId,
            taskParameters.getEntityId(), taskParameters.getPlanType(), jsonPlan);
      }
    }
    logCallback.saveExecutionLog("Plan creation completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return TerraformCloudRunTaskResponse.builder().runId(runData.getId()).tfPlanJsonFileId(tfPlanJsonFileId).build();
  }

  private TerraformCloudRunTaskResponse autoApply(
      TerraformCloudApiTokenCredentials credentials, TerraformCloudTaskParams taskParameters) throws IOException {
    LogCallback logCallback =
        getLogCallback(TerraformCloudCommandUnit.RUN.name(), CommandUnitsProgress.builder().build());

    RunData runData =
        terraformCloudTaskHelper.createRun(credentials.getUrl(), credentials.getToken(), taskParameters, logCallback);

    String output = terraformCloudTaskHelper.getApplyOutput(credentials.getUrl(), credentials.getToken(), runData);
    logCallback.saveExecutionLog("Execution completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return TerraformCloudRunTaskResponse.builder().runId(runData.getId()).tfOutput(output).build();
  }

  public TerraformCloudRunTaskResponse apply(
      TerraformCloudApiTokenCredentials credentials, TerraformCloudTaskParams taskParameters) throws IOException {
    LogCallback logCallback =
        getLogCallback(TerraformCloudCommandUnit.RUN.name(), CommandUnitsProgress.builder().build());

    String url = credentials.getUrl();
    String token = credentials.getToken();
    String runId = taskParameters.getRunId();

    RunStatus status = terraformCloudTaskHelper.getRunStatus(url, token, runId);
    if (status == RunStatus.policy_checked) {
      String output = terraformCloudTaskHelper.applyRun(url, token, runId, taskParameters.getMessage(), logCallback);
      logCallback.saveExecutionLog("Apply completed", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return TerraformCloudRunTaskResponse.builder().runId(runId).tfOutput(output).build();
    } else {
      logCallback.saveExecutionLog(format("Apply can't be done when run is in status %s", status.name()),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      // toDo custom exception will be thrown here
      throw new InvalidRequestException(format("Apply can't be done when run is in status %s", status.name()));
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  public LogCallback getLogCallback(String commandUnitName, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(getLogStreamingTaskClient(), commandUnitName, true, commandUnitsProgress);
  }
}
