/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;
import static io.harness.cdng.provision.terragrunt.TerragruntStepHelper.DEFAULT_TIMEOUT;
import static io.harness.provision.TerragruntConstants.APPLY;
import static io.harness.provision.TerragruntConstants.DESTROY;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;

import static software.wings.beans.TaskType.TERRAGRUNT_APPLY_TASK_NG;
import static software.wings.beans.TaskType.TERRAGRUNT_DESTROY_TASK_NG;

import static java.lang.String.format;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntApplyTaskParameters.TerragruntApplyTaskParametersBuilder;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntDestroyTaskParameters.TerragruntDestroyTaskParametersBuilder;
import io.harness.delegate.beans.terragrunt.response.AbstractTerragruntTaskResponse;
import io.harness.delegate.beans.terragrunt.response.TerragruntApplyTaskResponse;
import io.harness.delegate.beans.terragrunt.response.TerragruntDestroyTaskResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.UnitProgress;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerragruntRollbackStep extends CdTaskExecutable<AbstractTerragruntTaskResponse> {
  public static final StepType STEP_TYPE =
      TerragruntStepHelper.addStepType(ExecutionNodeType.TERRAGRUNT_ROLLBACK.getYamlType());

  @Inject private TerragruntStepHelper terragruntStepHelper;
  @Inject private TerragruntConfigDAL terragruntConfigDAL;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private AccountService accountService;

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TerragruntRollbackStepParameters stepParametersSpec = (TerragruntRollbackStepParameters) stepParameters.getSpec();
    log.info("Running Obtain Task for Terragrunt Rollback Step");
    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(stepParametersSpec.getProvisionerIdentifier());
    String entityId = terragruntStepHelper.generateFullIdentifier(provisionerIdentifier, ambiance);

    try (HIterator<TerragruntConfig> configIterator = terragruntConfigDAL.getIterator(ambiance, entityId)) {
      if (!configIterator.hasNext()) {
        return TaskRequest.newBuilder()
            .setSkipTaskRequest(
                SkipTaskRequest.newBuilder()
                    .setMessage(format(
                        "No successful Provisioning found with provisionerIdentifier: [%s]. Skipping terragrunt rollback.",
                        provisionerIdentifier))
                    .build())
            .build();
      }

      TerragruntConfig rollbackConfig = null;
      TerragruntConfig currentConfig = null;
      while (configIterator.hasNext()) {
        rollbackConfig = configIterator.next();
        if (rollbackConfig.getPipelineExecutionId().equals(
                AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))) {
          if (currentConfig == null) {
            currentConfig = rollbackConfig;
          }
        } else {
          // Found previous successful terragrunt config
          break;
        }
      }

      TerragruntRollbackTaskType rollbackTaskType;
      if (rollbackConfig == currentConfig) {
        log.info(
            format("No previous successful Terragrunt execution exists with the identifier : [%s], hence Destroying.",
                provisionerIdentifier));
        rollbackTaskType = TerragruntRollbackTaskType.DESTROY;
      } else {
        log.info(format(
            "Inheriting Terragrunt Config from last successful Terragrunt Pipeline Execution  %s", rollbackConfig));
        rollbackTaskType = TerragruntRollbackTaskType.APPLY;
      }

      cdExpressionResolver.updateExpressions(ambiance, rollbackConfig);
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TERRAGRUNT_CONFIG,
          TerragruntConfigSweepingOutput.builder()
              .terragruntConfig(rollbackConfig)
              .rollbackTaskType(rollbackTaskType)
              .build(),
          StepOutcomeGroup.STEP.name());

      List<String> commandUnitsList = new ArrayList<>();
      commandUnitsList.add(FETCH_CONFIG_FILES);

      if (TerragruntRollbackTaskType.APPLY == rollbackTaskType) {
        TerragruntApplyTaskParametersBuilder<?, ?> builderApply =
            createApplyTaskParameters(rollbackConfig, ambiance, stepParameters, provisionerIdentifier);
        commandUnitsList.add(APPLY);
        return prepareCDTaskRequest(ambiance, builderApply.build(), stepParameters, stepParametersSpec,
            commandUnitsList, TERRAGRUNT_APPLY_TASK_NG);
      } else {
        TerragruntDestroyTaskParametersBuilder<?, ?> builderDestroy =
            createDestroyTaskParameters(rollbackConfig, ambiance, stepParameters, provisionerIdentifier);
        commandUnitsList.add(DESTROY);
        return prepareCDTaskRequest(ambiance, builderDestroy.build(), stepParameters, stepParametersSpec,
            commandUnitsList, TERRAGRUNT_DESTROY_TASK_NG);
      }
    }
  }

  private TaskRequest prepareCDTaskRequest(Ambiance ambiance, Object parameters,
      StepElementParameters stepElementParameters, TerragruntRollbackStepParameters stepParameters,
      List<String> commandUnitsList, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(taskType.name())
                            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {parameters})
                            .build();

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer, commandUnitsList,
        taskType.getDisplayName(), TaskSelectorYaml.toTaskSelector(stepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<AbstractTerragruntTaskResponse> responseDataSupplier) throws Exception {
    log.info("Handling Task Result With Security Context for Terragrunt Rollback Step");
    StepResponse stepResponse = null;
    List<UnitProgress> unitProgresses = null;
    String stateFileId = null;

    try {
      StepResponseBuilder stepResponseBuilder = StepResponse.builder();
      AbstractTerragruntTaskResponse response = responseDataSupplier.get();
      if (response instanceof TerragruntDestroyTaskResponse) {
        TerragruntDestroyTaskResponse destroyTaskResponse = (TerragruntDestroyTaskResponse) response;
        stateFileId = destroyTaskResponse.getStateFileId();
        unitProgresses = destroyTaskResponse.getUnitProgressData() == null
            ? Collections.emptyList()
            : destroyTaskResponse.getUnitProgressData().getUnitProgresses();
      }

      if (response instanceof TerragruntApplyTaskResponse) {
        TerragruntApplyTaskResponse applyTaskResponse = (TerragruntApplyTaskResponse) response;
        stateFileId = applyTaskResponse.getStateFileId();
        unitProgresses = applyTaskResponse.getUnitProgressData() == null
            ? Collections.emptyList()
            : applyTaskResponse.getUnitProgressData().getUnitProgresses();
      }

      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.TERRAGRUNT_CONFIG));
      TerragruntConfigSweepingOutput rollbackConfigOutput =
          (TerragruntConfigSweepingOutput) optionalSweepingOutput.getOutput();
      TerragruntConfig rollbackConfig = rollbackConfigOutput.getTerragruntConfig();

      if (stateFileId != null) {
        terragruntStepHelper.updateParentEntityIdAndVersion(rollbackConfig.getEntityId(), stateFileId);
      }

      if (TerragruntRollbackTaskType.APPLY == rollbackConfigOutput.getRollbackTaskType()) {
        terragruntStepHelper.saveTerragruntConfig(rollbackConfig, ambiance);
      } else {
        terragruntConfigDAL.clearTerragruntConfig(ambiance, rollbackConfig.getEntityId());
      }

      stepResponseBuilder.unitProgressList(unitProgresses);
      stepResponseBuilder.status(Status.SUCCEEDED);
      stepResponse = stepResponseBuilder.build();
      return stepResponse;

    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }
  }

  private TerragruntApplyTaskParametersBuilder<?, ?> createApplyTaskParameters(TerragruntConfig terragruntConfig,
      Ambiance ambiance, StepElementParameters stepParameters, String provisionerIdentifier) {
    TerragruntApplyTaskParametersBuilder<?, ?> builder = TerragruntApplyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String entityId = terragruntStepHelper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.accountId(accountId)
        .entityId(entityId)
        .tgModuleSourceInheritSSH(terragruntConfig.isUseConnectorCredentials())
        .stateFileId(terragruntStepHelper.getLatestFileId(entityId))
        .workspace(terragruntConfig.getWorkspace())
        .configFilesStore(terragruntStepHelper.getGitFetchFilesConfig(
            terragruntConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(
            terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(terragruntConfig.getVarFileConfigs(), ambiance))
        .backendFilesStore(
            terragruntStepHelper.getBackendConfigFromTgConfig(terragruntConfig.getBackendConfigFile(), ambiance))
        .runConfiguration(terragruntConfig.getRunConfiguration())
        .targets(terragruntConfig.getTargets())
        .envVars(terragruntConfig.environmentVariables)
        .encryptedDataDetailList(terragruntStepHelper.getEncryptionDetailsFromTgInheritConfig(
            terragruntConfig.getConfigFiles().toGitStoreConfig(), terragruntConfig.getBackendConfigFile(),
            terragruntConfig.getVarFileConfigs(), ambiance))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT));
    builder.build();

    return builder;
  }

  private TerragruntDestroyTaskParametersBuilder<?, ?> createDestroyTaskParameters(TerragruntConfig terragruntConfig,
      Ambiance ambiance, StepElementParameters stepParameters, String provisionerIdentifier) {
    TerragruntDestroyTaskParametersBuilder<?, ?> builder = TerragruntDestroyTaskParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String entityId = terragruntStepHelper.generateFullIdentifier(provisionerIdentifier, ambiance);
    builder.accountId(accountId)
        .entityId(entityId)
        .tgModuleSourceInheritSSH(terragruntConfig.isUseConnectorCredentials())
        .stateFileId(terragruntStepHelper.getLatestFileId(entityId))
        .workspace(terragruntConfig.getWorkspace())
        .configFilesStore(terragruntStepHelper.getGitFetchFilesConfig(
            terragruntConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerragruntStepHelper.TG_CONFIG_FILES))
        .varFiles(
            terragruntStepHelper.toStoreDelegateVarFilesFromTgConfig(terragruntConfig.getVarFileConfigs(), ambiance))
        .backendFilesStore(
            terragruntStepHelper.getBackendConfigFromTgConfig(terragruntConfig.getBackendConfigFile(), ambiance))
        .runConfiguration(terragruntConfig.getRunConfiguration())
        .targets(terragruntConfig.getTargets())
        .envVars(terragruntConfig.environmentVariables)
        .encryptedDataDetailList(terragruntStepHelper.getEncryptionDetailsFromTgInheritConfig(
            terragruntConfig.getConfigFiles().toGitStoreConfig(), terragruntConfig.getBackendConfigFile(),
            terragruntConfig.getVarFileConfigs(), ambiance))
        .timeoutInMillis(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), DEFAULT_TIMEOUT));
    builder.build();

    return builder;
  }

  @Override
  public Class getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // no connectors/secret managers to validate
  }
}
