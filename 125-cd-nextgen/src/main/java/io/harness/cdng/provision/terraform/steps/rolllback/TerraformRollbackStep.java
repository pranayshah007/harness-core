package io.harness.cdng.provision.terraform.steps.rolllback;

import static java.lang.String.format;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.TerraformConfigDAL;
import io.harness.cdng.provision.terraform.TerraformConfigHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.persistence.HIterator;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
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
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformRollbackStep extends TaskExecutableWithRollbackAndRbac<TerraformTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private KryoSerializer kryoSerializer;
  @Inject private TerraformConfigHelper terraformConfigHelper;
  @Inject private TerraformStepHelper terraformStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private StepHelper stepHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject public TerraformConfigDAL terraformConfigDAL;
  @Inject private AccountService accountService;

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TerraformRollbackStepParameters stepParametersSpec = (TerraformRollbackStepParameters) stepParameters.getSpec();
    log.info("Running Obtain Inline Task for the Rollback Step");
    String provisionerIdentifier = stepParametersSpec.getProvisionerIdentifier();
    String entityId =
        terraformStepHelper.generateFullIdentifier(stepParametersSpec.getProvisionerIdentifier(), ambiance);
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

        if (rollbackConfig.getPipelineExecutionId().equals(ambiance.getPlanExecutionId())) {
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
              .configFile(terraformStepHelper.getGitFetchFilesConfig(
                  rollbackConfig.getConfigFiles().toGitStoreConfig(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
              .varFileInfos(
                  terraformStepHelper.prepareTerraformVarFileInfo(rollbackConfig.getVarFileConfigs(), ambiance));

      builder.backendConfig(rollbackConfig.getBackendConfig())
          .targets(rollbackConfig.getTargets())
          .environmentVariables(rollbackConfig.getEnvironmentVariables())
          .timeoutInMillis(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT));

      TaskData taskData =
          TaskData.builder()
              .async(true)
              .taskType(TaskType.TERRAFORM_TASK_NG.name())
              .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
              .parameters(new Object[] {builder.build()})
              .build();

      ParameterField<List<String>> delegateSelectors = stepParametersSpec.getDelegateSelectors();

      List<TaskSelector> taskSelectors = StepUtils.getTaskSelectors(delegateSelectors);

      return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
          Collections.singletonList(TerraformCommandUnit.Rollback.name()), TaskType.TERRAFORM_TASK_NG.getDisplayName(),
          taskSelectors, stepHelper.getEnvironmentType(ambiance));
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
      stepResponse = generateStepResponse(ambiance, stepParameters, responseDataSupplier);
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }

    return stepResponse;
  }

  private StepResponse generateStepResponse(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<TerraformTaskNGResponse> responseDataSupplier) throws Exception {
    TerraformRollbackStepParameters stepParametersSpec = (TerraformRollbackStepParameters) stepParameters.getSpec();
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

    return stepResponseBuilder.build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
