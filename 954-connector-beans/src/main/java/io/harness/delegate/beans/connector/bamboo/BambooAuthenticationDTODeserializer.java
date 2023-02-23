/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.bamboo;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class BambooAuthenticationDTODeserializer extends StdDeserializer<BambooAuthenticationDTO> {
  public BambooAuthenticationDTODeserializer() {
    super(BambooAuthenticationDTODeserializer.class);
  }

  public BambooAuthenticationDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public BambooAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    BambooAuthType type = getType(typeNode);
    BambooAuthCredentialsDTO bambooAuthentication = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == BambooAuthType.USER_PASSWORD) {
      bambooAuthentication = mapper.readValue(authSpec.toString(), BambooUserNamePasswordDTO.class);
    } else {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the anonymous type");
      }
    }

    return BambooAuthenticationDTO.builder().authType(type).credentials(bambooAuthentication).build();
  }

  BambooAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return BambooAuthType.fromString(typeValue);
  }
}
