/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.helper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.GitServiceConfiguration;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.caching.beans.CacheDetails.CacheDetailsBuilder;
import io.harness.gitsync.caching.utils.GitCacheUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.Date;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheTTLHelper {
  private GitServiceConfiguration gitServiceConfiguration;

  @Inject
  public GitFileCacheTTLHelper(@Named("gitServiceConfiguration") GitServiceConfiguration gitServiceConfiguration) {
    this.gitServiceConfiguration = gitServiceConfiguration;
  }

  public CacheDetails getCacheDetails(long cacheUpdatedAt, long cacheExpiryAt) {
    long currentTime = System.currentTimeMillis();
    if (isExpiredCache(cacheExpiryAt, currentTime)) {
      return null;
    }

    CacheDetailsBuilder cacheDetailsBuilder =
        CacheDetails.builder().lastUpdatedAt(cacheUpdatedAt).cacheExpiryTTL(cacheExpiryAt - currentTime);

    long timeElapsedSinceUpdate = currentTime - cacheUpdatedAt;
    long validUntilTTL = getValidCacheDuration() - timeElapsedSinceUpdate;
    if (validUntilTTL < 0) {
      cacheDetailsBuilder.isStale(true);
    }
    cacheDetailsBuilder.validUntilTTL(validUntilTTL);
    return cacheDetailsBuilder.build();
  }

  public Date getValidUntilTime(long currentTime) {
    return GitCacheUtils.getValidUntilTime(currentTime, getMaxCacheDuration());
  }

  public Date getFormattedValidUntilTime(long validUntilTime) {
    return Date.from(Instant.ofEpochMilli(validUntilTime));
  }

  private boolean isExpiredCache(long validUntil, long currentTime) {
    return validUntil - currentTime < 0;
  }

  private long getValidCacheDuration() {
    return gitServiceConfiguration.getGitServiceCacheConfiguration().getValidCacheDurationInMillis();
  }

  private long getMaxCacheDuration() {
    return gitServiceConfiguration.getGitServiceCacheConfiguration().getMaxCacheDurationInMillis();
  }
}
