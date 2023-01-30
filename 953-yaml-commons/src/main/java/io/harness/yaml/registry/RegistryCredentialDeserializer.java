/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.registry;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CI)
public class RegistryCredentialDeserializer extends StdDeserializer<RegistryCredential> {
  private static final String name = "name";
  private static final String match = "match";

  public RegistryCredentialDeserializer() {
    super(RegistryCredential.class);
  }

  protected RegistryCredentialDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public RegistryCredential deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode currentJsonNode = jp.getCodec().readTree(jp);
    if (currentJsonNode != null) {
      switch (currentJsonNode.getNodeType()) {
        case STRING:
          return RegistryCredential.builder()
              .name(getStringParameterField(currentJsonNode))
              .match(ParameterField.ofNull())
              .build();
        case OBJECT:
          return RegistryCredential.builder()
              .name(getStringParameterField(currentJsonNode.get(name)))
              .match(getStringParameterField(currentJsonNode.get(match)))
              .build();
        default:
          throw new InvalidRequestException("Invalid value given for registry credential");
      }
    }
    return RegistryCredential.builder().build();
  }

  private ParameterField<String> getStringParameterField(JsonNode node) {
    if (node != null) {
      String value = node.asText();
      if (NGExpressionUtils.isExpressionField(value)) {
        return ParameterField.createExpressionField(true, value, null, true);
      }
      return ParameterField.createValueField(value);
    }
    return ParameterField.ofNull();
  }
}
