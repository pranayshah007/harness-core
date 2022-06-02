package io.harness.jira.deserializer;

import io.harness.jira.JiraUserData;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class JiraUserDataDeserializer extends StdDeserializer<JiraUserData> {
  public JiraUserDataDeserializer() {
    this(null);
  }

  public JiraUserDataDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public JiraUserData deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    return new JiraUserData(node);
  }
}
