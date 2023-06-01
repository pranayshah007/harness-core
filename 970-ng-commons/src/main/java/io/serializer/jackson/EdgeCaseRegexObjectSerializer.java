/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.serializer.jackson;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.PIPELINE)
public class EdgeCaseRegexObjectSerializer extends StdSerializer<ObjectNode> {
  public EdgeCaseRegexObjectSerializer() {
    super(ObjectNode.class);
  }
  @Override
  public void serialize(ObjectNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    gen.writeStartObject();
    value.fields().forEachRemaining(entry -> {
      String key = entry.getKey();
      JsonNode jsonNode = entry.getValue();
      try {
        gen.writeFieldName(key);
        provider.defaultSerializeValue(jsonNode, gen);
      } catch (IOException e) {
        throw new RuntimeException("Failed to serialize jsonNode", e);
      }
    });
    gen.writeEndObject();
  }
}