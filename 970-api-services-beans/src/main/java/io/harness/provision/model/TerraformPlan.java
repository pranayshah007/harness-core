/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.provision.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlan {
  // based on https://www.terraform.io/internals/json-format
  @JsonProperty("format_version") private String formatVersion;
  @JsonProperty("terraform_version") private String terraformVersion;
  // planned_values - not used currently, hence not implemented
  // resource_drift - not used currently, hence not implemented
  @JsonProperty("resource_changes") private List<TerraformResourceChange> terraformResourceChanges;
  // prior_state - not used currently, hence not implemented
  // configuration - not used currently, hence not implemented
}
