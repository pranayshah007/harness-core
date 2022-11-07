/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.spot.elastigroup.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpecType;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.cdng.common.capacity.PercentageCapacitySpec;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.OutputExpressionConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class ElastigroupDeployStepHelper extends CDStepHelper {
  @Inject private ElastigroupEntityHelper entityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public ElastigroupDeployTaskParameters getElastigroupDeployTaskParameters(
      ElastigroupDeployStepParameters stepParameters, Ambiance ambiance) {
    InfrastructureOutcome infrastructureOutcome = getInfrastructureOutcome(ambiance);

    ElastigroupSetupDataOutcome elastigroupSetupOutcome = getElastigroupSetupOutcome(ambiance);

    boolean isFinalDeployStep = isFinalDeployStep(stepParameters, elastigroupSetupOutcome);

    ElastiGroup newElastigroup = calculateNewForUpsize(
        stepParameters.getNewService(), elastigroupSetupOutcome.getNewElastiGroupOriginalConfig(), isFinalDeployStep);
    ElastiGroup oldElastigroup = calculateOldForDownsize(
        stepParameters.getOldService(), elastigroupSetupOutcome.getOldElastiGroupOriginalConfig(), isFinalDeployStep);

    return ElastigroupDeployTaskParameters.builder()
        .spotConnector(getSpotConnector(ambiance, infrastructureOutcome))
        .encryptionDetails(getEncryptionDetails(ambiance, infrastructureOutcome))
        .newElastigroup(newElastigroup)
        .oldElastigroup(oldElastigroup)
        .build();
  }

  private boolean isFinalDeployStep(
      ElastigroupDeployStepParameters stepParameters, ElastigroupSetupDataOutcome elastigroupSetupOutcome) {
    if (CapacitySpecType.COUNT.equals(stepParameters.getNewService().getType())) {
      CountCapacitySpec spec = (CountCapacitySpec) stepParameters.getNewService().getSpec();

      int requestedTarget = ParameterFieldHelper.getParameterFieldValue(spec.getCount());
      int setupTarget = elastigroupSetupOutcome.getNewElastiGroupOriginalConfig().getCapacity().getTarget();

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
    if (CapacitySpecType.COUNT.equals(requestedCapacity.getType())) {
      CountCapacitySpec spec = (CountCapacitySpec) requestedCapacity.getSpec();

      int requestedTarget = ParameterFieldHelper.getParameterFieldValue(spec.getCount());
      int setupTarget = result.getCapacity().getTarget();

      result.getCapacity().setTarget(Math.min(requestedTarget, setupTarget));
    } else if (CapacitySpecType.PERCENTAGE.equals(requestedCapacity.getType())) {
      PercentageCapacitySpec spec = (PercentageCapacitySpec) requestedCapacity.getSpec();

      int requestedPercentage = Math.min(ParameterFieldHelper.getParameterFieldValue(spec.getPercentage()), 100);
      int setupTarget = result.getCapacity().getTarget();

      int target = (int) Math.round((requestedPercentage * setupTarget) / 100.0);

      result.getCapacity().setTarget(Math.max(target, 1));
    } else {
      throw new InvalidRequestException("Unknown capacity type: " + requestedCapacity.getType());
    }

    if (!isFinalDeployStep) {
      forceElastigroupScale(result);
    }

    return result;
  }

  private ElastiGroup calculateOldForDownsize(
      Capacity requestedCapacity, ElastiGroup setupElastigroup, boolean isFinalDeployStep) {
    final ElastiGroup result = setupElastigroup.clone();
    if (result == null) {
      return null;
    }

    if (isFinalDeployStep) {
      scaleDownElastigroup(result);
    } else if (CapacitySpecType.COUNT.equals(requestedCapacity.getType())) {
      final CountCapacitySpec spec = (CountCapacitySpec) requestedCapacity.getSpec();

      int target = ParameterFieldHelper.getParameterFieldValue(spec.getCount());

      result.getCapacity().setTarget(target);
      result.getCapacity().setMinimum(target);
      result.getCapacity().setMaximum(target);
    } else if (CapacitySpecType.PERCENTAGE.equals(requestedCapacity.getType())) {
      final PercentageCapacitySpec spec = (PercentageCapacitySpec) requestedCapacity.getSpec();

      int requestedPercentage = Math.min(ParameterFieldHelper.getParameterFieldValue(spec.getPercentage()), 100);
      int setupTarget = result.getCapacity().getTarget();

      int target = (int) Math.round((requestedPercentage * setupTarget) / 100.0);

      result.getCapacity().setTarget(target);
      result.getCapacity().setMinimum(target);
      result.getCapacity().setMaximum(target);
    } else {
      throw new InvalidRequestException("Unknown capacity type: " + requestedCapacity.getType());
    }

    return result;
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

  private ElastigroupSetupDataOutcome getElastigroupSetupOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalSetupDataOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));
    if (!optionalSetupDataOutput.isFound()) {
      throw new InvalidRequestException("No elastigroup setup output found.");
    }
    return (ElastigroupSetupDataOutcome) optionalSetupDataOutput;
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
}
