package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("DecryptedConnectorResponse")
@OwnedBy(DX)
@Schema(name = "DecryptedConnectorResponse",
    description = "This has the Decrypted Connector details along with its metadata.")
public class DecryptedConnectorResponseDTO {
  ConnectorResponseDTO connectorResponseDTO;
  Map<String, String> decryptedConnectorConfig;
}
