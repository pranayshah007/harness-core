package io.harness.jira;

import io.harness.exception.InvalidArgumentsException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraUserDataDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraUserDataDeserializer.class)
public class JiraUserData {
  private String accountId;
  private String name;
  private String displayName;
  private boolean active;

  public JiraUserData(JsonNode node) {
    try {
      this.accountId = JsonNodeUtils.mustGetString(node, "accountId");
    } catch (InvalidArgumentsException ex) {
      this.accountId = JsonNodeUtils.mustGetString(node, "key");
      this.name = JsonNodeUtils.mustGetString(node, "name");
    }
    this.displayName = JsonNodeUtils.mustGetString(node, "displayName");
    this.active = JsonNodeUtils.mustGetBoolean(node, "active");
  }

  public JiraUserData(String accountId, String displayName, boolean active) {
    this.accountId = accountId;
    this.displayName = displayName;
    this.active = active;
  }
}
