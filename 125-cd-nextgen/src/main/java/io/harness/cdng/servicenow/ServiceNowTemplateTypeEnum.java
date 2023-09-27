/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.servicenow.ServiceNowTemplateTypeEnum")
public enum ServiceNowTemplateTypeEnum {
  @JsonProperty("Form") FORM,
  @JsonProperty("Standard") STANDARD;
}
