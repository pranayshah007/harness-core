/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.GitFileCacheTTL;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.caching.beans.CacheDetails.CacheDetailsBuilder;

import java.time.Instant;
import java.util.Date;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheTTLHelper {
  public CacheDetails getCacheDetails(long cacheUpdatedAt, long cacheExpiryAt) {
    long currentTime = System.currentTimeMillis();
    if (isExpiredCache(cacheExpiryAt, currentTime)) {
      return null;
    }

    CacheDetailsBuilder cacheDetailsBuilder =
        CacheDetails.builder().lastUpdatedAt(cacheUpdatedAt).cacheExpiryTTL(cacheExpiryAt - currentTime);

    long timeElapsedSinceUpdate = currentTime - cacheUpdatedAt;
    long validUntilTTL = GitFileCacheTTL.VALID_CACHE_DURATION.getDurationInMs() - timeElapsedSinceUpdate;
    if (validUntilTTL < 0) {
      cacheDetailsBuilder.isStale(true);
    }
    cacheDetailsBuilder.validUntilTTL(validUntilTTL);
    return cacheDetailsBuilder.build();
  }

  public Date getValidUntilTime(long currentTime) {
    return Date.from(Instant.ofEpochMilli(currentTime + GitFileCacheTTL.MAX_CACHE_DURATION.getDurationInMs()));
  }

  private boolean isExpiredCache(long validUntil, long currentTime) {
    return validUntil - currentTime < 0;
  }
}
