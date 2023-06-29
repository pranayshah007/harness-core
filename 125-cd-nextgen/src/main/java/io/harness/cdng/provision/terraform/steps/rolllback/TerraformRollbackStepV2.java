/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.steps.rolllback;

import static java.lang.String.format;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.TerraformConfigDAL;
import io.harness.cdng.provision.terraform.TerraformConfigHelper;
import io.harness.cdng.provision.terraform.TerraformPassThroughData;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.StringOutcome;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformRollbackStepV2 extends CdTaskChainExecutable {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_ROLLBACK_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformConfigHelper terraformConfigHelper;
  @Inject private TerraformStepHelper terraformStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private StepHelper stepHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject public TerraformConfigDAL terraformConfigDAL;
  @Inject private AccountService accountService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TerraformRollbackStepParameters stepParametersSpec = (TerraformRollbackStepParameters) stepParameters.getSpec();
    log.info("Running Obtain Inline Task for the Rollback Step");
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParametersSpec.getProvisionerIdentifier());
    String entityId = terraformStepHelper.generateFullIdentifier(provisionerIdentifier, ambiance);
    try (HIterator<TerraformConfig> configIterator = terraformConfigHelper.getIterator(ambiance, entityId)) {
      if (!configIterator.hasNext()) {
        return TaskChainResponse.builder()
            .chainEnd(true)
            .passThroughData(TerraformPassThroughData.builder().skipTerraformRollback(true).build())
            .build();
      }

      TerraformConfig rollbackConfig = null;
      TerraformConfig currentConfig = null;
      while (configIterator.hasNext()) {
        rollbackConfig = configIterator.next();

        if (rollbackConfig.getPipelineExecutionId().equals(
                AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))) {
          if (currentConfig == null) {
            currentConfig = rollbackConfig;
          }
        } else {
          // Found previous successful terraform config
          break;
        }
      }
      StringBuilder rollbackMessage = new StringBuilder();
      TFTaskType tfTaskType;
      if (rollbackConfig == currentConfig) {
        rollbackMessage.append(
            format("No previous successful Terraform execution exists with the identifier : [%s], hence Destroying.",
                provisionerIdentifier));
        tfTaskType = TFTaskType.DESTROY;
      } else {
        tfTaskType = TFTaskType.APPLY;
        rollbackMessage.append("Inheriting Terraform Config from last successful Terraform Execution : ");
        rollbackMessage.append(prepareExecutionUrl(rollbackConfig.getPipelineExecutionId(), ambiance));
      }

      ExpressionEvaluatorUtils.updateExpressions(
          rollbackConfig, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TERRAFORM_CONFIG,
          TerraformConfigSweepingOutput.builder().terraformConfig(rollbackConfig).tfTaskType(tfTaskType).build(),
          StepOutcomeGroup.STEP.name());

      List<TerraformVarFileInfo> varFilesInfo =
          terraformStepHelper.prepareTerraformVarFileInfo(rollbackConfig.getVarFileConfigs(), ambiance);

      boolean hasGitVarFiles = terraformStepHelper.hasGitVarFiles(varFilesInfo);
      boolean hasS3VarFiles = terraformStepHelper.hasS3VarFiles(varFilesInfo);

      TerraformTaskNGParametersBuilder builder =
          getTerraformTaskNgBuilder(ambiance, rollbackConfig, tfTaskType, entityId, stepParametersSpec, stepParameters);

      TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                              .hasGitFiles(hasGitVarFiles)
                                                              .hasS3Files(hasS3VarFiles)
                                                              .terraformTaskNGParametersBuilder(builder)
                                                              .build();

      if (hasGitVarFiles || hasS3VarFiles) {
        return terraformStepHelper.fetchRemoteVarFiles(terraformPassThroughData, varFilesInfo, ambiance, stepParameters,
            TerraformCommandUnit.Rollback.name(), stepParametersSpec.getDelegateSelectors());
      }

      return terraformStepHelper.executeTerraformTask(builder.build(), stepParameters, ambiance,
          terraformPassThroughData, stepParametersSpec.getDelegateSelectors(), TerraformCommandUnit.Rollback.name());
    }
  }

  public TerraformTaskNGParametersBuilder getTerraformTaskNgBuilder(Ambiance ambiance, TerraformConfig rollbackConfig,
      TFTaskType tfTaskType, String entityId, TerraformRollbackStepParameters stepParametersSpec,
      StepElementParameters stepParameters) {
    TerraformTaskNGParametersBuilder builder =
        TerraformTaskNGParameters.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .currentStateFileId(terraformStepHelper.getLatestFileId(entityId))
            .taskType(tfTaskType)
            .terraformCommandUnit(TerraformCommandUnit.Rollback)
            .entityId(entityId)
            .workspace(rollbackConfig.getWorkspace())
            .varFileInfos(terraformStepHelper.prepareTerraformVarFileInfo(rollbackConfig.getVarFileConfigs(), ambiance))
            .backendConfigFileInfo(terraformStepHelper.prepareTerraformBackendConfigFileInfo(
                rollbackConfig.getBackendConfigFileConfig(), ambiance));

    if (rollbackConfig.getConfigFiles() != null) {
      builder.configFile(terraformStepHelper.getGitFetchFilesConfig(
          rollbackConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES));
      builder.tfModuleSourceInheritSSH(rollbackConfig.isUseConnectorCredentials());
    }
    if (rollbackConfig.getFileStoreConfig() != null) {
      builder.fileStoreConfigFiles(
          terraformStepHelper.prepareTerraformConfigFileInfo(rollbackConfig.getFileStoreConfig(), ambiance));
    }

    builder.isTerraformCloudCli(rollbackConfig.isTerraformCloudCli());

    builder.terraformCommandFlags(terraformStepHelper.getTerraformCliFlags(stepParametersSpec.getCommandFlags()));

    builder.backendConfig(rollbackConfig.getBackendConfig())
        .targets(rollbackConfig.getTargets())
        .environmentVariables(rollbackConfig.getEnvironmentVariables() == null
                ? new HashMap<>()
                : rollbackConfig.getEnvironmentVariables())
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT));

    ParameterField<Boolean> skipTerraformRefreshCommandParameter = stepParametersSpec.getSkipRefreshCommand();

    boolean skipRefreshCommand =
        ParameterFieldHelper.getBooleanParameterFieldValue(skipTerraformRefreshCommandParameter);

    builder.skipTerraformRefresh(skipRefreshCommand);
    return builder;
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    TerraformRollbackStepParameters stepParameters = (TerraformRollbackStepParameters) stepElementParameters.getSpec();

    return terraformStepHelper.executeNextLink(ambiance, responseSupplier, passThroughData,
        stepParameters.getDelegateSelectors(), stepElementParameters, TerraformCommandUnit.Rollback.name());
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for the Rollback Step");
    StepResponse stepResponse = null;

    if (passThroughData instanceof StepExceptionPassThroughData) {
      StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
      return terraformStepHelper.handleStepExceptionFailure(stepExceptionPassThroughData);
    }

    try {
      stepResponse = generateTerraformStepResponse(ambiance, responseDataSupplier, passThroughData, stepParameters);
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }
    return stepResponse;
  }

  private StepResponse generateTerraformStepResponse(Ambiance ambiance,
      ThrowingSupplier<ResponseData> responseDataSupplier, PassThroughData passThroughData,
      StepElementParameters stepParameters) throws Exception {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    TerraformPassThroughData terraformPassThroughData = (TerraformPassThroughData) passThroughData;

    if (terraformPassThroughData.skipTerraformRollback()) {
      TerraformRollbackStepParameters stepParametersSpec = (TerraformRollbackStepParameters) stepParameters.getSpec();
      String provisionerIdentifier =
          ParameterFieldHelper.getParameterFieldValue(stepParametersSpec.getProvisionerIdentifier());
      String rollbackSkipReason =
          format("No successful Provisioning found with provisionerIdentifier: [%s]. Skipping rollback.",
              provisionerIdentifier);
      stepResponseBuilder.status(Status.SKIPPED);
      stepResponseBuilder.unitProgressList(Collections.emptyList());
      stepResponseBuilder.failureInfo(FailureInfo.newBuilder().setErrorMessage(rollbackSkipReason).build())
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name("skipOutcome")
                           .outcome(StringOutcome.builder().message(rollbackSkipReason).build())
                           .build())
          .build();
      return stepResponseBuilder.build();
    }

    TerraformTaskNGResponse taskResponse = (TerraformTaskNGResponse) responseDataSupplier.get();
    List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : taskResponse.getUnitProgressData().getUnitProgresses();

    boolean responseHasFetchFiles = false;
    if (taskResponse.getUnitProgressData() != null) {
      responseHasFetchFiles = taskResponse.getUnitProgressData().getUnitProgresses().stream().anyMatch(
          unitProgress -> unitProgress.getUnitName().equalsIgnoreCase("Fetch Files"));
    }

    if (!responseHasFetchFiles && (terraformPassThroughData.hasGitFiles() || terraformPassThroughData.hasS3Files())) {
      unitProgresses.addAll(terraformPassThroughData.getUnitProgresses());
    }
    stepResponseBuilder.unitProgressList(unitProgresses)
        .status(StepUtils.getStepStatus(taskResponse.getCommandExecutionStatus()));

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.TERRAFORM_CONFIG));
    TerraformConfigSweepingOutput rollbackConfigOutput =
        (TerraformConfigSweepingOutput) optionalSweepingOutput.getOutput();
    TerraformConfig rollbackConfig = rollbackConfigOutput.getTerraformConfig();

    if (taskResponse.getStateFileId() != null) {
      terraformStepHelper.updateParentEntityIdAndVersion(rollbackConfig.getEntityId(), taskResponse.getStateFileId());
    }

    if (taskResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      if (rollbackConfigOutput.getTfTaskType() == TFTaskType.APPLY) {
        terraformStepHelper.saveTerraformConfig(rollbackConfig, ambiance);
      } else {
        terraformConfigDAL.clearTerraformConfig(ambiance, rollbackConfig.getEntityId());
      }
    }

    return stepResponseBuilder.build();
  }

  private String prepareExecutionUrl(String pipelineExecutionId, Ambiance ambiance) {
    // TODO: prepare execution url
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // no connectors/secret managers to validate
  }
}
