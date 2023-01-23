/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraIssueCreateMetadataDeserializer.class)
public class JiraIssueCreateMetadataNG {
  Map<String, JiraProjectNG> projects = new HashMap<>();

  public JiraIssueCreateMetadataNG(JsonNode node) {
    addProjects(node.get("projects"));
  }

  private void addProjects(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode projects = (ArrayNode) node;
    projects.forEach(p -> {
      JiraProjectNG project = new JiraProjectNG(p);
      this.projects.put(project.getKey(), project);
    });
  }

  public void updateStatuses(List<JiraStatusNG> statuses) {
    if (EmptyPredicate.isEmpty(statuses)) {
      return;
    }
    this.projects.values().forEach(p -> p.updateStatuses(statuses));
  }

  public void updateProjectStatuses(String projectKey, List<JiraIssueTypeNG> projectStatuses) {
    if (EmptyPredicate.isEmpty(projectStatuses)) {
      return;
    }
    this.projects.values()
        .stream()
        .filter(p -> p.getKey().equals(projectKey))
        .forEach(p -> p.updateProjectStatuses(projectStatuses));
  }

  public void removeField(String fieldName) {
    this.projects.values().forEach(p -> p.removeField(fieldName));
  }

  public void addField(JiraFieldNG field) {
    this.projects.values().forEach(p -> p.addField(field));
  }
}
