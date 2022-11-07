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
import io.harness.cdng.infra.beans.InfrastructureOutcome;
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

    ElastiGroup newElastigroup = calculateNewForUpsize(
        stepParameters.getNewService(), elastigroupSetupOutcome.getNewElastiGroupOriginalConfig().clone());
    ElastiGroup oldElastigroup = calculateOldForDownsize(
        stepParameters.getOldService(), elastigroupSetupOutcome.getOldElastiGroupOriginalConfig().clone());

    return ElastigroupDeployTaskParameters.builder()
        .spotConnector(getSpotConnector(ambiance, infrastructureOutcome))
        .encryptionDetails(getEncryptionDetails(ambiance, infrastructureOutcome))
        .newElastigroup(newElastigroup)
        .oldElastigroup(oldElastigroup)
        .build();
  }

  private ElastiGroup calculateNewForUpsize(Capacity capacity, ElastiGroup elastiGroup) {
    if (CapacitySpecType.COUNT.equals(capacity.getType())) {
    } else if (CapacitySpecType.PERCENTAGE.equals(capacity.getType())) {
    } else {
      throw new InvalidRequestException("Unknown capacity type: " + capacity.getType());
    }
    return elastiGroup;
  }

  private ElastiGroup calculateOldForDownsize(Capacity capacity, ElastiGroup elastiGroup) {
    if (elastiGroup == null) {
      return null;
    }

    return elastiGroup;
  }

  private ElastigroupSetupDataOutcome getElastigroupSetupOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalInfraOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));
    if (!optionalInfraOutput.isFound()) {
      throw new InvalidRequestException("No infrastructure output found.");
    }
    return (ElastigroupSetupDataOutcome) optionalInfraOutput;
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
