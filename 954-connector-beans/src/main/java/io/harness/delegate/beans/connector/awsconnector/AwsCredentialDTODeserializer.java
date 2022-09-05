/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.DX)
public class AwsCredentialDTODeserializer extends StdDeserializer<AwsCredentialDTO> {
  public AwsCredentialDTODeserializer() {
    super(io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO.class);
  }

  protected AwsCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AwsCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");
    JsonNode crossAccNode = parentJsonNode.get("crossAccountAccess");
    JsonNode regionNode = parentJsonNode.get("region");

    AwsCredentialType type = getType(typeNode);
    AwsCredentialSpecDTO awsCredentialSpecDTO = null;
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();

    CrossAccountAccessDTO crossAccountAccessDTO = null;
    if (crossAccNode != null && !crossAccNode.isNull()) {
      crossAccountAccessDTO = mapper.readValue(crossAccNode.toString(), CrossAccountAccessDTO.class);
    }
    if (type == AwsCredentialType.MANUAL_CREDENTIALS) {
      awsCredentialSpecDTO = mapper.readValue(authSpec.toString(), AwsManualConfigSpecDTO.class);
    } else if (type == AwsCredentialType.INHERIT_FROM_DELEGATE || type == AwsCredentialType.IRSA) {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the inherit from delegate type");
      }
    }
    String region = null;
    if (regionNode != null && !regionNode.isNull()) {
      region = regionNode.asText();
    }

    return AwsCredentialDTO.builder()
        .awsCredentialType(type)
        .config(awsCredentialSpecDTO)
        .crossAccountAccess(crossAccountAccessDTO)
        .testRegion(region)
        .build();
  }

  AwsCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return AwsCredentialType.fromString(typeValue);
  }
}
