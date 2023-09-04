/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpecType;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.cdng.common.capacity.PercentageCapacitySpec;
import io.harness.cdng.elastigroup.ElastigroupEntityHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskParameters;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.StepUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class ElastigroupDeployStepHelper extends CDStepHelper {
  @Inject private ElastigroupEntityHelper entityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public ElastigroupDeployTaskParameters getElastigroupDeployTaskParameters(
      ElastigroupDeployStepParameters stepParameters, Ambiance ambiance, StepBaseParameters stepElementParameters) {
    InfrastructureOutcome infrastructureOutcome = getInfrastructureOutcome(ambiance);

    ElastigroupSetupDataOutcome elastigroupSetupOutcome = getElastigroupSetupOutcome(ambiance);

    boolean isFinalDeployStep = isFinalDeployStep(stepParameters, elastigroupSetupOutcome);

    ElastiGroup newElastigroup = calculateNewForUpsize(
        stepParameters.getNewService(), elastigroupSetupOutcome.getNewElastigroupOriginalConfig(), isFinalDeployStep);
    ElastiGroup oldElastigroup = calculateOldForDownsize(stepParameters.getOldService(),
        elastigroupSetupOutcome.getOldElastigroupOriginalConfig(), isFinalDeployStep,
        newElastigroup.getCapacity().getTarget());

    return ElastigroupDeployTaskParameters.builder()
        .spotConnector(getSpotConnector(ambiance, infrastructureOutcome))
        .encryptionDetails(getEncryptionDetails(ambiance, infrastructureOutcome))
        .newElastigroup(newElastigroup)
        .oldElastigroup(oldElastigroup)
        .timeout(getTimeoutInMin(stepElementParameters))
        .build();
  }

  private boolean isFinalDeployStep(
      ElastigroupDeployStepParameters stepParameters, ElastigroupSetupDataOutcome elastigroupSetupOutcome) {
    if (CapacitySpecType.COUNT.equals(stepParameters.getNewService().getType())) {
      CountCapacitySpec spec = (CountCapacitySpec) stepParameters.getNewService().getSpec();

      int requestedTarget = ParameterFieldHelper.getParameterFieldValue(spec.getCount());
      int setupTarget = elastigroupSetupOutcome.getNewElastigroupOriginalConfig().getCapacity().getTarget();

      return requestedTarget >= setupTarget;
    } else if (CapacitySpecType.PERCENTAGE.equals(stepParameters.getNewService().getType())) {
      PercentageCapacitySpec spec = (PercentageCapacitySpec) stepParameters.getNewService().getSpec();

      int requestedPercentage = ParameterFieldHelper.getParameterFieldValue(spec.getPercentage());

      return requestedPercentage >= 100;
    }
    return false;
  }

  private ElastiGroup calculateNewForUpsize(
      Capacity requestedCapacity, ElastiGroup setupElastigroup, boolean isFinalDeployStep) {
    final ElastiGroup result = setupElastigroup.clone();

    result.getCapacity().setTarget(calculateTargetNumberOfInstancesForNew(requestedCapacity, result));

    if (!isFinalDeployStep) {
      forceElastigroupScale(result);
    }

    return result;
  }

  private int calculateTargetNumberOfInstancesForNew(Capacity requestedCapacity, ElastiGroup result)
      throws InvalidRequestException {
    if (CapacitySpecType.COUNT.equals(requestedCapacity.getType())) {
      CountCapacitySpec spec = (CountCapacitySpec) requestedCapacity.getSpec();

      int requestedTarget = ParameterFieldHelper.getParameterFieldValue(spec.getCount());
      int setupTarget = result.getCapacity().getTarget();

      return Math.min(requestedTarget, setupTarget);

    } else if (CapacitySpecType.PERCENTAGE.equals(requestedCapacity.getType())) {
      PercentageCapacitySpec spec = (PercentageCapacitySpec) requestedCapacity.getSpec();

      int requestedPercentage = Math.min(ParameterFieldHelper.getParameterFieldValue(spec.getPercentage()), 100);
      int setupTarget = result.getCapacity().getTarget();

      int target = (int) Math.round((requestedPercentage * setupTarget) / 100.0);
      return Math.max(target, 1);

    } else {
      throw new InvalidRequestException("Unknown capacity type: " + requestedCapacity.getType());
    }
  }

  private ElastiGroup calculateOldForDownsize(
      Capacity requestedCapacity, ElastiGroup setupElastigroup, boolean isFinalDeployStep, int upsizeTarget) {
    if (setupElastigroup == null) {
      return null;
    }
    final ElastiGroup result = setupElastigroup.clone();

    if (isFinalDeployStep) {
      scaleDownElastigroup(result);
    } else {
      int target;
      if (requestedCapacity == null) {
        target = Math.max(0, setupElastigroup.getCapacity().getTarget() - upsizeTarget);
      } else {
        target = calculateTargetNumberOfInstancesForOld(requestedCapacity, result);
      }
      result.getCapacity().setTarget(target);
      result.getCapacity().setMinimum(target);
      result.getCapacity().setMaximum(target);
    }

    return result;
  }

  private Integer calculateTargetNumberOfInstancesForOld(Capacity requestedCapacity, ElastiGroup result) {
    if (CapacitySpecType.COUNT.equals(requestedCapacity.getType())) {
      final CountCapacitySpec spec = (CountCapacitySpec) requestedCapacity.getSpec();

      return ParameterFieldHelper.getParameterFieldValue(spec.getCount());
    } else if (CapacitySpecType.PERCENTAGE.equals(requestedCapacity.getType())) {
      final PercentageCapacitySpec spec = (PercentageCapacitySpec) requestedCapacity.getSpec();

      int requestedPercentage = Math.min(ParameterFieldHelper.getParameterFieldValue(spec.getPercentage()), 100);
      int setupTarget = result.getCapacity().getTarget();

      return (int) Math.round((requestedPercentage * setupTarget) / 100.0);
    } else {
      throw new InvalidRequestException("Unknown capacity type: " + requestedCapacity.getType());
    }
  }

  private void scaleDownElastigroup(ElastiGroup result) {
    result.getCapacity().setTarget(0);
    result.getCapacity().setMinimum(0);
    result.getCapacity().setMaximum(0);
  }

  private void forceElastigroupScale(ElastiGroup result) {
    result.getCapacity().setMinimum(result.getCapacity().getTarget());
    result.getCapacity().setMaximum(result.getCapacity().getTarget());
  }

  public ElastigroupSetupDataOutcome getElastigroupSetupOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalSetupDataOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));
    if (!optionalSetupDataOutput.isFound()) {
      throw new InvalidRequestException("No elastigroup setup output found.");
    }
    return (ElastigroupSetupDataOutcome) optionalSetupDataOutput.getOutput();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    ConnectorInfoDTO connectorInfoDto =
        entityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), AmbianceUtils.getNgAccess(ambiance));
    return entityHelper.getEncryptionDataDetails(connectorInfoDto, AmbianceUtils.getNgAccess(ambiance));
  }

  private SpotConnectorDTO getSpotConnector(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    ConnectorInfoDTO connectorDTO =
        entityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), AmbianceUtils.getNgAccess(ambiance));
    return (SpotConnectorDTO) connectorDTO.getConnectorConfig();
  }

  public StepResponse handleTaskFailure(Ambiance ambiance, StepBaseParameters stepElementParameters, Exception e)
      throws Exception {
    log.error("Error in elastigroup step: {}", e.getMessage(), e);

    // Trying to figure out if exception is coming from command task or it is an exception from delegate service.
    // In the second case we need to close log stream and provide unit progress data as part of response
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }
    List<UnitProgress> commandExecutionUnits =
        getExecutionUnits()
            .stream()
            .map(unit -> UnitProgress.newBuilder().setUnitName(unit).setStatus(UnitStatus.RUNNING).build())
            .collect(Collectors.toList());

    UnitProgressData currentUnitProgressData = UnitProgressData.builder().unitProgresses(commandExecutionUnits).build();
    UnitProgressData unitProgressData =
        completeUnitProgressData(currentUnitProgressData, ambiance, ExceptionUtils.getMessage(e));
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(emptyIfNull(ExceptionUtils.getMessage(e)))
                                  .build();

    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public StepResponse handleTaskResult(Ambiance ambiance, StepBaseParameters stepParameters,
      ElastigroupDeployTaskResponse taskResponse, StepResponse.StepOutcome stepOutcome) {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
        ? emptyList()
        : taskResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    stepResponseBuilder.status(StepUtils.getStepStatus(taskResponse.getStatus()));
    stepResponseBuilder.stepOutcome(stepOutcome);

    if (isNotEmpty(taskResponse.getErrorMessage())) {
      FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
      failureInfoBuilder.addFailureData(FailureData.newBuilder()
                                            .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                            .setLevel(Level.ERROR.name())
                                            .setCode(GENERAL_ERROR.name())
                                            .setMessage(taskResponse.getErrorMessage()));
      stepResponseBuilder.failureInfo(failureInfoBuilder.build());
    }

    return stepResponseBuilder.build();
  }

  public List<String> getExecutionUnits() {
    return Arrays.asList(UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DOWN_SCALE_COMMAND_UNIT,
        DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
  }
}
