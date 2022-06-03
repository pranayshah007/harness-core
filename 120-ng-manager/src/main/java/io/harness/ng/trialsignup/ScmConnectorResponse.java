package io.harness.ng.trialsignup;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "ScmConnectorResponse", description = "ScmConnectorResponse")
public class ScmConnectorResponse {
  ConnectorResponseDTO connectorResponseDTO;
  SecretResponseWrapper secretResponseWrapper;
  ConnectorValidationResult connectorValidationResult;
}
