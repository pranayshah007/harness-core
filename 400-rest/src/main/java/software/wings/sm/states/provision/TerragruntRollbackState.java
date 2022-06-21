/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TIMEOUT_IN_MINUTES;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.getMarkerName;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.handleDefaultWorkspace;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.resolveTargets;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.terragrunt.TerragruntApplyMarkerParam;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.instance.TerragruntConfig;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FilenameUtils;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerragruntRollbackState extends TerragruntProvisionState {
  private TerragruntCommand rollbackCommand;

  public TerragruntRollbackState(String name) {
    super(name, StateType.TERRAGRUNT_ROLLBACK.name());
  }

  @Override
  protected TerragruntCommandUnit commandUnit() {
    return TerragruntCommandUnit.Rollback;
  }

  @Override
  protected TerragruntCommand command() {
    return rollbackCommand;
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);

    String pathToModule = context.renderExpression(FilenameUtils.normalize(getPathToModule()));
    if (pathToModule == null) {
      throw new InvalidRequestException("Invalid Terragrunt module path", USER);
    }
    if (!applyHappened(context, pathToModule)) {
      return ExecutionResponse.builder()
          .executionStatus(SUCCESS)
          .errorMessage(format("Apply did not happen with provisioner: [%s]", terragruntProvisioner.getName()))
          .build();
    }
    String path = context.renderExpression(terragruntProvisioner.getPath());
    String workspace = context.renderExpression(getWorkspace());
    String branch = context.renderExpression(terragruntProvisioner.getSourceRepoBranch());
    workspace = handleDefaultWorkspace(workspace);

    String entityId = generateEntityId(context, workspace, branch, pathToModule);
    try (HIterator<TerraformConfig> configIterator =
             terragruntStateHelper.getSavedTerraformConfig(context.getAppId(), entityId)) {
      if (!configIterator.hasNext()) {
        return ExecutionResponse.builder()
            .executionStatus(SUCCESS)
            .errorMessage("No Rollback Required. Provisioning seems to have failed. ")
            .build();
      }

      TerragruntConfig configParameter = null;
      TerragruntConfig currentConfig = null;
      while (configIterator.hasNext()) {
        TerraformConfig config = configIterator.next();
        if (!(config instanceof TerragruntConfig)) {
          throw new InvalidRequestException("Unknown config");
        }
        configParameter = (TerragruntConfig) config;

        if (configParameter.getWorkflowExecutionId().equals(context.getWorkflowExecutionId())) {
          if (currentConfig == null) {
            currentConfig = configParameter;
          }
        } else {
          TerragruntCommand savedCommand = configParameter.getTerragruntCommand();
          rollbackCommand = savedCommand != null ? savedCommand : APPLY;
          break;
        }
      }
      ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
      StringBuilder rollbackMessage = new StringBuilder();
      if (configParameter == currentConfig) {
        rollbackMessage.append("No previous successful terragrunt execution, hence destroying.");
        rollbackCommand = DESTROY;
      } else {
        rollbackMessage.append("Inheriting terragrunt execution from last successful terragrunt execution : ");
        rollbackMessage.append(
            terragruntStateHelper.getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext));
      }

      final String fileId = configParameter.isRunAll() ? null : fileService.getLatestFileId(entityId, TERRAFORM_STATE);
      notNullCheck("TerragruntConfig cannot be null", configParameter);
      final GitConfig gitConfig = terragruntStateHelper.getGitConfigAndPopulate(configParameter, branch);

      List<NameValuePair> allVariables = configParameter.getVariables();
      Map<String, String> textVariables = null;
      Map<String, EncryptedDataDetail> encryptedTextVariables = null;
      if (allVariables != null) {
        textVariables = infrastructureProvisionerService.extractUnresolvedTextVariables(allVariables);
        encryptedTextVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
            allVariables, context.getAppId(), context.getWorkflowExecutionId());
      }

      List<NameValuePair> allBackendConfigs = configParameter.getBackendConfigs();
      Map<String, String> backendConfigs = null;
      Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
      if (allBackendConfigs != null) {
        backendConfigs = infrastructureProvisionerService.extractUnresolvedTextVariables(allBackendConfigs);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            allBackendConfigs, context.getAppId(), context.getWorkflowExecutionId());
      }

      List<NameValuePair> allEnvironmentVariables = configParameter.getEnvironmentVariables();
      Map<String, String> envVars = null;
      Map<String, EncryptedDataDetail> encryptedEnvVars = null;
      if (allEnvironmentVariables != null) {
        envVars = infrastructureProvisionerService.extractUnresolvedTextVariables(allEnvironmentVariables);
        encryptedEnvVars = infrastructureProvisionerService.extractEncryptedTextVariables(
            allEnvironmentVariables, context.getAppId(), context.getWorkflowExecutionId());
      }

      List<String> targets = configParameter.getTargets();
      targets = resolveTargets(targets, context);
      gitConfigHelperService.convertToRepoGitConfig(
          gitConfig, context.renderExpression(terragruntProvisioner.getRepoName()));

      ManagerExecutionLogCallback executionLogCallback = infrastructureProvisionerService.getManagerExecutionCallback(
          terragruntProvisioner.getAppId(), activityId, commandUnit().name());
      executionLogCallback.saveExecutionLog(rollbackMessage.toString());

      setTfVarGitFileConfig(configParameter.getTfVarGitFileConfig());
      setTfVarFiles(configParameter.getTfVarFiles());

      TerragruntProvisionParameters parameters =
          TerragruntProvisionParameters.builder()
              .timeoutInMillis(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
              .accountId(executionContext.getApp().getAccountId())
              .activityId(activityId)
              .rawVariables(allVariables)
              .appId(executionContext.getAppId())
              .currentStateFileId(fileId)
              .entityId(entityId)
              .command(rollbackCommand)
              .commandUnit(TerragruntCommandUnit.Rollback)
              .sourceRepoSettingId(configParameter.getSourceRepoSettingId())
              .sourceRepo(gitConfig)
              .sourceRepoEncryptionDetails(
                  secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId()))
              .scriptPath(path)
              .variables(textVariables)
              .encryptedVariables(encryptedTextVariables)
              .backendConfigs(backendConfigs)
              .encryptedBackendConfigs(encryptedBackendConfigs)
              .environmentVariables(envVars)
              .encryptedEnvironmentVariables(encryptedEnvVars)
              .targets(targets)
              .runPlanOnly(false)
              .pathToModule(pathToModule)
              .runAll(configParameter.isRunAll())
              .tfVarFiles(configParameter.getTfVarFiles())
              .tfVarSource(terragruntStateHelper.getTfVarSource(context, getTfVarFiles(), getTfVarGitFileConfig()))
              .workspace(workspace)
              .delegateTag(configParameter.getDelegateTag())
              .build();

      return createAndRunTask(activityId, executionContext, parameters, configParameter.getDelegateTag());
    }
  }

  private boolean applyHappened(ExecutionContext context, String pathToModule) {
    SweepingOutputInstance sweepingOutputInstance = sweepingOutputService.find(
        context.prepareSweepingOutputInquiryBuilder().name(getMarkerName(getProvisionerId(), pathToModule)).build());
    if (sweepingOutputInstance == null) {
      return false;
    }
    return ((TerragruntApplyMarkerParam) sweepingOutputInstance.getValue()).isApplyCompleted();
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    Map.Entry<String, ResponseData> entry = response.entrySet().iterator().next();
    TerragruntExecutionData terragruntExecutionData = (TerragruntExecutionData) entry.getValue();
    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);
    String entityId = generateEntityId(context, terragruntExecutionData.getWorkspace(),
        terragruntExecutionData.getBranch(), terragruntExecutionData.getPathToModule());

    if (isNotBlank(terragruntExecutionData.getStateFileId()) && !terragruntExecutionData.isRunAll()) {
      fileService.updateParentEntityIdAndVersion(PhaseStep.class, terragruntExecutionData.getEntityId(), null,
          terragruntExecutionData.getStateFileId(), null, TERRAFORM_STATE);
    }
    if (terragruntExecutionData.getExecutionStatus() == SUCCESS) {
      if (terragruntExecutionData.getCommandExecuted() == APPLY) {
        terragruntStateHelper.saveTerragruntConfig(
            context, terragruntProvisioner.getSourceRepoSettingId(), terragruntExecutionData, entityId);
      } else if (terragruntExecutionData.getCommandExecuted() == DESTROY) {
        terragruntStateHelper.deleteTerragruntConfiguUsingOekflowExecutionId(context, entityId);
      }
    }

    return ExecutionResponse.builder().executionStatus(terragruntExecutionData.getExecutionStatus()).build();
  }

  @Override
  @SchemaIgnore
  public String getWorkspace() {
    return super.getWorkspace();
  }

  @Override
  @SchemaIgnore
  public String getProvisionerId() {
    return super.getProvisionerId();
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  @SchemaIgnore
  public String getPathToModule() {
    return super.getPathToModule();
  }

  @Override
  @SchemaIgnore
  public List<String> getTfVarFiles() {
    return super.getTfVarFiles();
  }

  @Override
  @SchemaIgnore
  public GitFileConfig getTfVarGitFileConfig() {
    return super.getTfVarGitFileConfig();
  }
}
