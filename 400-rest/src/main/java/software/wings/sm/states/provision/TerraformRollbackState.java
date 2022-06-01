/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.ACTIVITY_ID_BASED_TF_BASE_DIR;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.TERRAFORM_AWS_CP_AUTHENTICATION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.delegation.TerraformProvisionParameters.TIMEOUT_IN_MINUTES;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.TerraformApplyMarkerParam;
import software.wings.api.TerraformExecutionData;
import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.delegation.TerraformProvisionParameters.TerraformProvisionParametersBuilder;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.TerraformConfig.TerraformConfigKeys;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Getter
@Setter
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerraformRollbackState extends TerraformProvisionState {
  private TerraformCommand rollbackCommand;

  @Inject private GitUtilsManager gitUtilsManager;

  /**
   * Instantiates a new state.
   *
   * @param name      the name
   */
  public TerraformRollbackState(String name) {
    super(name, StateType.TERRAFORM_ROLLBACK.name());
  }

  @Override
  protected TerraformCommandUnit commandUnit() {
    return TerraformCommandUnit.Rollback;
  }

  @Override
  protected TerraformCommand command() {
    return rollbackCommand;
  }

  private boolean applyHappened(ExecutionContext context) {
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(getMarkerName()).build());
    if (sweepingOutputInstance == null) {
      return false;
    }
    return ((TerraformApplyMarkerParam) sweepingOutputInstance.getValue()).isApplyCompleted();
  }

  @Override
  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    if (!applyHappened(context)) {
      return ExecutionResponse.builder()
          .executionStatus(SUCCESS)
          .errorMessage(format("Apply did not happen with provisioner: [%s]", terraformProvisioner.getName()))
          .build();
    }
    String path = context.renderExpression(terraformProvisioner.getPath());
    String workspace = context.renderExpression(getWorkspace());
    workspace = handleDefaultWorkspace(workspace);
    String entityId = generateEntityId(context, workspace, terraformProvisioner, true);

    try (HIterator<TerraformConfig> configIterator = new HIterator(
             wingsPersistence.createQuery(TerraformConfig.class)
                 .filter(TerraformConfigKeys.appId, context.getAppId())
                 .field(TerraformConfigKeys.entityId)
                 .in(Arrays.asList(entityId, generateEntityId(context, workspace, terraformProvisioner, false)))
                 .order(Sort.descending(TerraformConfigKeys.createdAt))
                 .fetch())) {
      if (!configIterator.hasNext()) {
        return ExecutionResponse.builder()
            .executionStatus(SUCCESS)
            .errorMessage("No Rollback Required. Provisioning seems to have failed.")
            .build();
      }
      return executeInternalWithTerraformConfig(
          context, activityId, terraformProvisioner, path, workspace, entityId, configIterator);
    }
  }

  private ExecutionResponse executeInternalWithTerraformConfig(ExecutionContext context, String activityId,
      TerraformInfrastructureProvisioner terraformProvisioner, String path, String workspace, String entityId,
      HIterator<TerraformConfig> configIterator) {
    TerraformConfig configParameter = null;
    TerraformConfig currentConfig = null;
    while (configIterator.hasNext()) {
      configParameter = configIterator.next();

      if (configParameter.getWorkflowExecutionId().equals(context.getWorkflowExecutionId())) {
        if (currentConfig == null) {
          currentConfig = configParameter;
        }
      } else {
        TerraformCommand savedCommand = configParameter.getCommand();
        rollbackCommand = savedCommand != null ? savedCommand : TerraformCommand.APPLY;
        break;
      }
    }
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    StringBuilder rollbackMessage = new StringBuilder();
    if (configParameter == currentConfig) {
      rollbackMessage.append("No previous successful terraform execution, hence destroying.");
      rollbackCommand = TerraformCommand.DESTROY;
    } else {
      rollbackMessage.append("Inheriting terraform execution from last successful terraform execution : ");
      rollbackMessage.append(getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext));
    }

    notNullCheck("TerraformConfig cannot be null", configParameter);
    final GitConfig gitConfig = gitUtilsManager.getGitConfig(configParameter.getSourceRepoSettingId());
    if (StringUtils.isNotEmpty(configParameter.getSourceRepoReference())) {
      gitConfig.setReference(configParameter.getSourceRepoReference());
      String branch = context.renderExpression(terraformProvisioner.getSourceRepoBranch());
      if (isNotEmpty(branch)) {
        gitConfig.setBranch(branch);
      }
    }

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
      backendConfigs = infrastructureProvisionerService.extractTextVariables(allBackendConfigs, context);
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
        gitConfig, context.renderExpression(terraformProvisioner.getRepoName()));

    ManagerExecutionLogCallback executionLogCallback = infrastructureProvisionerService.getManagerExecutionCallback(
        terraformProvisioner.getAppId(), activityId, commandUnit().name());
    executionLogCallback.saveExecutionLog(rollbackMessage.toString());

    setTfVarGitFileConfig(configParameter.getTfVarGitFileConfig());
    setTfVarFiles(configParameter.getTfVarFiles());

    String fileId =
        fileService.getLatestFileId(generateEntityId(context, workspace, terraformProvisioner, true), TERRAFORM_STATE);

    if (fileId == null) {
      log.info("Retrieving fileId with old entityId");
      fileId = fileService.getLatestFileId(
          generateEntityId(context, workspace, terraformProvisioner, false), TERRAFORM_STATE);
      log.info("{} fileId with old entityId", fileId == null ? "Didn't retrieve" : "Retrieved");
    }

    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        TerraformProvisionParameters.builder()
            .timeoutInMillis(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
            .accountId(executionContext.getApp().getAccountId())
            .activityId(activityId)
            .rawVariables(allVariables)
            .appId(executionContext.getAppId())
            .currentStateFileId(fileId)
            .entityId(entityId)
            .command(rollbackCommand)
            .commandUnit(TerraformCommandUnit.Rollback)
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
            .tfVarFiles(configParameter.getTfVarFiles())
            .tfVarSource(getTfVarSource(context))
            .workspace(workspace)
            .delegateTag(configParameter.getDelegateTag())
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, executionContext.getApp().getAccountId()))
            .useActivityIdBasedTfBaseDir(
                featureFlagService.isEnabled(ACTIVITY_ID_BASED_TF_BASE_DIR, context.getAccountId()));

    if (featureFlagService.isEnabled(TERRAFORM_AWS_CP_AUTHENTICATION, context.getAccountId())) {
      SettingAttribute settingAttribute = getAwsConfigSettingAttribute(configParameter.getAwsConfigId());
      AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
      terraformProvisionParametersBuilder.awsConfig(awsConfig)
          .awsConfigId(configParameter.getAwsConfigId())
          .awsConfigEncryptionDetails(
              secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId()))
          .awsRoleArn(configParameter.getAwsRoleArn())
          .awsRegion(configParameter.getAwsRegion());
    }

    return createAndRunTask(
        activityId, executionContext, terraformProvisionParametersBuilder.build(), configParameter.getDelegateTag());
  }

  /**
   * @param configParameter of the last successful workflow execution.
   * @param executionContext context.
   * @return last successful workflow execution url.
   */
  @NotNull
  protected StringBuilder getLastSuccessfulWorkflowExecutionUrl(
      TerraformConfig configParameter, ExecutionContextImpl executionContext) {
    return new StringBuilder()
        .append(configuration.getPortal().getUrl())
        .append("/#/account/")
        .append(configParameter.getAccountId())
        .append("/app/")
        .append(configParameter.getAppId())
        .append("/env/")
        .append(executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : "null")
        .append("/executions/")
        .append(configParameter.getWorkflowExecutionId())
        .append("/details");
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    Entry<String, ResponseData> entry = response.entrySet().iterator().next();
    TerraformExecutionData terraformExecutionData = (TerraformExecutionData) entry.getValue();
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);

    if (isNotBlank(terraformExecutionData.getStateFileId())) {
      fileService.updateParentEntityIdAndVersion(PhaseStep.class, terraformExecutionData.getEntityId(), null,
          terraformExecutionData.getStateFileId(), null, FileBucket.TERRAFORM_STATE);
    }
    if (terraformExecutionData.getExecutionStatus() == SUCCESS) {
      if (terraformExecutionData.getCommandExecuted() == TerraformCommand.APPLY) {
        saveTerraformConfig(context, terraformProvisioner, terraformExecutionData);
      } else if (terraformExecutionData.getCommandExecuted() == TerraformCommand.DESTROY) {
        Query<TerraformConfig> query = getTerraformConfigQuery(
            context, generateEntityId(context, terraformExecutionData.getWorkspace(), terraformProvisioner, true));
        wingsPersistence.delete(query);

        Query<TerraformConfig> queryWithOldEntityId = getTerraformConfigQuery(
            context, generateEntityId(context, terraformExecutionData.getWorkspace(), terraformProvisioner, false));
        boolean deleted = wingsPersistence.delete(queryWithOldEntityId);
        log.info("{} TerraformConfig with old entityId", deleted ? "Deleted" : "Didn't delete");
      }
    }

    return ExecutionResponse.builder().executionStatus(terraformExecutionData.getExecutionStatus()).build();
  }

  private Query<TerraformConfig> getTerraformConfigQuery(ExecutionContext context, String entityId) {
    return wingsPersistence.createQuery(TerraformConfig.class)
        .filter(TerraformConfigKeys.entityId, entityId)
        .filter(TerraformConfigKeys.workflowExecutionId, context.getWorkflowExecutionId());
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
}
