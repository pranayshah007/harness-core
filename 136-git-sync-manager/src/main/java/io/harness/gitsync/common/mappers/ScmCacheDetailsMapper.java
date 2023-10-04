/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.common.beans.ScmCacheDetails;
import io.harness.gitsync.common.helper.ScmCacheStateHelper;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ScmCacheDetailsMapper {
  public ScmCacheDetails getScmCacheDetails(CacheDetails cacheDetails, boolean isSyncEnabled) {
    return ScmCacheDetails.builder()
        .cacheExpiryTTL(cacheDetails.getCacheExpiryTTL())
        .lastUpdatedAt(cacheDetails.getLastUpdatedAt())
        .validUntilTTL(cacheDetails.getValidUntilTTL())
        .scmCacheState(ScmCacheStateHelper.getScmCacheState(cacheDetails.isStale()))
        .isSyncEnabled(isSyncEnabled)
        .build();
  }
}
