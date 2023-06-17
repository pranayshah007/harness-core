/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitfileactivity.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus.COMPLETED;
import static io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus.QUEUED;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.git.model.GitFileChange;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.core.beans.ChangeWithErrorMsg;
import io.harness.gitsync.core.beans.GitCommit;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivity;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivity.Status;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivity.TriggeredBy;
import io.harness.gitsync.gitfileactivity.beans.GitFileActivitySummary;
import io.harness.repositories.gitFileActivity.GitFileActivityRepository;
import io.harness.repositories.gitFileActivitySummary.GitFileActivitySummaryRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(DX)
public class GitSyncServiceImplTest extends GitSyncTestBase {
  @Inject @Spy private GitSyncServiceImpl gitSyncService;
  @Inject GitFileActivityRepository gitFileActivityRepository;
  @Inject GitFileActivitySummaryRepository gitFileActivitySummaryRepository;

  private final String accountId = "accountId";
  private final String gitConnectorId = "gitConnectorId";
  private final String branchName = "branchName";
  private final String repo = "repo";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void test_shouldLogActivityForGitOperation() {
    final String commitId = "commitId";
    final String filePath = "rootpath/connector/file1.yaml";
    final String commitMessage = "commitMessage";
    Iterable<GitFileActivity> gitFileActivities =
        gitSyncService.logActivityForGitOperation(Collections.singletonList(GitFileChange.builder()
                                                                                .filePath(filePath)
                                                                                .commitMessage(commitMessage)
                                                                                .accountId(accountId)
                                                                                .commitId(commitId)
                                                                                .changeFromAnotherCommit(Boolean.TRUE)
                                                                                .build()),
            Status.SUCCESS, false, false, accountId, commitId, commitMessage,
            YamlGitConfigDTO.builder().branch(branchName).repo(repo).gitConnectorRef(gitConnectorId).build());

    GitFileActivity fileActivity = gitFileActivities.iterator().next();

    assertThat(fileActivity).isNotNull();
    assertThat(fileActivity.getCommitId()).isEqualTo(commitId);
    assertThat(fileActivity.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(fileActivity.getFilePath()).isEqualTo(filePath);
    assertThat(fileActivity.getCommitId()).isEqualTo(commitId);
    assertThat(fileActivity.getCommitMessage()).isEqualTo(commitMessage);
    assertThat(fileActivity.getProcessingCommitId()).isEqualTo(commitId);
    assertThat(fileActivity.getProcessingCommitMessage()).isEqualTo(commitMessage);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void test_shouldCreateGitFileActivitySummary() {
    String commitId = "commitId";
    String commitMessage = "commitMessage";
    String filePath1 = "filePath1";
    doReturn(Collections.singletonList(GitFileActivity.builder()
                                           .accountId(accountId)
                                           .triggeredBy(TriggeredBy.FULL_SYNC)
                                           .commitId(commitId)
                                           .processingCommitId(commitId)
                                           .filePath(filePath1)
                                           .gitConnectorId(gitConnectorId)
                                           .branchName(branchName)
                                           .repo(repo)
                                           .commitMessage(commitMessage)
                                           .status(Status.SUCCESS)
                                           .build()))
        .when(gitSyncService)
        .getFileActivitesForCommit(anyString(), anyString());

    GitFileActivitySummary fileActivitySummary = gitSyncService.createGitFileActivitySummaryForCommit(
        commitId, accountId, false, COMPLETED, YamlGitConfigDTO.builder().build());

    assertThat(fileActivitySummary.getAccountId()).isEqualTo(accountId);
    assertThat(fileActivitySummary.getCommitId()).isEqualTo(commitId);
    assertThat(fileActivitySummary.getGitConnectorId()).isEqualTo(gitConnectorId);
    assertThat(fileActivitySummary.getBranchName()).isEqualTo(branchName);
    assertThat(fileActivitySummary.getCommitMessage()).isEqualTo(commitMessage);
    assertThat(fileActivitySummary.getStatus()).isEqualTo(COMPLETED);
    assertThat(fileActivitySummary.getGitToHarness()).isEqualTo(false);
    assertThat(fileActivitySummary.getFileProcessingSummary().getSuccessCount()).isEqualTo(1);
    assertThat(fileActivitySummary.getFileProcessingSummary().getTotalCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSaveAll() {
    gitSyncService.saveAll(Collections.singletonList(GitFileActivity.builder()
                                                         .accountId(accountId)
                                                         .triggeredBy(TriggeredBy.FULL_SYNC)
                                                         .commitId("commitId")
                                                         .processingCommitId("commitId")
                                                         .filePath("filePath")
                                                         .gitConnectorId(gitConnectorId)
                                                         .branchName(branchName)
                                                         .commitMessage("commitMessage")
                                                         .status(Status.SUCCESS)
                                                         .build()));
    final Iterable<GitFileActivity> fileActivities = gitFileActivityRepository.findAll();
    assertThat(fileActivities.iterator().hasNext()).isEqualTo(true);
    assertThat(fileActivities.iterator().next()).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testUpdateGitFileActivity() {
    final String commitId = "commitId";
    GitFileChange gitFileChange = GitFileChange.builder()
                                      .filePath("filePath")
                                      .accountId(accountId)
                                      .processingCommitId(commitId)
                                      .changeFromAnotherCommit(true)
                                      .syncFromGit(true)
                                      .build();
    GitFileChange gitFileChange1 =
        GitFileChange.builder().filePath("filePath1").accountId(accountId).processingCommitId(commitId).build();
    GitFileChange gitFileChange2 = GitFileChange.builder()
                                       .filePath("filePath2")
                                       .accountId(accountId)
                                       .processingCommitId(commitId)
                                       .changeFromAnotherCommit(false)
                                       .syncFromGit(true)
                                       .build();
    GitFileChange gitFileChange3 = GitFileChange.builder()
                                       .filePath("filePath3")
                                       .accountId(accountId)
                                       .processingCommitId(commitId)
                                       .changeFromAnotherCommit(false)
                                       .syncFromGit(true)
                                       .build();

    GitFileActivity gitFileActivity = GitFileActivity.builder()
                                          .filePath("filePath")
                                          .repo(repo)
                                          .branchName(branchName)
                                          .gitConnectorId(gitConnectorId)
                                          .errorMessage("errorOld")
                                          .status(Status.FAILED)
                                          .processingCommitId("oldCommit")
                                          .accountId(accountId)
                                          .build();
    GitFileActivity gitFileActivity1 = GitFileActivity.builder()
                                           .filePath("filePath1")
                                           .repo(repo)
                                           .branchName(branchName)
                                           .gitConnectorId(gitConnectorId)
                                           .status(Status.QUEUED)
                                           .processingCommitId(commitId)
                                           .accountId(accountId)
                                           .build();
    GitFileActivity gitFileActivity2 = GitFileActivity.builder()
                                           .filePath("filePath2")
                                           .repo(repo)
                                           .branchName(branchName)
                                           .gitConnectorId(gitConnectorId)
                                           .status(Status.QUEUED)
                                           .processingCommitId(commitId)
                                           .accountId(accountId)
                                           .build();
    GitFileActivity gitFileActivity3 = GitFileActivity.builder()
                                           .filePath("filePath3")
                                           .repo(repo)
                                           .branchName(branchName)
                                           .gitConnectorId(gitConnectorId)
                                           .status(Status.QUEUED)
                                           .processingCommitId(commitId)
                                           .accountId(accountId)
                                           .build();

    List<ChangeWithErrorMsg> changeWithErrorMsgs =
        Arrays.asList(ChangeWithErrorMsg.builder().change(gitFileChange).errorMsg("error").build(),
            ChangeWithErrorMsg.builder().change(gitFileChange1).errorMsg("error1").build());

    gitFileActivityRepository.saveAll(
        Arrays.asList(gitFileActivity, gitFileActivity1, gitFileActivity2, gitFileActivity3));
    gitSyncService.updateGitFileActivity(changeWithErrorMsgs, Collections.singletonList(gitFileChange2),
        Collections.singletonList(gitFileChange3), accountId, false, "latesterror",
        YamlGitConfigDTO.builder().repo(repo).branch(branchName).build());
    List<GitFileActivity> gitFileActivities = new ArrayList<>();
    gitFileActivityRepository.findAll().forEach(gitFileActivities::add);
    assertThat(gitFileActivities).isNotNull();
    assertThat(gitFileActivities.size()).isEqualTo(5);
    assertThat(gitFileActivities.stream().map(GitFileActivity::getStatus)).isNotIn(Collections.singletonList(QUEUED));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCreateGitFileSummaryForFailedOrSkippedCommit() {
    final GitCommit gitCommit =
        GitCommit.builder().repoURL(repo).branchName(branchName).accountIdentifier(accountId).build();
    gitSyncService.createGitFileSummaryForFailedOrSkippedCommit(gitCommit, true);
    List<GitFileActivitySummary> gitFileActivities = new ArrayList<>();
    gitFileActivitySummaryRepository.findAll().forEach(gitFileActivities::add);
    assertThat(gitFileActivities.size()).isEqualTo(1);
  }
}
