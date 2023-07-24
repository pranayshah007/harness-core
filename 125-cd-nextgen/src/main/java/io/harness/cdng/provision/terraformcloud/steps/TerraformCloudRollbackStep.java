/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.steps;
import static io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome.OUTCOME_NAME;
import static io.harness.delegate.task.terraformcloud.TerraformCloudTaskType.ROLLBACK;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraformcloud.TerraformCloudConstants;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRollbackStepParameters;
import io.harness.cdng.provision.terraformcloud.TerraformCloudStepHelper;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfigDAL;
import io.harness.cdng.provision.terraformcloud.functor.TerraformCloudPolicyChecksJsonFunctor;
import io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.terraformcloud.RollbackType;
import io.harness.delegate.task.terraformcloud.TerraformCloudCommandUnit;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRollbackTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRollbackTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TerraformCloudRollbackStep extends CdTaskExecutable<TerraformCloudRollbackTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_CLOUD_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TerraformCloudStepHelper helper;
  @Inject private EncryptionHelper encryptionHelper;
  @Inject private TerraformCloudConfigDAL terraformCloudConfigDAL;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public Class getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Terraform Cloud Rollback Step");
    TerraformCloudRollbackStepParameters rollbackStepParameters =
        (TerraformCloudRollbackStepParameters) stepElementParameters.getSpec();

    String provisionerIdentifier =
        ParameterFieldHelper.getParameterFieldValue(rollbackStepParameters.getProvisionerIdentifier());
    String entityId = helper.generateFullIdentifier(provisionerIdentifier, ambiance);

    TerraformCloudConfig rollbackConfig =
        terraformCloudConfigDAL.getFirstTerraformCloudConfigForStage(ambiance, provisionerIdentifier);

    if (rollbackConfig == null) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder()
                  .setMessage(format(
                      "No successful Provisioning found with provisionerIdentifier: [%s]. Skipping terraform cloud rollback.",
                      provisionerIdentifier))
                  .build())
          .build();
    }

    log.info(format("Create Terraform cloud run with same config as run: %s", rollbackConfig.getLastSuccessfulRun()));
    TerraformCloudConnectorDTO terraformCloudConnector =
        helper.getTerraformCloudConnectorWithRef(rollbackConfig.getConnectorRef(), ambiance);

    TerraformCloudRollbackTaskParams terraformCloudTaskParamsImpl =
        TerraformCloudRollbackTaskParams.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .runId(rollbackConfig.getLastSuccessfulRun())
            .entityId(entityId)
            .terraformCloudConnectorDTO(terraformCloudConnector)
            .discardPendingRuns(
                ParameterFieldHelper.getBooleanParameterFieldValue(rollbackStepParameters.getDiscardPendingRuns()))
            .policyOverride(
                ParameterFieldHelper.getBooleanParameterFieldValue(rollbackStepParameters.getOverridePolicies()))
            .encryptionDetails(encryptionHelper.getEncryptionDetail(terraformCloudConnector.getCredential().getSpec(),
                AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
                AmbianceUtils.getProjectIdentifier(ambiance)))
            .message(ParameterFieldHelper.getParameterFieldValue(rollbackStepParameters.getMessage()))
            .rollbackType(rollbackConfig.getLastSuccessfulRun() == null ? RollbackType.DESTROY : RollbackType.APPLY)
            .workspace(rollbackConfig.getWorkspaceId())
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.TERRAFORM_CLOUD_TASK_NG.name())
                            .timeout(StepUtils.getTimeoutMillis(
                                stepElementParameters.getTimeout(), TerraformCloudConstants.DEFAULT_TIMEOUT))
                            .parameters(new Object[] {terraformCloudTaskParamsImpl})
                            .build();

    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        List.of(TerraformCloudCommandUnit.FETCH_LAST_APPLIED_RUN.getDisplayName(),
            TerraformCloudCommandUnit.PLAN.getDisplayName(), TerraformCloudCommandUnit.POLICY_CHECK.getDisplayName(),
            TerraformCloudCommandUnit.APPLY.getDisplayName()),
        format("%s : %s", TaskType.TERRAFORM_CLOUD_TASK_NG.getDisplayName(), ROLLBACK.getDisplayName()),
        TaskSelectorYaml.toTaskSelector(rollbackStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters,
      ThrowingSupplier<TerraformCloudRollbackTaskResponse> responseSupplier) throws Exception {
    log.info("Handling Task result with Security Context for the Terraform Cloud Rollback Step");
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformCloudRollbackTaskResponse terraformCloudRunTaskResponse = responseSupplier.get();
    List<UnitProgress> unitProgresses = terraformCloudRunTaskResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformCloudRunTaskResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    switch (terraformCloudRunTaskResponse.getCommandExecutionStatus()) {
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
        throw new InvalidRequestException("Unhandled type CommandExecutionStatus: "
                + terraformCloudRunTaskResponse.getCommandExecutionStatus().name(),
            WingsException.USER);
    }

    if (CommandExecutionStatus.SUCCESS == terraformCloudRunTaskResponse.getCommandExecutionStatus()) {
      String runId = terraformCloudRunTaskResponse.getRunId();
      String provisionerIdentifier = ParameterFieldHelper.getParameterFieldValue(
          ((TerraformCloudRollbackStepParameters) stepElementParameters.getSpec()).getProvisionerIdentifier());
      helper.saveTerraformCloudPlanExecutionDetails(
          ambiance, null, terraformCloudRunTaskResponse.getPolicyChecksJsonFileId(), provisionerIdentifier, null);
      stepResponseBuilder.stepOutcome(
          StepOutcome.builder()
              .name(OUTCOME_NAME)
              .outcome(
                  TerraformCloudRunOutcome.builder()
                      .detailedExitCode(terraformCloudRunTaskResponse.getDetailedExitCode())
                      .runId(runId)
                      .policyChecksFilePath(terraformCloudRunTaskResponse.getPolicyChecksJsonFileId() != null
                                  && provisionerIdentifier != null
                              ? TerraformCloudPolicyChecksJsonFunctor.getExpression(provisionerIdentifier)
                              : null)
                      .outputs(new HashMap<>(helper.parseTerraformOutputs(terraformCloudRunTaskResponse.getTfOutput())))
                      .build())
              .build());
    }
    return stepResponseBuilder.build();
  }
}
