/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@OwnedBy(CDP)

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@lombok.Data
public class Attributes {
  private String message;
  private RunStatus status;
  private String source;
  @JsonProperty("terraform-version") private String terraformVersion;
  @JsonProperty("plan_and_apply") private boolean planAndApply;
  @JsonProperty("plan_only") private boolean planOnly;
  @JsonProperty("refresh_only") private boolean refreshOnly;
  @JsonProperty("is-destroy") private boolean isDestroy;
  @JsonProperty("empty_apply") private boolean emptyApply;
  @JsonProperty("auto-apply") private boolean autoApply;
  @JsonProperty("target-addrs") private List<String> targets;
  @JsonProperty("has-changes") private boolean hasChanges;
  List<Variable> variables;
  private Actions actions;
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Builder
  @lombok.Data
  public static class Actions {
    @JsonProperty("is-confirmable") boolean isConfirmable;
    @JsonProperty("is-discardable") boolean isDiscardable;
  }
}
