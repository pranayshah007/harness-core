/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.services.ScopeInfoService.SCOPE_INFO_DATA_CACHE_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.beans.ScopeInfo;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class ScopeInfoModule extends AbstractModule {
  public ScopeInfoModule() {}

  @Provides
  @Singleton
  @Named(SCOPE_INFO_DATA_CACHE_KEY)
  Cache<String, ScopeInfo> getScopeInfoDataCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache(SCOPE_INFO_DATA_CACHE_KEY, String.class, ScopeInfo.class,
        CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 1)),
        versionInfoManager.getVersionInfo().getBuildNo());
  }
}
