/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.service;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKey;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheKeyFilterParams;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheResponse;
import io.harness.gitsync.caching.beans.GitDefaultBranchDeleteResponse;
import io.harness.gitsync.caching.entity.GitDefaultBranchCache;
import io.harness.gitsync.caching.helper.GitDefaultBranchCacheHelper;
import io.harness.repositories.gitDefaultBranchCache.GitDefaultBranchCacheRepository;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchCacheServiceImplTest extends GitSyncTestBase {
  @InjectMocks GitDefaultBranchCacheServiceImpl gitDefaultBranchCacheService;
  @Mock GitDefaultBranchCacheRepository gitDefaultBranchCacheRepository;

  @Mock GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper;

  private GitDefaultBranchCacheKey gitDefaultBranchCacheKey;
  private final String ACCOUNT_IDENTIFIER = "accountID";
  private final String REPO = "repo";
  private final String REPO_URL = "repoUrl";
  private final String BRANCH = "master";
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    gitDefaultBranchCacheKey = new GitDefaultBranchCacheKey(ACCOUNT_IDENTIFIER, REPO_URL, REPO);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchFromCacheForCacheHit() {
    GitDefaultBranchCache gitDefaultBranchCache = GitDefaultBranchCache.builder().branch(BRANCH).repo(REPO).build();
    doReturn(Collections.singletonList(gitDefaultBranchCache))
        .when(gitDefaultBranchCacheRepository)
        .findByAccountIdentifierAndRepoUrlAndRepo(ACCOUNT_IDENTIFIER, REPO_URL, REPO);
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        gitDefaultBranchCacheService.fetchFromCache(gitDefaultBranchCacheKey);
    assertThat(gitDefaultBranchCacheResponse).isNotNull();
    assertThat(gitDefaultBranchCacheResponse.getDefaultBranch()).isEqualTo(BRANCH);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchFromCacheForCacheMiss() {
    doReturn(null)
        .when(gitDefaultBranchCacheRepository)
        .findByAccountIdentifierAndRepoUrlAndRepo(ACCOUNT_IDENTIFIER, REPO_URL, REPO);
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        gitDefaultBranchCacheService.fetchFromCache(gitDefaultBranchCacheKey);
    assertThat(gitDefaultBranchCacheResponse).isNull();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpsertCacheIsSuccessful() {
    GitDefaultBranchCache gitDefaultBranchCache = GitDefaultBranchCache.builder().branch(BRANCH).repo(REPO).build();
    doReturn(gitDefaultBranchCache).when(gitDefaultBranchCacheRepository).upsert(any(), any());
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        gitDefaultBranchCacheService.upsertCache(gitDefaultBranchCacheKey, BRANCH);
    assertThat(gitDefaultBranchCacheResponse).isNotNull();
    assertThat(gitDefaultBranchCacheResponse.getDefaultBranch()).isEqualTo(BRANCH);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testInvalidateCache() {
    GitDefaultBranchCacheKeyFilterParams gitDefaultBranchCacheKeyFilterParams =
        GitDefaultBranchCacheKeyFilterParams.builder().repo(REPO).build();
    doReturn(new DeleteResult() {
      @Override
      public boolean wasAcknowledged() {
        return true;
      }

      @Override
      public long getDeletedCount() {
        return 1;
      }
    })
        .when(gitDefaultBranchCacheRepository)
        .delete((Criteria) any());

    GitDefaultBranchDeleteResponse gitDefaultBranchDeleteResponse =
        gitDefaultBranchCacheService.invalidateCache(gitDefaultBranchCacheKeyFilterParams);
    assertThat(gitDefaultBranchDeleteResponse).isNotNull();
    assertThat(gitDefaultBranchDeleteResponse.getCount()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testListFromCache() {
    GitDefaultBranchCacheKeyFilterParams gitDefaultBranchCacheKeyFilterParams =
        GitDefaultBranchCacheKeyFilterParams.builder().repo(REPO).build();
    List<GitDefaultBranchCache> gitDefaultBranchCacheList = new ArrayList<>();
    GitDefaultBranchCache gitDefaultBranchCache1 = GitDefaultBranchCache.builder().branch(BRANCH).repo(REPO).build();
    GitDefaultBranchCache gitDefaultBranchCache2 = GitDefaultBranchCache.builder().branch(BRANCH).repo(REPO).build();
    gitDefaultBranchCacheList.add(gitDefaultBranchCache1);
    gitDefaultBranchCacheList.add(gitDefaultBranchCache2);
    doReturn(gitDefaultBranchCacheList).when(gitDefaultBranchCacheRepository).list(any());
    List<GitDefaultBranchCacheResponse> gitDefaultBranchCacheResponseList =
        gitDefaultBranchCacheService.listFromCache(gitDefaultBranchCacheKeyFilterParams);
    assertThat(gitDefaultBranchCacheResponseList).isNotNull();
    assertThat(gitDefaultBranchCacheResponseList.size()).isEqualTo(2);
  }
}
