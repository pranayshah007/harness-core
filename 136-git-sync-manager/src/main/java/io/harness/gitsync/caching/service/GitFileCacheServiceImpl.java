/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.beans.GitFileCacheObject;
import io.harness.gitsync.caching.beans.GitFileCacheResponse;
import io.harness.gitsync.caching.entity.CacheDetails;
import io.harness.gitsync.caching.entity.GitFileCache;
import io.harness.gitsync.caching.helper.GitFileCacheTTLHelper;
import io.harness.gitsync.caching.mapper.GitFileCacheObjectMapper;
import io.harness.repositories.gitfilecache.GitFileCacheRepository;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheServiceImpl implements GitFileCacheService {
  @Inject GitFileCacheRepository gitFileCacheRepository;

  @Override
  public GitFileCacheResponse fetchFromCache(GitFileCacheKey gitFileCacheKey) {
    GitFileCache gitFileCache =
        gitFileCacheRepository.findByAccountIdentifierAndGitProviderAndRepoNameAndRefAndCompleteFilepath(
            gitFileCacheKey.getAccountIdentifier(), gitFileCacheKey.getGitProvider(), gitFileCacheKey.getRepoName(),
            gitFileCacheKey.getRef(), gitFileCacheKey.getCompleteFilePath());
    if (gitFileCache == null) {
      return null;
    }

    CacheDetails cacheDetails =
        GitFileCacheTTLHelper.getCacheDetails(gitFileCache.getLastUpdatedAt(), gitFileCache.getValidUntil().getTime());
    if (cacheDetails == null) {
      return null;
    }

    return GitFileCacheResponse.builder()
        .cacheDetails(cacheDetails)
        .gitFileCacheObject(GitFileCacheObjectMapper.fromEntity(gitFileCache.getGitFileObject()))
        .build();
  }

  @Override
  public void upsertCache(GitFileCacheKey gitFileCacheKey, GitFileCacheObject gitFileCacheObject) {}

  @Override
  public void invalidateCache(GitFileCacheKey gitFileCacheKey) {}
}
