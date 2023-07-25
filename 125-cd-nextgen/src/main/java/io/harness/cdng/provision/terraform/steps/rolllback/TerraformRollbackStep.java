/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.steps.rolllback;
import static java.lang.String.format;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.TerraformConfigDAL;
import io.harness.cdng.provision.terraform.TerraformConfigHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformRollbackStep extends CdTaskExecutable<TerraformTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private TerraformConfigHelper terraformConfigHelper;
  @Inject private TerraformStepHelper terraformStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private StepHelper stepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;

  @Inject public TerraformConfigDAL terraformConfigDAL;
  @Inject private AccountService accountService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TerraformRollbackStepParameters stepParametersSpec = (TerraformRollbackStepParameters) stepParameters.getSpec();
    log.info("Running Obtain Inline Task for the Rollback Step");
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParametersSpec.getProvisionerIdentifier());
    String entityId = terraformStepHelper.generateFullIdentifier(provisionerIdentifier, ambiance);
    try (HIterator<TerraformConfig> configIterator = terraformConfigHelper.getIterator(ambiance, entityId)) {
      if (!configIterator.hasNext()) {
        return TaskRequest.newBuilder()
            .setSkipTaskRequest(
                SkipTaskRequest.newBuilder()
                    .setMessage(
                        format("No successful Provisioning found with provisionerIdentifier: [%s]. Skipping rollback.",
                            provisionerIdentifier))
                    .build())
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

      cdExpressionResolver.updateExpressions(ambiance, rollbackConfig);
      // TODO:  log rollback message
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TERRAFORM_CONFIG,
          TerraformConfigSweepingOutput.builder().terraformConfig(rollbackConfig).tfTaskType(tfTaskType).build(),
          StepOutcomeGroup.STEP.name());

      TerraformTaskNGParametersBuilder builder =
          TerraformTaskNGParameters.builder()
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .currentStateFileId(terraformStepHelper.getLatestFileId(entityId))
              .taskType(tfTaskType)
              .terraformCommandUnit(TerraformCommandUnit.Rollback)
              .entityId(entityId)
              .workspace(rollbackConfig.getWorkspace())
              .varFileInfos(
                  terraformStepHelper.prepareTerraformVarFileInfo(rollbackConfig.getVarFileConfigs(), ambiance, false))
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

      TerraformTaskNGParameters terraformTaskNGParameters = builder.build();
      TaskData taskData =
          TaskData.builder()
              .async(true)
              .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
              .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
              .parameters(new Object[] {terraformTaskNGParameters})
              .build();

      ParameterField<List<TaskSelectorYaml>> delegateSelectors = stepParametersSpec.getDelegateSelectors();
      return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          Collections.singletonList(TerraformCommandUnit.Rollback.name()),
          terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
          TaskSelectorYaml.toTaskSelector(delegateSelectors), stepHelper.getEnvironmentType(ambiance));
    }
  }

  private String prepareExecutionUrl(String pipelineExecutionId, Ambiance ambiance) {
    // TODO: prepare execution url
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // no connectors/secret managers to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<TerraformTaskNGResponse> responseDataSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for the Rollback Step");
    StepResponse stepResponse = null;

    try {
      stepResponse = generateStepResponse(ambiance, responseDataSupplier);
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }

    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, ThrowingSupplier<TerraformTaskNGResponse> responseDataSupplier) throws Exception {
    TerraformTaskNGResponse taskResponse = responseDataSupplier.get();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : taskResponse.getUnitProgressData().getUnitProgresses();
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

    Map<String, String> outputKeys = terraformStepHelper.getRevisionsMap(
        rollbackConfig.getVarFileConfigs(), taskResponse.getCommitIdForConfigFilesMap());
    terraformStepHelper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, outputKeys);
    return stepResponseBuilder.build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}