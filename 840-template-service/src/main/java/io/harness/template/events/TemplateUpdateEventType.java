/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.template.TemplateEntityConstants.OTHERS;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_CHANGE_SCOPE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_CREATE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_LAST_UPDATED_FALSE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_LAST_UPDATED_TRUE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_STABLE_FALSE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_STABLE_TRUE;
import static io.harness.ng.core.template.TemplateEntityConstants.TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateListType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(CDC)
public enum TemplateUpdateEventType {
  @JsonProperty(TEMPLATE_STABLE_TRUE) TEMPLATE_STABLE_TRUE_EVENT(TEMPLATE_STABLE_TRUE),
  @JsonProperty(TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE)
  TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE_EVENT(TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE),
  @JsonProperty(TEMPLATE_STABLE_FALSE) TEMPLATE_STABLE_FALSE_EVENT(TEMPLATE_STABLE_FALSE),
  @JsonProperty(TEMPLATE_LAST_UPDATED_TRUE) TEMPLATE_LAST_UPDATED_TRUE_EVENT(TEMPLATE_LAST_UPDATED_TRUE),
  @JsonProperty(TEMPLATE_LAST_UPDATED_FALSE) TEMPLATE_LAST_UPDATED_FALSE_EVENT(TEMPLATE_LAST_UPDATED_FALSE),
  @JsonProperty(TEMPLATE_CHANGE_SCOPE) TEMPLATE_CHANGE_SCOPE_EVENT(TEMPLATE_CHANGE_SCOPE),
  @JsonProperty(TEMPLATE_CREATE) TEMPLATE_CREATE_EVENT(TEMPLATE_CREATE),
  @JsonProperty(OTHERS) OTHERS_EVENT(OTHERS);

  private final String yamlType;

  TemplateUpdateEventType(String yamlType) {
    this.yamlType = yamlType;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TemplateUpdateEventType getTemplateType(@JsonProperty("type") String yamlType) {
    for (TemplateUpdateEventType value : TemplateUpdateEventType.values()) {
      if (value.yamlType.equalsIgnoreCase(yamlType)) {
        return value;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", yamlType, Arrays.toString(TemplateListType.values())));
  }

  @Override
  @JsonValue
  public String toString() {
    return this.yamlType;
  }

  public String fetchYamlType() {
    return yamlType;
  }
}
