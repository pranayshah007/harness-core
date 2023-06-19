/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TERRAFORM_CLI_OPTIONS_NG;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.DESTROY;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformDestroyStep extends CdTaskExecutable<TerraformTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_DESTROY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject public TerraformConfigDAL terraformConfigDAL;

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

    TerraformDestroyStepParameters stepParametersSpec = (TerraformDestroyStepParameters) stepParameters.getSpec();

    if (stepParametersSpec.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      // Config Files connector
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
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Running Obtain Inline Task for the Destroy Step");
    TerraformDestroyStepParameters parameters = (TerraformDestroyStepParameters) stepElementParameters.getSpec();
    helper.validateDestroyStepParamsInline(parameters);

    if (parameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      return obtainInlineTask(ambiance, parameters, stepElementParameters);
    } else if (parameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName())) {
      return obtainInheritedTask(ambiance, parameters, stepElementParameters);
    } else if (parameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_APPLY.getDisplayName())) {
      return obtainLastApplyTask(ambiance, parameters, stepElementParameters);
    } else {
      throw new InvalidRequestException(
          String.format("Unknown configuration Type: [%s]", parameters.getConfiguration().getType().getDisplayName()));
    }
  }

  private TaskRequest obtainInlineTask(
      Ambiance ambiance, TerraformDestroyStepParameters parameters, StepElementParameters stepElementParameters) {
    log.info("Running Obtain Inline Task for the Destroy Step");
    boolean isTerraformCloudCli = parameters.getConfiguration().getSpec().getIsTerraformCloudCli().getValue();

    helper.validateDestroyStepConfigFilesInline(parameters);
    TerraformExecutionDataParameters spec = parameters.getConfiguration().getSpec();
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);

    if (!isTerraformCloudCli) {
      builder.workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()));
    }

    if (cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_CLI_OPTIONS_NG)) {
      builder.terraformCommandFlags(helper.getTerraformCliFlags(parameters.getConfiguration().getCliOptions()));
    }

    ParameterField<Boolean> skipTerraformRefreshCommandParameter =
        parameters.getConfiguration().getIsSkipTerraformRefresh();
    boolean skipRefreshCommand =
        ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommandParameter);

    builder.skipTerraformRefresh(skipRefreshCommand);

    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.currentStateFileId(helper.getLatestFileId(entityId))
            .taskType(TFTaskType.DESTROY)
            .terraformCommand(TerraformCommand.DESTROY)
            .terraformCommandUnit(TerraformCommandUnit.Destroy)
            .entityId(entityId)
            .tfModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
                parameters.getConfiguration().getSpec().getConfigFiles(), stepElementParameters.getType()))
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
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Destroy.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(parameters.getDelegateSelectors()), stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainInheritedTask(
      Ambiance ambiance, TerraformDestroyStepParameters parameters, StepElementParameters stepElementParameters) {
    log.info("Running Obtain Inherited Task for the Destroy Step");
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder().taskType(TFTaskType.DESTROY);
    builder.terraformCommandUnit(TerraformCommandUnit.Destroy);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier = ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.entityId(entityId);
    builder.currentStateFileId(helper.getLatestFileId(entityId));

    if (cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_CLI_OPTIONS_NG)) {
      builder.terraformCommandFlags(helper.getTerraformCliFlags(parameters.getConfiguration().getCliOptions()));
    }

    TerraformInheritOutput inheritOutput =
        helper.getSavedInheritOutput(provisionerIdentifier, DESTROY.name(), ambiance);
    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.workspace(inheritOutput.getWorkspace())
            .configFile(helper.getGitFetchFilesConfig(
                inheritOutput.getConfigFiles(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .tfModuleSourceInheritSSH(inheritOutput.isUseConnectorCredentials())
            .fileStoreConfigFiles(inheritOutput.getFileStorageConfigDTO() != null
                    ? helper.prepareTerraformConfigFileInfo(inheritOutput.getFileStorageConfigDTO(), ambiance)
                    : helper.getFileStoreFetchFilesConfig(
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
        Collections.singletonList(TerraformCommandUnit.Destroy.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(parameters.getDelegateSelectors()), stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainLastApplyTask(
      Ambiance ambiance, TerraformDestroyStepParameters parameters, StepElementParameters stepElementParameters) {
    log.info("Getting the Last Apply Task for the Destroy Step");
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder().taskType(TFTaskType.DESTROY);
    builder.terraformCommandUnit(TerraformCommandUnit.Destroy);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);
    builder.entityId(entityId);
    builder.currentStateFileId(helper.getLatestFileId(entityId));

    if (cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_CLI_OPTIONS_NG)) {
      builder.terraformCommandFlags(helper.getTerraformCliFlags(parameters.getConfiguration().getCliOptions()));
    }

    TerraformConfig terraformConfig = helper.getLastSuccessfulApplyConfig(parameters, ambiance);
    builder.workspace(terraformConfig.getWorkspace())
        .varFileInfos(helper.prepareTerraformVarFileInfo(terraformConfig.getVarFileConfigs(), ambiance))
        .backendConfig(terraformConfig.getBackendConfig())
        .backendConfigFileInfo(
            helper.prepareTerraformBackendConfigFileInfo(terraformConfig.getBackendConfigFileConfig(), ambiance))
        .targets(terraformConfig.getTargets())
        .saveTerraformStateJson(false)
        .saveTerraformHumanReadablePlan(false)
        .environmentVariables(terraformConfig.getEnvironmentVariables() == null
                ? new HashMap<>()
                : terraformConfig.getEnvironmentVariables())
        .timeoutInMillis(
            StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
        .useOptimizedTfPlan(true);
    if (terraformConfig.getConfigFiles() != null) {
      builder.configFile(helper.getGitFetchFilesConfig(
          terraformConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES));
      builder.tfModuleSourceInheritSSH(terraformConfig.isUseConnectorCredentials());
    }
    if (terraformConfig.getFileStoreConfig() != null) {
      builder.fileStoreConfigFiles(
          helper.prepareTerraformConfigFileInfo(terraformConfig.getFileStoreConfig(), ambiance));
    }

    ParameterField<Boolean> skipTerraformRefreshCommand = parameters.getConfiguration().getIsSkipTerraformRefresh();
    builder.skipTerraformRefresh(ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommand));

    TerraformTaskNGParameters terraformTaskNGParameters = builder.build();
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Destroy.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(parameters.getDelegateSelectors()), stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerraformTaskNGResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the Destroy Step");
    TerraformDestroyStepParameters parameters = (TerraformDestroyStepParameters) stepElementParameters.getSpec();
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

    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      terraformConfigDAL.clearTerraformConfig(ambiance,
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance));
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }
    return stepResponseBuilder.build();
  }
}