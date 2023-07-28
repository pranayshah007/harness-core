/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.steps;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_DESTROY;
import static io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome.OUTCOME_NAME;
import static io.harness.delegate.task.terraformcloud.TerraformCloudTaskType.GET_LAST_APPLIED_RUN;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraform.executions.RunDetails;
import io.harness.cdng.provision.terraformcloud.TerraformCloudConstants;
import io.harness.cdng.provision.terraformcloud.TerraformCloudParamsMapper;
import io.harness.cdng.provision.terraformcloud.TerraformCloudPassThroughData;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunSpecParameters;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunStepParameters;
import io.harness.cdng.provision.terraformcloud.TerraformCloudRunType;
import io.harness.cdng.provision.terraformcloud.TerraformCloudStepHelper;
import io.harness.cdng.provision.terraformcloud.functor.TerraformCloudPlanJsonFunctor;
import io.harness.cdng.provision.terraformcloud.functor.TerraformCloudPolicyChecksJsonFunctor;
import io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.terraformcloud.TerraformCloudCommandUnit;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetLastAppliedTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudDelegateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudGetLastAppliedTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndDestroyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanOnlyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRefreshTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TerraformCloudRunStep extends TaskChainExecutableWithRollbackAndRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_CLOUD_RUN.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TerraformCloudStepHelper helper;
  @Inject private TerraformCloudParamsMapper paramsMapper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    TerraformCloudRunStepParameters runStepParameters = (TerraformCloudRunStepParameters) stepParameters.getSpec();

    // Validate connectors
    List<EntityDetail> entityDetailList =
        runStepParameters.getSpec()
            .extractConnectorRefs()
            .stream()
            .map(connectorRef
                -> EntityDetail.builder()
                       .type(EntityType.CONNECTORS)
                       .entityRef(IdentifierRefHelper.getIdentifierRef(
                           connectorRef.getValue(), accountId, orgIdentifier, projectIdentifier))
                       .build())
            .collect(Collectors.toList());

    if (!entityDetailList.isEmpty()) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
    }
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Terraform Cloud Run Step");
    TerraformCloudRunStepParameters runStepParameters =
        (TerraformCloudRunStepParameters) stepElementParameters.getSpec();
    TerraformCloudRunSpecParameters runSpec = runStepParameters.getSpec();

    runSpec.validate();
    TerraformCloudRunType runType = runSpec.getType();
    TerraformCloudTaskParams terraformCloudTaskParams;
    if (runType == PLAN_AND_APPLY || runType == PLAN_AND_DESTROY || runType == APPLY) {
      // If we are doing any previsioning get the latest applied run that might be used for rollback
      terraformCloudTaskParams = getTerraformCloudGetLatestAppliedParams(runSpec, ambiance);
    } else {
      terraformCloudTaskParams = getTerraformCloudTaskParams(ambiance, runStepParameters);
    }
    return getTaskChainResponse(stepElementParameters, terraformCloudTaskParams, runStepParameters, ambiance);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    TerraformCloudRunStepParameters runStepParameters =
        (TerraformCloudRunStepParameters) stepElementParameters.getSpec();
    TerraformCloudDelegateTaskResponse delegateTaskResponse =
        (TerraformCloudDelegateTaskResponse) responseSupplier.get();

    TerraformCloudTaskType taskType = delegateTaskResponse.getTaskType();
    if (taskType == GET_LAST_APPLIED_RUN) {
      TerraformCloudGetLastAppliedTaskResponse terraformCloudRunTaskResponse =
          (TerraformCloudGetLastAppliedTaskResponse) delegateTaskResponse;

      helper.saveTerraformCloudConfig(runStepParameters.getSpec(), terraformCloudRunTaskResponse.getWorkspaceId(),
          terraformCloudRunTaskResponse.getLastAppliedRun(), ambiance);
      TerraformCloudTaskParams terraformCloudTaskParams = getTerraformCloudTaskParams(ambiance, runStepParameters);
      terraformCloudTaskParams.setCommandUnitsProgress(
          UnitProgressDataMapper.toCommandUnitsProgress(terraformCloudRunTaskResponse.getUnitProgressData()));
      return getTaskChainResponse(stepElementParameters, terraformCloudTaskParams, runStepParameters, ambiance);
    } else {
      log.error(format("Unsupported task type [%s] in Task Chain", taskType));
      throw new InvalidRequestException(
          format("Unsupported task type [%s] in Task Chain", taskType), WingsException.USER);
    }
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Handling Task result with Security Context for the Terraform Cloud Run Step");
    TerraformCloudRunStepParameters runStepParameters =
        (TerraformCloudRunStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformCloudDelegateTaskResponse terraformCloudRunTaskResponse =
        (TerraformCloudDelegateTaskResponse) responseSupplier.get();
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

    TerraformCloudRunType runType = runStepParameters.getSpec().getType();
    if (CommandExecutionStatus.SUCCESS == terraformCloudRunTaskResponse.getCommandExecutionStatus()) {
      TerraformCloudRunOutcome terraformCloudRunOutcome;
      switch (runType) {
        case REFRESH_STATE:
          TerraformCloudRefreshTaskResponse refreshTaskResponse =
              (TerraformCloudRefreshTaskResponse) terraformCloudRunTaskResponse;
          terraformCloudRunOutcome = TerraformCloudRunOutcome.builder().runId(refreshTaskResponse.getRunId()).build();
          break;
        case PLAN_ONLY:
          TerraformCloudPlanOnlyTaskResponse planOnlyResponse =
              (TerraformCloudPlanOnlyTaskResponse) terraformCloudRunTaskResponse;
          TerraformCloudPlanOnlySpecParameters planOnlySpecParameters =
              (TerraformCloudPlanOnlySpecParameters) runStepParameters.getSpec();
          terraformCloudRunOutcome = handePlanOnlyResponse(planOnlyResponse, planOnlySpecParameters, ambiance);
          break;
        case PLAN_AND_APPLY:
          TerraformCloudPlanAndApplyTaskResponse planAndApplyResponse =
              (TerraformCloudPlanAndApplyTaskResponse) terraformCloudRunTaskResponse;
          TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
              (TerraformCloudPlanAndApplySpecParameters) runStepParameters.getSpec();
          terraformCloudRunOutcome =
              handePlanAndApplyResponse(planAndApplyResponse, planAndApplySpecParameters, ambiance);
          break;
        case PLAN_AND_DESTROY:
          TerraformCloudPlanAndDestroyTaskResponse planAndDestroyResponse =
              (TerraformCloudPlanAndDestroyTaskResponse) terraformCloudRunTaskResponse;
          TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
              (TerraformCloudPlanAndDestroySpecParameters) runStepParameters.getSpec();
          terraformCloudRunOutcome =
              handlePlanAndDestroyResponse(planAndDestroyResponse, planAndDestroySpecParameters, ambiance);
          break;
        case PLAN:
          TerraformCloudPlanTaskResponse planResponse = (TerraformCloudPlanTaskResponse) terraformCloudRunTaskResponse;
          TerraformCloudPlanSpecParameters planSpecParameters =
              (TerraformCloudPlanSpecParameters) runStepParameters.getSpec();
          terraformCloudRunOutcome = handlePlanResponse(planResponse, planSpecParameters, ambiance);
          break;
        case APPLY:
          TerraformCloudApplyTaskResponse applyTaskResponse =
              (TerraformCloudApplyTaskResponse) terraformCloudRunTaskResponse;
          terraformCloudRunOutcome = handleApplyResponse(applyTaskResponse, ambiance);
          break;
        default:
          throw new InvalidRequestException("Unhandled run typ: " + runType.name(), WingsException.USER);
      }
      stepResponseBuilder.stepOutcome(
          StepOutcome.builder().name(OUTCOME_NAME).outcome(terraformCloudRunOutcome).build());
    }
    return stepResponseBuilder.build();
  }

  private TerraformCloudRunOutcome handleApplyResponse(
      TerraformCloudApplyTaskResponse applyTaskResponse, Ambiance ambiance) {
    String runId = applyTaskResponse.getRunId();
    HashMap<String, Object> outputs = new HashMap<>(helper.parseTerraformOutputs(applyTaskResponse.getTfOutput()));
    helper.updateRunDetails(ambiance, runId);
    return getTerraformCloudRunOutcome(runId, null, null, null, outputs);
  }

  private TerraformCloudRunOutcome handlePlanResponse(TerraformCloudPlanTaskResponse planResponse,
      TerraformCloudPlanSpecParameters planSpecParameters, Ambiance ambiance) {
    String runId = planResponse.getRunId();
    String tfPlanJsonFileId = planResponse.getTfPlanJsonFileId();
    String policyChecksJsonFileId = planResponse.getPolicyChecksJsonFileId();
    String provisionIdentifier =
        ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getProvisionerIdentifier());
    RunDetails runDetails =
        RunDetails.builder()
            .runId(runId)
            .connectorRef(ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getConnectorRef()))
            .build();

    helper.saveTerraformCloudPlanExecutionDetails(
        ambiance, tfPlanJsonFileId, policyChecksJsonFileId, provisionIdentifier, runDetails, true);
    return getTerraformCloudRunOutcome(runId, policyChecksJsonFileId, tfPlanJsonFileId, provisionIdentifier, null);
  }

  private TerraformCloudRunOutcome handlePlanAndDestroyResponse(
      TerraformCloudPlanAndDestroyTaskResponse planAndDestroyResponse,
      TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters, Ambiance ambiance) {
    String runId = planAndDestroyResponse.getRunId();
    HashMap<String, Object> outputs = new HashMap<>(helper.parseTerraformOutputs(planAndDestroyResponse.getTfOutput()));
    String policyChecksJsonFileId = planAndDestroyResponse.getPolicyChecksJsonFileId();
    String provisionIdentifier =
        ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getProvisionerIdentifier());
    helper.saveTerraformCloudPlanExecutionDetails(ambiance, null, policyChecksJsonFileId, provisionIdentifier, null);
    return getTerraformCloudRunOutcome(runId, policyChecksJsonFileId, null, provisionIdentifier, outputs);
  }

  private TerraformCloudRunOutcome handePlanAndApplyResponse(
      TerraformCloudPlanAndApplyTaskResponse planAndApplyResponse,
      TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters, Ambiance ambiance) {
    String runId = planAndApplyResponse.getRunId();
    HashMap<String, Object> outputs = new HashMap<>(helper.parseTerraformOutputs(planAndApplyResponse.getTfOutput()));
    String policyChecksJsonFileId = planAndApplyResponse.getPolicyChecksJsonFileId();
    String provisionIdentifier =
        ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getProvisionerIdentifier());
    helper.saveTerraformCloudPlanExecutionDetails(ambiance, null, policyChecksJsonFileId, provisionIdentifier, null);
    return getTerraformCloudRunOutcome(runId, policyChecksJsonFileId, null, provisionIdentifier, outputs);
  }

  private TerraformCloudRunOutcome handePlanOnlyResponse(TerraformCloudPlanOnlyTaskResponse planOnlyResponse,
      TerraformCloudPlanOnlySpecParameters planOnlySpecParameters, Ambiance ambiance) {
    String runId = planOnlyResponse.getRunId();
    String tfPlanJsonFileId = planOnlyResponse.getTfPlanJsonFileId();
    String policyChecksJsonFileId = planOnlyResponse.getPolicyChecksJsonFileId();
    String provisionIdentifier =
        ParameterFieldHelper.getParameterFieldValue(planOnlySpecParameters.getProvisionerIdentifier());
    helper.saveTerraformCloudPlanExecutionDetails(
        ambiance, tfPlanJsonFileId, policyChecksJsonFileId, provisionIdentifier, null);
    return getTerraformCloudRunOutcome(runId, policyChecksJsonFileId, tfPlanJsonFileId, provisionIdentifier, null);
  }

  private TerraformCloudRunOutcome getTerraformCloudRunOutcome(String runId, String policyChecksJsonFileId,
      String tfPlanJsonFileId, String provisionerIdentifier, HashMap<String, Object> outputs) {
    return TerraformCloudRunOutcome.builder()
        .policyChecksFilePath(policyChecksJsonFileId != null
                ? TerraformCloudPolicyChecksJsonFunctor.getExpression(provisionerIdentifier)
                : null)
        .jsonFilePath(
            tfPlanJsonFileId != null ? TerraformCloudPlanJsonFunctor.getExpression(provisionerIdentifier) : null)
        .outputs(outputs)
        .runId(runId)
        .build();
  }

  private TaskChainResponse getTaskChainResponse(StepElementParameters stepElementParameters,
      TerraformCloudTaskParams terraformCloudTaskParams, TerraformCloudRunStepParameters runStepParameters,
      Ambiance ambiance) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.TERRAFORM_CLOUD_TASK_NG.name())
                            .timeout(StepUtils.getTimeoutMillis(
                                stepElementParameters.getTimeout(), TerraformCloudConstants.DEFAULT_TIMEOUT))
                            .parameters(new Object[] {terraformCloudTaskParams})
                            .build();

    TerraformCloudTaskType taskType = terraformCloudTaskParams.getTaskType();
    return TaskChainResponse.builder()
        .chainEnd(taskType != GET_LAST_APPLIED_RUN)
        .passThroughData(TerraformCloudPassThroughData.builder().build())
        .taskRequest(TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            getCommandUnits(runStepParameters.getSpec().getType()),
            format("%s : %s ", TaskType.TERRAFORM_CLOUD_TASK_NG.getDisplayName(), taskType.getDisplayName()),
            TaskSelectorYaml.toTaskSelector(runStepParameters.getDelegateSelectors()),
            stepHelper.getEnvironmentType(ambiance)))
        .build();
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(
      Ambiance ambiance, TerraformCloudRunStepParameters runStepParameters) {
    TerraformCloudRunSpecParameters runSpec = runStepParameters.getSpec();
    TerraformCloudTaskParams terraformCloudTaskParams =
        paramsMapper.mapRunSpecToTaskParams(runStepParameters, ambiance);
    TerraformCloudConnectorDTO terraformCloudConnector = helper.getTerraformCloudConnector(runSpec, ambiance);
    terraformCloudTaskParams.setTerraformCloudConnectorDTO(terraformCloudConnector);
    terraformCloudTaskParams.setEncryptionDetails(helper.getEncryptionDetail(ambiance, terraformCloudConnector));
    return terraformCloudTaskParams;
  }

  private TerraformCloudGetLastAppliedTaskParams getTerraformCloudGetLatestAppliedParams(
      TerraformCloudRunSpecParameters runSpec, Ambiance ambiance) {
    TerraformCloudRunType type = runSpec.getType();
    String runId = null;
    String workspaceId = null;
    if (type == APPLY) {
      TerraformCloudApplySpecParameters applySpecParameters = (TerraformCloudApplySpecParameters) runSpec;
      runId = helper.getPlanRunId(
          ParameterFieldHelper.getParameterFieldValue(applySpecParameters.getProvisionerIdentifier()), ambiance);
    } else if (type == PLAN_AND_APPLY) {
      TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
          (TerraformCloudPlanAndApplySpecParameters) runSpec;
      workspaceId = ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getWorkspace());
    } else if (type == PLAN_AND_DESTROY) {
      TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
          (TerraformCloudPlanAndDestroySpecParameters) runSpec;
      workspaceId = ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getWorkspace());
    }
    TerraformCloudConnectorDTO terraformCloudConnector = helper.getTerraformCloudConnector(runSpec, ambiance);
    return TerraformCloudGetLastAppliedTaskParams.builder()
        .runId(runId)
        .workspace(workspaceId)
        .terraformCloudConnectorDTO(terraformCloudConnector)
        .encryptionDetails(helper.getEncryptionDetail(ambiance, terraformCloudConnector))
        .build();
  }

  private List<String> getCommandUnits(TerraformCloudRunType type) {
    switch (type) {
      case REFRESH_STATE:
      case PLAN_ONLY:
      case PLAN:
        return List.of(
            TerraformCloudCommandUnit.PLAN.getDisplayName(), TerraformCloudCommandUnit.POLICY_CHECK.getDisplayName());
      case PLAN_AND_APPLY:
      case PLAN_AND_DESTROY:
        return List.of(TerraformCloudCommandUnit.FETCH_LAST_APPLIED_RUN.getDisplayName(),
            TerraformCloudCommandUnit.PLAN.getDisplayName(), TerraformCloudCommandUnit.POLICY_CHECK.getDisplayName(),
            TerraformCloudCommandUnit.APPLY.getDisplayName());
      case APPLY:
        return List.of(TerraformCloudCommandUnit.FETCH_LAST_APPLIED_RUN.getDisplayName(),
            TerraformCloudCommandUnit.APPLY.getDisplayName());
      default:
        throw new IllegalStateException("Unexpected value: " + type);
    }
  }
}
