/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
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
public class ServiceNowFieldNG {
  @NotNull String key;
  @NotNull String name;
  boolean required;
  boolean isCustom;
  @NotNull ServiceNowFieldSchemaNG schema;
  // This is internal type returned by serviceNow API
  String internalType;
  @Builder.Default @NotNull List<ServiceNowFieldAllowedValueNG> allowedValues = new ArrayList<>();

  public ServiceNowFieldNG() {
    this.allowedValues = new ArrayList<>();
  }

  private ServiceNowFieldNG(String key, JsonNode node) {
    this.key = JsonNodeUtils.getString(node, "key", key);
    this.name = JsonNodeUtils.mustGetString(node, "name");
    this.required = JsonNodeUtils.getBoolean(node, "required", false);
    this.isCustom = this.key.startsWith("customfield_");
    this.schema = new ServiceNowFieldSchemaNG(node.get("schema"));
    this.allowedValues = new ArrayList<>();
    addAllowedValues(node.get("allowedValues"));
    this.internalType = JsonNodeUtils.getString(node, "internalType");
    if (isBlank(this.internalType)) {
      this.internalType = JsonNodeUtils.getString(node, "type");
    }
  }

  private void addAllowedValues(JsonNode node) {
    if (node == null || !node.isArray()) {
      return;
    }

    ArrayNode allowedValues = (ArrayNode) node;
    allowedValues.forEach(av -> this.allowedValues.add(new ServiceNowFieldAllowedValueNG(av)));
  }
}
