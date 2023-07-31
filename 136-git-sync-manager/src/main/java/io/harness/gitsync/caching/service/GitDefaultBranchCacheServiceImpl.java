/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.service;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKey;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKeyFilterParams;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheResponse;
import io.harness.gitsync.caching.beans.GitDefaultBranchDeleteResponse;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache.GitDefaultBranchCacheKeys;
import io.harness.gitsync.caching.helper.GitDefaultBranchCacheHelper;
import io.harness.repositories.gitDefaultBranchCache.GitDefaultBranchCacheRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchCacheServiceImpl implements GitDefaultBranchCacheService {
  @Inject GitDefaultBranchCacheRepository gitDefaultBranchCacheRepository;
  @Inject GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper;

  @Override
  public GitDefaultBranchCacheResponse fetchFromCache(GitDefaultBranchCacheKey gitDefaultBranchCacheKey) {
    List<GitDefaultBranchCache> gitDefaultBranchCacheList =
        gitDefaultBranchCacheRepository.findByAccountIdentifierAndRepoUrlAndRepo(
            gitDefaultBranchCacheKey.getAccountIdentifier(), gitDefaultBranchCacheKey.getRepoUrl(),
            gitDefaultBranchCacheKey.getRepo());

    if (isEmpty(gitDefaultBranchCacheList)) {
      return null;
    }
    List<GitDefaultBranchCacheResponse> gitDefaultBranchCacheResponseList =
        prepareGitDefaultBranchCache(gitDefaultBranchCacheList);
    if (gitDefaultBranchCacheResponseList.size() > 1) {
      throw new InvalidRequestException(String.format(
          "For the given key with accountIdentifier %s, repoURL %s and repo %s found more than one unique record.",
          gitDefaultBranchCacheKey.getAccountIdentifier(), gitDefaultBranchCacheKey.getRepoUrl(),
          gitDefaultBranchCacheKey.getRepo()));
    }
    return gitDefaultBranchCacheResponseList.get(0);
  }

  @Override
  public GitDefaultBranchCacheResponse upsertCache(GitDefaultBranchCacheKey gitDefaultBranchCacheKey, String branch) {
    Criteria criteria = getCriteria(gitDefaultBranchCacheKey);
    Update update = getUpsertOperationUpdates(gitDefaultBranchCacheKey, branch);
    GitDefaultBranchCache gitDefaultBranchCache = gitDefaultBranchCacheRepository.upsert(criteria, update);

    return GitDefaultBranchCacheResponse.builder()
        .repo(gitDefaultBranchCache.getRepo())
        .defaultBranch(gitDefaultBranchCache.getBranch())
        .build();
  }

  @Override
  public GitDefaultBranchDeleteResponse invalidateCache(
      GitDefaultBranchCacheKeyFilterParams gitDefaultBranchCacheKeyFilterParams) {
    Criteria criteria = buildCriteria(gitDefaultBranchCacheKeyFilterParams);
    DeleteResult deleteResult = gitDefaultBranchCacheRepository.delete(criteria);
    return GitDefaultBranchDeleteResponse.builder().count(deleteResult.getDeletedCount()).build();
  }

  @Override
  public List<GitDefaultBranchCacheResponse> listFromCache(
      GitDefaultBranchCacheKeyFilterParams gitDefaultBranchCacheKeyFilterParams) {
    Criteria criteria = buildCriteria(gitDefaultBranchCacheKeyFilterParams);
    List<GitDefaultBranchCache> gitDefaultBranchCacheList = gitDefaultBranchCacheRepository.list(criteria);
    return prepareGitDefaultBranchCache(gitDefaultBranchCacheList);
  }

  private List<GitDefaultBranchCacheResponse> prepareGitDefaultBranchCache(
      List<GitDefaultBranchCache> gitDefaultBranchCacheList) {
    return emptyIfNull(gitDefaultBranchCacheList)
        .stream()
        .map(gitDefaultBranchCache
            -> GitDefaultBranchCacheResponse.builder()
                   .defaultBranch(gitDefaultBranchCache.getBranch())
                   .repo(gitDefaultBranchCache.getRepo())
                   .build())
        .collect(Collectors.toList());
  }

  private Criteria getCriteria(GitDefaultBranchCacheKey gitDefaultBranchCacheKey) {
    return Criteria.where(GitDefaultBranchCacheKeys.accountIdentifier)
        .is(gitDefaultBranchCacheKey.getAccountIdentifier())
        .and(GitDefaultBranchCacheKeys.repoUrl)
        .is(gitDefaultBranchCacheKey.getRepoUrl())
        .and(GitDefaultBranchCacheKeys.repo)
        .is(gitDefaultBranchCacheKey.getRepo());
  }

  private Update getUpsertOperationUpdates(GitDefaultBranchCacheKey gitDefaultBranchCacheKey, String branch) {
    long currentTimeInMilliseconds = System.currentTimeMillis();
    Update update = new Update();
    update.setOnInsert(GitDefaultBranchCacheKeys.accountIdentifier, gitDefaultBranchCacheKey.getAccountIdentifier());
    update.setOnInsert(GitDefaultBranchCacheKeys.repoUrl, gitDefaultBranchCacheKey.getRepoUrl());
    update.setOnInsert(GitDefaultBranchCacheKeys.repo, gitDefaultBranchCacheKey.getRepo());
    update.setOnInsert(GitDefaultBranchCacheKeys.branch, branch);
    update.setOnInsert(GitDefaultBranchCacheKeys.createdAt, currentTimeInMilliseconds);
    update.set(
        GitDefaultBranchCacheKeys.validUntil, gitDefaultBranchCacheHelper.getValidUntilTime(currentTimeInMilliseconds));
    return update;
  }

  private Criteria buildCriteria(GitDefaultBranchCacheKeyFilterParams gitDefaultBranchCacheKeyFilterParams) {
    Criteria criteria = new Criteria();
    criteria = addToCriteriaIfNotEmpty(criteria, GitDefaultBranchCacheKeys.accountIdentifier,
        gitDefaultBranchCacheKeyFilterParams.getAccountIdentifier());
    criteria = addToCriteriaIfNotEmpty(
        criteria, GitDefaultBranchCacheKeys.repoUrl, gitDefaultBranchCacheKeyFilterParams.getRepoUrl());
    criteria = addToCriteriaIfNotEmpty(
        criteria, GitDefaultBranchCacheKeys.repo, gitDefaultBranchCacheKeyFilterParams.getRepo());

    return criteria;
  }

  private Criteria addToCriteriaIfNotEmpty(Criteria criteria, String key, String value) {
    if (value != null) {
      criteria = criteria.and(key).is(value);
    }
    return criteria;
  }
}
