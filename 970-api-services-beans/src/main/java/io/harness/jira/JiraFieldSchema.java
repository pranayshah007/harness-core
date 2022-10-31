package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Data;

@OwnedBy(CDC)
@Data
public class JiraFieldSchema {
  @NotNull String type;

  public JiraFieldSchema(JiraFieldSchemaNG jiraFieldSchemaNG) {
    this.type = jiraFieldSchemaNG.getTypeStr();
    if (jiraFieldSchemaNG.isArray()) {
      this.type = "array";
    }
  }
}
