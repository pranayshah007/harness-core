/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TF_TG_SKIP_ERROR_LOGS_COLORING;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.DESTROY;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_BACKEND_CONFIG_FILE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_CONFIG_FILES;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformDestroyStepV2 extends CdTaskChainExecutable {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_DESTROY_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject public TerraformConfigDAL terraformConfigDAL;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
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

  private TaskChainResponse handleDestroyInlineStartChain(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    helper.validateDestroyStepConfigFilesInline(stepParameters);
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    List<TerraformVarFileInfo> varFilesInfo = helper.getRemoteVarFilesInfo(spec.getVarFiles(), ambiance);
    boolean hasGitVarFiles = helper.hasGitVarFiles(varFilesInfo);
    boolean hasS3VarFiles = helper.hasS3VarFiles(varFilesInfo);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(hasGitVarFiles).hasS3Files(hasS3VarFiles).build();

    TerraformTaskNGParametersBuilder builder =
        getTerraformTaskNGParametersBuilderInline(ambiance, stepParameters, stepElementParameters);
    terraformPassThroughData.setTerraformTaskNGParametersBuilder(builder);
    terraformPassThroughData.setOriginalStepVarFiles(spec.getVarFiles());

    if (hasGitVarFiles || hasS3VarFiles) {
      return helper.fetchRemoteVarFiles(terraformPassThroughData, varFilesInfo, ambiance, stepElementParameters,
          TerraformCommandUnit.Destroy.name(), stepParameters.getDelegateSelectors());
    }

    return helper.executeTerraformTask(builder.build(), stepElementParameters, ambiance, terraformPassThroughData,
        stepParameters.getDelegateSelectors(), TerraformCommandUnit.Destroy.name());
  }

  private TaskChainResponse handleDestroyInheritPlanStartChain(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    // When Destroy Inherit from Plan no need to fetch remote var-files, as tfPlan from Plan step is applied.
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    TerraformTaskNGParametersBuilder builder =
        getTerraformTaskNGParametersBuilderInheritFromPlan(ambiance, stepParameters, stepElementParameters);
    terraformPassThroughData.setTerraformTaskNGParametersBuilder(builder);

    return helper.executeTerraformTask(builder.build(), stepElementParameters, ambiance, terraformPassThroughData,
        stepParameters.getDelegateSelectors(), TerraformCommandUnit.Destroy.name());
  }

  private TaskChainResponse handleDestroyInheritApplyStartChain(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    TerraformConfig terraformConfig = helper.getLastSuccessfulApplyConfig(stepParameters, ambiance);
    List<TerraformVarFileInfo> varFilesInfo =
        helper.prepareTerraformVarFileInfo(terraformConfig.getVarFileConfigs(), ambiance, true);
    boolean hasGitVarFiles = helper.hasGitVarFiles(varFilesInfo);
    boolean hasS3VarFiles = helper.hasS3VarFiles(varFilesInfo);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(hasGitVarFiles).hasS3Files(hasS3VarFiles).build();

    TerraformTaskNGParametersBuilder builder =
        getTerraformTaskNGParametersBuilderInheritFromApply(ambiance, stepParameters, stepElementParameters);
    terraformPassThroughData.setTerraformTaskNGParametersBuilder(builder);
    terraformPassThroughData.setOriginalVarFileConfigs(terraformConfig.getVarFileConfigs());

    if (hasGitVarFiles || hasS3VarFiles) {
      return helper.fetchRemoteVarFiles(terraformPassThroughData, varFilesInfo, ambiance, stepElementParameters,
          TerraformCommandUnit.Destroy.name(), stepParameters.getDelegateSelectors());
    }

    return helper.executeTerraformTask(builder.build(), stepElementParameters, ambiance, terraformPassThroughData,
        stepParameters.getDelegateSelectors(), TerraformCommandUnit.Destroy.name());
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Running Obtain Inline Task for the Destroy Step");
    TerraformDestroyStepParameters stepParameters = (TerraformDestroyStepParameters) stepElementParameters.getSpec();
    String destroyConfigurationType = stepParameters.getConfiguration().getType().getDisplayName();

    if (TerraformStepConfigurationType.INLINE.getDisplayName().equalsIgnoreCase(destroyConfigurationType)) {
      return handleDestroyInlineStartChain(ambiance, stepParameters, stepElementParameters);

    } else if (TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName().equalsIgnoreCase(
                   destroyConfigurationType)) {
      return handleDestroyInheritPlanStartChain(ambiance, stepParameters, stepElementParameters);

    } else if (TerraformStepConfigurationType.INHERIT_FROM_APPLY.getDisplayName().equalsIgnoreCase(
                   destroyConfigurationType)) {
      return handleDestroyInheritApplyStartChain(ambiance, stepParameters, stepElementParameters);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    TerraformDestroyStepParameters stepParameters = (TerraformDestroyStepParameters) stepElementParameters.getSpec();

    return helper.executeNextLink(ambiance, responseSupplier, passThroughData, stepParameters.getDelegateSelectors(),
        stepElementParameters, TerraformCommandUnit.Destroy.name());
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for the Apply Step");

    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return helper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }

    TerraformDestroyStepParameters parameters = (TerraformDestroyStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformTaskNGResponse terraformTaskNGResponse = (TerraformTaskNGResponse) responseDataSupplier.get();
    List<UnitProgress> unitProgresses = terraformTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses();

    TerraformPassThroughData terraformPassThroughData = (TerraformPassThroughData) passThroughData;

    boolean responseHasFetchFiles = false;
    if (terraformTaskNGResponse.getUnitProgressData() != null) {
      responseHasFetchFiles = terraformTaskNGResponse.getUnitProgressData().getUnitProgresses().stream().anyMatch(
          unitProgress -> unitProgress.getUnitName().equalsIgnoreCase("Fetch Files"));
    }

    if (!responseHasFetchFiles && (terraformPassThroughData.hasGitFiles() || terraformPassThroughData.hasS3Files())) {
      unitProgresses.addAll(terraformPassThroughData.getUnitProgresses());
    }

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

    Map<String, String> outputKeys =
        getGitRevisionsOutput(parameters, terraformTaskNGResponse, terraformPassThroughData);

    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      terraformConfigDAL.clearTerraformConfig(ambiance,
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance));
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);

    return stepResponseBuilder.build();
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilderInline(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    log.info("Running Obtain Inline Task for the Destroy Step");
    boolean isTerraformCloudCli = stepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().getValue();

    helper.validateDestroyStepConfigFilesInline(stepParameters);
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance);

    if (!isTerraformCloudCli) {
      builder.workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()));
    }

    builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));

    ParameterField<Boolean> skipTerraformRefreshCommandParameter =
        stepParameters.getConfiguration().getIsSkipTerraformRefresh();
    boolean skipRefreshCommand =
        ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommandParameter);

    builder.skipTerraformRefresh(skipRefreshCommand);

    return builder.currentStateFileId(helper.getLatestFileId(entityId))
        .taskType(TFTaskType.DESTROY)
        .terraformCommand(TerraformCommand.DESTROY)
        .terraformCommandUnit(TerraformCommandUnit.Destroy)
        .entityId(entityId)
        .tfModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
            stepParameters.getConfiguration().getSpec().getConfigFiles(), stepElementParameters.getType()))
        .configFile(helper.getGitFetchFilesConfig(
            spec.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .fileStoreConfigFiles(helper.getFileStoreFetchFilesConfig(
            spec.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .varFileInfos(helper.toTerraformVarFileInfoWithIdentifierAndManifest(spec.getVarFiles(), ambiance))
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
        .skipColorLogs(cdFeatureFlagHelper.isEnabled(accountId, CDS_TF_TG_SKIP_ERROR_LOGS_COLORING));
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilderInheritFromPlan(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    log.info("Running Obtain Inherited Task for the Destroy Step");
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder().taskType(TFTaskType.DESTROY);
    builder.terraformCommandUnit(TerraformCommandUnit.Destroy);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.entityId(entityId);
    builder.currentStateFileId(helper.getLatestFileId(entityId));

    builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));

    TerraformInheritOutput inheritOutput =
        helper.getSavedInheritOutput(provisionerIdentifier, DESTROY.name(), ambiance);

    return builder.workspace(inheritOutput.getWorkspace())
        .configFile(helper.getGitFetchFilesConfig(
            inheritOutput.getConfigFiles(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .tfModuleSourceInheritSSH(inheritOutput.isUseConnectorCredentials())
        .fileStoreConfigFiles(inheritOutput.getFileStorageConfigDTO() != null
                ? helper.prepareTerraformConfigFileInfo(inheritOutput.getFileStorageConfigDTO(), ambiance)
                : helper.getFileStoreFetchFilesConfig(
                    inheritOutput.getFileStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
        .varFileInfos(helper.prepareTerraformVarFileInfo(inheritOutput.getVarFileConfigs(), ambiance, true))
        .backendConfig(inheritOutput.getBackendConfig())
        .backendConfigFileInfo(
            helper.prepareTerraformBackendConfigFileInfo(inheritOutput.getBackendConfigurationFileConfig(), ambiance))
        .targets(inheritOutput.getTargets())
        .saveTerraformStateJson(false)
        .saveTerraformHumanReadablePlan(false)
        .encryptionConfig(inheritOutput.getEncryptionConfig())
        .encryptedTfPlan(inheritOutput.getEncryptedTfPlan())
        .planName(inheritOutput.getPlanName())
        .environmentVariables(
            inheritOutput.getEnvironmentVariables() == null ? new HashMap<>() : inheritOutput.getEnvironmentVariables())
        .encryptDecryptPlanForHarnessSMOnManager(
            helper.tfPlanEncryptionOnManager(accountId, inheritOutput.getEncryptionConfig()))
        .timeoutInMillis(
            StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
        .useOptimizedTfPlan(true)
        .skipColorLogs(cdFeatureFlagHelper.isEnabled(accountId, CDS_TF_TG_SKIP_ERROR_LOGS_COLORING));
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilderInheritFromApply(
      Ambiance ambiance, TerraformDestroyStepParameters stepParameters, StepBaseParameters stepElementParameters) {
    log.info("Getting the Last Apply Task for the Destroy Step");
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder().taskType(TFTaskType.DESTROY);
    builder.terraformCommandUnit(TerraformCommandUnit.Destroy);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance);
    builder.entityId(entityId);
    builder.currentStateFileId(helper.getLatestFileId(entityId));

    builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));

    TerraformConfig terraformConfig = helper.getLastSuccessfulApplyConfig(stepParameters, ambiance);

    builder.workspace(terraformConfig.getWorkspace())
        .varFileInfos(helper.prepareTerraformVarFileInfo(terraformConfig.getVarFileConfigs(), ambiance, true))
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
        .skipColorLogs(cdFeatureFlagHelper.isEnabled(accountId, CDS_TF_TG_SKIP_ERROR_LOGS_COLORING))
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

    ParameterField<Boolean> skipTerraformRefreshCommand = stepParameters.getConfiguration().getIsSkipTerraformRefresh();
    builder.skipTerraformRefresh(ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommand));
    return builder;
  }

  @VisibleForTesting
  @NotNull
  Map<String, String> getGitRevisionsOutput(TerraformDestroyStepParameters parameters,
      TerraformTaskNGResponse terraformTaskNGResponse, TerraformPassThroughData passThroughData) {
    Map<String, String> outputKeys = new HashMap<>();
    if (isNotEmpty(terraformTaskNGResponse.getCommitIdForConfigFilesMap())) {
      outputKeys.put(TF_CONFIG_FILES, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_CONFIG_FILES));
      outputKeys.put(
          TF_BACKEND_CONFIG_FILE, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_BACKEND_CONFIG_FILE));
    }
    if ((parameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
             TerraformStepConfigurationType.INLINE.getDisplayName())
            || parameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                TerraformStepConfigurationType.INHERIT_FROM_APPLY.getDisplayName()))
        && isNotEmpty(passThroughData.getFetchedCommitIdsMap())) {
      outputKeys.putAll(passThroughData.getFetchedCommitIdsMap());
    }
    return outputKeys;
  }
}
