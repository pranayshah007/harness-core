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
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW;
import static io.harness.beans.FeatureName.TERRAFORM_AWS_CP_AUTHENTICATION;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.context.ContextElementType.TERRAFORM_INHERIT_PLAN;
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
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.TaskType.TERRAFORM_PROVISION_TASK;
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
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TerraformInfrastructureProvisioner;
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
import org.mongodb.morphia.query.Query;

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
  @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> environmentVariables;
  @Getter @Setter private List<String> targets;

  @Getter @Setter private List<String> tfVarFiles;
  @Getter @Setter private GitFileConfig tfVarGitFileConfig;

  @Getter @Setter private boolean runPlanOnly;
  @Getter @Setter private boolean inheritApprovedPlan;
  @Getter @Setter private boolean exportPlanToApplyStep;
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

    TerraformProvisionInheritPlanElement inheritPlanElement =
        TerraformProvisionInheritPlanElement.builder()
            .entityId(generateEntityId(context, terraformExecutionData.getWorkspace(), terraformProvisioner, true))
            .provisionerId(provisionerId)
            .targets(terraformExecutionData.getTargets())
            .delegateTag(terraformExecutionData.getDelegateTag())
            .tfVarFiles(terraformExecutionData.getTfVarFiles())
            .tfVarSource(terraformExecutionData.getTfVarSource())
            .sourceRepoSettingId(terraformProvisioner.getSourceRepoSettingId())
            .sourceRepoReference(terraformExecutionData.getSourceRepoReference())
            .variables(terraformExecutionData.getVariables())
            .backendConfigs(terraformExecutionData.getBackendConfigs())
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
    boolean saveTfPlanSweepingOutput =
        executionData.getTfPlanJsonFiledId() != null || executionData.getTfPlanJson() != null;
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

      TerraformPlanParamBuilder tfPlanParamBuilder = TerraformPlanParam.builder();
      if (featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())) {
        tfPlanParamBuilder.tfPlanJsonFileId(executionData.getTfPlanJsonFiledId());
      } else {
        tfPlanParamBuilder.tfplan(format("'%s'", JsonUtils.prettifyJsonString(executionData.getTfPlanJson())));
      }

      Scope scope =
          featureFlagService.isEnabled(SAVE_TERRAFORM_APPLY_SWEEPING_OUTPUT_TO_WORKFLOW, context.getAccountId())
          ? Scope.WORKFLOW
          : Scope.PIPELINE;
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
    }

    if (terraformExecutionData.getExecutionStatus() == FAILED) {
      return ExecutionResponse.builder()
          .stateExecutionData(terraformExecutionData)
          .executionStatus(terraformExecutionData.getExecutionStatus())
          .errorMessage(terraformExecutionData.getErrorMessage())
          .build();
    }

    saveUserInputs(context, terraformExecutionData, terraformProvisioner);
    TerraformOutputInfoElement outputInfoElement = context.getContextElement(ContextElementType.TERRAFORM_PROVISION);
    if (outputInfoElement == null) {
      outputInfoElement = TerraformOutputInfoElement.builder().build();
    }
    if (terraformExecutionData.getOutputs() != null) {
      Map<String, Object> outputs = parseOutputs(terraformExecutionData.getOutputs());
      if (featureFlagService.isEnabled(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT, context.getAccountId())) {
        saveOutputs(context, outputs);
      } else {
        outputInfoElement.addOutPuts(outputs);
      }
      ManagerExecutionLogCallback executionLogCallback = infrastructureProvisionerService.getManagerExecutionCallback(
          terraformProvisioner.getAppId(), terraformExecutionData.getActivityId(), commandUnit().name());
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

  private void saveOutputs(ExecutionContext context, Map<String, Object> outputs) {
    TerraformOutputInfoElement outputInfoElement = context.getContextElement(ContextElementType.TERRAFORM_PROVISION);
    SweepingOutputInstance instance = sweepingOutputService.find(
        context.prepareSweepingOutputInquiryBuilder().name(TerraformOutputVariables.SWEEPING_OUTPUT_NAME).build());
    TerraformOutputVariables terraformOutputVariables =
        instance != null ? (TerraformOutputVariables) instance.getValue() : new TerraformOutputVariables();

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
      collectVariables(others, terraformExecutionData.getBackendConfigs(), BACKEND_CONFIGS_KEY,
          ENCRYPTED_BACKEND_CONFIGS_KEY, false);
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
    String path = context.renderExpression(terraformProvisioner.getNormalizedPath());
    if (path == null) {
      path = context.renderExpression(FilenameUtils.normalize(terraformProvisioner.getPath()));
      if (path == null) {
        throw new InvalidRequestException("Invalid Terraform script path", USER);
      }
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
    GitConfig gitConfig = gitUtilsManager.getGitConfig(element.getSourceRepoSettingId());
    if (isNotEmpty(element.getSourceRepoReference())) {
      gitConfig.setReference(element.getSourceRepoReference());
      String branch = context.renderExpression(terraformProvisioner.getSourceRepoBranch());
      if (isNotEmpty(branch)) {
        gitConfig.setBranch(branch);
      }

    } else {
      throw new InvalidRequestException("No commit id found in context inherit tf plan element.");
    }

    List<NameValuePair> allBackendConfigs = element.getBackendConfigs();
    Map<String, String> backendConfigs = null;
    Map<String, EncryptedDataDetail> encryptedBackendConfigs = null;
    if (isNotEmpty(allBackendConfigs)) {
      backendConfigs = infrastructureProvisionerService.extractTextVariables(allBackendConfigs, context);
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

    gitConfigHelperService.convertToRepoGitConfig(
        gitConfig, context.renderExpression(terraformProvisioner.getRepoName()));

    SecretManagerConfig secretManagerConfig = isSecretManagerRequired()
        ? getSecretManagerContainingTfPlan(terraformProvisioner.getKmsId(), context.getAccountId())
        : null;

    TfVarSource tfVarSource = element.getTfVarSource();
    if (tfVarSource != null && TfVarSourceType.GIT.equals(tfVarSource.getTfVarSourceType())) {
      setTfVarGitFileConfig(((TfVarGitSource) element.getTfVarSource()).getGitFileConfig());
      if (getTfVarGitFileConfig() != null) {
        tfVarSource = fetchTfVarGitSource(context);
      }
    }

    EncryptedRecordData encryptedTfPlan =
        featureFlagService.isEnabled(FeatureName.OPTIMIZED_TF_PLAN, context.getAccountId())
        ? terraformPlanHelper.getEncryptedTfPlanFromSweepingOutput(context, getPlanName(context))
        : element.getEncryptedTfPlan();

    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        TerraformProvisionParameters.builder()
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
            .tfVarSource(tfVarSource)
            .runPlanOnly(false)
            .exportPlanToApplyStep(false)
            .workspace(workspace)
            .delegateTag(element.getDelegateTag())
            .skipRefreshBeforeApplyingPlan(terraformProvisioner.isSkipRefreshBeforeApplyingPlan())
            .encryptedTfPlan(encryptedTfPlan)
            .secretManagerConfig(secretManagerConfig)
            .planName(getEncryptedPlanName(context))
            .useTfClient(
                featureFlagService.isEnabled(FeatureName.USE_TF_CLIENT, executionContext.getApp().getAccountId()))
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, executionContext.getApp().getAccountId()))
            .useActivityIdBasedTfBaseDir(
                featureFlagService.isEnabled(ACTIVITY_ID_BASED_TF_BASE_DIR, context.getAccountId()));

    if (featureFlagService.isEnabled(TERRAFORM_AWS_CP_AUTHENTICATION, context.getAccountId())) {
      setAWSAuthParamsIfPresent(context, terraformProvisionParametersBuilder);
    }
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
                      .taskType(TERRAFORM_PROVISION_TASK.name())
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

    String delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(executionContext, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  private ExecutionResponse executeInternalRegular(ExecutionContext context, String activityId) {
    TerraformInfrastructureProvisioner terraformProvisioner = getTerraformInfrastructureProvisioner(context);
    GitConfig gitConfig = gitUtilsManager.getGitConfig(terraformProvisioner.getSourceRepoSettingId());

    SecretManagerConfig secretManagerConfig = isSecretManagerRequired()
        ? getSecretManagerContainingTfPlan(terraformProvisioner.getKmsId(), context.getAccountId())
        : null;

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
    String path = context.renderExpression(terraformProvisioner.getNormalizedPath());
    if (path == null) {
      path = context.renderExpression(FilenameUtils.normalize(terraformProvisioner.getPath()));
      if (path == null) {
        throw new InvalidRequestException("Invalid Terraform script path", USER);
      }
    }

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

    validateTerraformVariables();

    if (isNotEmpty(this.variables) || isNotEmpty(this.backendConfigs) || isNotEmpty(this.environmentVariables)) {
      List<NameValuePair> validVariables = isNotEmpty(getVariables()) ? getVariables() : new ArrayList<>();
      rawVariablesList.addAll(validVariables);

      variables = infrastructureProvisionerService.extractUnresolvedTextVariables(validVariables);
      encryptedVariables = infrastructureProvisionerService.extractEncryptedTextVariables(
          validVariables, context.getAppId(), context.getWorkflowExecutionId());

      if (this.backendConfigs != null) {
        backendConfigs = infrastructureProvisionerService.extractTextVariables(this.backendConfigs, context);
        encryptedBackendConfigs = infrastructureProvisionerService.extractEncryptedTextVariables(
            this.backendConfigs, context.getAppId(), context.getWorkflowExecutionId());
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
        FileMetadata fileMetadata = fileService.getFileMetadata(fileId, FileBucket.TERRAFORM_STATE);

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
          encryptedEnvironmentVars = extractEncryptedData(context, fileMetadata, ENCRYPTED_ENVIRONMENT_VARS_KEY);

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
      tfVarSource = fetchTfVarScriptRepositorySource(context);
    } else if (null != tfVarGitFileConfig) {
      tfVarSource = fetchTfVarGitSource(context);
    }

    targets = resolveTargets(targets, context);
    gitConfigHelperService.convertToRepoGitConfig(
        gitConfig, context.renderExpression(terraformProvisioner.getRepoName()));

    if (runPlanOnly && this instanceof DestroyTerraformProvisionState) {
      exportPlanToApplyStep = true;
    }

    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        TerraformProvisionParameters.builder()
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
            .useTfClient(
                featureFlagService.isEnabled(FeatureName.USE_TF_CLIENT, executionContext.getApp().getAccountId()))
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, executionContext.getApp().getAccountId()))
            .useActivityIdBasedTfBaseDir(
                featureFlagService.isEnabled(ACTIVITY_ID_BASED_TF_BASE_DIR, context.getAccountId()));

    if (featureFlagService.isEnabled(TERRAFORM_AWS_CP_AUTHENTICATION, context.getAccountId())) {
      setAWSAuthParamsIfPresent(context, terraformProvisionParametersBuilder);
    }
    return createAndRunTask(activityId, executionContext, terraformProvisionParametersBuilder.build(), delegateTag);
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
    }
    return tfVarSource;
  }

  @VisibleForTesting
  TfVarScriptRepositorySource fetchTfVarScriptRepositorySource(ExecutionContext context) {
    return TfVarScriptRepositorySource.builder().tfVarFilePaths(getRenderedTfVarFiles(tfVarFiles, context)).build();
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
    Map<String, Object> rawBackendConfigs = (Map<String, Object>) fileMetadata.getMetadata().get(BACKEND_CONFIGS_KEY);
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

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .entityId(generateEntityId(context, executionData.getWorkspace(), provisioner, true))
            .sourceRepoSettingId(provisioner.getSourceRepoSettingId())
            .sourceRepoReference(executionData.getSourceRepoReference())
            .variables(executionData.getVariables())
            .delegateTag(executionData.getDelegateTag())
            .backendConfigs(executionData.getBackendConfigs())
            .environmentVariables(executionData.getEnvironmentVariables())
            .tfVarFiles(executionData.getTfVarFiles())
            .tfVarGitFileConfig(gitFileConfig)
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
    return isEmpty(secretManagerId) ? secretManagerConfigService.getDefaultSecretManager(accountId)
                                    : secretManagerConfigService.getSecretManager(accountId, secretManagerId, false);
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
    if (!(awsSettingAttribute.getValue() instanceof AwsConfig)) {
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
