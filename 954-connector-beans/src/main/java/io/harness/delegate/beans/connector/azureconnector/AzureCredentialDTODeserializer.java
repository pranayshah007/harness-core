/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDP)
public class AzureCredentialDTODeserializer extends StdDeserializer<AzureCredentialDTO> {
  public AzureCredentialDTODeserializer() {
    super(AzureCredentialDTO.class);
  }
  protected AzureCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AzureCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    AzureCredentialType type = getType(typeNode);
    AzureCredentialSpecDTO azureCredentialSpecDTO = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == AzureCredentialType.MANUAL_CREDENTIALS) {
      azureCredentialSpecDTO = mapper.readValue(authSpec.toString(), AzureManualDetailsDTO.class);
    } else if (type == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      azureCredentialSpecDTO = mapper.readValue(authSpec.toString(), AzureInheritFromDelegateDetailsDTO.class);
    }

    return AzureCredentialDTO.builder().azureCredentialType(type).config(azureCredentialSpecDTO).build();
  }

  AzureCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return AzureCredentialType.fromString(typeValue);
  }
}
