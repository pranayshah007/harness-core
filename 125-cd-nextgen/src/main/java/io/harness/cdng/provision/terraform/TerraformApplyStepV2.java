/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_ENCRYPT_TERRAFORM_APPLY_JSON_OUTPUT;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_BACKEND_CONFIG_FILE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_CONFIG_FILES;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
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
import io.harness.plancreator.steps.common.StepElementParameters;
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
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TerraformApplyStepV2 extends CdTaskChainExecutable {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_APPLY_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;

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

      if (stepParametersSpec.getConfiguration().getEncryptOutputSecretManager() != null
          && stepParametersSpec.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef() != null
          && !ParameterField.isBlank(
              stepParametersSpec.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef())
          && cdFeatureFlagHelper.isEnabled(accountId, CDS_ENCRYPT_TERRAFORM_APPLY_JSON_OUTPUT)) {
        String secretManagerRef = ParameterFieldHelper.getParameterFieldValue(
            stepParametersSpec.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef());

        IdentifierRef secretManagerIdentifierRef =
            IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);
        EntityDetail entityDetailSM =
            EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(secretManagerIdentifierRef).build();
        entityDetailList.add(entityDetailSM);
        helper.validateSecretManager(ambiance, secretManagerIdentifierRef);
      }
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  private void addStepOutcomeToStepResponse(
      StepResponseBuilder stepResponseBuilder, TerraformApplyOutcome terraformApplyOutcome) {
    stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                        .name(OutcomeExpressionConstants.OUTPUT)
                                        .outcome(terraformApplyOutcome)
                                        .build());
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) stepElementParameters.getSpec();
    log.info("Starting execution for the Apply Step");
    String applyConfigurationType = stepParameters.getConfiguration().getType().getDisplayName();

    if (TerraformStepConfigurationType.INLINE.getDisplayName().equalsIgnoreCase(applyConfigurationType)) {
      return handleApplyInlineStartChain(ambiance, stepParameters, stepElementParameters);
    } else if (TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName().equalsIgnoreCase(
                   applyConfigurationType)) {
      return handleApplyInheritPlanStartChain(ambiance, stepParameters, stepElementParameters);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) stepElementParameters.getSpec();

    return helper.executeNextLink(ambiance, responseSupplier, passThroughData, stepParameters.getDelegateSelectors(),
        stepElementParameters, TerraformCommandUnit.Apply.name());
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for the Apply Step");

    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return helper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }

    TerraformPassThroughData terraformPassThroughData = (TerraformPassThroughData) passThroughData;
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) stepElementParameters.getSpec();

    if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      return handleTaskResultApplyInline(ambiance, terraformPassThroughData, stepParameters, responseSupplier);
    } else if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName())) {
      return handleTaskResultApplyInherited(ambiance, terraformPassThroughData, stepParameters, responseSupplier);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  private TaskChainResponse handleApplyInlineStartChain(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    helper.validateApplyStepConfigFilesInline(stepParameters);

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
          TerraformCommandUnit.Apply.name(), stepParameters.getDelegateSelectors());
    }

    return helper.executeTerraformTask(builder.build(), stepElementParameters, ambiance, terraformPassThroughData,
        stepParameters.getDelegateSelectors(), TerraformCommandUnit.Apply.name());
  }

  private TaskChainResponse handleApplyInheritPlanStartChain(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    // When Apply Inherit from Plan no need to fetch remote var-files, as tfPlan from Plan step is applied.
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    TerraformTaskNGParametersBuilder builder =
        getTerraformTaskNGParametersBuilderInheritFromPlan(ambiance, stepParameters, stepElementParameters);
    terraformPassThroughData.setTerraformTaskNGParametersBuilder(builder);

    return helper.executeTerraformTask(builder.build(), stepElementParameters, ambiance, terraformPassThroughData,
        stepParameters.getDelegateSelectors(), TerraformCommandUnit.Apply.name());
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilderInline(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepElementParameters stepElementParameters) {
    log.info("Obtaining Inline Task for the Apply Step");
    boolean isTerraformCloudCli = stepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().getValue();

    ParameterField<Boolean> skipTerraformRefreshCommandParameter =
        stepParameters.getConfiguration().getIsSkipTerraformRefresh();
    boolean skipRefreshCommand =
        ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommandParameter);

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

    builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));

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
        .skipTerraformRefresh(skipRefreshCommand);
    return builder;
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskNGParametersBuilderInheritFromPlan(
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

    builder.terraformCommandFlags(helper.getTerraformCliFlags(stepParameters.getConfiguration().getCliOptions()));

    TerraformInheritOutput inheritOutput = helper.getSavedInheritOutput(provisionerIdentifier, APPLY.name(), ambiance);

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
        .timeoutInMillis(
            StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
        .encryptDecryptPlanForHarnessSMOnManager(
            helper.tfPlanEncryptionOnManager(accountId, inheritOutput.getEncryptionConfig()))
        .useOptimizedTfPlan(true);
  }

  private StepResponse handleTaskResultApplyInline(Ambiance ambiance, TerraformPassThroughData terraformPassThroughData,
      TerraformApplyStepParameters stepParameters, ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Handling Task Result Inline for the Apply Step");

    TerraformTaskNGResponse terraformTaskNGResponse = (TerraformTaskNGResponse) responseSupplier.get();

    StepResponseBuilder stepResponseBuilder =
        createStepResponseBuilder(terraformTaskNGResponse, terraformPassThroughData);
    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      helper.saveRollbackDestroyConfigInline(
          stepParameters, terraformTaskNGResponse, ambiance, terraformPassThroughData);
      addStepOutcome(ambiance, stepResponseBuilder, terraformTaskNGResponse.getOutputs(), stepParameters);
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }

    Map<String, String> outputKeys = helper.getRevisionsMap(terraformPassThroughData, terraformTaskNGResponse);
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);

    return stepResponseBuilder.build();
  }

  private StepResponse handleTaskResultApplyInherited(Ambiance ambiance,
      TerraformPassThroughData terraformPassThroughData, TerraformApplyStepParameters stepParameters,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Handling Task Result Inherited for the Apply Step");
    TerraformTaskNGResponse terraformTaskNGResponse = (TerraformTaskNGResponse) responseSupplier.get();

    StepResponseBuilder stepResponseBuilder =
        createStepResponseBuilder(terraformTaskNGResponse, terraformPassThroughData);
    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      helper.saveRollbackDestroyConfigInherited(stepParameters, ambiance);
      addStepOutcome(ambiance, stepResponseBuilder, terraformTaskNGResponse.getOutputs(), stepParameters);
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
    }

    Map<String, String> outputKeys = new HashMap<>();
    if (isNotEmpty(terraformTaskNGResponse.getCommitIdForConfigFilesMap())) {
      outputKeys.put(TF_CONFIG_FILES, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_CONFIG_FILES));
      outputKeys.put(
          TF_BACKEND_CONFIG_FILE, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_BACKEND_CONFIG_FILE));
    }
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);

    return stepResponseBuilder.build();
  }

  private StepResponseBuilder createStepResponseBuilder(
      TerraformTaskNGResponse terraformTaskNGResponse, TerraformPassThroughData terraformPassThroughData) {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    List<UnitProgress> unitProgresses = new ArrayList<>(terraformTaskNGResponse.getUnitProgressData() == null
            ? Collections.emptyList()
            : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses());

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
    return stepResponseBuilder;
  }

  private void addStepOutcome(Ambiance ambiance, StepResponseBuilder stepResponseBuilder, String outputs,
      TerraformApplyStepParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    TerraformApplyOutcome terraformApplyOutcome;
    if (stepParameters.getConfiguration().getEncryptOutputSecretManager() != null
        && stepParameters.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef() != null
        && !ParameterField.isBlank(
            stepParameters.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef())
        && cdFeatureFlagHelper.isEnabled(accountId, CDS_ENCRYPT_TERRAFORM_APPLY_JSON_OUTPUT)) {
      String secretManagerRef = ParameterFieldHelper.getParameterFieldValue(
          stepParameters.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef());
      String provisionerId = ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier());

      terraformApplyOutcome = new TerraformApplyOutcome(
          helper.encryptTerraformJsonOutput(outputs, ambiance, secretManagerRef, provisionerId));
    } else {
      terraformApplyOutcome = new TerraformApplyOutcome(helper.parseTerraformOutputs(outputs));
    }
    provisionerOutputHelper.saveProvisionerOutputByStepIdentifier(ambiance, terraformApplyOutcome);
    addStepOutcomeToStepResponse(stepResponseBuilder, terraformApplyOutcome);
  }
}
