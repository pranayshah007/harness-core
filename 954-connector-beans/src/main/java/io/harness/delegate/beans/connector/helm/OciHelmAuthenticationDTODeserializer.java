/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class OciHelmAuthenticationDTODeserializer extends StdDeserializer<OciHelmAuthenticationDTO> {
  public OciHelmAuthenticationDTODeserializer() {
    super(OciHelmAuthenticationDTODeserializer.class);
  }

  public OciHelmAuthenticationDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public OciHelmAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    OciHelmAuthType type = getType(typeNode);
    OciHelmAuthCredentialsDTO helmAuthCredentials = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == OciHelmAuthType.USER_PASSWORD) {
      helmAuthCredentials = mapper.readValue(authSpec.toString(), OciHelmUsernamePasswordDTO.class);
    } else if (type == OciHelmAuthType.ANONYMOUS) {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the anonymous type");
      }
    }

    return OciHelmAuthenticationDTO.builder().authType(type).credentials(helmAuthCredentials).build();
  }

  OciHelmAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return OciHelmAuthType.fromString(typeValue);
  }
}
