/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG;
import static io.harness.beans.FeatureName.TG_USE_AUTO_APPROVE_FLAG;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.context.ContextElementType.TERRAGRUNT_INHERIT_PLAN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.provision.TerraformConstants.BACKEND_CONFIGS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_BACKEND_CONFIGS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_ENVIRONMENT_VARS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_VARIABLES_KEY;
import static io.harness.provision.TerraformConstants.ENVIRONMENT_VARS_KEY;
import static io.harness.provision.TerraformConstants.QUALIFIER_APPLY;
import static io.harness.provision.TerraformConstants.TARGETS_KEY;
import static io.harness.provision.TerraformConstants.TF_APPLY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_DESTROY_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_DESTROY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_COMMIT_ID_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_CONNECTOR_ID_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_FILE_PATH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_REPO_NAME_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_USE_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_KEY;
import static io.harness.provision.TerraformConstants.VARIABLES_KEY;
import static io.harness.provision.TerraformConstants.WORKSPACE_KEY;
import static io.harness.provision.TerragruntConstants.DESTROY_PLAN;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;
import static io.harness.provision.TerragruntConstants.INIT;
import static io.harness.provision.TerragruntConstants.PLAN;
import static io.harness.provision.TerragruntConstants.WRAP_UP;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.TaskType.TERRAGRUNT_PROVISION_TASK;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.TERRAGRUNT_PROVISION;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TIMEOUT_IN_MINUTES;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.extractVariables;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.fetchTfVarScriptRepositorySource;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.getRenderedTaskTags;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.getRenderedTfVarFiles;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.handleDefaultWorkspace;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.isSecretManagerRequired;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.resolveTargets;
import static software.wings.helpers.ext.terragrunt.TerragruntStateHelper.validateTerragruntVariables;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.TriggeredBy;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.FileMetadata;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.provision.TerragruntConstants;
import io.harness.provision.TfVarSource;
import io.harness.provision.TfVarSource.TfVarSourceType;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.tasks.ResponseData;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntProvisionInheritPlanElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.TerragruntDummyCommandUnit;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit;
import software.wings.helpers.ext.terragrunt.TerragruntStateHelper;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

