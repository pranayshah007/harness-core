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
import io.harness.gitsync.caching.beans.GitFileCacheDeleteResult;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.beans.GitFileCacheObject;
import io.harness.gitsync.caching.beans.GitFileCacheResponse;
import io.harness.gitsync.caching.beans.GitFileCacheResponseMetadata;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateRequestKey;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateRequestValues;
import io.harness.gitsync.caching.beans.GitFileCacheUpdateResult;
import io.harness.gitsync.caching.entity.GitFileCache;
import io.harness.gitsync.caching.entity.GitFileCache.GitFileCacheKeys;
import io.harness.gitsync.caching.helper.GitFileCacheTTLHelper;
import io.harness.gitsync.caching.mapper.GitFileCacheObjectMapper;
import io.harness.gitsync.caching.mapper.GitProviderMapper;
import io.harness.repositories.gitfilecache.GitFileCacheRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitFileCacheServiceImpl implements GitFileCacheService {
  @Inject GitFileCacheRepository gitFileCacheRepository;
  @Inject GitFileCacheTTLHelper gitFileCacheTTLHelper;

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
        gitFileCacheTTLHelper.getCacheDetails(gitFileCache.getLastUpdatedAt(), gitFileCache.getValidUntil().getTime());
    if (cacheDetails == null) {
      return null;
    }

    return GitFileCacheResponse.builder()
        .cacheDetails(cacheDetails)
        .gitFileCacheObject(GitFileCacheObjectMapper.fromEntity(gitFileCache.getGitFileObject()))
        .gitFileCacheResponseMetadata(GitFileCacheResponseMetadata.builder().ref(gitFileCache.getRef()).build())
        .build();
  }

  @Override
  public GitFileCacheResponse upsertCache(GitFileCacheKey gitFileCacheKey, GitFileCacheObject gitFileCacheObject) {
    Criteria criteria = getCriteria(gitFileCacheKey);
    Update update = getUpsertOperationUpdates(gitFileCacheKey, gitFileCacheObject);
    GitFileCache gitFileCache = gitFileCacheRepository.upsert(criteria, update);

    CacheDetails cacheDetails =
        gitFileCacheTTLHelper.getCacheDetails(gitFileCache.getLastUpdatedAt(), gitFileCache.getValidUntil().getTime());
    return GitFileCacheResponse.builder()
        .cacheDetails(cacheDetails)
        .gitFileCacheObject(GitFileCacheObjectMapper.fromEntity(gitFileCache.getGitFileObject()))
        .gitFileCacheResponseMetadata(GitFileCacheResponseMetadata.builder().ref(gitFileCache.getRef()).build())
        .build();
  }

  @Override
  public GitFileCacheDeleteResult invalidateCache(GitFileCacheKey gitFileCacheKey) {
    Criteria criteria = getOptionalCriteria(gitFileCacheKey);
    DeleteResult deleteResult = gitFileCacheRepository.delete(criteria);
    return GitFileCacheDeleteResult.builder().count(deleteResult.getDeletedCount()).build();
  }

  public GitFileCacheUpdateResult updateCache(
      GitFileCacheUpdateRequestKey key, GitFileCacheUpdateRequestValues values) {
    Criteria criteria = getOptionalCriteria(GitFileCacheKey.builder()
                                                .repoName(key.getRepoName())
                                                .completeFilePath(key.getFilepath())
                                                .ref(key.getRef())
                                                .accountIdentifier(key.getAccountIdentifier())
                                                .gitProvider(key.getGitProvider())
                                                .build());
    Update update = new Update();
    update.set(GitFileCacheKeys.lastUpdatedAt, values.getUpdatedAt());
    update.set(GitFileCacheKeys.validUntil, gitFileCacheTTLHelper.getFormattedValidUntilTime(values.getValidUntil()));
    UpdateResult updateResult = gitFileCacheRepository.update(criteria, update);
    return GitFileCacheUpdateResult.builder().count(updateResult.getModifiedCount()).build();
  }

  private Update getUpsertOperationUpdates(GitFileCacheKey gitFileCacheKey, GitFileCacheObject gitFileCacheObject) {
    long currentTime = System.currentTimeMillis();
    Update update = new Update();
    update.setOnInsert(GitFileCacheKeys.accountIdentifier, gitFileCacheKey.getAccountIdentifier());
    update.setOnInsert(GitFileCacheKeys.gitProvider, GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()));
    update.setOnInsert(GitFileCacheKeys.repoName, gitFileCacheKey.getRepoName());
    update.setOnInsert(GitFileCacheKeys.ref, gitFileCacheKey.getRef());
    update.setOnInsert(GitFileCacheKeys.completeFilepath, gitFileCacheKey.getCompleteFilePath());
    update.setOnInsert(GitFileCacheKeys.createdAt, currentTime);
    update.set(GitFileCacheKeys.gitFileObject, GitFileCacheObjectMapper.toEntity(gitFileCacheObject));
    update.set(GitFileCacheKeys.validUntil, gitFileCacheTTLHelper.getValidUntilTime(currentTime));
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

  private Criteria getOptionalCriteria(GitFileCacheKey gitFileCacheKey) {
    Criteria criteria = new Criteria();
    criteria =
        addToCriteriaIfNotEmpty(criteria, GitFileCacheKeys.accountIdentifier, gitFileCacheKey.getAccountIdentifier());
    criteria = addToCriteriaIfNotEmpty(criteria, GitFileCacheKeys.ref, gitFileCacheKey.getRef());
    criteria = addToCriteriaIfNotEmpty(criteria, GitFileCacheKeys.repoName, gitFileCacheKey.getRepoName());
    criteria =
        addToCriteriaIfNotEmpty(criteria, GitFileCacheKeys.completeFilepath, gitFileCacheKey.getCompleteFilePath());
    if (gitFileCacheKey.getGitProvider() != null) {
      criteria =
          criteria.and(GitFileCacheKeys.gitProvider).is(GitProviderMapper.toEntity(gitFileCacheKey.getGitProvider()));
    }
    return criteria;
  }

  private Criteria addToCriteriaIfNotEmpty(Criteria criteria, String key, String value) {
    if (value != null) {
      criteria = criteria.and(key).is(value);
    }
    return criteria;
  }
}
