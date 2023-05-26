/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.rancher;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class RancherConnectorConfigAuthCredentialsDTOSerializer
    extends StdDeserializer<RancherConnectorConfigAuthCredentialsDTO> {
  public RancherConnectorConfigAuthCredentialsDTOSerializer() {
    super(RancherConnectorConfigAuthCredentialsDTOSerializer.class);
  }

  public RancherConnectorConfigAuthCredentialsDTOSerializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public RancherConnectorConfigAuthCredentialsDTO deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    JsonNode parentJsonNode = jsonParser.getCodec().readTree(jsonParser);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode specNode = parentJsonNode.get("spec");

    RancherAuthType type = getType(typeNode);
    ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();

    if (type == RancherAuthType.BEARER_TOKEN) {
      RancherConnectorConfigAuthenticationSpecDTO authenticationDTO =
          mapper.readValue(specNode.toString(), RancherConnectorBearerTokenAuthenticationDTO.class);
      return RancherConnectorConfigAuthCredentialsDTO.builder().auth(authenticationDTO).authType(type).build();
    }

    throw new InvalidRequestException(format("Unsupported rancher auth type %s", type));
  }

  private RancherAuthType getType(JsonNode typeNode) {
    return RancherAuthType.fromString(typeNode.asText());
  }
}
