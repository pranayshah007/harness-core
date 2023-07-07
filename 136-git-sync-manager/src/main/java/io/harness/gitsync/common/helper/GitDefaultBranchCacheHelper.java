/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKey;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheResponse;
import io.harness.gitsync.caching.service.GitDefaultBranchCacheService;
import io.harness.gitsync.core.beans.GitDefaultBranchCacheRunnableParams;
import io.harness.gitsync.core.runnable.GitBackgroundCacheRefreshHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class GitDefaultBranchCacheHelper {
  @Inject GitDefaultBranchCacheService gitDefaultBranchCacheService;
  @Inject GitRepoHelper gitRepoHelper;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject GitBackgroundCacheRefreshHelper gitBackgroundCacheRefreshHelper;
  public static final String GIT_DEFAULT_BRANCH_CACHE = "GIT_DEFAULT_BRANCH_CACHE";

  public void upsertDefaultBranch(
      String accountIdentifier, String repoName, String defaultBranch, ScmConnector scmConnector) {
    GitDefaultBranchCacheKey gitDefaultBranchCacheKey =
        buildGitDefaultBranchCacheKey(accountIdentifier, repoName, scmConnector);
    log.info(String.format(
        "In user flow [%s] inserting defaultBranch [%s] in the cache for repoURL [%s] in the account [%s].",
        GIT_DEFAULT_BRANCH_CACHE, defaultBranch, gitDefaultBranchCacheKey.getRepoUrl(), accountIdentifier));
    try {
      gitDefaultBranchCacheService.upsertCache(gitDefaultBranchCacheKey, defaultBranch);
    } catch (Exception ex) {
      log.error("Faced exception while upserting default branch into cache", ex);
    }
  }

  public String getDefaultBranchFromCache(String accountIdentifier, String repoName, ScmConnector scmConnector) {
    GitDefaultBranchCacheKey gitDefaultBranchCacheKey =
        buildGitDefaultBranchCacheKey(accountIdentifier, repoName, scmConnector);
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse = null;
    try {
      gitDefaultBranchCacheResponse = gitDefaultBranchCacheService.fetchFromCache(gitDefaultBranchCacheKey);
    } catch (Exception ex) {
      log.error("Faced exception while fetching default branch from cache, fetching from GIT now", ex);
      return null;
    }
    if (gitDefaultBranchCacheResponse == null) {
      return null;
    }
    log.info(String.format(
        "In user flow [%s] Retrieved the defaultBranch [%s] from cache for repoURL [%s] in the account [%s].",
        GIT_DEFAULT_BRANCH_CACHE, gitDefaultBranchCacheResponse.getDefaultBranch(),
        gitDefaultBranchCacheKey.getRepoUrl(), accountIdentifier));
    return gitDefaultBranchCacheResponse.getDefaultBranch();
  }

  public String getDefaultBranchIfInputBranchEmpty(
      String accountIdentifier, ScmConnector scmConnector, String repoName, String inputBranch) {
    if (isEmpty(inputBranch)
        && ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PIE_GIT_DEFAULT_BRANCH_CACHE)) {
      return getDefaultBranchFromCache(accountIdentifier, repoName, scmConnector);
    } else {
      return inputBranch;
    }
  }

  public void cacheDefaultBranchResponse(String accountIdentifier, ScmConnector scmConnector, String repoName,
      String workingBranch, String defaultBranchToSet) {
    if (isEmpty(workingBranch)) {
      upsertDefaultBranch(accountIdentifier, repoName, defaultBranchToSet, scmConnector);
    }
  }

  private GitDefaultBranchCacheKey buildGitDefaultBranchCacheKey(
      String accountIdentifier, String repoName, ScmConnector scmConnector) {
    String repoUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);
    return new GitDefaultBranchCacheKey(accountIdentifier, repoUrl, repoName);
  }

  public boolean isGitDefaultBranch(
      Scope scope, ScmConnector scmConnector, String repoName, String requestBranch, String responseBranch) {
    if (isEmpty(requestBranch)) {
      return true;
    }
    if (isEmpty(responseBranch)) {
      return false;
    }
    String defaultBranchFromCache = getDefaultBranchFromCache(scope.getAccountIdentifier(), repoName, scmConnector);
    if (isEmpty(defaultBranchFromCache)) {
      gitBackgroundCacheRefreshHelper.submitDefaultBranchTask(GitDefaultBranchCacheRunnableParams.builder()
                                                                  .accountIdentifier(scope.getAccountIdentifier())
                                                                  .orgIdentifier(scope.getOrgIdentifier())
                                                                  .projectIdentifier(scope.getProjectIdentifier())
                                                                  .scmConnector(scmConnector)
                                                                  .repoName(repoName)
                                                                  .build());
    }
    return responseBranch.equals(defaultBranchFromCache);
  }
}
