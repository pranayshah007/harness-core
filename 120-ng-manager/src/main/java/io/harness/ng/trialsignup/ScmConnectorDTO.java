package io.harness.ng.trialsignup;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "ScmConnector", description = "Connector and secret details")
public class ScmConnectorDTO implements YamlDTO {
  @JsonProperty("secret") SecretDTOV2 secret;
  @JsonProperty("connector") ConnectorInfoDTO connectorInfo;
}
