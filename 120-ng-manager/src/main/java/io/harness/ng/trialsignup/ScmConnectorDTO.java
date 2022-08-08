/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
