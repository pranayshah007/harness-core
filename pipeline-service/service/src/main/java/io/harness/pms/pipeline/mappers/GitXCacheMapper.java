/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.CacheState;
import io.harness.spec.server.pipeline.v1.model.CacheResponseMetadataDTO;

import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class GitXCacheMapper {
  public static final String BOOLEAN_TRUE_VALUE = "true";

  public CacheResponseMetadataDTO.CacheStateEnum getCacheStateEnum(CacheState cacheState) {
    if (CacheState.STALE_CACHE.equals(cacheState)) {
      return CacheResponseMetadataDTO.CacheStateEnum.STALE_CACHE;
    } else if (CacheState.VALID_CACHE.equals(cacheState)) {
      return CacheResponseMetadataDTO.CacheStateEnum.VALID_CACHE;
    }
    return CacheResponseMetadataDTO.CacheStateEnum.UNKNOWN;
  }

  public boolean parseLoadFromCacheHeaderParam(String loadFromCache) {
    if (isEmpty(loadFromCache)) {
      return false;
    } else {
      return BOOLEAN_TRUE_VALUE.equalsIgnoreCase(loadFromCache);
    }
  }
}
