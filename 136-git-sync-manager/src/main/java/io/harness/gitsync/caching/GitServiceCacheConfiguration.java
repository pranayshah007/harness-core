/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.threading.ThreadPoolConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class GitServiceCacheConfiguration {
  @JsonProperty("validCacheDurationInMillis") long validCacheDurationInMillis;
  @JsonProperty("maxCacheDurationInMillis") long maxCacheDurationInMillis;
  @JsonProperty("backgroundUpdateThreadPool") ThreadPoolConfig backgroundUpdateThreadPoolConfig;
  @JsonProperty("defaultBranchCacheDurationTimeInMinutes") long defaultBranchCacheDurationTimeInMinutes;
}
