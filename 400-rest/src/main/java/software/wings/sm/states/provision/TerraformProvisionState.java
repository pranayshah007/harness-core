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
import static io.harness.beans.FeatureName.ACTIVITY_ID_BASED_TF_BASE_DIR;
import static io.harness.beans.FeatureName.ANALYSE_TF_PLAN_SUMMARY;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_S3_SUPPORT;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW;
import static io.harness.beans.FeatureName.SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR;
import static io.harness.beans.FeatureName.TERRAFORM_AWS_CP_AUTHENTICATION;
import static io.harness.beans.FeatureName.TERRAFORM_REMOTE_BACKEND_CONFIG;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.context.ContextElementType.TERRAFORM_INHERIT_PLAN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.provision.TerraformConstants.BACKEND_CONFIGS_KEY;
import static io.harness.provision.TerraformConstants.BACKEND_CONFIG_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_BACKEND_CONFIGS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_ENVIRONMENT_VARS_KEY;
import static io.harness.provision.TerraformConstants.ENCRYPTED_VARIABLES_KEY;
import static io.harness.provision.TerraformConstants.ENVIRONMENT_VARS_KEY;
import static io.harness.provision.TerraformConstants.LOCAL_STORE_TYPE;
import static io.harness.provision.TerraformConstants.QUALIFIER_APPLY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_COMMIT_ID_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_CONNECTOR_ID_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_FILE_PATH_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_REPO_NAME_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_GIT_USE_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_S3_CONFIG_ID_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_S3_URI_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_BE_CONFIG_S3_URI_LIST_KEY;
import static io.harness.provision.TerraformConstants.REMOTE_STORE_TYPE;
import static io.harness.provision.TerraformConstants.S3_STORE_TYPE;
import static io.harness.provision.TerraformConstants.TARGETS_KEY;
import static io.harness.provision.TerraformConstants.TF_APPLY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_DESTROY_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_DESTROY_VAR_NAME;
import static io.harness.provision.TerraformConstants.TF_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_PLAN_RESOURCES_ADD;
import static io.harness.provision.TerraformConstants.TF_PLAN_RESOURCES_CHANGE;
import static io.harness.provision.TerraformConstants.TF_PLAN_RESOURCES_DESTROY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_COMMIT_ID_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_CONNECTOR_ID_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_FILE_PATH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_REPO_NAME_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_GIT_USE_BRANCH_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_S3_CONFIG_ID_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_S3_URI_KEY;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_S3_URI_LIST_KEY;
import static io.harness.provision.TerraformConstants.VARIABLES_KEY;
import static io.harness.provision.TerraformConstants.WORKSPACE_KEY;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.beans.TaskType.TERRAFORM_PROVISION_TASK;
import static software.wings.beans.TaskType.TERRAFORM_PROVISION_TASK_V2;
import static software.wings.beans.delegation.TerraformProvisionParameters.TIMEOUT_IN_MINUTES;
import static software.wings.utils.Utils.splitCommaSeparatedFilePath;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

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
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.TriggeredBy;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.beans.terraform.TerraformPlanParam.TerraformPlanParamBuilder;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.LogLevel;
import io.harness.provision.TfVarScriptRepositorySource;
import io.harness.provision.TfVarSource;
import io.harness.provision.TfVarSource.TfVarSourceType;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.serializer.JsonUtils;
import io.harness.tasks.ResponseData;

import software.wings.api.PhaseElement;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.TerraformApplyMarkerParam;
import software.wings.api.TerraformExecutionData;
import software.wings.api.TerraformOutputInfoElement;
import software.wings.api.terraform.TerraformOutputVariables;
import software.wings.api.terraform.TerraformProvisionInheritPlanElement;
import software.wings.api.terraform.TfVarGitSource;
import software.wings.api.terraform.TfVarS3Source;
import software.wings.app.MainConfiguration;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.S3FileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TerraformBackendConfig;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.command.Command.Builder;
import software.wings.beans.command.CommandType;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.delegation.TerraformProvisionParameters.TerraformProvisionParametersBuilder;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.TerraformConfig.TerraformConfigKeys;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.dl.WingsPersistence;
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
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.reinert.jjschema.Attributes;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

