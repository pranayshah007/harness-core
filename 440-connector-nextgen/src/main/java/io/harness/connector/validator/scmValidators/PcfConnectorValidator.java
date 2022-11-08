package io.harness.connector.validator.scmValidators;

import static software.wings.beans.TaskType.VALIDATE_PCF_CONNECTOR_TASK_NG;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.validator.AbstractCloudProviderConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialSpecDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfTaskParams;
import io.harness.delegate.beans.connector.pcfconnector.PcfTaskType;
import io.harness.delegate.task.TaskParameters;

public class PcfConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    PcfConnectorDTO connectorDTO = (PcfConnectorDTO) connectorConfig;
    final PcfCredentialSpecDTO pcfCredentialSpecDTO =
        connectorDTO.getCredential().getType() == PcfCredentialType.MANUAL_CREDENTIALS
        ? ((PcfManualDetailsDTO) connectorDTO.getCredential().getSpec())
        : null;
    return PcfTaskParams.builder()
        .pcfTaskType(PcfTaskType.VALIDATE)
        .pcfConnector(connectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(pcfCredentialSpecDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }
  @Override
  public String getTaskType() {
    return VALIDATE_PCF_CONNECTOR_TASK_NG.name();
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
