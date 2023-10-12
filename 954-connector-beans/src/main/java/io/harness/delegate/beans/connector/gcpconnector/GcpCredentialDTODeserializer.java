/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class GcpCredentialDTODeserializer extends StdDeserializer<GcpConnectorCredentialDTO> {
  public GcpCredentialDTODeserializer() {
    super(io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO.class);
  }
  protected GcpCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public GcpConnectorCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    GcpCredentialType type = getType(typeNode);
    GcpCredentialSpecDTO gcpCredentialSpecDTO = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == GcpCredentialType.MANUAL_CREDENTIALS) {
      gcpCredentialSpecDTO = mapper.readValue(authSpec.toString(), GcpManualDetailsDTO.class);
    } else if (type == GcpCredentialType.INHERIT_FROM_DELEGATE) {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the inherit from delegate type");
      }
    } else if (type == GcpCredentialType.OIDC_AUTHENTICATION) {
      gcpCredentialSpecDTO = mapper.readValue(authSpec.toString(), GcpOidcDetailsDTO.class);
    }

    return GcpConnectorCredentialDTO.builder().gcpCredentialType(type).config(gcpCredentialSpecDTO).build();
  }

  GcpCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return GcpCredentialType.fromString(typeValue);
  }
}
