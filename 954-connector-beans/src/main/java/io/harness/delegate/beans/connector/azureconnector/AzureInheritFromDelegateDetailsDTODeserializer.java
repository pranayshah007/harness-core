/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDP)
public class AzureInheritFromDelegateDetailsDTODeserializer
    extends StdDeserializer<AzureInheritFromDelegateDetailsDTO> {
  public AzureInheritFromDelegateDetailsDTODeserializer() {
    super(AzureInheritFromDelegateDetailsDTO.class);
  }
  protected AzureInheritFromDelegateDetailsDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AzureInheritFromDelegateDetailsDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode authNode = parentJsonNode.get("auth");
    String auth = String.valueOf(authNode);
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();

    if (EmptyPredicate.isNotEmpty(auth)) {
      AzureManagedIdentityType type = getType(authNode.get("type"));

      switch (type) {
        case USER_ASSIGNED_MANAGED_IDENTITY: {
          return AzureInheritFromDelegateDetailsDTO.builder()
              .authDTO(mapper.readValue(auth, AzureMSIAuthUADTO.class))
              .build();
        }
        case SYSTEM_ASSIGNED_MANAGED_IDENTITY: {
          return AzureInheritFromDelegateDetailsDTO.builder()
              .authDTO(mapper.readValue(auth, AzureMSIAuthSADTO.class))
              .build();
        }
        default: {
          return null;
        }
      }
    }

    return null;
  }

  AzureManagedIdentityType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return AzureManagedIdentityType.fromString(typeValue);
  }
}
