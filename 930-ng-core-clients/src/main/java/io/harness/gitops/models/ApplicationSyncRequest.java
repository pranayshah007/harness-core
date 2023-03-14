/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitops.models;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(GITOPS)
public class ApplicationSyncRequest {
  @JsonProperty("name") String applicationName;
  @JsonProperty("revision") String targetRevision;
  boolean dryRun;
  boolean prune;
  SyncStrategy strategy;
  List<SyncOperationResource> resources;
  RetryStrategy retryStrategy;
  SyncOptions syncOptions;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncOptions {
    List<String> items;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncStrategy {
    SyncStrategyApply apply;
    SyncStrategyHook hook;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncStrategyApply {
    boolean force;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncStrategyHook {
    SyncStrategyApply syncStrategyApply;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RetryStrategy {
    int limit;
    Backoff backoff;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Backoff {
    @JsonProperty("duration") String baseDuration;
    int factor;
    String maxDuration;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncOperationResource {
    String group;
    String kind;
    String name;
    String namespace;
  }
}