@FieldNameConstants(onlyExplicitlyIncluded = true, innerTypeName = "TerragruntProvisionStateKeys")
@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public abstract class TerragruntProvisionState extends State {
  @Inject private transient AppService appService;
  @Inject private transient ActivityService activityService;
  @Inject protected transient InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient GitUtilsManager gitUtilsManager;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private transient GitFileConfigHelperService gitFileConfigHelperService;

  @Inject private transient ServiceVariableService serviceVariableService;
  @Inject private transient EncryptionService encryptionService;

  @Inject protected transient DelegateService delegateService;
  @Inject protected transient FileService fileService;
  @Inject protected transient SecretManager secretManager;
  @Inject protected transient GitConfigHelperService gitConfigHelperService;
  @Inject private transient LogService logService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject protected TerragruntStateHelper terragruntStateHelper;
  @Inject protected TerraformPlanHelper terraformPlanHelper;

  @FieldNameConstants.Include @Attributes(title = "Provisioner") @Getter @Setter String provisionerId;

  @Attributes(title = "Variables") @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> variables;
  @Attributes(title = "Backend Configs")
  @FieldNameConstants.Include
  @Getter
  @Setter
  private List<NameValuePair> backendConfigs;
  @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> environmentVariables;
  @Getter @Setter private List<String> targets;

  @Getter @Setter private List<String> tfVarFiles;
  @Getter @Setter private GitFileConfig tfVarGitFileConfig;

  @Getter @Setter private boolean runPlanOnly;
  @Getter @Setter private boolean inheritApprovedPlan;
  @Getter @Setter private boolean exportPlanToApplyStep;
  @Getter @Setter private String workspace;
  @Getter @Setter private String delegateTag;

  // should be relative to root directory
  @Getter @Setter private String pathToModule;
  @Getter @Setter private boolean runAll;

  public TerragruntProvisionState(String name, String stateType) {
    super(name, stateType);
  }

  protected abstract TerragruntCommandUnit commandUnit();
  protected abstract TerragruntCommand command();

  public static final String TERRAGRUNT_PROVISION_COMMAND_UNIT_TYPE = "Terragrunt Provision";

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    List<CommandUnit> terragruntCommandUnits = getTerragruntCommandUnits();
    String activityId = createActivity(context, terragruntCommandUnits);
    return executeInternal(context, activityId);
  }

  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    validateRunAllConditions();
    validateTerragruntVariables(variables, backendConfigs, environmentVariables);
    if (inheritApprovedPlan) {
      return executeInternalInherited(context, activityId);
    } else {
      return executeInternalRegular(context, activityId);
    }
  }

  private void validateRunAllConditions() {
    if (runAll) {
      if (exportPlanToApplyStep || inheritApprovedPlan) {
        throw new InvalidRequestException("Terraform plan can't be exported or inherited while using run-all commands");
      }
    }
  }

  private ExecutionResponse executeInternalRegular(ExecutionContext context, String activityId) {
    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);

    GitConfig gitConfig = terragruntStateHelper.populateAndGetGitConfig(context, terragruntProvisioner);

    // secret manager wont be needed in run-all cases, as no exporting/encrypting/decrypting of terraform plan involved
    SecretManagerConfig secretManagerConfig =
        isSecretManagerRequired(runPlanOnly, exportPlanToApplyStep, inheritApprovedPlan, isRunAll(), command())
        ? terragruntStateHelper.getSecretManagerContainingTfPlan(
            terragruntProvisioner.getSecretManagerId(), context.getAccountId())
        : null;

    String path = context.renderExpression(terragruntProvisioner.getNormalizedPath());
    if (path == null) {
      path = context.renderExpression(FilenameUtils.normalize(terragruntProvisioner.getPath()));
      if (path == null) {
        throw new InvalidRequestException("Invalid Terragrunt script path", USER);
      }
    }

    String pathToModule = context.renderExpression(FilenameUtils.normalize(this.pathToModule));
    if (pathToModule == null) {
      throw new InvalidRequestException("Invalid Terragrunt module path", USER);
    }

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String workspace = context.renderExpression(this.workspace);
    workspace = handleDefaultWorkspace(workspace);
    String entityId = generateEntityId(context, workspace, gitConfig.getBranch(), pathToModule);
    String fileId = fileService.getLatestFileId(entityId, TERRAFORM_STATE);
    Map<String, String> variables = null;
    Map<String, EncryptedDataDetail> encryptedVariables = null;
    Map<String, String> backendConfigs = null;
    Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
    Map<String, String> environmentVars = null;
    Map<String, EncryptedDataDetail> encryptedEnvironmentVars = null;
    List<NameValuePair> rawVariablesList = new ArrayList<>();

    if (isNotEmpty(this.variables) || isNotEmpty(this.backendConfigs) || isNotEmpty(this.environmentVariables)) {
      if (this.variables != null) {
        rawVariablesList.addAll(this.variables);
        variables = infrastructureProvisionerService.extractUnresolvedTextVariables(this.variables);
        encryptedVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
            this.variables, context.getAppId(), context.getWorkflowExecutionId());
      }

      if (this.backendConfigs != null) {
        backendConfigs = infrastructureProvisionerService.extractUnresolvedTextVariables(this.backendConfigs);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            this.backendConfigs, context.getAppId(), context.getWorkflowExecutionId());
      }

      if (this.environmentVariables != null) {
        environmentVars = infrastructureProvisionerService.extractUnresolvedTextVariables(this.environmentVariables);
        encryptedEnvironmentVars = infrastructureProvisionerService.extractEncryptedTextVariables(
            this.environmentVariables, context.getAppId(), context.getWorkflowExecutionId());
      }

    } else if (this instanceof TerragruntDestroyState) {
      if (this.runAll) {
        throw new InvalidRequestException(
            "Configuration Required, Local state file is not supported for Run-All commands", USER);
      }
      fileId = fileService.getLatestFileIdByQualifier(entityId, TERRAFORM_STATE, QUALIFIER_APPLY);
      if (fileId != null) {
        FileMetadata fileMetadata = fileService.getFileMetadata(fileId, TERRAFORM_STATE);

        if (fileMetadata != null && fileMetadata.getMetadata() != null) {
          variables = terragruntStateHelper.extractData(fileMetadata, VARIABLES_KEY);
          Map<String, Object> rawVariables = (Map<String, Object>) fileMetadata.getMetadata().get(VARIABLES_KEY);
          if (isNotEmpty(rawVariables)) {
            rawVariablesList.addAll(extractVariables(rawVariables, "TEXT"));
          }

          backendConfigs = terragruntStateHelper.extractBackendConfigs(fileMetadata);

          encryptedVariables =
              terragruntStateHelper.extractEncryptedData(context, fileMetadata, ENCRYPTED_VARIABLES_KEY);
          Map<String, Object> rawEncryptedVariables =
              (Map<String, Object>) fileMetadata.getMetadata().get(ENCRYPTED_VARIABLES_KEY);
          if (isNotEmpty(rawEncryptedVariables)) {
            rawVariablesList.addAll(extractVariables(rawEncryptedVariables, "ENCRYPTED_TEXT"));
          }

          encryptedBackendConfigs =
              terragruntStateHelper.extractEncryptedData(context, fileMetadata, ENCRYPTED_BACKEND_CONFIGS_KEY);

          environmentVars = terragruntStateHelper.extractData(fileMetadata, ENVIRONMENT_VARS_KEY);
          encryptedEnvironmentVars =
              terragruntStateHelper.extractEncryptedData(context, fileMetadata, ENCRYPTED_ENVIRONMENT_VARS_KEY);

          List<String> targets = (List<String>) fileMetadata.getMetadata().get(TARGETS_KEY);
          if (isNotEmpty(targets)) {
            setTargets(targets);
          }

          List<String> tfVarFiles = (List<String>) fileMetadata.getMetadata().get(TF_VAR_FILES_KEY);
          if (isNotEmpty(tfVarFiles)) {
            setTfVarFiles(tfVarFiles);
          }

          String tfVarGitFileConnectorId = (String) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_CONNECTOR_ID_KEY);
          if (isNotEmpty(tfVarGitFileConnectorId)) {
            GitFileConfig gitFileConfig =
                GitFileConfig.builder()
                    .connectorId(tfVarGitFileConnectorId)
                    .filePath((String) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_FILE_PATH_KEY))
                    .branch((String) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_BRANCH_KEY))
                    .commitId((String) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_COMMIT_ID_KEY))
                    .useBranch((boolean) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_USE_BRANCH_KEY))
                    .repoName((String) fileMetadata.getMetadata().get(TF_VAR_FILES_GIT_REPO_NAME_KEY))
                    .build();
            setTfVarGitFileConfig(gitFileConfig);
          }
        }
      }
    }

    TfVarSource tfVarSource = null;

    // Currently we allow only one tfVar source
    if (isNotEmpty(tfVarFiles)) {
      tfVarSource = fetchTfVarScriptRepositorySource(context, tfVarFiles);
    } else if (null != tfVarGitFileConfig) {
      tfVarSource = terragruntStateHelper.fetchTfVarGitSource(context, tfVarGitFileConfig);
    }

    targets = resolveTargets(targets, context);
    gitConfigHelperService.convertToRepoGitConfig(
        gitConfig, context.renderExpression(terragruntProvisioner.getRepoName()));

    if (runPlanOnly && !runAll && this instanceof TerragruntDestroyState) {
      exportPlanToApplyStep = true;
    }

    TerragruntProvisionParameters parameters =
        TerragruntProvisionParameters.builder()
            .accountId(executionContext.getApp().getAccountId())
            .activityId(activityId)
            .timeoutInMillis(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
            .appId(executionContext.getAppId())
            .currentStateFileId(fileId)
            .entityId(entityId)
            .command(command())
            .commandUnit(commandUnit())
            .sourceRepoSettingId(terragruntProvisioner.getSourceRepoSettingId())
            .sourceRepo(gitConfig)
            .sourceRepoEncryptionDetails(
                secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId()))
            .scriptPath(path)
            .variables(variables)
            .rawVariables(rawVariablesList)
            .encryptedVariables(encryptedVariables)
            .backendConfigs(backendConfigs)
            .encryptedBackendConfigs(encryptedBackendConfigs)
            .environmentVariables(environmentVars)
            .encryptedEnvironmentVariables(encryptedEnvironmentVars)
            .targets(targets)
            .runPlanOnly(runPlanOnly)
            .exportPlanToApplyStep(exportPlanToApplyStep)
            .saveTerragruntJson(featureFlagService.isEnabled(FeatureName.EXPORT_TF_PLAN, context.getAccountId()))
            .tfVarFiles(getRenderedTfVarFiles(tfVarFiles, context))
            .workspace(workspace)
            .delegateTag(delegateTag)
            .tfVarSource(tfVarSource)
            .skipRefreshBeforeApplyingPlan(terragruntProvisioner.isSkipRefreshBeforeApplyingPlan())
            .secretManagerConfig(secretManagerConfig)
            .encryptedTfPlan(null)
            .planName(getPlanName(context))
            .pathToModule(pathToModule)
            .runAll(runAll)
            .encryptDecryptPlanForHarnessSMOnManager(
                featureFlagService.isEnabled(
                    CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, executionContext.getApp().getAccountId())
                && terragruntStateHelper.isHarnessSecretManager(secretManagerConfig))
            .useAutoApproveFlag(featureFlagService.isEnabled(TG_USE_AUTO_APPROVE_FLAG, context.getAccountId()))
            .build();

    return createAndRunTask(activityId, executionContext, parameters, delegateTag);
  }

  private ExecutionResponse executeInternalInherited(ExecutionContext context, String activityId) {
    List<TerragruntProvisionInheritPlanElement> allPlanElements =
        context.getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    if (isEmpty(allPlanElements)) {
      throw new InvalidRequestException(
          "No previous Terragrunt plan execution found. Unable to inherit configuration from Terragrunt Plan");
    }
    Optional<TerragruntProvisionInheritPlanElement> elementOptional =
        allPlanElements.stream().filter(element -> element.getProvisionerId().equals(provisionerId)).findFirst();
    if (!elementOptional.isPresent()) {
      throw new InvalidRequestException("No Terragrunt provision command found with current provisioner");
    }
    TerragruntProvisionInheritPlanElement element = elementOptional.get();

    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);
    String path = context.renderExpression(terragruntProvisioner.getNormalizedPath());
    if (path == null) {
      path = context.renderExpression(FilenameUtils.normalize(terragruntProvisioner.getPath()));
      if (path == null) {
        throw new InvalidRequestException("Invalid Terragrunt script path", USER);
      }
    }

    String pathToModule = context.renderExpression(element.getPathToModule());
    if (pathToModule == null) {
      pathToModule = context.renderExpression(FilenameUtils.normalize(element.getPathToModule()));
      if (pathToModule == null) {
        throw new InvalidRequestException("Invalid Terragrunt module path", USER);
      }
    }

    String workspace = context.renderExpression(element.getWorkspace());
    String branch = context.renderExpression(element.getBranch());
    String entityId = generateEntityId(context, workspace, branch, pathToModule);
    String fileId = fileService.getLatestFileId(entityId, TERRAFORM_STATE);

    GitConfig gitConfig = gitUtilsManager.getGitConfig(element.getSourceRepoSettingId());
    if (isNotEmpty(element.getSourceRepoReference())) {
      gitConfig.setReference(element.getSourceRepoReference());
      if (isNotEmpty(branch)) {
        gitConfig.setBranch(branch);
      }
    } else {
      throw new InvalidRequestException("No commit id found in context inherit terragrunt plan element. ");
    }

    gitConfigHelperService.convertToRepoGitConfig(
        gitConfig, context.renderExpression(terragruntProvisioner.getRepoName()));

    List<NameValuePair> allBackendConfigs = element.getBackendConfigs();
    Map<String, String> backendConfigs = null;
    Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
    if (isNotEmpty(allBackendConfigs)) {
      backendConfigs = infrastructureProvisionerService.extractUnresolvedTextVariables(allBackendConfigs);
      encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
          allBackendConfigs, context.getAppId(), context.getWorkflowExecutionId());
    }

    List<NameValuePair> allVariables = element.getVariables();
    Map<String, String> textVariables = null;
    Map<String, EncryptedDataDetail> encryptedTextVariables = null;
    if (isNotEmpty(allVariables)) {
      textVariables = infrastructureProvisionerService.extractUnresolvedTextVariables(allVariables);
      encryptedTextVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
          allVariables, context.getAppId(), context.getWorkflowExecutionId());
    }

    List<NameValuePair> allEnvVars = element.getEnvironmentVariables();
    Map<String, String> envVars = null;
    Map<String, EncryptedDataDetail> encryptedEnvVars = null;
    if (isNotEmpty(allEnvVars)) {
      envVars = infrastructureProvisionerService.extractUnresolvedTextVariables(allEnvVars);
      encryptedEnvVars = infrastructureProvisionerService.extractEncryptedTextVariables(
          allEnvVars, context.getAppId(), context.getWorkflowExecutionId());
    }

    List<String> targets = element.getTargets();
    targets = resolveTargets(targets, context);

    // secret manager wont be needed in run-all cases, as no exporting/encrypting/decrypting of terraform plan involved
    SecretManagerConfig secretManagerConfig =
        isSecretManagerRequired(runPlanOnly, exportPlanToApplyStep, inheritApprovedPlan, isRunAll(), command())
            && !isRunAll()
        ? terragruntStateHelper.getSecretManagerContainingTfPlan(
            terragruntProvisioner.getSecretManagerId(), context.getAccountId())
        : null;

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    TerragruntProvisionParameters parameters =
        TerragruntProvisionParameters.builder()
            .accountId(executionContext.getApp().getAccountId())
            .timeoutInMillis(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
            .activityId(activityId)
            .appId(executionContext.getAppId())
            .currentStateFileId(fileId)
            .entityId(entityId)
            .rawVariables(allVariables)
            .command(command())
            .commandUnit(commandUnit())
            .sourceRepoSettingId(element.getSourceRepoSettingId())
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
            .tfVarFiles(element.getTfVarFiles())
            .tfVarSource(element.getTfVarSource())
            .runPlanOnly(false)
            .exportPlanToApplyStep(false)
            .workspace(workspace)
            .delegateTag(element.getDelegateTag())
            .skipRefreshBeforeApplyingPlan(terragruntProvisioner.isSkipRefreshBeforeApplyingPlan())
            .encryptedTfPlan(element.getEncryptedTfPlan())
            .secretManagerConfig(secretManagerConfig)
            .planName(getPlanName(context))
            .pathToModule(pathToModule)
            .runAll(runAll)
            .encryptDecryptPlanForHarnessSMOnManager(
                featureFlagService.isEnabled(
                    CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, executionContext.getAccountId())
                && terragruntStateHelper.isHarnessSecretManager(secretManagerConfig))
            .useAutoApproveFlag(featureFlagService.isEnabled(TG_USE_AUTO_APPROVE_FLAG, context.getAccountId()))
            .build();
    return createAndRunTask(activityId, executionContext, parameters, element.getDelegateTag());
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to do
  }

  protected String generateEntityId(ExecutionContext context, String workspace, String branch, String pathToModule) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String envId = executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : EMPTY;
    String entityIdPrefix =
        new StringBuilder(provisionerId).append(pathToModule).append(branch).append(envId).toString();
    String entityId = isEmpty(workspace) ? entityIdPrefix : (entityIdPrefix + "-" + workspace);
    return String.valueOf(entityId.hashCode());
  }

  private String createActivity(ExecutionContext executionContext, List<CommandUnit> terragruntCommandUnits) {
    Application app = requireNonNull(executionContext.getApp());
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .commandName(getName())
                                          .type(Type.Other)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnitType(TERRAGRUNT_PROVISION)
                                          .commandUnits(terragruntCommandUnits)
                                          .status(ExecutionStatus.RUNNING)
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      Environment env = requireNonNull(((ExecutionContextImpl) executionContext).getEnv());
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }

    Activity activity = activityBuilder.build();
    activity.setAppId(app.getUuid());
    return activityService.save(activity).getUuid();
  }

  protected ExecutionResponse createAndRunTask(String activityId, ExecutionContextImpl executionContext,
      TerragruntProvisionParameters parameters, String delegateTag) {
    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(requireNonNull(executionContext.getApp()).getAccountId())
            .waitId(activityId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, requireNonNull(executionContext.getApp()).getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : null)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getEnvironmentType().name() : null)
            .tags(getRenderedTaskTags(delegateTag, executionContext))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TERRAGRUNT_PROVISION_TASK.name())
                      .parameters(new Object[] {parameters})
                      .timeout(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    ScriptStateExecutionData stateExecutionData = ScriptStateExecutionData.builder().activityId(activityId).build();
    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    renderDelegateTask(executionContext, delegateTask, stateExecutionContext);

    appendDelegateTaskDetails(executionContext, delegateTask);
    String delegateTaskId = delegateService.queueTaskV2(delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  private String getPlanName(ExecutionContext context) {
    String planPrefix = DESTROY == command() ? TF_DESTROY_NAME_PREFIX : TF_NAME_PREFIX;
    return String.format(planPrefix, context.getWorkflowExecutionId());
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    if (runPlanOnly) {
      return handleAsyncResponseInternalRunPlanOnly(context, response);
    } else {
      return handleAsyncResponseInternalRegular(context, response);
    }
  }

  private ExecutionResponse handleAsyncResponseInternalRegular(
      ExecutionContext context, Map<String, ResponseData> response) {
    Map.Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();
    TerragruntExecutionData terragruntExecutionData = (TerragruntExecutionData) responseEntry.getValue();
    terragruntExecutionData.setActivityId(activityId);

    // delete the plan if it exists
    String planName = getPlanName(context);
    terraformPlanHelper.deleteEncryptedTfPlanFromSweepingOutput(context, planName);
    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);
    if (!(this instanceof TerragruntDestroyState)) {
      terragruntStateHelper.markApplyExecutionCompleted(
          context, provisionerId, terragruntExecutionData.getPathToModule());
    }

    if (terragruntExecutionData.getExecutionStatus() == FAILED) {
      return ExecutionResponse.builder()
          .stateExecutionData(terragruntExecutionData)
          .executionStatus(terragruntExecutionData.getExecutionStatus())
          .errorMessage(terragruntExecutionData.getErrorMessage())
          .build();
    }

    saveUserInputs(context, terragruntExecutionData, terragruntProvisioner);

    if (terragruntExecutionData.getOutputs() != null) {
      Map<String, Object> outputs = terragruntStateHelper.parseTerragruntOutputs(terragruntExecutionData.getOutputs());
      terragruntStateHelper.saveOutputs(context, outputs);

      ManagerExecutionLogCallback executionLogCallback = infrastructureProvisionerService.getManagerExecutionCallback(
          terragruntProvisioner.getAppId(), terragruntExecutionData.getActivityId(), commandUnit().name());
      infrastructureProvisionerService.regenerateInfrastructureMappings(
          provisionerId, context, outputs, Optional.of(executionLogCallback), Optional.empty());
    }

    updateActivityStatus(activityId, context.getAppId(), terragruntExecutionData.getExecutionStatus());

    return ExecutionResponse.builder()
        .stateExecutionData(terragruntExecutionData)
        .executionStatus(terragruntExecutionData.getExecutionStatus())
        .errorMessage(terragruntExecutionData.getErrorMessage())
        .build();
  }

  private ExecutionResponse handleAsyncResponseInternalRunPlanOnly(
      ExecutionContext context, Map<String, ResponseData> response) {
    java.util.Map.Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();

    TerragruntExecutionData terragruntExecutionData = (TerragruntExecutionData) responseEntry.getValue();
    terragruntExecutionData.setActivityId(activityId);
    TerragruntInfrastructureProvisioner terragruntProvisioner = getTerragruntInfrastructureProvisioner(context);
    updateActivityStatus(activityId, context.getAppId(), terragruntExecutionData.getExecutionStatus());
    // do not upload stateFile in case of run-all
    if ((exportPlanToApplyStep || (runPlanOnly && DESTROY == command())) && !terragruntExecutionData.isRunAll()) {
      String planName = getPlanName(context);
      if (terragruntExecutionData.getEncryptedTfPlan() != null) {
        terraformPlanHelper.saveEncryptedTfPlanToSweepingOutput(
            terragruntExecutionData.getEncryptedTfPlan(), context, planName);
      }
      if (isNotBlank(terragruntExecutionData.getStateFileId())) {
        fileService.updateParentEntityIdAndVersion(PhaseStep.class, terragruntExecutionData.getEntityId(), null,
            terragruntExecutionData.getStateFileId(), null, TERRAFORM_STATE);
      }
    }

    if (terragruntExecutionData.getExecutionStatus() == FAILED) {
      return ExecutionResponse.builder()
          .stateExecutionData(terragruntExecutionData)
          .executionStatus(terragruntExecutionData.getExecutionStatus())
          .errorMessage(terragruntExecutionData.getErrorMessage())
          .build();
    }

    saveTerraformPlanJson(terragruntExecutionData.getTfPlanJson(), context, command());

    TerragruntProvisionInheritPlanElement inheritPlanElement =
        TerragruntProvisionInheritPlanElement.builder()
            .entityId(generateEntityId(context, terragruntExecutionData.getWorkspace(),
                terragruntExecutionData.getBranch(), terragruntExecutionData.getPathToModule()))
            .provisionerId(provisionerId)
            .targets(terragruntExecutionData.getTargets())
            .delegateTag(terragruntExecutionData.getDelegateTag())
            .tfVarFiles(terragruntExecutionData.getTfVarFiles())
            .tfVarSource(terragruntExecutionData.getTfVarSource())
            .sourceRepoSettingId(terragruntProvisioner.getSourceRepoSettingId())
            .sourceRepoReference(terragruntExecutionData.getSourceRepoReference())
            .variables(terragruntExecutionData.getVariables())
            .backendConfigs(terragruntExecutionData.getBackendConfigs())
            .environmentVariables(terragruntExecutionData.getEnvironmentVariables())
            .workspace(terragruntExecutionData.getWorkspace())
            .encryptedTfPlan(terragruntExecutionData.getEncryptedTfPlan())
            .pathToModule(terragruntExecutionData.getPathToModule())
            .branch(terragruntExecutionData.getBranch())
            .build();

    return ExecutionResponse.builder()
        .stateExecutionData(terragruntExecutionData)
        .contextElement(inheritPlanElement)
        .notifyElement(inheritPlanElement)
        .executionStatus(terragruntExecutionData.getExecutionStatus())
        .errorMessage(terragruntExecutionData.getErrorMessage())
        .build();
  }

  protected TerragruntInfrastructureProvisioner getTerragruntInfrastructureProvisioner(ExecutionContext context) {
    InfrastructureProvisioner infrastructureProvisioner =
        infrastructureProvisionerService.get(context.getAppId(), provisionerId);

    if (infrastructureProvisioner == null) {
      throw new InvalidRequestException("Infrastructure Provisioner does not exist. Please check again.");
    }
    if (!(infrastructureProvisioner instanceof TerragruntInfrastructureProvisioner)) {
      throw new InvalidRequestException("Infrastructure Provisioner " + infrastructureProvisioner.getName()
          + "should be of Terragrunt type. Please check again.");
    }
    return (TerragruntInfrastructureProvisioner) infrastructureProvisioner;
  }

  private void updateActivityStatus(String activityId, String appId, ExecutionStatus status) {
    activityService.updateStatus(activityId, appId, status);
  }

  private void saveTerraformPlanJson(
      String terraformPlan, ExecutionContext context, TerragruntCommand terragruntCommand) {
    if (featureFlagService.isEnabled(FeatureName.EXPORT_TF_PLAN, context.getAccountId())) {
      String variableName = terragruntCommand == APPLY ? TF_APPLY_VAR_NAME : TF_DESTROY_VAR_NAME;
      // if the plan variable exists overwrite it
      SweepingOutputInstance sweepingOutputInstance =
          sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(variableName).build());
      if (sweepingOutputInstance != null) {
        sweepingOutputService.deleteById(context.getAppId(), sweepingOutputInstance.getUuid());
      }

      sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.PIPELINE)
                                     .name(variableName)
                                     .value(TerraformPlanParam.builder()
                                                .tfplan(format("'%s'", JsonUtils.prettifyJsonString(terraformPlan)))
                                                .build())
                                     .build());
    }
  }

  public void saveUserInputs(ExecutionContext context, TerragruntExecutionData terragruntExecutionData,
      TerragruntInfrastructureProvisioner terragruntProvisioner) {
    String workspace = terragruntExecutionData.getWorkspace();
    String entityId = generateEntityId(context, terragruntExecutionData.getWorkspace(),
        terragruntExecutionData.getBranch(), terragruntExecutionData.getPathToModule());
    Map<String, Object> others = new HashMap<>();
    if (!(this instanceof TerragruntDestroyState)) {
      others.put("qualifier", QUALIFIER_APPLY);
      terragruntStateHelper.collectVariables(
          others, terragruntExecutionData.getVariables(), VARIABLES_KEY, ENCRYPTED_VARIABLES_KEY, true);
      terragruntStateHelper.collectVariables(others, terragruntExecutionData.getBackendConfigs(), BACKEND_CONFIGS_KEY,
          ENCRYPTED_BACKEND_CONFIGS_KEY, false);
      terragruntStateHelper.collectVariables(others, terragruntExecutionData.getEnvironmentVariables(),
          ENVIRONMENT_VARS_KEY, ENCRYPTED_ENVIRONMENT_VARS_KEY, false);

      List<String> tfVarFiles = terragruntExecutionData.getTfVarFiles();
      TfVarSource tfVarSource = terragruntExecutionData.getTfVarSource();
      List<String> targets = terragruntExecutionData.getTargets();
      if (isNotEmpty(targets)) {
        others.put(TARGETS_KEY, targets);
      }
      if (isNotEmpty(tfVarFiles)) {
        others.put(TF_VAR_FILES_KEY, tfVarFiles);
      }
      updateParentEntityGitConfig(others, tfVarSource, targets);
      if (isNotEmpty(workspace)) {
        others.put(WORKSPACE_KEY, workspace);
      }
      if (terragruntExecutionData.getExecutionStatus() == SUCCESS) {
        terragruntStateHelper.saveTerragruntConfig(
            context, terragruntProvisioner.getSourceRepoSettingId(), terragruntExecutionData, entityId);
      }
    } else {
      if (getStateType().equals(StateType.TERRAGRUNT_DESTROY.name())) {
        if (terragruntExecutionData.getExecutionStatus() == SUCCESS) {
          if (isNotEmpty(getTargets())) {
            terragruntStateHelper.saveTerragruntConfig(
                context, terragruntProvisioner.getSourceRepoSettingId(), terragruntExecutionData, entityId);
          } else {
            terragruntStateHelper.deleteTerragruntConfig(entityId);
          }
        }
      }
    }
    if (!terragruntExecutionData.isRunAll()) {
      fileService.updateParentEntityIdAndVersion(PhaseStep.class, terragruntExecutionData.getEntityId(), null,
          terragruntExecutionData.getStateFileId(), others, TERRAFORM_STATE);
    }
  }

  public void updateParentEntityGitConfig(Map<String, Object> others, TfVarSource tfVarSource, List<String> targets) {
    if (null != tfVarSource) {
      if (tfVarSource.getTfVarSourceType() == TfVarSourceType.GIT) {
        GitFileConfig gitFileConfig = ((TfVarGitSource) tfVarSource).getGitFileConfig();
        others.put(TF_VAR_FILES_GIT_BRANCH_KEY, gitFileConfig.getBranch());
        others.put(TF_VAR_FILES_GIT_CONNECTOR_ID_KEY, gitFileConfig.getConnectorId());
        others.put(TF_VAR_FILES_GIT_COMMIT_ID_KEY, gitFileConfig.getCommitId());
        others.put(TF_VAR_FILES_GIT_FILE_PATH_KEY, gitFileConfig.getFilePath());
        others.put(TF_VAR_FILES_GIT_REPO_NAME_KEY, gitFileConfig.getRepoName());
        others.put(TF_VAR_FILES_GIT_USE_BRANCH_KEY, gitFileConfig.isUseBranch());
      }
    }
  }

  private List<CommandUnit> getTerragruntCommandUnits() {
    List<CommandUnit> commandUnits = new ArrayList<>();
    commandUnits.add(new TerragruntDummyCommandUnit(FETCH_CONFIG_FILES));
    commandUnits.add(new TerragruntDummyCommandUnit(INIT));

    if (this instanceof TerragruntDestroyState) {
      if (runPlanOnly) {
        commandUnits.add(new TerragruntDummyCommandUnit(DESTROY_PLAN));
      } else {
        commandUnits.add(new TerragruntDummyCommandUnit(TerragruntConstants.DESTROY));
      }

    } else {
      if (!runAll || runPlanOnly) {
        commandUnits.add(new TerragruntDummyCommandUnit(PLAN));
      }
      if (!runPlanOnly) {
        commandUnits.add(new TerragruntDummyCommandUnit(TerragruntConstants.APPLY));
      }
    }
    commandUnits.add(new TerragruntDummyCommandUnit(WRAP_UP));
    return commandUnits;
  }

  @Override
  protected void appendDelegateTaskDetails(ExecutionContext context, DelegateTask delegateTask) {
    super.appendDelegateTaskDetails(context, delegateTask);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    if (isEmpty(provisionerId)) {
      results.put("Provisioner", "Provisioner must be provided.");
    }
    // if more fields need to validated, please make sure templatized fields are not broken.
    return results;
  }
}
