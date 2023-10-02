/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import io.harness.beans.cache.api.CacheMetadataDetail;
import io.harness.ci.config.CIDockerLayerCachingConfig;

import java.util.List;

public interface DlcCacheManager {
  CIDockerLayerCachingConfig getCacheConfig(String accountId);
  List<CacheMetadataDetail> getCacheMetadata(String accountId);
  List<CacheMetadataDetail> deleteCache(String accountId);
}
