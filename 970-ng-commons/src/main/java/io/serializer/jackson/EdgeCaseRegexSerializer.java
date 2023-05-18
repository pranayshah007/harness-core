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
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.util.regex.Pattern;

@OwnedBy(HarnessTeam.PIPELINE)
// This serializer handles the edge cases when we need a string to be surrounded by quotes in the compiled yaml.
// UnitTests for these cases are added in TemplateYamlUtilsTest.java
public class EdgeCaseRegexSerializer extends StdSerializer<TextNode> {
  // The string is an SHA digest. If it's something like 97e0087, it matches the scientific notation and hence without
  // quotes, it gets converted to a number 9.7E88, to avoid this, we are adding quotes around the string which matches
  // following regex
  private Pattern shaRegex = Pattern.compile("[0-9]+[eE][0-9]+");
  // When string is of type +1234.23, it also needs to be wrapped around quotes, else, it'll be considered a number and
  // user won't be able to save a string variable with this value
  private Pattern positiveNumberRegex = Pattern.compile("[+][0-9]*(\\.[0-9]*)?");

  public EdgeCaseRegexSerializer() {
    super(TextNode.class);
  }

  @Override
  public void serialize(TextNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
    if (value != null) {
      String textValue = value.asText();
      if (shaRegex.matcher(textValue).matches() || positiveNumberRegex.matcher(textValue).matches()) {
        YAMLGenerator yamlGenerator = (YAMLGenerator) gen;
        yamlGenerator.disable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        yamlGenerator.writeString(textValue);
        yamlGenerator.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
      } else {
        provider.defaultSerializeValue(value.asText(), gen);
      }
    }
  }
}
