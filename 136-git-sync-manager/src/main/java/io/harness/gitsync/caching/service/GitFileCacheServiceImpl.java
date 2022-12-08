/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.beans.GitFileCacheObject;
import io.harness.gitsync.caching.beans.GitFileCacheResponse;
import io.harness.gitsync.caching.entity.GitFileCache;
import io.harness.gitsync.caching.entity.GitFileCache.GitFileCacheKeys;
import io.harness.gitsync.caching.helper.GitFileCacheTTLHelper;
import io.harness.gitsync.caching.mapper.GitFileCacheObjectMapper;
import io.harness.gitsync.caching.mapper.GitProviderMapper;
import io.harness.repositories.gitfilecache.GitFileCacheRepository;

import com.google.inject.Inject;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheServiceImpl implements GitFileCacheService {
  @Inject GitFileCacheRepository gitFileCacheRepository;

  @Override
  public GitFileCacheResponse fetchFromCache(GitFileCacheKey gitFileCacheKey) {
    GitFileCache gitFileCache;
    if (gitFileCacheKey.isDefaultBranch()) {
      gitFileCache =
          gitFileCacheRepository.findByAccountIdentifierAndGitProviderAndRepoNameAndCompleteFilepathAndIsDefaultBranch(
              gitFileCacheKey.getAccountIdentifier(), GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()),
              gitFileCacheKey.getRepoName(), gitFileCacheKey.getCompleteFilePath(), true);
    } else {
      gitFileCache = gitFileCacheRepository.findByAccountIdentifierAndGitProviderAndRepoNameAndRefAndCompleteFilepath(
          gitFileCacheKey.getAccountIdentifier(), GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()),
          gitFileCacheKey.getRepoName(), gitFileCacheKey.getRef(), gitFileCacheKey.getCompleteFilePath());
    }
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
  public GitFileCacheResponse upsertCache(GitFileCacheKey gitFileCacheKey, GitFileCacheObject gitFileCacheObject) {
    Criteria criteria = getCriteria(gitFileCacheKey);
    Update update = getUpsertOperationUpdates(gitFileCacheKey, gitFileCacheObject);
    GitFileCache gitFileCache = gitFileCacheRepository.upsert(criteria, update);

    CacheDetails cacheDetails =
        GitFileCacheTTLHelper.getCacheDetails(gitFileCache.getLastUpdatedAt(), gitFileCache.getValidUntil().getTime());
    return GitFileCacheResponse.builder()
        .cacheDetails(cacheDetails)
        .gitFileCacheObject(GitFileCacheObjectMapper.fromEntity(gitFileCache.getGitFileObject()))
        .build();
  }

  @Override
  public void invalidateCache(GitFileCacheKey gitFileCacheKey) {}

  private Update getUpsertOperationUpdates(GitFileCacheKey gitFileCacheKey, GitFileCacheObject gitFileCacheObject) {
    long currentTime = System.currentTimeMillis();
    Update update = new Update();
    update.setOnInsert(GitFileCacheKeys.accountIdentifier, gitFileCacheKey.getAccountIdentifier());
    update.setOnInsert(GitFileCacheKeys.gitProvider, GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()));
    update.setOnInsert(GitFileCacheKeys.repoName, gitFileCacheKey.getRepoName());
    update.setOnInsert(GitFileCacheKeys.ref, gitFileCacheKey.getRef());
    update.setOnInsert(GitFileCacheKeys.completeFilepath, gitFileCacheKey.getCompleteFilePath());
    update.setOnInsert(GitFileCacheKeys.gitFileObject, GitFileCacheObjectMapper.toEntity(gitFileCacheObject));
    update.setOnInsert(GitFileCacheKeys.createdAt, currentTime);
    update.set(GitFileCacheKeys.validUntil, GitFileCacheTTLHelper.getValidUntilTime(currentTime));
    update.set(GitFileCacheKeys.lastUpdatedAt, currentTime);
    if (gitFileCacheKey.isDefaultBranch()) {
      update.set(GitFileCacheKeys.isDefaultBranch, true);
    }

    return update;
  }

  private Criteria getCriteria(GitFileCacheKey gitFileCacheKey) {
    return Criteria.where(GitFileCacheKeys.accountIdentifier)
        .is(gitFileCacheKey.getAccountIdentifier())
        .and(GitFileCacheKeys.gitProvider)
        .is(GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()))
        .and(GitFileCacheKeys.repoName)
        .is(gitFileCacheKey.getRepoName())
        .and(GitFileCacheKeys.ref)
        .is(gitFileCacheKey.getRef())
        .and(GitFileCacheKeys.completeFilepath)
        .is(gitFileCacheKey.getCompleteFilePath());
  }
}
