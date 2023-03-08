/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.steps;

import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_DESTROY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.REFRESH_STATE;
import static io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome.OUTCOME_NAME;
import static io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType.GET_LAST_APPLIED_RUN;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
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
import io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome.TerraformCloudRunOutcomeBuilder;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.task.terraformcloud.TerraformCloudCommandUnit;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
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
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_TERRAFORM_CLOUD)) {
      throw new AccessDeniedException(
          format("Step is not enabled for account '%s'. Please contact harness customer care to enable FF '%s'.",
              AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_TERRAFORM_CLOUD.name()),
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }

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
    TerraformCloudTaskParams terraformCloudTaskParams = getTerraformCloudTaskParams(ambiance, runStepParameters);

    if (runType == PLAN_AND_APPLY || runType == PLAN_AND_DESTROY || runType == APPLY) {
      // If we are doing any previsioning get the latest applied run that might be used for rollback
      terraformCloudTaskParams.setTerraformCloudTaskType(GET_LAST_APPLIED_RUN);
    }
    return getTaskChainResponse(stepElementParameters, terraformCloudTaskParams, runStepParameters, ambiance);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    TerraformCloudRunStepParameters runStepParameters =
        (TerraformCloudRunStepParameters) stepElementParameters.getSpec();
    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        (TerraformCloudRunTaskResponse) responseSupplier.get();

    helper.saveTerraformCloudConfig(runStepParameters.getSpec(), terraformCloudRunTaskResponse.getWorkspaceId(),
        terraformCloudRunTaskResponse.getLastAppliedRun(), ambiance);
    TerraformCloudTaskParams terraformCloudTaskParams = getTerraformCloudTaskParams(ambiance, runStepParameters);
    terraformCloudTaskParams.setCommandUnitsProgress(
        UnitProgressDataMapper.toCommandUnitsProgress(terraformCloudRunTaskResponse.getUnitProgressData()));
    return getTaskChainResponse(stepElementParameters, terraformCloudTaskParams, runStepParameters, ambiance);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Handling Task result with Security Context for the Terraform Cloud Run Step");
    TerraformCloudRunStepParameters runStepParameters =
        (TerraformCloudRunStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        (TerraformCloudRunTaskResponse) responseSupplier.get();
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
    if (CommandExecutionStatus.SUCCESS == terraformCloudRunTaskResponse.getCommandExecutionStatus()
        && runType != TerraformCloudRunType.REFRESH_STATE) {
      String provisionerIdentifier = helper.getProvisionIdentifier(runStepParameters.getSpec());
      String policyChecksJsonFileId = terraformCloudRunTaskResponse.getPolicyChecksJsonFileId();
      String tfPlanJsonFileId = terraformCloudRunTaskResponse.getTfPlanJsonFileId();
      if (runType != APPLY) {
        RunDetails runDetails = PLAN.equals(runType)
            ? RunDetails.builder()
                  .runId(terraformCloudRunTaskResponse.getRunId())
                  .connectorRef(ParameterFieldHelper.getParameterFieldValue(
                      ((TerraformCloudPlanSpecParameters) runStepParameters.getSpec()).getConnectorRef()))
                  .build()
            : null;
        helper.saveTerraformCloudPlanExecutionDetails(
            ambiance, tfPlanJsonFileId, policyChecksJsonFileId, provisionerIdentifier, runDetails);
      }
      TerraformCloudRunOutcomeBuilder terraformCloudRunOutcomeBuilder =
          TerraformCloudRunOutcome.builder()
              .detailedExitCode(terraformCloudRunTaskResponse.getDetailedExitCode())
              .policyChecksFilePath(policyChecksJsonFileId != null && provisionerIdentifier != null
                      ? TerraformCloudPolicyChecksJsonFunctor.getExpression(provisionerIdentifier)
                      : null)
              .jsonFilePath((helper.isExportTfPlanJson(runStepParameters.getSpec()) && tfPlanJsonFileId != null
                                && provisionerIdentifier != null)
                      ? TerraformCloudPlanJsonFunctor.getExpression(provisionerIdentifier)
                      : null)
              .runId(terraformCloudRunTaskResponse.getRunId());

      if (runType == PLAN) {
        TerraformCloudPlanSpecParameters planSpecParameters =
            (TerraformCloudPlanSpecParameters) runStepParameters.getSpec();
        helper.saveTerraformCloudPlanOutput(planSpecParameters, terraformCloudRunTaskResponse, ambiance);
      }

      if (runType == TerraformCloudRunType.APPLY || runType == TerraformCloudRunType.PLAN_AND_APPLY
          || runType == PLAN_AND_DESTROY) {
        if (runType == TerraformCloudRunType.APPLY) {
          helper.updateRunDetails(ambiance, terraformCloudRunTaskResponse.getRunId());
        }
        terraformCloudRunOutcomeBuilder.outputs(
            new HashMap<>(helper.parseTerraformOutputs(terraformCloudRunTaskResponse.getTfOutput())));
      }

      stepResponseBuilder.stepOutcome(
          StepOutcome.builder().name(OUTCOME_NAME).outcome(terraformCloudRunOutcomeBuilder.build()).build());
    }
    return stepResponseBuilder.build();
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

    return TaskChainResponse.builder()
        .chainEnd(terraformCloudTaskParams.getTerraformCloudTaskType() != GET_LAST_APPLIED_RUN)
        .passThroughData(TerraformCloudPassThroughData.builder().build())
        .taskRequest(TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            getCommandUnits(runStepParameters.getSpec().getType()), TaskType.TERRAFORM_CLOUD_TASK_NG.getDisplayName(),
            TaskSelectorYaml.toTaskSelector(runStepParameters.getDelegateSelectors()),
            stepHelper.getEnvironmentType(ambiance)))
        .build();
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(
      Ambiance ambiance, TerraformCloudRunStepParameters runStepParameters) {
    TerraformCloudRunSpecParameters runSpec = runStepParameters.getSpec();
    TerraformCloudTaskParams terraformCloudTaskParams = paramsMapper.mapRunSpecToTaskParams(runSpec, ambiance);

    terraformCloudTaskParams.setAccountId(AmbianceUtils.getAccountId(ambiance));
    terraformCloudTaskParams.setMessage(ParameterFieldHelper.getParameterFieldValue(runStepParameters.getMessage()));

    TerraformCloudConnectorDTO terraformCloudConnector = helper.getTerraformCloudConnector(runSpec, ambiance);
    terraformCloudTaskParams.setTerraformCloudConnectorDTO(terraformCloudConnector);
    terraformCloudTaskParams.setEncryptionDetails(helper.getEncryptionDetail(ambiance, terraformCloudConnector));
    terraformCloudTaskParams.setEntityId(runSpec.getType() != REFRESH_STATE
            ? helper.generateFullIdentifier(helper.getProvisionIdentifier(runSpec), ambiance)
            : null);
    return terraformCloudTaskParams;
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
