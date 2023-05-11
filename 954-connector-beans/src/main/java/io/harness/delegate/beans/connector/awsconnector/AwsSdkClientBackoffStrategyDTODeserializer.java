/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDP)
public class AwsSdkClientBackoffStrategyDTODeserializer extends StdDeserializer<AwsSdkClientBackoffStrategyDTO> {
  public AwsSdkClientBackoffStrategyDTODeserializer() {
    super(AwsSdkClientBackoffStrategyDTO.class);
  }

  protected AwsSdkClientBackoffStrategyDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AwsSdkClientBackoffStrategyDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    AwsSdkClientBackoffStrategyType type = getType(typeNode);
    AwsSdkClientBackoffStrategySpecDTO awsSdkClientBackoffStrategySpecDTO = null;
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();

    if (type == AwsSdkClientBackoffStrategyType.FIXED_DELAY_BACKOFF_STRATEGY) {
      awsSdkClientBackoffStrategySpecDTO =
          mapper.readValue(authSpec.toString(), AwsFixedDelayBackoffStrategySpecDTO.class);
    } else if (type == AwsSdkClientBackoffStrategyType.EQUAL_JITTER_BACKOFF_STRATEGY) {
      awsSdkClientBackoffStrategySpecDTO =
          mapper.readValue(authSpec.toString(), AwsEqualJitterBackoffStrategySpecDTO.class);
    } else if (type == AwsSdkClientBackoffStrategyType.FULL_JITTER_BACKOFF_STRATEGY) {
      awsSdkClientBackoffStrategySpecDTO =
          mapper.readValue(authSpec.toString(), AwsFullJitterBackoffStrategySpecDTO.class);
    }

    return AwsSdkClientBackoffStrategyDTO.builder()
        .awsSdkClientBackoffStrategyType(type)
        .backoffStrategyConfig(awsSdkClientBackoffStrategySpecDTO)
        .build();
  }

  AwsSdkClientBackoffStrategyType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return AwsSdkClientBackoffStrategyType.fromString(typeValue);
  }
}
