/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * Use ServiceNowFieldNGUtils to parse ServiceNowFieldNG from jsonNode.
 *
 * Ref: 125-cd-nextgen/src/main/java/io/harness/cdng/servicenow/utils/ServiceNowFieldNGUtils.java
 * */
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
}
