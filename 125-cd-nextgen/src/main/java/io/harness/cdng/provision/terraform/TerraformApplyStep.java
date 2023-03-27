/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TERRAFORM_CLI_OPTIONS_NG;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.CLI_OPTIONS;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.SKIP_REFRESH_COMMAND;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TERRAFORM_CLOUD_CLI;

import static software.wings.beans.TaskType.TERRAFORM_TASK_NG_V3;
import static software.wings.beans.TaskType.TERRAFORM_TASK_NG_V4;
import static software.wings.beans.TaskType.TERRAFORM_TASK_NG_V5;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TerraformApplyStep extends CdTaskExecutable<TerraformTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_APPLY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    TerraformApplyStepParameters stepParametersSpec = (TerraformApplyStepParameters) stepParameters.getSpec();

    if (stepParametersSpec.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      String connectorRef = stepParametersSpec.getConfiguration()
                                .getSpec()
                                .configFiles.store.getSpec()
                                .getConnectorReference()
                                .getValue();
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
      entityDetailList.add(entityDetail);

      // Var Files connectors
      LinkedHashMap<String, TerraformVarFile> varFiles = stepParametersSpec.getConfiguration().getSpec().getVarFiles();
      List<EntityDetail> varFileEntityDetails =
          TerraformStepHelper.prepareEntityDetailsForVarFiles(accountId, orgIdentifier, projectIdentifier, varFiles);
      entityDetailList.addAll(varFileEntityDetails);

      // Backend config connectors
      TerraformBackendConfig backendConfig = stepParametersSpec.getConfiguration().getSpec().getBackendConfig();
      Optional<EntityDetail> bcFileEntityDetails = TerraformStepHelper.prepareEntityDetailForBackendConfigFiles(
          accountId, orgIdentifier, projectIdentifier, backendConfig);
      bcFileEntityDetails.ifPresent(entityDetailList::add);
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) stepElementParameters.getSpec();
    log.info("Starting execution Obtain Task after Rbac for the Apply Step");
    helper.validateApplyStepParamsInline(stepParameters);

    if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      return obtainInlineTask(ambiance, stepParameters, stepElementParameters);
    } else if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName())) {
      return obtainInheritedTask(ambiance, stepParameters, stepElementParameters);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  private TaskRequest obtainInlineTask(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inline Task for the Apply Step");
    boolean isTerraformCloudCli = stepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().getValue();

    if (isTerraformCloudCli) {
      helper.checkIfTerraformCloudCliIsEnabled(FeatureName.CD_TERRAFORM_CLOUD_CLI_NG, true, ambiance);
      io.harness.delegate.TaskType taskTypeV3 =
          io.harness.delegate.TaskType.newBuilder().setType(TERRAFORM_TASK_NG_V3.name()).build();
      helper.checkIfTaskIsSupportedByDelegate(ambiance, taskTypeV3, TERRAFORM_CLOUD_CLI);
    }

    ParameterField<Boolean> skipTerraformRefreshCommandParameter =
        stepParameters.getConfiguration().getIsSkipTerraformRefresh();
    boolean skipRefreshCommand =
        ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommandParameter);

    if (skipRefreshCommand) {
      io.harness.delegate.TaskType taskTypeV4 =
          io.harness.delegate.TaskType.newBuilder().setType(TERRAFORM_TASK_NG_V4.name()).build();
      helper.checkIfTaskIsSupportedByDelegate(ambiance, taskTypeV4, SKIP_REFRESH_COMMAND);
    }

    helper.validateApplyStepConfigFilesInline(stepParameters);
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);

    if (!isTerraformCloudCli) {
      builder.workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()));
    }

    if (cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_CLI_OPTIONS_NG)) {
      io.harness.delegate.TaskType taskTypeV5 =
          io.harness.delegate.TaskType.newBuilder().setType(TERRAFORM_TASK_NG_V5.name()).build();
      helper.checkIfTaskIsSupportedByDelegate(ambiance, taskTypeV5, CLI_OPTIONS);

      builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));
    }

    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.currentStateFileId(helper.getLatestFileId(entityId))
            .taskType(TFTaskType.APPLY)
            .terraformCommand(TerraformCommand.APPLY)
            .terraformCommandUnit(TerraformCommandUnit.Apply)
            .entityId(entityId)
            .tfModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
                stepParameters.getConfiguration().getSpec().getConfigFiles(), stepElementParameters.getType()))
            .configFile(helper.getGitFetchFilesConfig(
                spec.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .fileStoreConfigFiles(helper.getFileStoreFetchFilesConfig(
                spec.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .varFileInfos(helper.toTerraformVarFileInfo(spec.getVarFiles(), ambiance))
            .backendConfig(helper.getBackendConfig(spec.getBackendConfig()))
            .backendConfigFileInfo(helper.toTerraformBackendFileInfo(spec.getBackendConfig(), ambiance))
            .targets(ParameterFieldHelper.getParameterFieldValue(spec.getTargets()))
            .saveTerraformStateJson(false)
            .saveTerraformHumanReadablePlan(false)
            .environmentVariables(helper.getEnvironmentVariablesMap(spec.getEnvironmentVariables()) == null
                    ? new HashMap<>()
                    : helper.getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
            .timeoutInMillis(
                StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .useOptimizedTfPlan(true)
            .isTerraformCloudCli(isTerraformCloudCli)
            .skipTerraformRefresh(skipRefreshCommand)
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Apply.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainInheritedTask(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inherited Task for the Apply Step");
    TerraformTaskNGParametersBuilder builder =
        TerraformTaskNGParameters.builder().taskType(TFTaskType.APPLY).terraformCommandUnit(TerraformCommandUnit.Apply);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.entityId(entityId);
    builder.currentStateFileId(helper.getLatestFileId(entityId));

    if (cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_CLI_OPTIONS_NG)) {
      io.harness.delegate.TaskType taskTypeV5 =
          io.harness.delegate.TaskType.newBuilder().setType(TERRAFORM_TASK_NG_V5.name()).build();
      helper.checkIfTaskIsSupportedByDelegate(ambiance, taskTypeV5, CLI_OPTIONS);

      builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));
    }

    TerraformInheritOutput inheritOutput = helper.getSavedInheritOutput(provisionerIdentifier, APPLY.name(), ambiance);
    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.workspace(inheritOutput.getWorkspace())
            .configFile(helper.getGitFetchFilesConfig(
                inheritOutput.getConfigFiles(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .tfModuleSourceInheritSSH(inheritOutput.isUseConnectorCredentials())
            .fileStoreConfigFiles(helper.getFileStoreFetchFilesConfig(
                inheritOutput.getFileStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .varFileInfos(helper.prepareTerraformVarFileInfo(inheritOutput.getVarFileConfigs(), ambiance))
            .backendConfig(inheritOutput.getBackendConfig())
            .backendConfigFileInfo(helper.prepareTerraformBackendConfigFileInfo(
                inheritOutput.getBackendConfigurationFileConfig(), ambiance))
            .targets(inheritOutput.getTargets())
            .saveTerraformStateJson(false)
            .saveTerraformHumanReadablePlan(false)
            .encryptionConfig(inheritOutput.getEncryptionConfig())
            .encryptedTfPlan(inheritOutput.getEncryptedTfPlan())
            .planName(inheritOutput.getPlanName())
            .environmentVariables(inheritOutput.getEnvironmentVariables() == null
                    ? new HashMap<>()
                    : inheritOutput.getEnvironmentVariables())
            .timeoutInMillis(
                StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .useOptimizedTfPlan(true)
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Apply.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerraformTaskNGResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the Apply Step");
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) stepElementParameters.getSpec();

    if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      return handleTaskResultInline(ambiance, stepParameters, responseSupplier);
    } else if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName())) {
      return handleTaskResultInherited(ambiance, stepParameters, responseSupplier);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  private StepResponseBuilder createStepResponseBuilder(ThrowingSupplier<TerraformTaskNGResponse> responseSupplier)
      throws Exception {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformTaskNGResponse terraformTaskNGResponse = responseSupplier.get();
    List<UnitProgress> unitProgresses = terraformTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    switch (terraformTaskNGResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        stepResponseBuilder.status(Status.SUCCEEDED);
        break;
      case FAILURE:
        stepResponseBuilder.status(Status.FAILED);
        break;
      case RUNNING:
        stepResponseBuilder.status(Status.RUNNING);
        break;
      case QUEUED:
        stepResponseBuilder.status(Status.QUEUED);
        break;
      default:
        throw new InvalidRequestException(
            "Unhandled type CommandExecutionStatus: " + terraformTaskNGResponse.getCommandExecutionStatus().name(),
            WingsException.USER);
    }
    return stepResponseBuilder;
  }

  private void addStepOutcomeToStepResponse(
      StepResponseBuilder stepResponseBuilder, TerraformTaskNGResponse terraformTaskNGResponse) {
    stepResponseBuilder.stepOutcome(
        StepResponse.StepOutcome.builder()
            .name(OutcomeExpressionConstants.OUTPUT)
            .outcome(new TerraformApplyOutcome(helper.parseTerraformOutputs(terraformTaskNGResponse.getOutputs())))
            .build());
  }

  private StepResponse handleTaskResultInline(Ambiance ambiance, TerraformApplyStepParameters stepParameters,
      ThrowingSupplier<TerraformTaskNGResponse> responseSupplier) throws Exception {
    log.info("Handling Task Result Inline for the Apply Step");
    StepResponseBuilder stepResponseBuilder = createStepResponseBuilder(responseSupplier);
    TerraformTaskNGResponse terraformTaskNGResponse = responseSupplier.get();
    List<UnitProgress> unitProgresses = terraformTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);
    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      helper.saveRollbackDestroyConfigInline(stepParameters, terraformTaskNGResponse, ambiance);
      addStepOutcomeToStepResponse(stepResponseBuilder, terraformTaskNGResponse);
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }
    return stepResponseBuilder.build();
  }

  private StepResponse handleTaskResultInherited(Ambiance ambiance, TerraformApplyStepParameters stepParameters,
      ThrowingSupplier<TerraformTaskNGResponse> responseSupplier) throws Exception {
    log.info("Handling Task Result Inherited for the Apply Step");
    StepResponseBuilder stepResponseBuilder = createStepResponseBuilder(responseSupplier);
    TerraformTaskNGResponse terraformTaskNGResponse = responseSupplier.get();
    List<UnitProgress> unitProgresses = terraformTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);
    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      helper.saveRollbackDestroyConfigInherited(stepParameters, ambiance);
      addStepOutcomeToStepResponse(stepResponseBuilder, terraformTaskNGResponse);
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }
    return stepResponseBuilder.build();
  }
}