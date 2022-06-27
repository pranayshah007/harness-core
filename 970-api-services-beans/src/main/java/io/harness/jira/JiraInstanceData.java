package io.harness.jira;

import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraInstanceDataDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraInstanceDataDeserializer.class)
public class JiraInstanceData {
  public JiraInstanceData(JsonNode node) {
    this.deploymentType = JiraDeploymentType.valueOf(JsonNodeUtils.mustGetString(node, "deploymentType").toUpperCase());
  }

  public JiraInstanceData(JiraDeploymentType deploymentType) {
    this.deploymentType = deploymentType;
  }

  public JiraDeploymentType deploymentType;

  public enum JiraDeploymentType { SERVER, CLOUD }
}
