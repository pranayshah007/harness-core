/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.template.TemplateEntityConstants.ALL;
import static io.harness.ng.core.template.TemplateEntityConstants.LAST_UPDATES_TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityConstants.STABLE_TEMPLATE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(CDC)
public enum TemplateListType {
  @JsonProperty(STABLE_TEMPLATE) STABLE_TEMPLATE_TYPE(STABLE_TEMPLATE),
  @JsonProperty(LAST_UPDATES_TEMPLATE) LAST_UPDATED_TEMPLATE_TYPE(LAST_UPDATES_TEMPLATE),
  @JsonProperty(ALL) ALL_TEMPLATE_TYPE(ALL);

  private final String yamlType;

  TemplateListType(String yamlType) {
    this.yamlType = yamlType;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TemplateListType getTemplateType(@JsonProperty("type") String yamlType) {
    for (TemplateListType value : TemplateListType.values()) {
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
}
