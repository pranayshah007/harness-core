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
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.PIPELINE)
public class EdgeCaseRegexTextSerializer extends StdSerializer<TextNode> {
  public EdgeCaseRegexTextSerializer() {
    super(TextNode.class);
  }

  @Override
  public void serialize(TextNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    provider.defaultSerializeValue(value.asText(), gen);
  }
}
