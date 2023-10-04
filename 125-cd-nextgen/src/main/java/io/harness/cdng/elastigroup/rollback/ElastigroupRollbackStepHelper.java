/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.DELETE_NEW_ELASTI_GROUP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_OLD_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.ElastigroupEntityHelper;
import io.harness.cdng.elastigroup.ElastigroupStepCommonHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupPreFetchOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters.ElastigroupRollbackTaskParametersBuilder;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskResponse;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.SkipRollbackException;
import io.harness.logging.CommandExecutionStatus;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class ElastigroupRollbackStepHelper extends CDStepHelper {
  private static final List<String> BG_SETUP_ROLLBACK_EXECUTION_UNITS =
      Arrays.asList(DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DELETE_NEW_ELASTI_GROUP);
  private static final List<String> BG_SWAP_ROLLBACK_EXECUTION_UNITS = Arrays.asList(UP_SCALE_COMMAND_UNIT,
      UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, RENAME_OLD_COMMAND_UNIT, SWAP_ROUTES_COMMAND_UNIT,
      DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DELETE_NEW_ELASTI_GROUP);
  private static final List<String> BASIC_AND_CANARY_ROLLBACK_EXECUTION_UNITS =
      Arrays.asList(UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DOWN_SCALE_COMMAND_UNIT,
          DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DELETE_NEW_ELASTI_GROUP);

  private static final List<String> BASIC_AND_CANARY_ROLLBACK_SETUP_FAILED_EXECUTION_UNITS =
      Arrays.asList(DELETE_NEW_ELASTI_GROUP);

  @Inject private ElastigroupEntityHelper entityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ElastigroupStepCommonHelper elastigroupStepCommonHelper;

  public ElastigroupRollbackTaskParameters getElastigroupRollbackTaskParameters(
      ElastigroupRollbackStepParameters elastigroupRollbackStepParameters, Ambiance ambiance,
      StepBaseParameters stepElementParameters) {
    InfrastructureOutcome infrastructureOutcome = getInfrastructureOutcome(ambiance);

    ElastigroupPreFetchOutcome preFetchOutcome = getElastigroupPreFetchOutcome(ambiance);
    ElastigroupSetupDataOutcome elastigroupSetupOutcome = getElastigroupSetupOutcome(ambiance);

    ElastigroupRollbackTaskParametersBuilder parametersBuilder =
        ElastigroupRollbackTaskParameters.builder()
            .blueGreen(preFetchOutcome.isBlueGreen())
            .prevElastigroups(preFetchOutcome.getElastigroups())
            .elastigroupNamePrefix(preFetchOutcome.getElastigroupNamePrefix())
            .spotConnector(getSpotConnector(ambiance, infrastructureOutcome))
            .encryptionDetails(getEncryptionDetails(ambiance, infrastructureOutcome))
            .timeout(getTimeoutInMin(stepElementParameters));

    if (null != elastigroupSetupOutcome) {
      ElastiGroup newElastigroup = calculateNewForUpsize(elastigroupSetupOutcome.getNewElastigroupOriginalConfig());
      ElastiGroup oldElastigroup = calculateOldForDownsize(elastigroupSetupOutcome.getOldElastigroupOriginalConfig());

      parametersBuilder.newElastigroup(newElastigroup).oldElastigroup(oldElastigroup);

      if (preFetchOutcome.isBlueGreen()) {
        ConnectorInfoDTO awsConnectorInfo = getConnector(elastigroupSetupOutcome.getAwsConnectorRef(), ambiance);
        parametersBuilder
            .loadBalancerDetailsForBGDeployments(elastigroupSetupOutcome.getLoadBalancerDetailsForBGDeployments())
            .awsRegion(elastigroupSetupOutcome.getAwsRegion())

            .awsConnectorInfo(awsConnectorInfo)
            .awsEncryptedDetails(getEncryptionDetails(ambiance, awsConnectorInfo));
      }
    }

    return parametersBuilder.build();
  }

  private ElastiGroup calculateNewForUpsize(ElastiGroup setupElastigroup) {
    if (setupElastigroup == null) {
      return null;
    }

    final ElastiGroup result = setupElastigroup.clone();

    result.getCapacity().setTarget(0);
    result.getCapacity().setMinimum(0);
    result.getCapacity().setMaximum(0);

    return result;
  }

  private ElastiGroup calculateOldForDownsize(ElastiGroup setupElastigroup) {
    if (setupElastigroup == null) {
      return null;
    }

    return setupElastigroup.clone();
  }

  public ElastigroupPreFetchOutcome getElastigroupPreFetchOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalSetupDataOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_PRE_FETCH_OUTCOME));
    if (!optionalSetupDataOutput.isFound()) {
      throw new SkipRollbackException("Elastigroup pre fetch outcome not found");
    }
    return (ElastigroupPreFetchOutcome) optionalSetupDataOutput.getOutput();
  }

  public ElastigroupSetupDataOutcome getElastigroupSetupOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalSetupDataOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));
    if (!optionalSetupDataOutput.isFound()) {
      return null;
    }
    return (ElastigroupSetupDataOutcome) optionalSetupDataOutput.getOutput();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    ConnectorInfoDTO connectorInfoDto =
        entityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), AmbianceUtils.getNgAccess(ambiance));
    return entityHelper.getEncryptionDataDetails(connectorInfoDto, AmbianceUtils.getNgAccess(ambiance));
  }

  private List<EncryptedDataDetail> getEncryptionDetails(Ambiance ambiance, ConnectorInfoDTO connectorInfoDto) {
    return entityHelper.getEncryptionDataDetails(connectorInfoDto, AmbianceUtils.getNgAccess(ambiance));
  }

  private SpotConnectorDTO getSpotConnector(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    ConnectorInfoDTO connectorDTO =
        entityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), AmbianceUtils.getNgAccess(ambiance));
    return (SpotConnectorDTO) connectorDTO.getConnectorConfig();
  }

  public Collection<String> getExecutionUnits() {
    final HashSet<String> allExecutionUnits = new HashSet<>();

    allExecutionUnits.addAll(BASIC_AND_CANARY_ROLLBACK_SETUP_FAILED_EXECUTION_UNITS);
    allExecutionUnits.addAll(BASIC_AND_CANARY_ROLLBACK_EXECUTION_UNITS);
    allExecutionUnits.addAll(BG_SETUP_ROLLBACK_EXECUTION_UNITS);
    allExecutionUnits.addAll(BG_SWAP_ROLLBACK_EXECUTION_UNITS);

    return allExecutionUnits;
  }

  public List<String> getExecutionUnits(ElastigroupRollbackTaskParameters parameters) {
    if (parameters.isBlueGreen()) {
      if (parameters.isSetupSuccessful()) {
        return BG_SWAP_ROLLBACK_EXECUTION_UNITS;
      } else {
        return BG_SETUP_ROLLBACK_EXECUTION_UNITS;
      }
    } else {
      if (parameters.isSetupSuccessful()) {
        return BASIC_AND_CANARY_ROLLBACK_EXECUTION_UNITS;
      } else {
        return BASIC_AND_CANARY_ROLLBACK_SETUP_FAILED_EXECUTION_UNITS;
      }
    }
  }

  public StepResponse handleTaskFailure(Ambiance ambiance, StepBaseParameters stepElementParameters, Exception e)
      throws Exception {
    log.error("Error in elastigroup step: {}", e.getMessage(), e);

    // Trying to figure out if exception is coming from command task or it is an exception from delegate service.
    // In the second case we need to close log stream and provide unit progress data as part of response
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }
    List<UnitProgress> executionUnits =
        getExecutionUnits()
            .stream()
            .map(unit -> UnitProgress.newBuilder().setUnitName(unit).setStatus(UnitStatus.RUNNING).build())
            .collect(Collectors.toList());

    UnitProgressData currentUnitProgressData = UnitProgressData.builder().unitProgresses(executionUnits).build();
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

  public StepResponse handleTaskResult(
      Ambiance ambiance, StepBaseParameters stepParameters, ElastigroupRollbackTaskResponse response) {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    List<UnitProgress> unitProgresses =
        response.getUnitProgressData() == null ? emptyList() : response.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    stepResponseBuilder.status(StepUtils.getStepStatus(response.getStatus()));

    if (response.getStatus() != CommandExecutionStatus.SUCCESS) {
      FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
      failureInfoBuilder.addFailureData(FailureData.newBuilder()
                                            .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                            .setLevel(Level.ERROR.name())
                                            .setCode(GENERAL_ERROR.name())
                                            .setMessage(response.getErrorMessage()));
      stepResponseBuilder.failureInfo(failureInfoBuilder.build());
    } else {
      elastigroupStepCommonHelper.saveSpotServerInstanceInfosToSweepingOutput(
          Collections.emptyList(), response.getEc2InstanceIdsExisting(), ambiance);
    }

    return stepResponseBuilder.build();
  }
}
