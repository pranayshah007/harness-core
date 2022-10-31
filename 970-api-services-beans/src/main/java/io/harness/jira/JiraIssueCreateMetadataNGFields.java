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
import io.harness.exception.InvalidArgumentsException;
import io.harness.jira.deserializer.JiraIssueCreateMetadataFieldsDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
@JsonDeserialize(using = JiraIssueCreateMetadataFieldsDeserializer.class)
public class JiraIssueCreateMetadataNGFields {
  @JsonProperty("values") @NotNull Map<String, JiraFieldNG> fields = new HashMap<>();
  @NotNull List<JiraStatusNG> statuses = new ArrayList<>();
  private static final String REPORTER_FIELD = "reporter";

  public JiraIssueCreateMetadataNGFields(JsonNode node) {
    updateStatuses(node.get("statuses"));
    addFields(node.get("values"));
  }

  private void addFields(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode fields = (ArrayNode) node;
    JiraFieldNG.addStatusField(this.fields, statuses);
    fields.forEach(field -> {
      JiraFieldNG jiraFieldNG;
      try {
        jiraFieldNG = new JiraFieldNG(field.get("fieldId").asText(), field);
      } catch (InvalidArgumentsException ignored) {
        return;
      }
      this.fields.put(jiraFieldNG.getName(), jiraFieldNG);
    });
  }
  private void updateStatuses(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode statuses = (ArrayNode) node;
    statuses.forEach(s -> this.statuses.add(new JiraStatusNG(s)));
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
