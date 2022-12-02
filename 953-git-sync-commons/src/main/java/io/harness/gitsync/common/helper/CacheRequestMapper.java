package io.harness.gitsync.common.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.CacheRequestParams;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class CacheRequestMapper {
  public CacheRequestParams getCacheRequest(boolean loadFromCache) {
    return CacheRequestParams.newBuilder().setUseCache(loadFromCache).build();
  }
}
