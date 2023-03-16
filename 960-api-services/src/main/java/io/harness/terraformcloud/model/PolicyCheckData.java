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
import java.util.Map;
import lombok.Builder;

@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@lombok.Data
public class PolicyCheckData extends Data {
  private Attributes attributes;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Builder
  @lombok.Data
  public static class Attributes {
    private String status;
    private Map<String, Boolean> permissions;
    private Action actions;
    private Result result;
    private String scope;
    @JsonProperty("status-timestamps") private Object statusTimestamp;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @lombok.Data
    public static class Action {
      @JsonProperty("is-overridable") boolean isOverridable;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Builder
    @lombok.Data
    public static class Result {
      Sentinel sentinel;

      @JsonIgnoreProperties(ignoreUnknown = true)
      @Builder
      @lombok.Data
      public static class Sentinel {
        Map<String, PolicyData> data;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @Builder
        @lombok.Data
        public static class PolicyData {
          List<PolicySummary> policies;

          @JsonIgnoreProperties(ignoreUnknown = true)
          @Builder
          @lombok.Data
          public static class PolicySummary {
            Policy policy;
            boolean result;

            @JsonIgnoreProperties(ignoreUnknown = true)
            @Builder
            @lombok.Data
            public static class Policy {
              @JsonProperty("enforcement-level") String enforcementLevel;
              String name;
            }
          }
        }
      }
    }
  }
}
