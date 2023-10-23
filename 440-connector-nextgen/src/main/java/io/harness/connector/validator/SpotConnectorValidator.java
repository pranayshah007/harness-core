/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.delegate.beans.connector.spotconnector.SpotCredentialType.PERMANENT_TOKEN;
import static io.harness.delegate.beans.connector.spotconnector.SpotTaskType.VALIDATE;

import static software.wings.beans.TaskType.SPOT_TASK_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotTaskParams;
import io.harness.delegate.task.TaskParameters;

// Specific to Delegate side ConnectorDisconnectHandler validation
@OwnedBy(HarnessTeam.CDP)
public class SpotConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    SpotConnectorDTO connectorDTO = (SpotConnectorDTO) connectorConfig;
    final SpotPermanentTokenConfigSpecDTO spotCredentialDTO =
        connectorDTO.getCredential().getSpotCredentialType() == PERMANENT_TOKEN
        ? ((SpotPermanentTokenConfigSpecDTO) connectorDTO.getCredential().getConfig())
        : null;
    return SpotTaskParams.builder()
        .spotTaskType(VALIDATE)
        .spotConnector(connectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(spotCredentialDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return SPOT_TASK_NG.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return super.validate(connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  // For CCM implementation
  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}
