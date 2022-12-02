package io.harness.gitsync.sdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PIPELINE)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CacheResponse {
  CacheState cacheState;
  long ttlLeft;
  long lastUpdatedAt;
}
