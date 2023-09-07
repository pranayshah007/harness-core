/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.cache.api.CICacheManagementResource;
import io.harness.beans.cache.api.CacheMetadataInfo;
import io.harness.beans.cache.api.DeleteCacheResponse;
import io.harness.ci.cache.CICacheManagementService;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CICacheManagementResourceImpl implements CICacheManagementResource {
  private final CICacheManagementService ciCacheManagementService;

  public ResponseDTO<CacheMetadataInfo> getCacheInfo(String accountIdentifier) {
    log.info("Getting cache information");

    return ResponseDTO.newResponse(ciCacheManagementService.getCacheMetadata(accountIdentifier));
  }

  public ResponseDTO<DeleteCacheResponse> deleteCache(String accountIdentifier, String path, String cacheType) {
    log.info("Deleting cache");

    return ResponseDTO.newResponse(ciCacheManagementService.deleteCache(accountIdentifier, path, cacheType));
  }
}
