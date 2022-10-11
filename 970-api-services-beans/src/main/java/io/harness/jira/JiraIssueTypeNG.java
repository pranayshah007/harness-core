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
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraIssueTypeDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
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
@JsonDeserialize(using = JiraIssueTypeDeserializer.class)
public class JiraIssueTypeNG {
  @NotNull String id;
  @NotNull String name;
  String description;
  boolean isSubTask;
  @NotNull List<JiraStatusNG> statuses = new ArrayList<>();
  @NotNull Map<String, JiraFieldNG> fields = new HashMap<>();

  public JiraIssueTypeNG(JsonNode node) {
    this.id = JsonNodeUtils.mustGetString(node, "id");
    this.name = JsonNodeUtils.mustGetString(node, "name");
    this.description = JsonNodeUtils.getString(node, "description");
    this.isSubTask = JsonNodeUtils.getBoolean(node, "subtask", false);
    updateStatuses(node.get("statuses"));
    addFields(node.get("fields"));
  }

  private void updateStatuses(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode statuses = (ArrayNode) node;
    statuses.forEach(s -> this.statuses.add(new JiraStatusNG(s)));
  }

  private void addFields(JsonNode node) {
    if (node == null || !node.isObject()) {
      return;
    }

    ObjectNode fields = (ObjectNode) node;
    JiraFieldNG.addStatusField(this.fields, statuses);
    fields.fields().forEachRemaining(f -> JiraFieldNG.addFields(this.fields, f.getKey(), f.getValue()));
  }

  public void updateStatuses(List<JiraStatusNG> statuses) {
    if (EmptyPredicate.isEmpty(statuses)) {
      return;
    }

    this.statuses.addAll(statuses);
    // Add status as a field but first remove one if it's already present.
    this.fields.remove(JiraConstantsNG.STATUS_NAME);
    JiraFieldNG.addStatusField(this.fields, statuses);
  }

  public void addField(JiraFieldNG field) {
    if (field != null) {
      this.fields.put(field.getName(), field);
    }
  }

  public void removeField(String fieldName) {
    if (fieldName != null) {
      this.fields.remove(fieldName);
    }
  }
}