@FieldNameConstants(onlyExplicitlyIncluded = true, innerTypeName = "TerraformProvisionStateKeys")
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public abstract class TerraformProvisionState extends State {
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

  @Inject protected transient WingsPersistence wingsPersistence;
  @Inject protected transient DelegateService delegateService;
  @Inject protected transient FileService fileService;
  @Inject protected transient SecretManager secretManager;
  @Inject protected transient GitConfigHelperService gitConfigHelperService;
  @Inject private transient LogService logService;
  @Inject protected FeatureFlagService featureFlagService;
  @Inject protected SweepingOutputService sweepingOutputService;
  @Inject protected TerraformPlanHelper terraformPlanHelper;
  @Inject protected transient MainConfiguration configuration;
  @Inject protected transient TemplateExpressionProcessor templateExpressionProcessor;

  @FieldNameConstants.Include @Attributes(title = "Provisioner") @Getter @Setter String provisionerId;

  @Attributes(title = "Variables") @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> variables;
  @Attributes(title = "Backend Configs")
  @FieldNameConstants.Include
  @Getter
  @Setter
  private List<NameValuePair> backendConfigs;

  @Attributes(title = "Backend Config")
  @FieldNameConstants.Include
  @Getter
  @Setter
  private TerraformBackendConfig backendConfig;

  @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> environmentVariables;
  @Getter @Setter private List<String> targets;

  @Getter @Setter private List<String> tfVarFiles;

  @Getter @Setter private S3FileConfig tfVarS3FileConfig;
  @Getter @Setter private GitFileConfig tfVarGitFileConfig;

  @Getter @Setter private boolean runPlanOnly;
  @Getter @Setter private boolean inheritApprovedPlan;
  @Getter @Setter private boolean exportPlanToApplyStep;
  @Getter @Setter private boolean exportPlanToHumanReadableOutput;
  @Getter @Setter private String workspace;
  @Getter @Setter private String delegateTag;
  @Attributes(title = "awsConfigId") @Getter @Setter private String awsConfigId;
  @Attributes(title = "awsRoleArn") @Getter @Setter private String awsRoleArn;
  @Attributes(title = "awsRegion") @Getter @Setter private String awsRegion;

  static final String DUPLICATE_VAR_MSG_PREFIX = "variable names should be unique, duplicate variable(s) found: ";

  /**
   * Instantiates a new state.
   *
   * @param name      the name
   * @param stateType the state type
   */
  public TerraformProvisionState(String name, String stateType) {
    super(name, stateType);
  }

  protected abstract TerraformCommandUnit commandUnit();

  protected abstract TerraformCommand command();

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    return executeInternal(context, activityId);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to do
  }

  static Map<String, Object> parseOutputs(String all) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isBlank(all)) {
      return outputs;
    }

    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(all), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", ExceptionMessageSanitizer.sanitizeException(exception));
    }

    return outputs;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    if (runPlanOnly) {
      return handleAsyncResponseInternalRunPlanOnly(context, response);
    } else {
      return handleAsyncResponseInternalRegular(context, response);
    }
  }

  private ExecutionResponse handleAsyncResponseInternalRunPlanOnly(
      ExecutionContext context, Map<String, ResponseData> response) {
    Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();
    TerraformExecutionData terraformExecutionData = (TerraformExecutionData) responseEntry.getValue();
    terraformExecutionData.setActivityId(activityId);
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    updateActivityStatus(activityId, context.getAppId(), terraformExecutionData.getExecutionStatus());
    boolean useOptimizedTfPlan = featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId());
    if (exportPlanToApplyStep || (runPlanOnly && TerraformCommand.DESTROY == command())) {
      String planName = getPlanName(context);
      terraformPlanHelper.saveEncryptedTfPlanToSweepingOutput(
          terraformExecutionData.getEncryptedTfPlan(), context, planName);
      fileService.updateParentEntityIdAndVersion(PhaseStep.class, terraformExecutionData.getEntityId(), null,
          terraformExecutionData.getStateFileId(), null, FileBucket.TERRAFORM_STATE);
    }
    saveTerraformPlanJson(terraformExecutionData, context, command());

    GitFileConfig remoteBackendConfig = null;
    S3FileConfig remoteBackendS3Config = null;
    String backendConfigStoreType = null;
    if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())
        && terraformExecutionData.getRemoteBackendConfig() != null) {
      backendConfigStoreType = LOCAL_STORE_TYPE;
      backendConfigs = terraformExecutionData.getBackendConfigs();
      if (REMOTE_STORE_TYPE.equals(terraformExecutionData.getBackendConfigStoreType())) {
        backendConfigStoreType = REMOTE_STORE_TYPE;
        remoteBackendConfig = terraformExecutionData.getRemoteBackendConfig().getGitFileConfig();
      }
    } else if (featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, context.getAccountId())
        && terraformExecutionData.getRemoteS3BackendConfig() != null) {
      backendConfigStoreType = S3_STORE_TYPE;
      remoteBackendS3Config = terraformExecutionData.getRemoteS3BackendConfig().getS3FileConfig();
    }

    TerraformProvisionInheritPlanElement inheritPlanElement =
        TerraformProvisionInheritPlanElement.builder()
            .entityId(terraformExecutionData.getEntityId())
            .provisionerId(provisionerId)
            .targets(terraformExecutionData.getTargets())
            .delegateTag(terraformExecutionData.getDelegateTag())
            .tfVarFiles(terraformExecutionData.getTfVarFiles())
            .tfVarSource(terraformExecutionData.getTfVarSource())
            .sourceRepoSettingId(terraformProvisioner.getSourceRepoSettingId())
            .sourceRepoReference(terraformExecutionData.getSourceRepoReference())
            .variables(terraformExecutionData.getVariables())
            .backendConfigs(terraformExecutionData.getBackendConfigs())
            .backendConfigStoreType(backendConfigStoreType)
            .remoteBackendConfig(remoteBackendConfig)
            .remoteBackendS3Config(remoteBackendS3Config)
            .environmentVariables(terraformExecutionData.getEnvironmentVariables())
            .workspace(terraformExecutionData.getWorkspace())
            // In case of OPTIMIZED_TF_PLAN FF enabled we don't want to store encryptedTfPlan record in context element
            // We're ending in storing 3 times, since the encryptedValue for KMS secret manager will be the value itself
            // it will reduce the max encrypted plan size to 5mb. Will use sweeping output for getting encrypted tf plan
            .encryptedTfPlan(useOptimizedTfPlan ? null : terraformExecutionData.getEncryptedTfPlan())
            .tfPlanJsonFileId(terraformExecutionData.getTfPlanJsonFiledId())
            .build();

    return ExecutionResponse.builder()
        .stateExecutionData(terraformExecutionData)
        .contextElement(inheritPlanElement)
        .notifyElement(inheritPlanElement)
        .executionStatus(terraformExecutionData.getExecutionStatus())
        .errorMessage(terraformExecutionData.getErrorMessage())
        .build();
  }

  private void saveTerraformPlanJson(
      TerraformExecutionData executionData, ExecutionContext context, TerraformCommand terraformCommand) {
    // We are checking for nulls in tfPlanJson field because it can be null even if feature flag is set to true.
    // Customer sometimes enables that flag because the customer is using multiple terraform versions at the same time,
    // some of which do not support exporting in json format
    TerraformPlanParamBuilder tfPlanParamBuilder = TerraformPlanParam.builder();
    boolean saveTfPlanSweepingOutput =
        executionData.getTfPlanJsonFiledId() != null || executionData.getTfPlanJson() != null;

    if (exportPlanToHumanReadableOutput) {
      // Backward compatible changes, incase delegate doesn't send this field back
      try {
        tfPlanParamBuilder.tfplanHumanReadable(executionData.getTfPlanHumanReadable());
        tfPlanParamBuilder.tfplanHumanReadableFileId(executionData.getTfPlanHumanReadableFileId());
      } catch (Exception e) {
        String message =
            "Terraform tfplanHumanReadable not found in Delegate sent Execution Data, Possible reason could be that delegate is on a older version not supporting human readable plan for Terraform";
        log.error(message, e);
      }
    }

    if (featureFlagService.isEnabled(FeatureName.EXPORT_TF_PLAN, context.getAccountId()) && saveTfPlanSweepingOutput) {
      String variableName = terraformCommand == TerraformCommand.APPLY ? TF_APPLY_VAR_NAME : TF_DESTROY_VAR_NAME;
      // if the plan variable exists overwrite it
      SweepingOutputInstance sweepingOutputInstance =
          sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(variableName).build());
      if (sweepingOutputInstance != null) {
        sweepingOutputService.deleteById(context.getAppId(), sweepingOutputInstance.getUuid());

        if (featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())) {
          TerraformPlanParam existingTerraformPlanJson = (TerraformPlanParam) sweepingOutputInstance.getValue();
          if (isNotEmpty(existingTerraformPlanJson.getTfPlanJsonFileId())) {
            fileService.deleteFile(existingTerraformPlanJson.getTfPlanJsonFileId(), FileBucket.TERRAFORM_PLAN_JSON);
          }
        }
      }
      if (featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())) {
        tfPlanParamBuilder.tfPlanJsonFileId(executionData.getTfPlanJsonFiledId());
      } else {
        tfPlanParamBuilder.tfplan(format("'%s'", JsonUtils.prettifyJsonString(executionData.getTfPlanJson())));
      }

      Scope scope =
          featureFlagService.isEnabled(SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW, context.getAccountId())
          ? Scope.WORKFLOW
          : Scope.PIPELINE;
      populateTerraformPlanParamWithSummaryInfo(tfPlanParamBuilder, executionData, context);
      sweepingOutputService.save(
          context.prepareSweepingOutputBuilder(scope).name(variableName).value(tfPlanParamBuilder.build()).build());
    } else {
      populateTerraformPlanParamWithSummaryInfo(tfPlanParamBuilder, executionData, context);
      saveTerraformPlanSummary(context, terraformCommand, tfPlanParamBuilder);
    }
  }

  private void populateTerraformPlanParamWithSummaryInfo(
      TerraformPlanParamBuilder tfPlanParamBuilder, TerraformExecutionData executionData, ExecutionContext context) {
    if (featureFlagService.isEnabled(ANALYSE_TF_PLAN_SUMMARY, context.getAccountId())) {
      if (executionData.getEnvironmentVariables() != null) {
        final Map<String, Object> tfPlanSummaryVars =
            executionData.getEnvironmentVariables()
                .stream()
                .filter(item -> item.getValue() != null)
                .filter(item -> "TEXT".equals(item.getValueType()))
                .collect(toMap(NameValuePair::getName, NameValuePair::getValue));

        if (tfPlanSummaryVars.containsKey(TF_PLAN_RESOURCES_ADD)
            && tfPlanSummaryVars.containsKey(TF_PLAN_RESOURCES_CHANGE)
            && tfPlanSummaryVars.containsKey(TF_PLAN_RESOURCES_DESTROY)) {
          tfPlanParamBuilder.add(Integer.parseInt(String.valueOf(tfPlanSummaryVars.get(TF_PLAN_RESOURCES_ADD))))
              .change(Integer.parseInt(String.valueOf(tfPlanSummaryVars.get(TF_PLAN_RESOURCES_CHANGE))))
              .destroy(Integer.parseInt(String.valueOf(tfPlanSummaryVars.get(TF_PLAN_RESOURCES_DESTROY))));
        }
      }
    }
  }

  private void saveTerraformPlanSummary(
      ExecutionContext context, TerraformCommand terraformCommand, TerraformPlanParamBuilder tfPlanParamBuilder) {
    if (featureFlagService.isEnabled(ANALYSE_TF_PLAN_SUMMARY, context.getAccountId())) {
      Scope scope =
          featureFlagService.isEnabled(SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW, context.getAccountId())
          ? Scope.WORKFLOW
          : Scope.PIPELINE;

      String variableName = terraformCommand == TerraformCommand.APPLY ? TF_APPLY_VAR_NAME : TF_DESTROY_VAR_NAME;

      SweepingOutputInstance sweepingOutputInstance =
          sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(variableName).build());
      if (sweepingOutputInstance != null) {
        sweepingOutputService.deleteById(context.getAppId(), sweepingOutputInstance.getUuid());
      }
      sweepingOutputService.save(
          context.prepareSweepingOutputBuilder(scope).name(variableName).value(tfPlanParamBuilder.build()).build());
    }
  }

  private String getPlanName(ExecutionContext context) {
    String planPrefix = TerraformCommand.DESTROY == command() ? TF_DESTROY_NAME_PREFIX : TF_NAME_PREFIX;
    return String.format(planPrefix, context.getWorkflowExecutionId());
  }

  private String getEncryptedPlanName(ExecutionContext context) {
    return getPlanName(context).replaceAll("_", "-");
  }

  protected String getMarkerName() {
    return format("tfApplyCompleted_%s", provisionerId).trim();
  }

  private void markApplyExecutionCompleted(ExecutionContext context) {
    String markerName = getMarkerName();
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder().name(markerName).build());
    if (sweepingOutputInstance != null) {
      return;
    }
    sweepingOutputInstance =
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name(markerName)
            .value(TerraformApplyMarkerParam.builder().applyCompleted(true).provisionerId(provisionerId).build())
            .build();
    sweepingOutputService.save(sweepingOutputInstance);
  }

  private ExecutionResponse handleAsyncResponseInternalRegular(
      ExecutionContext context, Map<String, ResponseData> response) {
    Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();
    TerraformExecutionData terraformExecutionData = (TerraformExecutionData) responseEntry.getValue();
    terraformExecutionData.setActivityId(activityId);

    // delete the plan if it exists
    String planName = getPlanName(context);
    terraformPlanHelper.deleteEncryptedTfPlanFromSweepingOutput(context, planName);
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    if (!(this instanceof DestroyTerraformProvisionState)) {
      markApplyExecutionCompleted(context);
    }

    if (featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())) {
      context.getContextElementList(TERRAFORM_INHERIT_PLAN)
          .stream()
          .map(TerraformProvisionInheritPlanElement.class ::cast)
          .filter(element -> element.getProvisionerId().equals(provisionerId))
          .filter(element -> isNotEmpty(element.getTfPlanJsonFileId()))
          .findFirst()
          .map(TerraformProvisionInheritPlanElement::getTfPlanJsonFileId)
          .ifPresent(tfPlanJsonFileId -> fileService.deleteFile(tfPlanJsonFileId, FileBucket.TERRAFORM_PLAN_JSON));

      // remove tfPlanJsonFileId from sweeping output as file is deleted
      String variableName = command() == TerraformCommand.APPLY ? TF_APPLY_VAR_NAME : TF_DESTROY_VAR_NAME;
      Scope scope =
          featureFlagService.isEnabled(SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW, context.getAccountId())
          ? Scope.WORKFLOW
          : Scope.PIPELINE;
      terraformPlanHelper.removeTfPlanJsonFileIdFromSweepingOutput(context, variableName, scope);
    }

    if (terraformExecutionData.getExecutionStatus() == FAILED) {
      return ExecutionResponse.builder()
          .stateExecutionData(terraformExecutionData)
          .executionStatus(terraformExecutionData.getExecutionStatus())
          .errorMessage(terraformExecutionData.getErrorMessage())
          .build();
    }

    TerraformPlanParamBuilder tfPlanParamBuilder = TerraformPlanParam.builder();
    populateTerraformPlanParamWithSummaryInfo(tfPlanParamBuilder, terraformExecutionData, context);
    saveTerraformPlanSummary(context, command(), tfPlanParamBuilder);

    saveUserInputs(context, terraformExecutionData, terraformProvisioner);
    TerraformOutputInfoElement outputInfoElement = context.getContextElement(ContextElementType.TERRAFORM_PROVISION);
    if (outputInfoElement == null) {
      outputInfoElement = TerraformOutputInfoElement.builder().build();
    }
    if (terraformExecutionData.getOutputs() != null) {
      Map<String, Object> outputs = parseOutputs(terraformExecutionData.getOutputs());
      ManagerExecutionLogCallback executionLogCallback = infrastructureProvisionerService.getManagerExecutionCallback(
          terraformProvisioner.getAppId(), terraformExecutionData.getActivityId(), commandUnit().name());
      if (featureFlagService.isEnabled(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT, context.getAccountId())) {
        saveOutputs(context, outputs, executionLogCallback);
      } else {
        outputInfoElement.addOutPuts(outputs);
      }
      infrastructureProvisionerService.regenerateInfrastructureMappings(
          provisionerId, context, outputs, Optional.of(executionLogCallback), Optional.empty());
    }

    updateActivityStatus(activityId, context.getAppId(), terraformExecutionData.getExecutionStatus());

    // subsequent execution
    return ExecutionResponse.builder()
        .stateExecutionData(terraformExecutionData)
        .contextElement(outputInfoElement)
        .notifyElement(outputInfoElement)
        .executionStatus(terraformExecutionData.getExecutionStatus())
        .errorMessage(terraformExecutionData.getErrorMessage())
        .build();
  }

  private void saveOutputs(
      ExecutionContext context, Map<String, Object> outputs, ManagerExecutionLogCallback executionLogCallback) {
    TerraformOutputInfoElement outputInfoElement = context.getContextElement(ContextElementType.TERRAFORM_PROVISION);
    SweepingOutputInstance instance = sweepingOutputService.find(
        context.prepareSweepingOutputInquiryBuilder().name(TerraformOutputVariables.SWEEPING_OUTPUT_NAME).build());
    TerraformOutputVariables terraformOutputVariables =
        instance != null ? (TerraformOutputVariables) instance.getValue() : new TerraformOutputVariables();

    if (null == terraformOutputVariables) {
      String message = format("%s variable is being used in some other step. The terraform output is not saved.",
          TerraformOutputVariables.SWEEPING_OUTPUT_NAME);
      log.warn(message);
      executionLogCallback.saveExecutionLog(color(message, Yellow, Bold), LogLevel.WARN);
      return;
    }

    terraformOutputVariables.putAll(outputs);
    if (outputInfoElement != null && outputInfoElement.getOutputVariables() != null) {
      // Ensure that we're not missing any variables during migration from context element to sweeping output
      // can be removed with the next releases
      terraformOutputVariables.putAll(outputInfoElement.getOutputVariables());
    }

    if (instance != null) {
      sweepingOutputService.deleteById(context.getAppId(), instance.getUuid());
    }

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(TerraformOutputVariables.SWEEPING_OUTPUT_NAME)
                                   .value(terraformOutputVariables)
                                   .build());

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);

    if (phaseElement != null && isNotEmpty(phaseElement.getInfraDefinitionId())
        && phaseElement.getServiceElement() != null && isNotEmpty(phaseElement.getServiceElement().getUuid())) {
      String name = format("%s_%s_%s", TerraformOutputVariables.SWEEPING_OUTPUT_NAME,
          phaseElement.getInfraDefinitionId(), phaseElement.getServiceElement().getUuid())
                        .replaceAll("-", "_");
      try {
        sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.PIPELINE)
                                       .name(name)
                                       .value(terraformOutputVariables)
                                       .build());
      } catch (Exception ex) {
        log.error("Failed to save sweeping output against name {}", name);
      }
    }
  }

  private void saveUserInputs(ExecutionContext context, TerraformExecutionData terraformExecutionData,
      TerraformInfrastructureProvisioner terraformProvisioner) {
    String workspace = terraformExecutionData.getWorkspace();
    Map<String, Object> others = new HashMap<>();
    if (!(this instanceof DestroyTerraformProvisionState)) {
      others.put("qualifier", QUALIFIER_APPLY);
      collectVariables(others, terraformExecutionData.getVariables(), VARIABLES_KEY, ENCRYPTED_VARIABLES_KEY, true);

      if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())
          && isNotEmpty(terraformExecutionData.getBackendConfigs())
          && terraformExecutionData.getBackendConfigStoreType() != null
          && !terraformExecutionData.getBackendConfigStoreType().equals(S3_STORE_TYPE)) {
        if (LOCAL_STORE_TYPE.equals(terraformExecutionData.getBackendConfigStoreType())) {
          collectVariables(others, terraformExecutionData.getBackendConfigs(), BACKEND_CONFIGS_KEY,
              ENCRYPTED_BACKEND_CONFIGS_KEY, false);
        } else {
          // remote backend config
          if (terraformExecutionData.getRemoteBackendConfig() != null) {
            GitFileConfig gitFileConfig = terraformExecutionData.getRemoteBackendConfig().getGitFileConfig();
            others.put(REMOTE_BE_CONFIG_GIT_BRANCH_KEY, gitFileConfig.getBranch());
            others.put(REMOTE_BE_CONFIG_GIT_CONNECTOR_ID_KEY, gitFileConfig.getConnectorId());
            others.put(REMOTE_BE_CONFIG_GIT_COMMIT_ID_KEY, gitFileConfig.getCommitId());
            others.put(REMOTE_BE_CONFIG_GIT_FILE_PATH_KEY, gitFileConfig.getFilePath());
            others.put(REMOTE_BE_CONFIG_GIT_REPO_NAME_KEY, gitFileConfig.getRepoName());
            others.put(REMOTE_BE_CONFIG_GIT_USE_BRANCH_KEY, gitFileConfig.isUseBranch());
          }
        }
      } else if (featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, context.getAccountId())
          && terraformExecutionData.getRemoteS3BackendConfig() != null
          && terraformExecutionData.getBackendConfigStoreType() != null
          && terraformExecutionData.getBackendConfigStoreType().equals(S3_STORE_TYPE)) {
        S3FileConfig s3FileConfig = terraformExecutionData.getRemoteS3BackendConfig().getS3FileConfig();
        others.put(REMOTE_BE_CONFIG_S3_CONFIG_ID_KEY, s3FileConfig.getAwsConfigId());
        others.put(REMOTE_BE_CONFIG_S3_URI_LIST_KEY, s3FileConfig.getS3URIList());
        others.put(REMOTE_BE_CONFIG_S3_URI_KEY, s3FileConfig.getS3URI());
      } else {
        collectVariables(others, terraformExecutionData.getBackendConfigs(), BACKEND_CONFIGS_KEY,
            ENCRYPTED_BACKEND_CONFIGS_KEY, false);
      }
      collectVariables(others, terraformExecutionData.getEnvironmentVariables(), ENVIRONMENT_VARS_KEY,
          ENCRYPTED_ENVIRONMENT_VARS_KEY, false);

      List<String> tfVarFiles = terraformExecutionData.getTfVarFiles();
      TfVarSource tfVarSource = terraformExecutionData.getTfVarSource();
      List<String> targets = terraformExecutionData.getTargets();

      if (isNotEmpty(targets)) {
        others.put(TARGETS_KEY, targets);
      }

      if (isNotEmpty(tfVarFiles)) {
        others.put(TF_VAR_FILES_KEY, tfVarFiles);
      }

      if (null != tfVarSource) {
        if (tfVarSource.getTfVarSourceType() == TfVarSourceType.GIT) {
          GitFileConfig gitFileConfig = ((TfVarGitSource) tfVarSource).getGitFileConfig();
          others.put(TF_VAR_FILES_GIT_BRANCH_KEY, gitFileConfig.getBranch());
          others.put(TF_VAR_FILES_GIT_CONNECTOR_ID_KEY, gitFileConfig.getConnectorId());
          others.put(TF_VAR_FILES_GIT_COMMIT_ID_KEY, gitFileConfig.getCommitId());
          others.put(TF_VAR_FILES_GIT_FILE_PATH_KEY, gitFileConfig.getFilePath());
          others.put(TF_VAR_FILES_GIT_REPO_NAME_KEY, gitFileConfig.getRepoName());
          others.put(TF_VAR_FILES_GIT_USE_BRANCH_KEY, gitFileConfig.isUseBranch());
        } else if (tfVarSource.getTfVarSourceType() == TfVarSourceType.S3) {
          S3FileConfig s3FileConfig = ((TfVarS3Source) tfVarSource).getS3FileConfig();
          others.put(TF_VAR_FILES_S3_CONFIG_ID_KEY, s3FileConfig.getAwsConfigId());
          others.put(TF_VAR_FILES_S3_URI_KEY, s3FileConfig.getS3URI());
          others.put(TF_VAR_FILES_S3_URI_LIST_KEY, s3FileConfig.getS3URIList());
        }
      }

      if (isNotEmpty(workspace)) {
        others.put(WORKSPACE_KEY, workspace);
      }

      if (terraformExecutionData.getExecutionStatus() == SUCCESS) {
        saveTerraformConfig(context, terraformProvisioner, terraformExecutionData);
      }

    } else {
      if (getStateType().equals(StateType.TERRAFORM_DESTROY.name())) {
        if (terraformExecutionData.getExecutionStatus() == SUCCESS) {
          if (isNotEmpty(getTargets())) {
            saveTerraformConfig(context, terraformProvisioner, terraformExecutionData);
          } else {
            deleteTerraformConfig(context, terraformExecutionData, terraformProvisioner);
          }
        }
      }
    }

    fileService.updateParentEntityIdAndVersion(PhaseStep.class, terraformExecutionData.getEntityId(), null,
        terraformExecutionData.getStateFileId(), others, FileBucket.TERRAFORM_STATE);
    if (isNotEmpty(workspace)) {
      updateProvisionerWorkspaces(terraformProvisioner, workspace);
    }
  }

  private void collectVariables(Map<String, Object> others, List<NameValuePair> nameValuePairList, String varsKey,
      String encyptedVarsKey, boolean valueTypeCanBeNull) {
    if (isNotEmpty(nameValuePairList)) {
      others.put(varsKey,
          nameValuePairList.stream()
              .filter(item -> item.getValue() != null)
              .filter(item -> (valueTypeCanBeNull && item.getValueType() == null) || "TEXT".equals(item.getValueType()))
              .collect(toMap(NameValuePair::getName, NameValuePair::getValue)));
      others.put(encyptedVarsKey,
          nameValuePairList.stream()
              .filter(item -> item.getValue() != null)
              .filter(item -> "ENCRYPTED_TEXT".equals(item.getValueType()))
              .collect(toMap(NameValuePair::getName, NameValuePair::getValue)));
    }
  }

  protected void updateProvisionerWorkspaces(
      TerraformInfrastructureProvisioner terraformProvisioner, String workspace) {
    if (isNotEmpty(terraformProvisioner.getWorkspaces()) && terraformProvisioner.getWorkspaces().contains(workspace)) {
      return;
    }
    List<String> workspaces =
        isNotEmpty(terraformProvisioner.getWorkspaces()) ? terraformProvisioner.getWorkspaces() : new ArrayList<>();
    workspaces.add(workspace);
    terraformProvisioner.setWorkspaces(workspaces);
    infrastructureProvisionerService.update(terraformProvisioner);
  }

  protected void updateActivityStatus(String activityId, String appId, ExecutionStatus status) {
    activityService.updateStatus(activityId, appId, status);
  }

  private void validateTerraformVariables() {
    ensureNoDuplicateVars(getBackendConfigs());
    ensureNoDuplicateVars(getEnvironmentVariables());
    ensureNoDuplicateVars(getVariables());
    boolean areVariablesValid = areKeysMongoCompliant(getVariables(), getBackendConfigs(), getEnvironmentVariables());

    if (getBackendConfig() != null && LOCAL_STORE_TYPE.equals(getBackendConfig().getStoreType())
        && !isEmpty(getBackendConfig().getInlineBackendConfig())) {
      List<NameValuePair> inlineBackendConfig = getBackendConfig().getInlineBackendConfig();
      ensureNoDuplicateVars(inlineBackendConfig);
      areVariablesValid = areVariablesValid && areKeysMongoCompliant(inlineBackendConfig);
    }
    if (!areVariablesValid) {
      throw new InvalidRequestException("The following characters are not allowed in terraform "
          + "variable names: . and $");
    }
  }

  private void ensureNoDuplicateVars(List<NameValuePair> variables) {
    if (isEmpty(variables)) {
      return;
    }

    HashSet<String> distinctVariableNames = new HashSet<>();
    Set<String> duplicateVariableNames = new HashSet<>();
    for (NameValuePair variable : variables) {
      if (!distinctVariableNames.contains(variable.getName().trim())) {
        distinctVariableNames.add(variable.getName());
      } else {
        duplicateVariableNames.add(variable.getName());
      }
    }

    if (!duplicateVariableNames.isEmpty()) {
      throw new InvalidRequestException(
          DUPLICATE_VAR_MSG_PREFIX + duplicateVariableNames.toString(), WingsException.USER);
    }
  }

  private boolean areKeysMongoCompliant(List<NameValuePair>... variables) {
    Predicate<String> terraformVariableNameCheckFail = value -> value.contains(".") || value.contains("$");
    return Stream.of(variables)
        .filter(EmptyPredicate::isNotEmpty)
        .flatMap(Collection::stream)
        .map(NameValuePair::getName)
        .noneMatch(terraformVariableNameCheckFail);
  }

  protected static List<NameValuePair> validateAndFilterVariables(
      List<NameValuePair> workflowVariables, List<NameValuePair> provisionerVariables) {
    Map<String, String> variableTypesMap = isNotEmpty(provisionerVariables)
        ? provisionerVariables.stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValueType))
        : new HashMap<>();
    List<NameValuePair> validVariables = new ArrayList<>();
    if (isNotEmpty(workflowVariables)) {
      workflowVariables.stream()
          .distinct()
          .filter(variable -> variableTypesMap.containsKey(variable.getName()))
          .forEach(validVariables::add);
    }

    return validVariables;
  }

  protected ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    if (inheritApprovedPlan) {
      return executeInternalInherited(context, activityId);
    } else {
      return executeInternalRegular(context, activityId);
    }
  }

  private ExecutionResponse executeInternalInherited(ExecutionContext context, String activityId) {
    List<TerraformProvisionInheritPlanElement> allPlanElements = context.getContextElementList(TERRAFORM_INHERIT_PLAN);
    if (isEmpty(allPlanElements)) {
      throw new InvalidRequestException(
          "No previous Terraform plan execution found. Unable to inherit configuration from Terraform Plan");
    }
    Optional<TerraformProvisionInheritPlanElement> elementOptional =
        allPlanElements.stream().filter(element -> element.getProvisionerId().equals(provisionerId)).findFirst();
    if (!elementOptional.isPresent()) {
      throw new InvalidRequestException("No Terraform provision command found with current provisioner");
    }
    TerraformProvisionInheritPlanElement element = elementOptional.get();

    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    GitConfig gitConfig = null;
    String path = null;

    if (terraformProvisioner.getSourceType() != null
        && terraformProvisioner.getSourceType().equals(TerraformSourceType.GIT)) {
      path = context.renderExpression(terraformProvisioner.getNormalizedPath());
      if (path == null) {
        path = context.renderExpression(FilenameUtils.normalize(terraformProvisioner.getPath()));
        if (path == null) {
          throw new InvalidRequestException("Invalid Terraform script path", USER);
        }
      }
      gitConfig = gitUtilsManager.getGitConfig(element.getSourceRepoSettingId());
      if (isNotEmpty(element.getSourceRepoReference())) {
        gitConfig.setReference(element.getSourceRepoReference());
        String branch = context.renderExpression(terraformProvisioner.getSourceRepoBranch());
        if (isNotEmpty(branch)) {
          gitConfig.setBranch(branch);
        }

      } else {
        throw new InvalidRequestException("No commit id found in context inherit tf plan element.");
      }
      gitConfigHelperService.convertToRepoGitConfig(
          gitConfig, context.renderExpression(terraformProvisioner.getRepoName()));
    }

    String workspace = context.renderExpression(element.getWorkspace());
    String entityId = generateEntityId(context, workspace, terraformProvisioner, true);
    String fileId = fileService.getLatestFileId(entityId, TERRAFORM_STATE);
    if (fileId == null) {
      log.info("Retrieving fileId with old entityId");
      fileId = fileService.getLatestFileId(
          generateEntityId(context, workspace, terraformProvisioner, false), TERRAFORM_STATE);
      log.info("{} fileId with old entityId", fileId == null ? "Didn't retrieve" : "Retrieved");
    }

    List<NameValuePair> allBackendConfigs = element.getBackendConfigs();
    Map<String, String> backendConfigs = null;
    Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
    String storeType = null;
    TfVarGitSource remoteBackendConfig = null;
    TfVarS3Source remoteBackendS3Config = null;
    if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())) {
      storeType = element.getBackendConfigStoreType();
      TerraformBackendConfig inhertiedConfig = new TerraformBackendConfig();
      inhertiedConfig.setStoreType(storeType != null ? storeType : LOCAL_STORE_TYPE);
      inhertiedConfig.setRemoteBackendConfig(element.getRemoteBackendConfig());
      inhertiedConfig.setS3BackendConfig(element.getRemoteBackendS3Config());
      if (REMOTE_STORE_TYPE.equals(storeType)) {
        setBackendConfig(inhertiedConfig);
        remoteBackendConfig = fetchRemoteConfigGitSource(context);
      } else if (S3_STORE_TYPE.equals(storeType)) {
        remoteBackendS3Config = fetchRemoteConfigS3Source(context, element.getRemoteBackendS3Config());
      } else {
        backendConfigs = infrastructureProvisionerService.extractTextVariables(element.getBackendConfigs(), context);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            element.getBackendConfigs(), context.getAppId(), context.getWorkflowExecutionId());
      }
    } else {
      if (isNotEmpty(allBackendConfigs)) {
        backendConfigs = infrastructureProvisionerService.extractTextVariables(allBackendConfigs, context);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            allBackendConfigs, context.getAppId(), context.getWorkflowExecutionId());
      }
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

    SecretManagerConfig secretManagerConfig = isSecretManagerRequired()
        ? getSecretManagerContainingTfPlan(terraformProvisioner.getKmsId(), context.getAccountId())
        : null;

    TfVarSource tfVarSource = element.getTfVarSource();
    if (tfVarSource != null && TfVarSourceType.GIT.equals(tfVarSource.getTfVarSourceType())) {
      setTfVarGitFileConfig(((TfVarGitSource) element.getTfVarSource()).getGitFileConfig());
      if (getTfVarGitFileConfig() != null) {
        tfVarSource = fetchTfVarGitSource(context);
      }
    } else if (tfVarSource != null && TfVarSourceType.S3.equals(tfVarSource.getTfVarSourceType())) {
      setTfVarS3FileConfig(((TfVarS3Source) element.getTfVarSource()).getS3FileConfig());
      tfVarSource = fetchTfVarS3Source(context);
    }

    EncryptedRecordData encryptedTfPlan =
        featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())
        ? terraformPlanHelper.getEncryptedTfPlanFromSweepingOutput(context, getPlanName(context))
        : element.getEncryptedTfPlan();

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        TerraformProvisionParameters.builder()
            .sourceType(terraformProvisioner.getSourceType())
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
            .sourceRepoEncryptionDetails(terraformProvisioner.getSourceType().equals(TerraformSourceType.GIT)
                    ? secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId())
                    : null)
            .scriptPath(path)
            .variables(textVariables)
            .encryptedVariables(encryptedTextVariables)
            .backendConfigs(backendConfigs)
            .encryptedBackendConfigs(encryptedBackendConfigs)
            .remoteBackendConfig(remoteBackendConfig)
            .remoteS3BackendConfig(remoteBackendS3Config)
            .backendConfigStoreType(storeType)
            .environmentVariables(envVars)
            .encryptedEnvironmentVariables(encryptedEnvVars)
            .targets(targets)
            .tfVarFiles(element.getTfVarFiles())
            .tfVarSource(tfVarSource)
            .runPlanOnly(false)
            .exportPlanToApplyStep(false)
            .workspace(workspace)
            .delegateTag(element.getDelegateTag())
            .skipRefreshBeforeApplyingPlan(terraformProvisioner.isSkipRefreshBeforeApplyingPlan())
            .encryptedTfPlan(encryptedTfPlan)
            .secretManagerConfig(secretManagerConfig)
            .planName(getEncryptedPlanName(context))
            .encryptDecryptPlanForHarnessSMOnManager(
                featureFlagService.isEnabled(
                    CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, executionContext.getApp().getAccountId())
                && isHarnessSecretManager(secretManagerConfig))
            .useTfClient(
                featureFlagService.isEnabled(FeatureName.USE_TF_CLIENT, executionContext.getApp().getAccountId()))
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, executionContext.getApp().getAccountId()))
            .useActivityIdBasedTfBaseDir(
                featureFlagService.isEnabled(ACTIVITY_ID_BASED_TF_BASE_DIR, context.getAccountId()))
            .syncGitCloneAndCopyToDestDir(
                featureFlagService.isEnabled(SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR, context.getAccountId()))
            .analyseTfPlanSummary(
                featureFlagService.isEnabled(FeatureName.ANALYSE_TF_PLAN_SUMMARY, context.getAccountId()));

    if (featureFlagService.isEnabled(TERRAFORM_AWS_CP_AUTHENTICATION, context.getAccountId())) {
      setAWSAuthParamsIfPresent(context, terraformProvisionParametersBuilder);
    }
    if (terraformProvisioner.getSourceType() != null
            && terraformProvisioner.getSourceType().equals(TerraformSourceType.S3)
        || remoteBackendS3Config != null
        || (tfVarSource != null && tfVarSource.getTfVarSourceType().equals(TfVarSourceType.S3))) {
      if (!featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, context.getAccountId())) {
        throw new InvalidRequestException(
            "Unable to execute this workflow because either the input Terraform config files or tfvars files or backend config are stored in S3, and are unreachable due to a disabled feature flag CDS_TERRAFORM_S3_SUPPORT");
      }
    }

    setAWSS3SourceConfigSourceIfPresent(terraformProvisioner, context, terraformProvisionParametersBuilder);

    return createAndRunTask(
        activityId, executionContext, terraformProvisionParametersBuilder.build(), element.getDelegateTag());
  }

  private List<String> getRenderedTaskTags(String rawTag, ExecutionContextImpl executionContext) {
    if (isEmpty(rawTag)) {
      return null;
    }
    return singletonList(executionContext.renderExpression(rawTag));
  }

  protected ExecutionResponse createAndRunTask(String activityId, ExecutionContextImpl executionContext,
      TerraformProvisionParameters parameters, String delegateTag) {
    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    String accountId = requireNonNull(executionContext.getApp()).getAccountId();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(activityId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, requireNonNull(executionContext.getApp()).getAppId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : null)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
                executionContext.getEnv() != null ? executionContext.getEnv().getEnvironmentType().name() : null)
            .tags(getRenderedTaskTags(delegateTag, executionContext))
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Terraform provision task execution")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(getTaskType(parameters))
                      .parameters(new Object[] {parameters})
                      .timeout(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .build();

    ScriptStateExecutionData stateExecutionData = ScriptStateExecutionData.builder().activityId(activityId).build();
    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(stateExecutionData)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    renderDelegateTask(executionContext, delegateTask, stateExecutionContext);

    String delegateTaskId = delegateService.queueTaskV2(delegateTask);
    appendDelegateTaskDetails(executionContext, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  protected String getTaskType(TerraformProvisionParameters parameters) {
    if (parameters.getSourceType().equals(TerraformSourceType.S3) || parameters.getRemoteS3BackendConfig() != null
        || (parameters.getTfVarSource() != null
            && parameters.getTfVarSource().getTfVarSourceType().equals(TfVarSourceType.S3))) {
      return TERRAFORM_PROVISION_TASK_V2.name();
    }
    return TERRAFORM_PROVISION_TASK.name();
  }

  private ExecutionResponse executeInternalRegular(ExecutionContext context, String activityId) {
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    GitConfig gitConfig = null;
    String path = null;

    if (terraformProvisioner.getSourceType() == null
        || (terraformProvisioner.getSourceType() != null
            && terraformProvisioner.getSourceType().equals(TerraformSourceType.GIT))) {
      gitConfig = gitUtilsManager.getGitConfig(terraformProvisioner.getSourceRepoSettingId());

      String branch = context.renderExpression(terraformProvisioner.getSourceRepoBranch());
      if (isNotEmpty(branch)) {
        gitConfig.setBranch(branch);
      }
      if (isNotEmpty(terraformProvisioner.getCommitId())) {
        String commitId = context.renderExpression(terraformProvisioner.getCommitId());
        if (isNotEmpty(commitId)) {
          gitConfig.setReference(commitId);
        }
      }
      path = context.renderExpression(terraformProvisioner.getNormalizedPath());
      if (path == null) {
        path = context.renderExpression(FilenameUtils.normalize(terraformProvisioner.getPath()));
        if (path == null) {
          throw new InvalidRequestException("Invalid Terraform script path", USER);
        }
      }
      gitConfigHelperService.convertToRepoGitConfig(
          gitConfig, context.renderExpression(terraformProvisioner.getRepoName()));
    }

    SecretManagerConfig secretManagerConfig = isSecretManagerRequired()
        ? getSecretManagerContainingTfPlan(terraformProvisioner.getKmsId(), context.getAccountId())
        : null;

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String workspace = context.renderExpression(this.workspace);
    workspace = handleDefaultWorkspace(workspace);
    String entityId = generateEntityId(context, workspace, terraformProvisioner, true);
    String fileId = fileService.getLatestFileId(entityId, TERRAFORM_STATE);
    if (fileId == null) {
      log.info("Retrieving fileId with old entityId");
      fileId = fileService.getLatestFileId(
          generateEntityId(context, workspace, terraformProvisioner, false), TERRAFORM_STATE);
      log.info("{} fileId with old entityId", fileId == null ? "Didn't retrieve" : "Retrieved");
    }

    Map<String, String> variables = null;
    Map<String, EncryptedDataDetail> encryptedVariables = null;
    Map<String, String> backendConfigs = null;
    Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
    Map<String, String> environmentVars = null;
    Map<String, EncryptedDataDetail> encryptedEnvironmentVars = null;
    List<NameValuePair> rawVariablesList = new ArrayList<>();
    String backendConfigStoreType = null;
    TfVarGitSource remoteBackendGitFileConfig = null;
    TfVarS3Source remoteS3BackendConfig = null;

    validateTerraformVariables();

    List<NameValuePair> inlineBackendConfig = this.backendConfigs;
    if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())
        && this.backendConfig != null) {
      backendConfigStoreType = getBackendConfig().getStoreType();
      if (LOCAL_STORE_TYPE.equals(backendConfigStoreType)) {
        inlineBackendConfig = this.backendConfig.getInlineBackendConfig();
      } else if (REMOTE_STORE_TYPE.equals(backendConfigStoreType)) {
        remoteBackendGitFileConfig = fetchRemoteConfigGitSource(context, this.backendConfig.getRemoteBackendConfig());
      } else if (S3_STORE_TYPE.equals(backendConfigStoreType)) {
        remoteS3BackendConfig = fetchRemoteConfigS3Source(context, this.backendConfig.getS3BackendConfig());
      }
    }

    if (isNotEmpty(this.variables) || isNotEmpty(inlineBackendConfig) || isNotEmpty(this.environmentVariables)) {
      List<NameValuePair> validVariables = isNotEmpty(getVariables()) ? getVariables() : new ArrayList<>();
      rawVariablesList.addAll(validVariables);

      variables = infrastructureProvisionerService.extractUnresolvedTextVariables(validVariables);
      encryptedVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
          validVariables, context.getAppId(), context.getWorkflowExecutionId());

      if (inlineBackendConfig != null) {
        backendConfigs = infrastructureProvisionerService.extractTextVariables(inlineBackendConfig, context);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            inlineBackendConfig, context.getAppId(), context.getWorkflowExecutionId());
      }

      if (this.environmentVariables != null) {
        List<NameValuePair> validEnvironmentVariables =
            isNotEmpty(getEnvironmentVariables()) ? getEnvironmentVariables() : new ArrayList<>();
        environmentVars = infrastructureProvisionerService.extractUnresolvedTextVariables(validEnvironmentVariables);
        encryptedEnvironmentVars = infrastructureProvisionerService.extractEncryptedTextVariables(
            validEnvironmentVariables, context.getAppId(), context.getWorkflowExecutionId());
      }

    } else if (this instanceof DestroyTerraformProvisionState) {
      fileId = fileService.getLatestFileIdByQualifier(entityId, TERRAFORM_STATE, QUALIFIER_APPLY);
      if (fileId == null) {
        log.info("Retrieving fileId with old entityId");
        fileId = fileService.getLatestFileIdByQualifier(
            generateEntityId(context, workspace, terraformProvisioner, false), TERRAFORM_STATE, QUALIFIER_APPLY);
        log.info("{} fileId with old entityId", fileId == null ? "Didn't retrieve" : "Retrieved");
      }

      if (fileId != null) {
        FileMetadata fileMetadata = fileService.getFileMetadata(fileId, TERRAFORM_STATE);

        if (fileMetadata != null && fileMetadata.getMetadata() != null) {
          variables = extractData(fileMetadata, VARIABLES_KEY);
          Map<String, Object> rawVariables = (Map<String, Object>) fileMetadata.getMetadata().get(VARIABLES_KEY);
          if (isNotEmpty(rawVariables)) {
            rawVariablesList.addAll(extractVariables(rawVariables, "TEXT"));
          }

          backendConfigs = extractBackendConfigs(context, fileMetadata);

          encryptedVariables = extractEncryptedData(context, fileMetadata, ENCRYPTED_VARIABLES_KEY);
          Map<String, Object> rawEncryptedVariables =
              (Map<String, Object>) fileMetadata.getMetadata().get(ENCRYPTED_VARIABLES_KEY);
          if (isNotEmpty(rawEncryptedVariables)) {
            rawVariablesList.addAll(extractVariables(rawEncryptedVariables, "ENCRYPTED_TEXT"));
          }

          encryptedBackendConfigs = extractEncryptedData(context, fileMetadata, ENCRYPTED_BACKEND_CONFIGS_KEY);

          environmentVars = extractData(fileMetadata, ENVIRONMENT_VARS_KEY);
          if (environmentVars != null
              && featureFlagService.isNotEnabled(ANALYSE_TF_PLAN_SUMMARY, context.getAccountId())) {
            if (environmentVars.containsKey(TF_PLAN_RESOURCES_ADD)) {
              environmentVars.remove(TF_PLAN_RESOURCES_ADD);
            }
            if (environmentVars.containsKey(TF_PLAN_RESOURCES_CHANGE)) {
              environmentVars.remove(TF_PLAN_RESOURCES_CHANGE);
            }
            if (environmentVars.containsKey(TF_PLAN_RESOURCES_DESTROY)) {
              environmentVars.remove(TF_PLAN_RESOURCES_DESTROY);
            }
          }
          encryptedEnvironmentVars = extractEncryptedData(context, fileMetadata, ENCRYPTED_ENVIRONMENT_VARS_KEY);

          List<String> targets = (List<String>) fileMetadata.getMetadata().get(TARGETS_KEY);
          if (isNotEmpty(targets)) {
            setTargets(targets);
          }

          List<String> tfVarFiles = (List<String>) fileMetadata.getMetadata().get(TF_VAR_FILES_KEY);
          if (isNotEmpty(tfVarFiles)) {
            setTfVarFiles(tfVarFiles);
          }

          if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())) {
            String gitFileConnectorId = (String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_CONNECTOR_ID_KEY);
            if (isNotEmpty(gitFileConnectorId)) {
              backendConfigStoreType = REMOTE_STORE_TYPE;
              GitFileConfig gitFileConfig =
                  GitFileConfig.builder()
                      .connectorId(gitFileConnectorId)
                      .filePath((String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_FILE_PATH_KEY))
                      .branch((String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_BRANCH_KEY))
                      .commitId((String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_COMMIT_ID_KEY))
                      .useBranch((boolean) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_USE_BRANCH_KEY))
                      .repoName((String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_GIT_REPO_NAME_KEY))
                      .build();
              TerraformBackendConfig remoteMetadataBackendConfig = new TerraformBackendConfig();
              remoteMetadataBackendConfig.setRemoteBackendConfig(gitFileConfig);
              setBackendConfig(remoteMetadataBackendConfig);
              remoteBackendGitFileConfig = fetchRemoteConfigGitSource(context);
            }
          }

          if (featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, context.getAccountId())) {
            String backendAwsConfigId = (String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_S3_CONFIG_ID_KEY);
            if (backendAwsConfigId != null) {
              S3FileConfig s3FileConfig =
                  S3FileConfig.builder()
                      .awsConfigId(backendAwsConfigId)
                      .s3URI((String) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_S3_URI_KEY))
                      .s3URIList(getS3URIList(fileMetadata, REMOTE_BE_CONFIG_S3_URI_LIST_KEY))
                      .build();

              AwsConfig awsConfig = (AwsConfig) getAwsConfigSettingAttribute(backendAwsConfigId).getValue();
              List<EncryptedDataDetail> encryptionDetails =
                  secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

              remoteS3BackendConfig = TfVarS3Source.builder()
                                          .awsConfig(awsConfig)
                                          .encryptedDataDetails(encryptionDetails)
                                          .s3FileConfig(s3FileConfig)
                                          .build();
              backendConfigStoreType = S3_STORE_TYPE;
            }
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

          String tfVarS3FileConfigId = (String) fileMetadata.getMetadata().get(TF_VAR_FILES_S3_CONFIG_ID_KEY);
          if (isNotEmpty(tfVarS3FileConfigId)) {
            S3FileConfig s3FileConfig = S3FileConfig.builder()
                                            .awsConfigId(tfVarS3FileConfigId)
                                            .s3URI((String) fileMetadata.getMetadata().get(TF_VAR_FILES_S3_URI_KEY))
                                            .s3URIList(getS3URIList(fileMetadata, TF_VAR_FILES_S3_URI_LIST_KEY))
                                            .build();

            setTfVarS3FileConfig(s3FileConfig);
          }
        }
      }
    }

    TfVarSource tfVarSource = null;

    // Currently we allow only one tfVar source
    if (isNotEmpty(tfVarFiles)) {
      tfVarSource = fetchTfVarScriptRepositorySource(context);
    } else if (null != tfVarGitFileConfig) {
      tfVarSource = fetchTfVarGitSource(context);
    } else if (null != tfVarS3FileConfig) {
      tfVarSource = fetchTfVarS3Source(context);
    }

    targets = resolveTargets(targets, context);

    if (runPlanOnly && this instanceof DestroyTerraformProvisionState) {
      exportPlanToApplyStep = true;
    }

    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        TerraformProvisionParameters.builder()
            .sourceType(terraformProvisioner.getSourceType())
            .accountId(executionContext.getApp().getAccountId())
            .activityId(activityId)
            .timeoutInMillis(defaultIfNullTimeout(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES)))
            .appId(executionContext.getAppId())
            .currentStateFileId(fileId)
            .entityId(entityId)
            .command(command())
            .commandUnit(commandUnit())
            .sourceRepoSettingId(terraformProvisioner.getSourceRepoSettingId())
            .sourceRepo(gitConfig)
            .sourceRepoEncryptionDetails(gitConfig != null
                    ? secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId())
                    : null)
            .scriptPath(path)
            .variables(variables)
            .rawVariables(rawVariablesList)
            .encryptedVariables(encryptedVariables)
            .backendConfigs(backendConfigs)
            .remoteBackendConfig(remoteBackendGitFileConfig)
            .remoteS3BackendConfig(remoteS3BackendConfig)
            .backendConfigStoreType(backendConfigStoreType)
            .encryptedBackendConfigs(encryptedBackendConfigs)
            .environmentVariables(environmentVars)
            .encryptedEnvironmentVariables(encryptedEnvironmentVars)
            .targets(targets)
            .runPlanOnly(runPlanOnly)
            .exportPlanToApplyStep(exportPlanToApplyStep)
            .exportPlanToHumanReadableOutput(exportPlanToHumanReadableOutput)
            .saveTerraformJson(featureFlagService.isEnabled(FeatureName.EXPORT_TF_PLAN, context.getAccountId()))
            .useOptimizedTfPlanJson(featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId()))
            .tfVarFiles(getRenderedTfVarFiles(tfVarFiles, context))
            .workspace(workspace)
            .delegateTag(delegateTag)
            .tfVarSource(tfVarSource)
            .skipRefreshBeforeApplyingPlan(terraformProvisioner.isSkipRefreshBeforeApplyingPlan())
            .secretManagerConfig(secretManagerConfig)
            .encryptedTfPlan(null)
            .planName(getEncryptedPlanName(context))
            .encryptDecryptPlanForHarnessSMOnManager(
                featureFlagService.isEnabled(
                    CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, executionContext.getApp().getAccountId())
                && isHarnessSecretManager(secretManagerConfig))
            .useTfClient(
                featureFlagService.isEnabled(FeatureName.USE_TF_CLIENT, executionContext.getApp().getAccountId()))
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, executionContext.getApp().getAccountId()))
            .useActivityIdBasedTfBaseDir(
                featureFlagService.isEnabled(ACTIVITY_ID_BASED_TF_BASE_DIR, context.getAccountId()))
            .syncGitCloneAndCopyToDestDir(
                featureFlagService.isEnabled(SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR, context.getAccountId()))
            .analyseTfPlanSummary(featureFlagService.isEnabled(ANALYSE_TF_PLAN_SUMMARY, context.getAccountId()));

    if (featureFlagService.isEnabled(TERRAFORM_AWS_CP_AUTHENTICATION, context.getAccountId())) {
      setAWSAuthParamsIfPresent(context, terraformProvisionParametersBuilder);
    }

    if (terraformProvisioner.getSourceType() != null
            && terraformProvisioner.getSourceType().equals(TerraformSourceType.S3)
        || remoteS3BackendConfig != null
        || (tfVarSource != null && tfVarSource.getTfVarSourceType().equals(TfVarSourceType.S3))) {
      if (!featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, context.getAccountId())) {
        throw new InvalidRequestException(
            "Unable to execute this workflow because either the input Terraform config files or tfvars files or backend config are stored in S3, and are unreachable due to a disabled feature flag CDS_TERRAFORM_S3_SUPPORT");
      }
    }
    setAWSS3SourceConfigSourceIfPresent(terraformProvisioner, context, terraformProvisionParametersBuilder);

    return createAndRunTask(activityId, executionContext, terraformProvisionParametersBuilder.build(), delegateTag);
  }

  protected void setAWSS3SourceConfigSourceIfPresent(
      TerraformInfrastructureProvisioner terraformInfrastructureProvisioner, ExecutionContext context,
      TerraformProvisionParametersBuilder terraformProvisionParametersBuilder) {
    if (terraformInfrastructureProvisioner.getAwsConfigId() == null
        || !terraformInfrastructureProvisioner.getSourceType().equals(TerraformSourceType.S3)) {
      return;
    }
    AwsConfig awsS3SourceBucketConfig =
        (AwsConfig) getAwsConfigSettingAttribute(terraformInfrastructureProvisioner.getAwsConfigId()).getValue();
    if (awsS3SourceBucketConfig != null) {
      terraformProvisionParametersBuilder.configFilesAwsSourceConfig(awsS3SourceBucketConfig)
          .configFileAWSEncryptionDetails(secretManager.getEncryptionDetails(
              awsS3SourceBucketConfig, context.getAppId(), context.getWorkflowExecutionId()))
          .configFilesS3URI(terraformInfrastructureProvisioner.getS3URI());
    }
  }
  protected void setAWSAuthParamsIfPresent(
      ExecutionContext context, TerraformProvisionParametersBuilder terraformProvisionParametersBuilder) {
    SettingAttribute settingAttribute = resolveAwsConfig(context);
    if (settingAttribute != null) {
      AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
      terraformProvisionParametersBuilder.awsConfig(awsConfig)
          .awsConfigId(settingAttribute.getUuid())
          .awsConfigEncryptionDetails(
              secretManager.getEncryptionDetails(awsConfig, context.getAppId(), context.getWorkflowExecutionId()))
          .awsRoleArn(context.renderExpression(getAwsRoleArn()))
          .awsRegion(getAwsRegion());
    }
  }

  protected TfVarSource getTfVarSource(ExecutionContext context) {
    TfVarSource tfVarSource = null;
    if (isNotEmpty(tfVarFiles)) {
      tfVarSource = fetchTfVarScriptRepositorySource(context);
    } else if (null != tfVarGitFileConfig) {
      tfVarSource = fetchTfVarGitSource(context);
    } else if (null != tfVarS3FileConfig) {
      tfVarSource = fetchTfVarS3Source(context);
    }
    return tfVarSource;
  }

  @VisibleForTesting
  TfVarScriptRepositorySource fetchTfVarScriptRepositorySource(ExecutionContext context) {
    return TfVarScriptRepositorySource.builder().tfVarFilePaths(getRenderedTfVarFiles(tfVarFiles, context)).build();
  }

  @VisibleForTesting
  TfVarGitSource fetchRemoteConfigGitSource(ExecutionContext context, GitFileConfig config) {
    GitConfig remoteBackendGitConfig = gitUtilsManager.getGitConfig(config.getConnectorId());
    gitConfigHelperService.renderGitConfig(context, remoteBackendGitConfig);
    gitFileConfigHelperService.renderGitFileConfig(context, config);

    gitConfigHelperService.convertToRepoGitConfig(
        remoteBackendGitConfig, context.renderExpression(config.getRepoName()));
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(remoteBackendGitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

    String filePath = config.getFilePath();

    if (isNotEmpty(filePath)) {
      List<String> multipleFiles = splitCommaSeparatedFilePath(filePath);
      config.setFilePathList(multipleFiles);
    }

    return TfVarGitSource.builder()
        .gitConfig(remoteBackendGitConfig)
        .encryptedDataDetails(encryptionDetails)
        .gitFileConfig(config)
        .build();
  }

  @VisibleForTesting
  TfVarGitSource fetchRemoteConfigGitSource(ExecutionContext context) {
    GitFileConfig config = backendConfig.getRemoteBackendConfig();
    return fetchRemoteConfigGitSource(context, config);
  }

  @VisibleForTesting
  TfVarS3Source fetchRemoteConfigS3Source(ExecutionContext context, S3FileConfig config) {
    AwsConfig awsConfig = (AwsConfig) getAwsConfigSettingAttribute(config.getAwsConfigId()).getValue();
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

    String s3URI = config.getS3URI();

    if (isNotEmpty(s3URI)) {
      config.setS3URIList(splitCommaSeparatedFilePath(s3URI));
    }

    return TfVarS3Source.builder()
        .awsConfig(awsConfig)
        .encryptedDataDetails(encryptionDetails)
        .s3FileConfig(config)
        .build();
  }

  @VisibleForTesting
  TfVarS3Source fetchTfVarS3Source(ExecutionContext context) {
    AwsConfig awsConfig = (AwsConfig) getAwsConfigSettingAttribute(tfVarS3FileConfig.getAwsConfigId()).getValue();
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(awsConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

    String s3URI = tfVarS3FileConfig.getS3URI();

    if (isNotEmpty(s3URI)) {
      List<String> multipleFiles = splitCommaSeparatedFilePath(s3URI);
      tfVarS3FileConfig.setS3URIList(multipleFiles);
    }

    return TfVarS3Source.builder()
        .awsConfig(awsConfig)
        .encryptedDataDetails(encryptionDetails)
        .s3FileConfig(tfVarS3FileConfig)
        .build();
  }

  @VisibleForTesting
  TfVarGitSource fetchTfVarGitSource(ExecutionContext context) {
    GitConfig tfVarGitConfig = gitUtilsManager.getGitConfig(tfVarGitFileConfig.getConnectorId());
    gitConfigHelperService.renderGitConfig(context, tfVarGitConfig);
    gitFileConfigHelperService.renderGitFileConfig(context, tfVarGitFileConfig);

    gitConfigHelperService.convertToRepoGitConfig(
        tfVarGitConfig, context.renderExpression(tfVarGitFileConfig.getRepoName()));
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails(tfVarGitConfig, GLOBAL_APP_ID, context.getWorkflowExecutionId());

    String filePath = tfVarGitFileConfig.getFilePath();

    if (isNotEmpty(filePath)) {
      List<String> multipleFiles = splitCommaSeparatedFilePath(filePath);
      tfVarGitFileConfig.setFilePathList(multipleFiles);
    }

    return TfVarGitSource.builder()
        .gitConfig(tfVarGitConfig)
        .encryptedDataDetails(encryptionDetails)
        .gitFileConfig(tfVarGitFileConfig)
        .build();
  }

  private Map<String, String> extractData(FileMetadata fileMetadata, String dataKey) {
    Map<String, Object> rawData = (Map<String, Object>) fileMetadata.getMetadata().get(dataKey);
    if (isNotEmpty(rawData)) {
      return infrastructureProvisionerService.extractUnresolvedTextVariables(extractVariables(rawData, "TEXT"));
    }
    return null;
  }

  private Map<String, EncryptedDataDetail> extractEncryptedData(
      ExecutionContext context, FileMetadata fileMetadata, String encryptedDataKey) {
    Map<String, Object> rawData = (Map<String, Object>) fileMetadata.getMetadata().get(encryptedDataKey);
    Map<String, EncryptedDataDetail> encryptedData = null;
    if (isNotEmpty(rawData)) {
      encryptedData = infrastructureProvisionerService.extractEncryptedTextVariables(
          extractVariables(rawData, "ENCRYPTED_TEXT"), context.getAppId(), context.getWorkflowExecutionId());
    }
    return encryptedData;
  }

  private Map<String, String> extractBackendConfigs(ExecutionContext context, FileMetadata fileMetadata) {
    Map<String, Object> rawBackendConfigs = null;
    if (featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, context.getAccountId())
        && getBackendConfig() != null && !LOCAL_STORE_TYPE.equals(getBackendConfig().getStoreType())) {
      rawBackendConfigs = (Map<String, Object>) fileMetadata.getMetadata().get(BACKEND_CONFIG_KEY);
    } else {
      rawBackendConfigs = (Map<String, Object>) fileMetadata.getMetadata().get(BACKEND_CONFIGS_KEY);
    }
    if (isNotEmpty(rawBackendConfigs)) {
      return infrastructureProvisionerService.extractTextVariables(
          extractVariables(rawBackendConfigs, "TEXT"), context);
    }
    return null;
  }

  private List<NameValuePair> extractVariables(Map<String, Object> variables, String valueType) {
    return variables.entrySet()
        .stream()
        .map(entry
            -> NameValuePair.builder()
                   .valueType(valueType)
                   .name(entry.getKey())
                   .value((String) entry.getValue())
                   .build())
        .collect(toList());
  }

  protected String handleDefaultWorkspace(String workspace) {
    // Default is as good as no workspace
    return isNotEmpty(workspace) && workspace.equals("default") ? null : workspace;
  }

  private List<String> getRenderedTfVarFiles(List<String> tfVarFiles, ExecutionContext context) {
    if (isEmpty(tfVarFiles)) {
      return tfVarFiles;
    }
    return tfVarFiles.stream().map(context::renderExpression).collect(toList());
  }

  private ArrayList<String> getS3URIList(FileMetadata fileMetadata, String key) {
    Object uriListObject = fileMetadata.getMetadata().get(key);
    if (uriListObject != null) {
      return new ArrayList<>((List<String>) fileMetadata.getMetadata().get(REMOTE_BE_CONFIG_S3_URI_LIST_KEY));
    }
    return null;
  }

  protected List<String> resolveTargets(List<String> targets, ExecutionContext context) {
    if (isEmpty(targets)) {
      return targets;
    }
    return targets.stream().map(context::renderExpression).collect(toList());
  }

  protected void saveTerraformConfig(
      ExecutionContext context, TerraformInfrastructureProvisioner provisioner, TerraformExecutionData executionData) {
    TfVarSource tfVarSource = executionData.getTfVarSource();
    GitFileConfig gitFileConfig = null != tfVarSource && tfVarSource.getTfVarSourceType() == TfVarSourceType.GIT
        ? ((TfVarGitSource) tfVarSource).getGitFileConfig()
        : null;
    S3FileConfig s3FileConfig = null != tfVarSource && tfVarSource.getTfVarSourceType() == TfVarSourceType.S3
        ? ((TfVarS3Source) tfVarSource).getS3FileConfig()
        : null;

    S3FileConfig s3RemoteBackendConfig = null;
    GitFileConfig remoteBackendConfig = null;

    if (executionData.getRemoteBackendConfig() != null) {
      remoteBackendConfig = executionData.getRemoteBackendConfig().getGitFileConfig();
    }

    if (executionData.getRemoteS3BackendConfig() != null) {
      s3RemoteBackendConfig = executionData.getRemoteS3BackendConfig().getS3FileConfig();
    }

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .entityId(generateEntityId(context, executionData.getWorkspace(), provisioner, true))
            .sourceRepoSettingId(provisioner.getSourceRepoSettingId())
            .sourceRepoReference(executionData.getSourceRepoReference())
            .variables(executionData.getVariables())
            .delegateTag(executionData.getDelegateTag())
            .backendConfigs(executionData.getBackendConfigs())
            .remoteBackendConfig(remoteBackendConfig)
            .backendConfigStoreType(executionData.getBackendConfigStoreType())
            .environmentVariables(executionData.getEnvironmentVariables())
            .tfVarFiles(executionData.getTfVarFiles())
            .tfVarGitFileConfig(gitFileConfig)
            .tfVarS3FileConfig(s3FileConfig)
            .s3BackendFileConfig(s3RemoteBackendConfig)
            .workflowExecutionId(context.getWorkflowExecutionId())
            .targets(executionData.getTargets())
            .command(executionData.getCommandExecuted())
            .appId(context.getAppId())
            .accountId(context.getAccountId())
            .awsConfigId(executionData.getAwsConfigId())
            .awsRoleArn(executionData.getAwsRoleArn())
            .awsRegion(executionData.getAwsRegion())
            .build();
    wingsPersistence.save(terraformConfig);
  }

  @VisibleForTesting
  protected String generateEntityId(ExecutionContext context, String workspace,
      TerraformInfrastructureProvisioner infrastructureProvisioner, boolean isNewEntityIdType) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String envId = executionContext.getEnv() != null ? executionContext.getEnv().getUuid() : EMPTY;

    StringBuilder stringBuilder = new StringBuilder(provisionerId).append('-').append(envId);

    if (isNewEntityIdType) {
      String normalizedPath = null;
      if (isNotEmpty(infrastructureProvisioner.getPath())) {
        normalizedPath =
            Paths.get("/", context.renderExpression(infrastructureProvisioner.getPath())).normalize().toString();
      }
      String branchAndPath = String.format("%s%s",
          isNotEmpty(infrastructureProvisioner.getSourceRepoBranch())
              ? context.renderExpression(infrastructureProvisioner.getSourceRepoBranch())
              : EMPTY,
          normalizedPath != null ? normalizedPath : EMPTY);
      stringBuilder.append(isNotEmpty(branchAndPath) ? "-" + branchAndPath.hashCode() : EMPTY);
    }
    stringBuilder.append(isEmpty(workspace) ? EMPTY : "-" + workspace);

    return stringBuilder.toString();
  }

  protected void deleteTerraformConfig(ExecutionContext context, TerraformExecutionData terraformExecutionData,
      TerraformInfrastructureProvisioner infrastructureProvisioner) {
    Query<TerraformConfig> query =
        wingsPersistence.createQuery(TerraformConfig.class)
            .filter(TerraformConfigKeys.entityId,
                generateEntityId(context, terraformExecutionData.getWorkspace(), infrastructureProvisioner, true));

    wingsPersistence.delete(query);

    boolean deleted = wingsPersistence.delete(
        wingsPersistence.createQuery(TerraformConfig.class)
            .filter(TerraformConfigKeys.entityId,
                generateEntityId(context, terraformExecutionData.getWorkspace(), infrastructureProvisioner, false)));
    log.info("{} TerraformConfig with old entityId", deleted ? "Deleted" : "Didn't delete");
  }

  protected TerraformInfrastructureProvisioner getTerraformInfrastructureProvisioner(ExecutionContext context) {
    InfrastructureProvisioner infrastructureProvisioner =
        infrastructureProvisionerService.get(context.getAppId(), provisionerId);

    if (infrastructureProvisioner == null) {
      throw new InvalidRequestException("Infrastructure Provisioner does not exist. Please check again.");
    }
    if (!(infrastructureProvisioner instanceof TerraformInfrastructureProvisioner)) {
      throw new InvalidRequestException("Infrastructure Provisioner " + infrastructureProvisioner.getName()
          + "should be of Terraform type. Please check again.");
    }
    return (TerraformInfrastructureProvisioner) infrastructureProvisioner;
  }

  private String createActivity(ExecutionContext executionContext) {
    Application app = requireNonNull(executionContext.getApp());
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(app.getName())
            .commandName(getName())
            .type(Type.Verification) // TODO : Change this to Type.Other
            .workflowType(executionContext.getWorkflowType())
            .workflowExecutionName(executionContext.getWorkflowExecutionName())
            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
            .commandType(getStateType())
            .workflowExecutionId(executionContext.getWorkflowExecutionId())
            .workflowId(executionContext.getWorkflowId())
            .commandUnits(
                asList(Builder.aCommand().withName(commandUnit().name()).withCommandType(CommandType.OTHER).build()))
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

  private boolean isRunAndExportEncryptedPlan() {
    return runPlanOnly && exportPlanToApplyStep;
  }

  private boolean isInheritingEncryptedPlan() {
    return !runPlanOnly && inheritApprovedPlan;
  }

  boolean isExportingDestroyPlan() {
    return runPlanOnly && TerraformCommand.DESTROY == command();
  }

  boolean isSecretManagerRequired() {
    return isRunAndExportEncryptedPlan() || isInheritingEncryptedPlan() || isExportingDestroyPlan();
  }

  SecretManagerConfig getSecretManagerContainingTfPlan(String secretManagerId, String accountId) {
    SecretManagerConfig secretManagerConfig = isEmpty(secretManagerId)
        ? secretManagerConfigService.getDefaultSecretManager(accountId)
        : secretManagerConfigService.getSecretManager(accountId, secretManagerId, false);
    if (featureFlagService.isEnabled(CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_CG, accountId)
        && isHarnessSecretManager(secretManagerConfig)) {
      removeCredFromHarnessSM(secretManagerConfig);
    }
    return secretManagerConfig;
  }

  private boolean isHarnessSecretManager(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig != null) {
      return secretManagerConfig.isGlobalKms();
    } else {
      return false;
    }
  }

  private void removeCredFromHarnessSM(SecretManagerConfig secretManagerConfig) {
    secretManagerConfig.maskSecrets();
  }

  protected SettingAttribute resolveAwsConfig(ExecutionContext context) {
    SettingAttribute settingAttribute = null;
    final List<TemplateExpression> templateExpressions = getTemplateExpressions();
    if (isNotEmpty(templateExpressions)) {
      TemplateExpression configIdExpression =
          templateExpressionProcessor.getTemplateExpression(templateExpressions, "awsConfigId");
      if (configIdExpression != null) {
        settingAttribute = templateExpressionProcessor.resolveSettingAttributeByNameOrId(
            context, configIdExpression, SettingVariableTypes.AWS);
      } else if (isNotEmpty(getAwsConfigId())) {
        settingAttribute = getAwsConfigSettingAttribute(getAwsConfigId());
      }
    } else if (isNotEmpty(getAwsConfigId())) {
      settingAttribute = getAwsConfigSettingAttribute(getAwsConfigId());
    }
    return settingAttribute;
  }

  protected SettingAttribute getAwsConfigSettingAttribute(String awsConfigId) {
    SettingAttribute awsSettingAttribute = settingsService.get(awsConfigId);
    if (awsSettingAttribute != null && !(awsSettingAttribute.getValue() instanceof AwsConfig)) {
      throw new InvalidRequestException("Setting attribute is not of type AwsConfig");
    }
    return awsSettingAttribute;
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