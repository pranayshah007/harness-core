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
import io.harness.jira.deserializer.JiraProjectDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraProjectDeserializer.class)
public class JiraProjectNG extends JiraProjectBasicNG {
  @JsonProperty("issuetypes") @NotNull Map<String, JiraIssueTypeNG> issueTypes = new HashMap<>();

  public JiraProjectNG(JsonNode node) {
    super(node);
    addIssueTypes(node.get("issuetypes"));
  }

  private void addIssueTypes(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode issueTypes = (ArrayNode) node;
    issueTypes.forEach(it -> {
      JiraIssueTypeNG issueType = new JiraIssueTypeNG(it);
      this.issueTypes.put(issueType.getName(), issueType);
    });
  }

  public void updateStatuses(List<JiraStatusNG> statuses) {
    this.issueTypes.values().forEach(it -> it.updateStatuses(statuses));
  }

  public void updateProjectStatuses(List<JiraIssueTypeNG> projectStatuses) {
    if (EmptyPredicate.isEmpty(projectStatuses)) {
      return;
    }

    Map<String, JiraIssueTypeNG> map =
        projectStatuses.stream().collect(Collectors.toMap(JiraIssueTypeNG::getId, Function.identity()));
    this.issueTypes.values().forEach(it -> {
      JiraIssueTypeNG issueTypeStatuses = map.get(it.getId());
      if (issueTypeStatuses != null) {
        it.updateStatuses(issueTypeStatuses.getStatuses());
      }
    });
  }

  public void addField(JiraFieldNG field) {
    this.issueTypes.values().forEach(it -> it.addField(field));
  }

  public void removeField(String fieldName) {
    this.issueTypes.values().forEach(it -> it.removeField(fieldName));
  }
}
