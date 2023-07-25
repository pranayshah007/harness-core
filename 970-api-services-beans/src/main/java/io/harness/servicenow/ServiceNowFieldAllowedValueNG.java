/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jira.JiraStatusNG;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ServiceNowFieldAllowedValueNG {
  String id;
  String name;
  String value;

  public boolean matchesValue(String value) {
    return value != null && (value.equals(this.id) || value.equals(this.name) || value.equals(this.value));
  }

  public String displayValue() {
    return value == null ? (name == null ? id : name) : value;
  }

  public static ServiceNowFieldAllowedValueNG fromStatus(JiraStatusNG status) {
    return ServiceNowFieldAllowedValueNG.builder().id(status.getId()).name(status.getName()).build();
  }
}
