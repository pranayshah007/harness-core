/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandTypeNG;
import io.harness.delegate.task.elastigroup.response.ElastigroupSetupResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.spotinst.SpotInstServiceSetup;
import software.wings.utils.ServiceVersionConvention;

import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static org.apache.commons.lang3.StringUtils.isBlank;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ElastigroupSetupStep extends TaskChainExecutableWithRollbackAndRbac implements ElastigroupStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ELASTIGROUP_SETUP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String ELASTIGROUP_SETUP_COMMAND_NAME = "ElastigroupSetup";
  public static final int DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT = 2;

  @Inject private ElastigroupStepCommonHelper elastigroupStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public TaskChainResponse executeElastigroupTask(Ambiance ambiance, StepElementParameters stepParameters,
                                                  ElastigroupExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
                                                  ElastigroupStepExecutorParams elastigroupStepExecutorParams) {
    ElastigroupInfrastructureOutcome infrastructureOutcome = (ElastigroupInfrastructureOutcome) executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    SpotInstConfig spotInstConfig = elastigroupStepCommonHelper.getSpotInstConfig(infrastructureOutcome, ambiance);

    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
        (ElastigroupSetupStepParameters) stepParameters.getSpec();

    String elastigroupNamePrefix = elastigroupSetupStepParameters.getName().getValue();

    elastigroupNamePrefix = isBlank(elastigroupNamePrefix)
            ? Misc.normalizeExpression(
            ServiceVersionConvention.getPrefix(elastigroupSetupStepParameters.getName().getValue(), infrastructureOutcome.getEnvironment().getName()))
            : Misc.normalizeExpression(elastigroupStepCommonHelper.renderExpression(ambiance, elastigroupNamePrefix));

    ElastiGroup elastiGroupOriginalConfig =
            generateOriginalConfigFromJson(elastigroupStepExecutorParams.getElastigroupParameters(), elastigroupSetupStepParameters.getInstances(), ambiance);

    ElastigroupSetupCommandRequest elastigroupSetupCommandRequest =
            ElastigroupSetupCommandRequest.builder()
                    .blueGreen(false)
                    .elastigroupNamePrefix(elastigroupNamePrefix)
            .accountId(accountId)
            .spotInstConfig(spotInstConfig)
                    .elastiGroupJson(elastigroupStepExecutorParams.getElastigroupParameters())
            .elastigroupCommandType(ElastigroupCommandTypeNG.ELASTIGROUP_SETUP)
                    .startupScript(elastigroupStepCommonHelper.getBase64EncodedStartupScript(ambiance, elastigroupStepExecutorParams.getStartupScript()))
            .commandName(ELASTIGROUP_SETUP_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
                    .maxInstanceCount(elastiGroupOriginalConfig.getCapacity().getMaximum())
                    .currentRunningInstanceCount(fetchCurrentRunningCountForSetupRequest(elastigroupSetupStepParameters.getInstances()))
                    .useCurrentRunningInstanceCount(ElastigroupInstancesType.CURRENT_RUNNING.equals(elastigroupSetupStepParameters.getInstances().getType()))
            .build();

    return elastigroupStepCommonHelper.queueElastigroupTask(
        stepParameters, elastigroupSetupCommandRequest, ambiance, executionPassThroughData, true);
  }

  private Integer fetchCurrentRunningCountForSetupRequest(ElastigroupInstances elastigroupInstances) {
    if (ElastigroupInstancesType.FIXED.equals(elastigroupInstances.getType())) {
      return null;
    }

    return Integer.valueOf(2);
    }

  private ElastiGroup generateOriginalConfigFromJson(
          String elastiGroupOriginalJson, ElastigroupInstances elastigroupInstances, Ambiance ambiance) {
    ElastiGroup elastiGroup = elastigroupStepCommonHelper.generateConfigFromJson(elastiGroupOriginalJson);
    ElastiGroupCapacity groupCapacity = elastiGroup.getCapacity();
    if (ElastigroupInstancesType.CURRENT_RUNNING.equals(elastigroupInstances.getType())) {
      groupCapacity.setMinimum(DEFAULT_ELASTIGROUP_MIN_INSTANCES);
      groupCapacity.setMaximum(DEFAULT_ELASTIGROUP_MAX_INSTANCES);
      groupCapacity.setTarget(DEFAULT_ELASTIGROUP_TARGET_INSTANCES);
    } else {
      ElastigroupFixedInstances elastigroupFixedInstances = (ElastigroupFixedInstances) elastigroupInstances.getSpec();
      groupCapacity.setMinimum(elastigroupStepCommonHelper.renderCount(elastigroupFixedInstances.getMin(), ambiance, DEFAULT_ELASTIGROUP_MIN_INSTANCES));
      groupCapacity.setMaximum(elastigroupStepCommonHelper.renderCount(elastigroupFixedInstances.getMax(), ambiance, DEFAULT_ELASTIGROUP_MAX_INSTANCES));
      groupCapacity.setTarget(
              elastigroupStepCommonHelper.renderCount(elastigroupFixedInstances.getDesired(), ambiance, DEFAULT_ELASTIGROUP_TARGET_INSTANCES));
    }
    return elastiGroup;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return elastigroupStepCommonHelper.executeNextLink(
        this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof ElastigroupStartupScriptFetchFailurePassThroughData) {
      return elastigroupStepCommonHelper.handleStartupScriptTaskFailure((ElastigroupStartupScriptFetchFailurePassThroughData) passThroughData);
    } else if(passThroughData instanceof ElastigroupParametersFetchFailurePassThroughData) {
      return elastigroupStepCommonHelper.handleElastigroupParametersTaskFailure((ElastigroupParametersFetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof ElastigroupStepExceptionPassThroughData) {
      return elastigroupStepCommonHelper.handleStepExceptionFailure((ElastigroupStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData = (ElastigroupExecutionPassThroughData) passThroughData;
    ElastigroupSetupResponse elastigroupSetupResponse;
    try {
      elastigroupSetupResponse = (ElastigroupSetupResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing elastigroup task response: {}", e.getMessage(), e);
      return elastigroupStepCommonHelper.handleTaskException(ambiance, elastigroupExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
            elastigroupSetupResponse.getUnitProgressData().getUnitProgresses());
    if (elastigroupSetupResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return ElastigroupStepCommonHelper.getFailureResponseBuilder(elastigroupSetupResponse, stepResponseBuilder)
          .build();
    }

    ElastigroupSetupResult elastigroupSetupResult =
            elastigroupSetupResponse.getElastigroupSetupResult();
    ElastiGroup oldElastiGroup = elastigroupStepCommonHelper.fetchOldElasticGroup(elastigroupSetupResult);
    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
            ElastigroupSetupDataOutcome.builder()
                    .resizeStrategy(elastigroupSetupResult.getResizeStrategy())
                    .elastiGroupNamePrefix(elastigroupSetupResult.getElastiGroupNamePrefix())
                    .useCurrentRunningInstanceCount(elastigroupSetupResult.isUseCurrentRunningInstanceCount())
                    .currentRunningInstanceCount(elastigroupSetupResult.getCurrentRunningInstanceCount())
                    .maxInstanceCount(elastigroupSetupResult.getMaxInstanceCount())
                    .isBlueGreen(elastigroupSetupResult.isBlueGreen())
                    .oldElastiGroupOriginalConfig(oldElastiGroup)
                    .newElastiGroupOriginalConfig(elastigroupSetupResult.getNewElastiGroup())
                    .build();
    if (oldElastiGroup != null && oldElastiGroup.getCapacity() != null) {
      elastigroupSetupDataOutcome.setCurrentRunningInstanceCount(oldElastiGroup.getCapacity().getTarget());
    } else {
      elastigroupSetupDataOutcome.setCurrentRunningInstanceCount(DEFAULT_CURRENT_RUNNING_INSTANCE_COUNT);
    }

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME,
            elastigroupSetupDataOutcome, StepOutcomeGroup.STEP.name());

    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return elastigroupStepCommonHelper.startChainLink(this, ambiance, stepParameters);
  }
}
