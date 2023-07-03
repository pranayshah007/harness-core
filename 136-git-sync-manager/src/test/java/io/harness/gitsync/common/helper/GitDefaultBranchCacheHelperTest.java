/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.caching.beans.GitDefaultBranchCacheResponse;
import io.harness.gitsync.caching.service.GitDefaultBranchCacheService;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitDefaultBranchCacheHelperTest extends GitSyncTestBase {
  @InjectMocks GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper;
  @Mock GitDefaultBranchCacheService gitDefaultBranchCacheService;
  @Mock GitRepoHelper gitRepoHelper;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  String accountIdentifier = "accountIdentifier";
  String repoName = "repoName";
  String repoURL = "https://github.com/harness";
  String defaultBranch = "default";
  String branch = "branch";
  ConnectorInfoDTO connectorInfo;
  ScmConnector scmConnector;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url(repoURL)
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetDefaultBranchFromCacheForCacheHit() {
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        GitDefaultBranchCacheResponse.builder().defaultBranch(defaultBranch).repo(repoName).build();
    when(gitDefaultBranchCacheService.fetchFromCache(any())).thenReturn(gitDefaultBranchCacheResponse);
    String defaultBranchFromCache =
        gitDefaultBranchCacheHelper.getDefaultBranchFromCache(accountIdentifier, repoName, scmConnector);
    assertThat(defaultBranchFromCache).isEqualTo(defaultBranch);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetDefaultBranchFromCacheForCacheMiss() {
    when(gitDefaultBranchCacheService.fetchFromCache(any())).thenReturn(null);
    String defaultBranchFromCache =
        gitDefaultBranchCacheHelper.getDefaultBranchFromCache(accountIdentifier, repoName, scmConnector);
    assertThat(defaultBranchFromCache).isEqualTo(null);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpsertDefaultBranch() {
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        GitDefaultBranchCacheResponse.builder().repo(repoName).defaultBranch(defaultBranch).build();
    when(gitDefaultBranchCacheService.upsertCache(any(), any())).thenReturn(gitDefaultBranchCacheResponse);
    gitDefaultBranchCacheHelper.upsertDefaultBranch(accountIdentifier, repoName, defaultBranch, scmConnector);
    verify(gitDefaultBranchCacheService, times(1)).upsertCache(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetDefaultBranchIfInputBranchEmptyWhenInputBranchIsEmptyAndCacheHit() {
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    GitDefaultBranchCacheResponse gitDefaultBranchCacheResponse =
        GitDefaultBranchCacheResponse.builder().defaultBranch(defaultBranch).build();
    when(gitDefaultBranchCacheService.fetchFromCache(any())).thenReturn(gitDefaultBranchCacheResponse);
    String defaultBranchResponse =
        gitDefaultBranchCacheHelper.getDefaultBranchIfInputBranchEmpty(accountIdentifier, scmConnector, repoName, "");
    assertEquals(defaultBranchResponse, defaultBranch);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetDefaultBranchIfInputBranchEmptyWhenInputBranchIsEmptyAndCacheMiss() {
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(gitDefaultBranchCacheService.fetchFromCache(any())).thenReturn(null);
    String defaultBranchResponse =
        gitDefaultBranchCacheHelper.getDefaultBranchIfInputBranchEmpty(accountIdentifier, scmConnector, repoName, "");
    assertNull(defaultBranchResponse);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetDefaultBranchIfInputBranchEmptyWhenInputBranchIsNotEmpty() {
    String defaultBranchResponse = gitDefaultBranchCacheHelper.getDefaultBranchIfInputBranchEmpty(
        accountIdentifier, scmConnector, repoName, branch);
    assertEquals(defaultBranchResponse, branch);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCacheDefaultBranchResponseWhenInputBranchEmptyAndLoadedFromCache() {
    String inputBranch = "";
    String branchName = defaultBranch;
    String responseBranch = defaultBranch;
    gitDefaultBranchCacheHelper.cacheDefaultBranchResponse(
        accountIdentifier, scmConnector, repoName, branchName, responseBranch);
    verify(gitDefaultBranchCacheService, times(0)).upsertCache(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCacheDefaultBranchResponseWhenInputBranchEmptyAndNotLoadedFromCache() {
    String inputBranch = "";
    String branchName = "";
    String responseBranch = defaultBranch;
    gitDefaultBranchCacheHelper.cacheDefaultBranchResponse(
        accountIdentifier, scmConnector, repoName, branchName, responseBranch);
    verify(gitDefaultBranchCacheService, times(1)).upsertCache(any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testCacheDefaultBranchResponseWhenInputBranchIsNotEmpty() {
    String inputBranch = branch;
    String branchName = branch;
    String responseBranch = branch;
    gitDefaultBranchCacheHelper.cacheDefaultBranchResponse(
        accountIdentifier, scmConnector, repoName, branchName, responseBranch);
    verify(gitDefaultBranchCacheService, times(0)).upsertCache(any(), any());
  }
}
