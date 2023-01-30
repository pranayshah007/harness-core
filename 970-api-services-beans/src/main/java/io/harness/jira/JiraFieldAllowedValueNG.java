/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraFieldAllowedValueDeserializer.class)
public class JiraFieldAllowedValueNG {
  String id;
  String name;
  String value;

  public JiraFieldAllowedValueNG(JsonNode node) {
    this.id = JsonNodeUtils.getString(node, "id");
    this.name = JsonNodeUtils.getString(node, "name");
    this.value = JsonNodeUtils.getString(node, "value");
  }

  public boolean matchesValue(String value) {
    return value != null && (value.equals(this.id) || value.equals(this.name) || value.equals(this.value));
  }

  public String displayValue() {
    return value == null ? (name == null ? id : name) : value;
  }

  public static JiraFieldAllowedValueNG fromStatus(JiraStatusNG status) {
    return JiraFieldAllowedValueNG.builder().id(status.getId()).name(status.getName()).build();
  }
}
