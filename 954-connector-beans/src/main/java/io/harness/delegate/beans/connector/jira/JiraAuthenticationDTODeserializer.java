/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(CDC)
public class JiraAuthenticationDTODeserializer extends StdDeserializer<JiraAuthenticationDTO> {
  public JiraAuthenticationDTODeserializer() {
    super(io.harness.delegate.beans.connector.jira.JiraAuthenticationDTODeserializer.class);
  }

  public JiraAuthenticationDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public JiraAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    JiraAuthType type = getType(typeNode);
    JiraAuthCredentialsDTO jiraAuthCredentials = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (authSpec == null || authSpec.isNull()) {
      throw new InvalidRequestException(
          String.format("Missing spec for %s jira auth type", JiraAuthType.USER_PASSWORD));
    }
    if (type == JiraAuthType.USER_PASSWORD) {
      jiraAuthCredentials = mapper.readValue(authSpec.toString(), JiraUserNamePasswordDTO.class);
    }
    if (type == JiraAuthType.PAT) {
      jiraAuthCredentials = mapper.readValue(authSpec.toString(), JiraPATDTO.class);
    }
    // add condition for future types here
    return JiraAuthenticationDTO.builder().authType(type).credentials(jiraAuthCredentials).build();
  }

  JiraAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return JiraAuthType.fromString(typeValue);
  }
}
