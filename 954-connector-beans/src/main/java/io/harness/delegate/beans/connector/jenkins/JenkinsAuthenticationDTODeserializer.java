/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jenkins;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class JenkinsAuthenticationDTODeserializer extends StdDeserializer<JenkinsAuthenticationDTO> {
  public JenkinsAuthenticationDTODeserializer() {
    super(JenkinsAuthenticationDTODeserializer.class);
  }

  public JenkinsAuthenticationDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public JenkinsAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    JenkinsAuthType type = getType(typeNode);
    JenkinsAuthCredentialsDTO jenkinsAuthentication = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == JenkinsAuthType.USER_PASSWORD) {
      jenkinsAuthentication = mapper.readValue(authSpec.toString(), JenkinsUserNamePasswordDTO.class);
    } else if (type == JenkinsAuthType.BEARER_TOKEN) {
      jenkinsAuthentication = mapper.readValue(authSpec.toString(), JenkinsBearerTokenDTO.class);
    } else {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the anonymous type");
      }
    }

    return JenkinsAuthenticationDTO.builder().authType(type).credentials(jenkinsAuthentication).build();
  }

  JenkinsAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return JenkinsAuthType.fromString(typeValue);
  }
}
