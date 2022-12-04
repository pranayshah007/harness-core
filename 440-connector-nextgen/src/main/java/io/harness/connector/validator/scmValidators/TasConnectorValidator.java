/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.connector.validator.scmValidators;

import static software.wings.beans.TaskType.VALIDATE_TAS_CONNECTOR_TASK_NG;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.validator.AbstractCloudProviderConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialSpecDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.delegate.beans.connector.tasconnector.TasTaskParams;
import io.harness.delegate.beans.connector.tasconnector.TasTaskType;
import io.harness.delegate.task.TaskParameters;

public class TasConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    TasConnectorDTO connectorDTO = (TasConnectorDTO) connectorConfig;
    final TasCredentialSpecDTO tasCredentialSpecDTO =
        connectorDTO.getCredential().getType() == TasCredentialType.MANUAL_CREDENTIALS
        ? ((TasManualDetailsDTO) connectorDTO.getCredential().getSpec())
        : null;
    return TasTaskParams.builder()
        .tasTaskType(TasTaskType.VALIDATE)
        .tasConnectorDTO(connectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(tasCredentialSpecDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }
  @Override
  public String getTaskType() {
    return VALIDATE_TAS_CONNECTOR_TASK_NG.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return super.validate(connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}
