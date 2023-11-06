/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TF_TG_SKIP_ERROR_LOGS_COLORING;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.ProvisionerOutputHelper;
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
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
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
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
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
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;

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

    if (stepParametersSpec.getConfiguration().getEncryptOutputSecretManager() != null
        && !ParameterField.isBlank(
            stepParametersSpec.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef())) {
      String secretManagerRef = ParameterFieldHelper.getParameterFieldValue(
          stepParametersSpec.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef());

      IdentifierRef secretManagerIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);
      EntityDetail entityDetail =
          EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(secretManagerIdentifierRef).build();
      entityDetailList.add(entityDetail);
      helper.validateSecretManager(ambiance, secretManagerIdentifierRef);
    }

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters StepBaseParameters, StepInputPackage inputPackage) {
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) StepBaseParameters.getSpec();
    log.info("Starting execution Obtain Task after Rbac for the Apply Step");
    helper.validateApplyStepParamsInline(stepParameters);

    if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
            TerraformStepConfigurationType.INLINE.getDisplayName())) {
      return obtainInlineTask(ambiance, stepParameters, StepBaseParameters);
    } else if (stepParameters.getConfiguration().getType().getDisplayName().equalsIgnoreCase(
                   TerraformStepConfigurationType.INHERIT_FROM_PLAN.getDisplayName())) {
      return obtainInheritedTask(ambiance, stepParameters, StepBaseParameters);
    } else {
      throw new InvalidRequestException(String.format(
          "Unknown configuration Type: [%s]", stepParameters.getConfiguration().getType().getDisplayName()));
    }
  }

  private TaskRequest obtainInlineTask(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepBaseParameters StepBaseParameters) {
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

    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.currentStateFileId(helper.getLatestFileId(entityId))
            .taskType(TFTaskType.APPLY)
            .terraformCommand(TerraformCommand.APPLY)
            .terraformCommandUnit(TerraformCommandUnit.Apply)
            .entityId(entityId)
            .tfModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
                stepParameters.getConfiguration().getSpec().getConfigFiles(), StepBaseParameters.getType()))
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
                StepUtils.getTimeoutMillis(StepBaseParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .useOptimizedTfPlan(true)
            .isTerraformCloudCli(isTerraformCloudCli)
            .skipTerraformRefresh(skipRefreshCommand)
            .providerCredentialDelegateInfo(
                helper.getProviderCredentialDelegateInfo(spec.getProviderCredential(), ambiance))
            .skipColorLogs(cdFeatureFlagHelper.isEnabled(accountId, CDS_TF_TG_SKIP_ERROR_LOGS_COLORING))
            .skipStateStorage(ParameterFieldHelper.getBooleanParameterFieldValue(
                stepParameters.getConfiguration().getSkipStateStorage()))
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(StepBaseParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Apply.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainInheritedTask(
      Ambiance ambiance, TerraformApplyStepParameters stepParameters, StepBaseParameters StepBaseParameters) {
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

    if (inheritOutput.getProviderCredentialConfig() != null) {
      TerraformProviderCredential terraformProviderCredential =
          helper.toTerraformProviderCredential(inheritOutput.getProviderCredentialConfig());
      builder.providerCredentialDelegateInfo(
          helper.getProviderCredentialDelegateInfo(terraformProviderCredential, ambiance));
    }

    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.workspace(inheritOutput.getWorkspace())
            .configFile(helper.getGitFetchFilesConfig(
                inheritOutput.getConfigFiles(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .tfModuleSourceInheritSSH(inheritOutput.isUseConnectorCredentials())
            .fileStoreConfigFiles(inheritOutput.getFileStorageConfigDTO() != null
                    ? helper.prepareTerraformConfigFileInfo(inheritOutput.getFileStorageConfigDTO(), ambiance)
                    : helper.getFileStoreFetchFilesConfig(
                        inheritOutput.getFileStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .varFileInfos(helper.prepareTerraformVarFileInfo(inheritOutput.getVarFileConfigs(), ambiance, false))
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
                StepUtils.getTimeoutMillis(StepBaseParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .encryptDecryptPlanForHarnessSMOnManager(
                helper.tfPlanEncryptionOnManager(accountId, inheritOutput.getEncryptionConfig()))
            .useOptimizedTfPlan(true)
            .skipColorLogs(cdFeatureFlagHelper.isEnabled(accountId, CDS_TF_TG_SKIP_ERROR_LOGS_COLORING))
            .skipStateStorage(inheritOutput.isSkipStateStorage())
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(StepBaseParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Apply.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters StepBaseParameters, ThrowingSupplier<TerraformTaskNGResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the Apply Step");
    TerraformApplyStepParameters stepParameters = (TerraformApplyStepParameters) StepBaseParameters.getSpec();

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
      StepResponseBuilder stepResponseBuilder, TerraformApplyOutcome terraformApplyOutcome) {
    stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                        .name(OutcomeExpressionConstants.OUTPUT)
                                        .outcome(terraformApplyOutcome)
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
      addStepOutcome(ambiance, stepResponseBuilder, terraformTaskNGResponse.getOutputs(), stepParameters);
      helper.saveRollbackDestroyConfigInline(stepParameters, terraformTaskNGResponse, ambiance, null);
      if (!ParameterFieldHelper.getBooleanParameterFieldValue(
              stepParameters.getConfiguration().getSkipStateStorage())) {
        helper.updateParentEntityIdAndVersion(
            helper.generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
            terraformTaskNGResponse.getStateFileId());
      }
    }

    Map<String, String> outputKeys = helper.getRevisionsMap(stepParameters.getConfiguration().getSpec().getVarFiles(),
        terraformTaskNGResponse.getCommitIdForConfigFilesMap());
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);

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
      addStepOutcome(ambiance, stepResponseBuilder, terraformTaskNGResponse.getOutputs(), stepParameters);
      helper.saveRollbackDestroyConfigInherited(stepParameters, ambiance);
      TerraformInheritOutput inheritOutput = helper.getSavedInheritOutput(
          ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), APPLY.name(),
          ambiance);
      if (!inheritOutput.isSkipStateStorage()) {
        helper.updateParentEntityIdAndVersion(
            helper.generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance),
            terraformTaskNGResponse.getStateFileId());
      }
    }

    Map<String, String> outputKeys =
        helper.getRevisionsMap(Collections.emptyList(), terraformTaskNGResponse.getCommitIdForConfigFilesMap());
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);
    return stepResponseBuilder.build();
  }

  private void addStepOutcome(Ambiance ambiance, StepResponseBuilder stepResponseBuilder, String outputs,
      TerraformApplyStepParameters stepParameters) {
    TerraformApplyOutcome terraformApplyOutcome;
    if (stepParameters.getConfiguration().getEncryptOutputSecretManager() != null
        && !ParameterField.isBlank(
            stepParameters.getConfiguration().getEncryptOutputSecretManager().getOutputSecretManagerRef())) {
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
