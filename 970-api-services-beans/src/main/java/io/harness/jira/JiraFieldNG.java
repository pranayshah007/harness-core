/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraFieldNG {
  @NotNull String key;
  @NotNull String name;
  boolean required;
  boolean isCustom;
  @NotNull JiraFieldSchemaNG schema;
  @Builder.Default @NotNull List<JiraFieldAllowedValueNG> allowedValues = new ArrayList<>();

  public JiraFieldNG() {
    this.allowedValues = new ArrayList<>();
  }

  private JiraFieldNG(String key, JsonNode node) {
    this.key = JsonNodeUtils.getString(node, "key", key);
    this.name = JsonNodeUtils.mustGetString(node, "name");
    this.required = JsonNodeUtils.getBoolean(node, "required", false);
    this.isCustom = this.key.startsWith("customfield_");
    this.schema = new JiraFieldSchemaNG(node.get("schema"));
    this.allowedValues = new ArrayList<>();
    addAllowedValues(node.get("allowedValues"));
  }

  private void addAllowedValues(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode allowedValues = (ArrayNode) node;
    allowedValues.forEach(av -> this.allowedValues.add(new JiraFieldAllowedValueNG(av)));
  }

  public static void addFields(Map<String, JiraFieldNG> fields, String key, JsonNode node) {
    JiraFieldNG field;
    try {
      field = new JiraFieldNG(key, node);
    } catch (InvalidArgumentsException ignored) {
      return;
    }

    if (field.getSchema().getType() != JiraFieldTypeNG.TIME_TRACKING) {
      fields.put(field.getName(), field);
      return;
    }

    // Special handling for timetracking field. Instead of having a `Time Tracking` field, we'll have 2 string fields
    // `Original Estimate` and `Remaining Estimate`.
    field.setAllowedValues(null);
    field.setSchema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.STRING).build());
    JiraFieldNG clone = JsonUtils.clone(field, JiraFieldNG.class);

    field.setKey(JiraConstantsNG.ORIGINAL_ESTIMATE_KEY);
    field.setName(JiraConstantsNG.ORIGINAL_ESTIMATE_NAME);
    fields.put(field.getName(), field);

    clone.setKey(JiraConstantsNG.REMAINING_ESTIMATE_KEY);
    clone.setName(JiraConstantsNG.REMAINING_ESTIMATE_NAME);
    fields.put(clone.getName(), clone);
  }

  public static void addStatusField(Map<String, JiraFieldNG> fields, List<JiraStatusNG> statuses) {
    JiraFieldNG field =
        JiraFieldNG.builder()
            .key(JiraConstantsNG.STATUS_KEY)
            .name(JiraConstantsNG.STATUS_NAME)
            .schema(JiraFieldSchemaNG.builder().type(JiraFieldTypeNG.OPTION).build())
            .allowedValues(statuses == null
                    ? Collections.emptyList()
                    : statuses.stream().map(JiraFieldAllowedValueNG::fromStatus).collect(Collectors.toList()))
            .build();
    fields.put(field.getName(), field);
  }
}
