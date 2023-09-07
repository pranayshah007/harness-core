/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cache;

import io.harness.beans.cache.api.CacheMetadataInfo;
import io.harness.beans.cache.api.DeleteCacheResponse;

public interface CICacheManagementService {
  CacheMetadataInfo getCacheMetadata(String accountId);
  DeleteCacheResponse deleteCache(String accountId, String path, String cacheType);
}
