/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;
import static software.wings.yaml.errorhandling.GitSyncError.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.GitCommit;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.yaml.errorhandling.GitSyncError;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitSyncEntitiesExpiryHandlerTest extends WingsBaseTest {
  @Mock GitSyncService gitSyncService;
  @Mock AppService appService;
  @InjectMocks @Inject GitSyncEntitiesExpiryHandler gitSyncEntitiesExpiryHandler;
  @Inject private HPersistence persistence;

  private final Long ONE_MONTH_IN_MILLS = 2592000000L;
  private final Long TWELVE_MONTH_IN_MILLS = 31104000000L;
  Account account;

  @Before
  public void Setup() {
    MockitoAnnotations.initMocks(this);
    account = getAccount("PAID");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitSyncError() {
    GitSyncError gitSyncError =
        builder().gitCommitId("commitId").accountId(account.getUuid()).fullSyncPath(false).build();
    persistence.save(gitSyncError);
    GitSyncError gitSyncError_1 =
        builder().gitCommitId("commitId1").accountId(account.getUuid()).fullSyncPath(false).build();
    persistence.save(gitSyncError_1);
    gitSyncError_1.setCreatedAt(gitSyncError_1.getCreatedAt() + 2);
    persistence.save(gitSyncError_1);

    gitSyncEntitiesExpiryHandler.handleGitError(account, gitSyncError_1.getCreatedAt() - 1);

    assertThat(persistence.createQuery(GitSyncError.class)
                   .filter(GitSyncErrorKeys.accountId, account.getUuid())
                   .asList()
                   .size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitCommits() {
    final String connectorId = "gitConnectorId";
    final String branchName = "branchName";
    final String accountId = account.getUuid();
    GitCommit gitCommit = buildGitCommit(connectorId, branchName, GitCommit.Status.COMPLETED, accountId);
    GitCommit gitCommit_1 = buildGitCommit(connectorId, branchName, GitCommit.Status.FAILED, accountId);
    persistence.save(gitCommit);
    persistence.save(gitCommit_1);

    // When we have failed commit followed by success one we dont delete any.
    testGitCommitsForExpiredCommits(account, 2, System.currentTimeMillis() + 2 * TWELVE_MONTH_IN_MILLS);

    GitCommit gitCommit_2 = buildGitCommit(connectorId, branchName, GitCommit.Status.COMPLETED_WITH_ERRORS, accountId);
    persistence.save(gitCommit_2);
    GitCommit gitCommit_3 = buildGitCommit(connectorId, branchName, GitCommit.Status.COMPLETED, accountId);
    persistence.save(gitCommit_3);

    testGitCommitsForExpiredCommits(account, 1, System.currentTimeMillis() + 2 * TWELVE_MONTH_IN_MILLS);

    persistence.save(gitCommit);
    persistence.save(gitCommit_1);
    persistence.save(gitCommit_2);
    persistence.save(gitCommit_3);

    testGitCommitsForExpiredCommits(account, 4, gitCommit_2.getCreatedAt() - 1);

    persistence.save(gitCommit);
    persistence.save(gitCommit_1);
    persistence.save(gitCommit_2);
    persistence.save(gitCommit_3);
    gitCommit_3.setCreatedAt(gitCommit_3.getCreatedAt() + 2);
    persistence.save(gitCommit_3);
    gitCommit_2.setCreatedAt(gitCommit_2.getCreatedAt() + 1);
    persistence.save(gitCommit_2);

    testGitCommitsForExpiredCommits(account, 2, gitCommit_2.getCreatedAt() + 1);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGitCommitsMixed() {
    final String connectorId = "gitConnectorId";
    final String branchName = "branchName";
    final String repository = "repository";
    final String accountId = account.getUuid();

    List<GitCommit> commits = new LinkedList<>();
    commits.add(buildGitCommit(connectorId, branchName, repository, GitCommit.Status.COMPLETED, accountId, 1L));
    commits.add(buildGitCommit(connectorId, branchName, repository, GitCommit.Status.COMPLETED, accountId, 2L));
    commits.add(buildGitCommit(connectorId, branchName, null, GitCommit.Status.COMPLETED, accountId, 3L));
    commits.add(buildGitCommit(connectorId, branchName, null, GitCommit.Status.COMPLETED, accountId, 4L));
    commits.add(buildGitCommit(connectorId, branchName, null, GitCommit.Status.FAILED, accountId, 5L));
    commits.add(
        buildGitCommit(connectorId, branchName, repository, GitCommit.Status.COMPLETED_WITH_ERRORS, accountId, 6L));
    commits.add(buildGitCommit(connectorId, branchName, repository, GitCommit.Status.FAILED, accountId, 7L));
    persistence.save(commits);
    testGitCommitsForExpiredCommits(account, 4, 8L);
  }

  private GitCommit buildGitCommit(String connectorId, String branchName, GitCommit.Status status, String accountId) {
    GitCommit commit = GitCommit.builder()
                           .gitConnectorId(connectorId)
                           .branchName(branchName)
                           .status(status)
                           .accountId(accountId)
                           .build();
    return commit;
  }

  private GitCommit buildGitCommit(String connectorId, String branchName, String repoName, GitCommit.Status status,
      String accountId, long createdAt) {
    GitCommit gitCommit = buildGitCommit(connectorId, branchName, status, accountId);
    gitCommit.setRepositoryName(repoName);
    gitCommit.setCreatedAt(createdAt);
    return gitCommit;
  }

  private void testGitCommitsForExpiredCommits(Account account, int expectedNotDeletedCount, long expiryTime) {
    gitSyncEntitiesExpiryHandler.handleGitCommits(account, expiryTime);
    List<GitCommit> postDeletedCommits =
        persistence.createQuery(GitCommit.class).filter(GitCommit.ACCOUNT_ID_KEY2, account.getUuid()).asList();
    assertThat(postDeletedCommits.size()).isEqualTo(expectedNotDeletedCount);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitFileActivity() {
    doReturn(true).when(gitSyncService).deleteGitActivityBeforeTime(anyLong(), anyString());
    gitSyncEntitiesExpiryHandler.handleGitFileActivity(account, 0L);
    verify(gitSyncService, times(1)).deleteGitActivityBeforeTime(anyLong(), anyString());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitFileActivitySummary() {
    doReturn(true).when(gitSyncService).deleteGitCommitsBeforeTime(anyLong(), anyString());
    gitSyncEntitiesExpiryHandler.handleGitCommitInGitFileActivitySummary(account, 0L);
    verify(gitSyncService, times(1)).deleteGitCommitsBeforeTime(anyLong(), anyString());
  }
}
