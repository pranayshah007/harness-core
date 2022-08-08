/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira.deserializer;

import io.harness.jira.JiraInstanceData;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class JiraInstanceDataDeserializer extends StdDeserializer<JiraInstanceData> {
  public JiraInstanceDataDeserializer() {
    this(null);
  }

  public JiraInstanceDataDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public JiraInstanceData deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    return new JiraInstanceData(node);
  }
}
