/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@Getter
@OwnedBy(HarnessTeam.PIPELINE)
public enum GitFileCacheTTL {
  VALID_CACHE_DURATION(5 * 60 * 1000L),
  MAX_CACHE_DURATION(60 * 60 * 1000L),
  ;

  final long durationInMs;

  GitFileCacheTTL(long durationInMs) {
    this.durationInMs = durationInMs;
  }
}
