/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.DX)
public class SpotCredentialDTODeserializer extends StdDeserializer<SpotCredentialDTO> {
  public SpotCredentialDTODeserializer() {
    super(SpotCredentialDTO.class);
  }

  protected SpotCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public SpotCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    SpotCredentialType type = getType(typeNode);
    SpotCredentialSpecDTO spotCredentialSpecDTO = null;
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();

    if (type == SpotCredentialType.PERMANENT_TOKEN) {
      spotCredentialSpecDTO = mapper.readValue(authSpec.toString(), SpotPermanentTokenConfigSpecDTO.class);
    }

    return SpotCredentialDTO.builder().spotCredentialType(type).config(spotCredentialSpecDTO).build();
  }

  SpotCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return SpotCredentialType.fromString(typeValue);
  }
}
