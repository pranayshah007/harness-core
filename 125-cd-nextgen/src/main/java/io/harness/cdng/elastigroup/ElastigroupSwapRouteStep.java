/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSwapRouteDataOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.elastigroup.ElastigroupSwapRouteResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSwapRouteCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupSwapRouteResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ElastigroupSwapRouteStep
    extends TaskChainExecutableWithRollbackAndRbac implements ElastigroupStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ELASTIGROUP_SWAP_ROUTE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String ELASTIGROUP_SWAP_ROUTE_COMMAND_NAME = "ElastigroupSwapRoute";
  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Inject private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public TaskChainResponse executeElastigroupTask(Ambiance ambiance, StepBaseParameters stepParameters,
      ElastigroupExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData) {
    ElastigroupInfrastructureOutcome infrastructureOutcome =
        (ElastigroupInfrastructureOutcome) executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    SpotInstConfig spotInstConfig = elastigroupStepCommonHelper.getSpotInstConfig(infrastructureOutcome, ambiance);

    OptionalSweepingOutput optionalElastigroupSetupOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));

    if (!optionalElastigroupSetupOutput.isFound()) {
      elastigroupStepCommonHelper.stepFailureTaskResponseWithMessage(
          unitProgressData, "Elastigroup BG Stage Setup Outcome not found");
    }

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
        (ElastigroupSetupDataOutcome) optionalElastigroupSetupOutput.getOutput();

    ElastigroupSwapRouteStepParameters elastigroupSwapRouteStepParameters =
        (ElastigroupSwapRouteStepParameters) stepParameters.getSpec();

    ConnectorInfoDTO connectorInfoDTO = elastigroupStepCommonHelper.getConnector(
        elastigroupStepCommonHelper.renderExpression(ambiance, elastigroupSetupDataOutcome.getAwsConnectorRef()),
        ambiance);

    ElastigroupSwapRouteCommandRequest elastigroupSwapRouteCommandRequest =
        ElastigroupSwapRouteCommandRequest.builder()
            .blueGreen(elastigroupSetupDataOutcome.isBlueGreen())
            .newElastigroup(elastigroupSetupDataOutcome.getNewElastigroupOriginalConfig())
            .oldElastigroup(elastigroupSetupDataOutcome.getOldElastigroupOriginalConfig())
            .elastigroupNamePrefix(elastigroupSetupDataOutcome.getElastigroupNamePrefix())
            .accountId(accountId)
            .downsizeOldElastigroup(
                String.valueOf(elastigroupSwapRouteStepParameters.getDownsizeOldElastigroup().getValue()))
            .resizeStrategy(elastigroupSetupDataOutcome.getResizeStrategy())
            .spotInstConfig(spotInstConfig)
            .commandName(ELASTIGROUP_SWAP_ROUTE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .loadBalancerConfig(
                AwsLoadBalancerConfig.builder()
                    .loadBalancerDetails(elastigroupSetupDataOutcome.getLoadBalancerDetailsForBGDeployments())
                    .build())
            .connectedCloudProvider(
                AwsConnectedCloudProvider.builder()
                    .connectorInfoDTO(connectorInfoDTO)
                    .encryptionDetails(elastigroupStepCommonHelper.getEncryptedDataDetail(connectorInfoDTO, ambiance))
                    .region(elastigroupSetupDataOutcome.getAwsRegion())
                    .build())
            .build();

    return elastigroupStepCommonHelper.queueElastigroupTask(stepParameters, elastigroupSwapRouteCommandRequest,
        ambiance, executionPassThroughData, true, TaskType.ELASTIGROUP_SWAP_ROUTE_COMMAND_TASK_NG);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return elastigroupStepCommonHelper.executeNextLink(
        this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof ElastigroupStepExceptionPassThroughData) {
      return elastigroupStepCommonHelper.handleStepExceptionFailure(
          (ElastigroupStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        (ElastigroupExecutionPassThroughData) passThroughData;
    ElastigroupSwapRouteResponse elastigroupSwapRouteResponse;
    try {
      elastigroupSwapRouteResponse = (ElastigroupSwapRouteResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing elastigroup task response: {}", e.getMessage(), e);
      return elastigroupStepCommonHelper.handleTaskException(ambiance, elastigroupExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(elastigroupSwapRouteResponse.getUnitProgressData().getUnitProgresses());
    if (elastigroupSwapRouteResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return ElastigroupStepCommonHelper.getFailureResponseBuilder(elastigroupSwapRouteResponse, stepResponseBuilder)
          .build();
    }

    ElastigroupSwapRouteResult elastigroupSwapRouteResult =
        elastigroupSwapRouteResponse.getElastigroupSwapRouteResult();

    ElastigroupSwapRouteDataOutcome elastigroupSwapRouteDataOutcome =
        ElastigroupSwapRouteDataOutcome.builder()
            .downsizeOldElastigroup(elastigroupSwapRouteResult.getDownsizeOldElastiGroup())
            .loadBalancerDetails(elastigroupSwapRouteResult.getLbDetails())
            .newElastigroupId(elastigroupSwapRouteResult.getNewElastiGroupId())
            .oldElastigroupId(elastigroupSwapRouteResult.getOldElastiGroupId())
            .newElastigroupName(elastigroupSwapRouteResult.getNewElastiGroupName())
            .oldElastigroupName(elastigroupSwapRouteResult.getOldElastiGroupName())
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ELASTIGROUP_SWAP_ROUTE_OUTCOME,
        elastigroupSwapRouteDataOutcome, StepOutcomeGroup.STAGE.name());

    elastigroupStepCommonHelper.saveSpotServerInstanceInfosToSweepingOutput(
        elastigroupSwapRouteResult.getEc2InstanceIdsAdded(), elastigroupSwapRouteResult.getEc2InstanceIdsExisting(),
        ambiance);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(elastigroupSwapRouteDataOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupStepCommonHelper.getInfrastructureOutcome(ambiance))
            .build();
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    return executeElastigroupTask(ambiance, stepParameters, elastigroupExecutionPassThroughData, unitProgressData);
  }
}
