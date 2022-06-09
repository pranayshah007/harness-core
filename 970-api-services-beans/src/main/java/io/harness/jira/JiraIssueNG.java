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
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraIssueDeserializer;
import io.harness.serializer.JsonUtils;
import io.harness.servicenow.TicketNG;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraIssueDeserializer.class)
public class JiraIssueNG implements TicketNG {
  String url;
  @NotNull String restUrl;
  @NotNull String id;
  @NotNull String key;
  @NotNull Map<String, Object> fields = new HashMap<>();

  public JiraIssueNG(JsonNode node) {
    this.restUrl = JsonNodeUtils.mustGetString(node, "self");
    this.id = JsonNodeUtils.mustGetString(node, "id");
    this.key = JsonNodeUtils.mustGetString(node, "key");
    this.fields.put("restUrl", this.restUrl);
    this.fields.put("id", this.id);
    this.fields.put("key", this.key);

    Map<String, JsonNode> names = JsonNodeUtils.getMap(node, "names");
    Map<String, JsonNode> schema = JsonNodeUtils.getMap(node, "schema");
    Map<String, JsonNode> fieldValues = JsonNodeUtils.getMap(node, "fields");

    Set<String> fieldKeys = Sets.intersection(Sets.intersection(names.keySet(), schema.keySet()), fieldValues.keySet());
    fieldKeys.forEach(key -> addKey(key, names.get(key), schema.get(key), fieldValues.get(key)));
  }

  public void updateJiraBaseUrl(String baseUrl) {
    this.url = JiraIssueUtilsNG.prepareIssueUrl(baseUrl, this.key);
    this.fields.put("url", this.url);
  }

  private void addKey(String key, JsonNode nameNode, JsonNode schemaNode, JsonNode valueNode) {
    if (EmptyPredicate.isEmpty(key)) {
      return;
    }

    String name = nameNode.textValue();
    String typeStr = JsonNodeUtils.mustGetString(schemaNode, "type");
    switch (typeStr) {
      case JiraConstantsNG.PROJECT_KEY:
        addProjectFields(name, valueNode);
        return;
      case JiraConstantsNG.ISSUE_TYPE_KEY:
        addIssueTypeFields(name, valueNode);
        return;
      case JiraConstantsNG.STATUS_KEY:
        addStatusFields(name, valueNode);
        return;
      default:
        // continue
    }

    JiraFieldSchemaNG schema;
    try {
      schema = new JiraFieldSchemaNG(schemaNode);
    } catch (InvalidArgumentsException ignored) {
      return;
    }

    if (!schema.isArray()) {
      Object value = convertToFinalValue(schema.getType(), valueNode);
      if (value == null) {
        return;
      }
      if (value instanceof JiraTimeTrackingFieldNG) {
        // Special handling for timetracking field.
        ((JiraTimeTrackingFieldNG) value).addToFields(fields);
      } else {
        // Refer to -  https://harness.atlassian.net/browse/CDS-38402
        if (fields.containsKey(name)) {
          log.warn("Jira Issue: Found already existing name - {}. Skipping.", name);
        }
        fields.putIfAbsent(name, value);
      }
      return;
    }

    List<Object> arr = new ArrayList<>();
    if (valueNode instanceof ArrayNode) {
      valueNode.forEach(vn -> {
        Object value = convertToFinalValue(schema.getType(), vn);
        // NOTE: timetracking field ignored inside of an array.
        if (value != null && !(value instanceof JiraTimeTrackingFieldNG)) {
          arr.add(value);
        }
      });
    }
    fields.put(name, arr);
  }

  private void addProjectFields(String name, JsonNode valueNode) {
    // Returns 3 field - "Project Key", "Project Name", "__project" (whole object for internal use)
    String projectKey = JsonNodeUtils.mustGetString(valueNode, "key");
    fields.put(name + " Key", projectKey);
    String projectName = JsonNodeUtils.mustGetString(valueNode, "name");
    fields.put(name + " Name", projectName);
    fields.put(JiraConstantsNG.PROJECT_INTERNAL_NAME, JsonUtils.treeToValue(valueNode, Object.class));
  }

  private void addIssueTypeFields(String name, JsonNode valueNode) {
    // Returns 2 fields - "Issue Type", "__issuetype" (whole object for internal use)
    String issueTypeName = JsonNodeUtils.mustGetString(valueNode, "name");
    fields.put(name, issueTypeName);
    fields.put(JiraConstantsNG.ISSUE_TYPE_INTERNAL_NAME, JsonUtils.treeToValue(valueNode, Object.class));
  }

  private void addStatusFields(String name, JsonNode valueNode) {
    // Returns 2 fields - "Status", "__status" (whole object for internal use)
    String statusName = JsonNodeUtils.mustGetString(valueNode, "name");
    fields.put(name, statusName);
    fields.put(JiraConstantsNG.STATUS_INTERNAL_NAME, JsonUtils.treeToValue(valueNode, Object.class));
  }

  private static Object convertToFinalValue(JiraFieldTypeNG type, JsonNode valueNode) {
    if (valueNode == null) {
      return null;
    }

    switch (type) {
      case STRING:
        return valueNode.textValue();
      case NUMBER:
        return valueNode.doubleValue();
      case DATE:
        return Date.from(LocalDate.parse(valueNode.textValue(), JiraConstantsNG.DATE_FORMATTER)
                             .atStartOfDay(ZoneOffset.UTC)
                             .toInstant());
      case DATETIME:
        return Date.from(ZonedDateTime.parse(valueNode.textValue(), JiraConstantsNG.DATETIME_FORMATTER).toInstant());
      case OPTION:
        return convertOptionToFinalValue(type, valueNode);
      case TIME_TRACKING:
        return JsonUtils.treeToValue(valueNode, JiraTimeTrackingFieldNG.class);
      default:
        return null;
    }
  }

  private static Object convertOptionToFinalValue(JiraFieldTypeNG type, JsonNode valueNode) {
    String valueStr = JsonNodeUtils.getString(valueNode, "value", null);
    return valueStr == null ? JsonNodeUtils.getString(valueNode, "name", null) : valueStr;
  }
}
