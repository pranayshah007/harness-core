/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDP)
public class TasCredentialDTODeserializer extends StdDeserializer<TasCredentialDTO> {
  public TasCredentialDTODeserializer() {
    super(TasCredentialDTO.class);
  }
  protected TasCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public TasCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    TasCredentialType type = getType(typeNode);
    TasCredentialSpecDTO tasCredentialSpecDTO = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == TasCredentialType.MANUAL_CREDENTIALS) {
      tasCredentialSpecDTO = mapper.readValue(authSpec.toString(), TasManualDetailsDTO.class);
    }
    return TasCredentialDTO.builder().type(type).spec(tasCredentialSpecDTO).build();
  }

  TasCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return TasCredentialType.fromString(typeValue);
  }
}
