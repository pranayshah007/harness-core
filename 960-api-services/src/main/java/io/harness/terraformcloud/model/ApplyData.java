/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@lombok.Data
public class ApplyData extends Data {
  private Attributes attributes;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Builder
  @lombok.Data
  public static class Attributes {
    private Status status;
    @JsonProperty("log-read-url") private String logReadUrl;
    @JsonProperty("resource-additions") private int resourceAdditions;
    @JsonProperty("resource-changes") private int resourceChanges;
    @JsonProperty("resource-destructions") private int resourceDestructions;

    public enum Status {
      @JsonProperty("pending") PENDING,
      @JsonProperty("managed_queued") MANAGED_QUEUED,
      @JsonProperty("queued") QUEUED,
      @JsonProperty("running") RUNNING,
      @JsonProperty("errored") ERRORED,
      @JsonProperty("canceled") CANCELED,
      @JsonProperty("finished") FINISHED,
      @JsonProperty("unreachable") UNREACHABLE,
      @JsonEnumDefaultValue UNKNOWN
    }
  }
}
