/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitsyncerror.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.common.dtos.RepoProviders.BITBUCKET;
import static io.harness.gitsync.common.dtos.RepoProviders.GITHUB;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.gitsyncerror.GitSyncErrorStatus;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorAggregateByCommit;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorDetails;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorType;
import io.harness.gitsync.gitsyncerror.beans.GitToHarnessErrorDetails;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorAggregateByCommitDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitSyncErrorDetailsDTO;
import io.harness.gitsync.gitsyncerror.dtos.GitToHarnessErrorDetailsDTO;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class GitSyncErrorMapperTest extends GitSyncTestBase {
  private String accountIdentifier;
  private String repoUrl;
  private String branchName;
  private GitSyncErrorType errorType;
  private GitSyncErrorStatus status;
  private String failureReason;
  private EntityType entityType;
  private GitSyncErrorDetails gitSyncErrorDetails;
  private GitSyncError gitSyncError;
  private GitSyncErrorDTO gitSyncErrorDTO;
  private GitSyncErrorDetailsDTO gitSyncErrorDetailsDTO;
  private GitSyncErrorAggregateByCommit gitSyncErrorAggregateByCommit;
  private GitSyncErrorAggregateByCommitDTO gitSyncErrorAggregateByCommitDTO;
  private String commitId;

  @Before
  public void setup() {
    accountIdentifier = randomAlphabetic(10);
    repoUrl = "repoUrl";
    branchName = "A";
    errorType = GitSyncErrorType.GIT_TO_HARNESS;
    status = GitSyncErrorStatus.ACTIVE;
    failureReason = "Invalid yaml";
    entityType = EntityType.CONNECTORS;
    commitId = "commitId";
    gitSyncErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commitId").commitMessage("Message").yamlContent(null).build();
    gitSyncErrorDetailsDTO = GitToHarnessErrorDetailsDTO.builder()
                                 .gitCommitId("commitId")
                                 .commitMessage("Message")
                                 .yamlContent(null)
                                 .entityUrl("repoUrl/blob/A/test")
                                 .build();

    gitSyncError = GitSyncError.builder()
                       .accountIdentifier(accountIdentifier)
                       .repoUrl(repoUrl)
                       .completeFilePath("test")
                       .branchName(branchName)
                       .errorType(errorType)
                       .status(status)
                       .failureReason(failureReason)
                       .entityType(entityType)
                       .additionalErrorDetails(gitSyncErrorDetails)
                       .build();

    gitSyncErrorDTO = GitSyncErrorDTO.builder()
                          .accountIdentifier(accountIdentifier)
                          .repoUrl(repoUrl)
                          .completeFilePath("test")
                          .branchName(branchName)
                          .errorType(errorType)
                          .status(status)
                          .failureReason(failureReason)
                          .entityType(entityType)
                          .additionalErrorDetails(gitSyncErrorDetailsDTO)
                          .build();

    gitSyncErrorAggregateByCommit = GitSyncErrorAggregateByCommit.builder()
                                        .gitCommitId(commitId)
                                        .failedCount(1)
                                        .branchName(branchName)
                                        .repoId(repoUrl)
                                        .errorsForSummaryView(ImmutableList.of(gitSyncError))
                                        .build();

    gitSyncErrorAggregateByCommitDTO = GitSyncErrorAggregateByCommitDTO.builder()
                                           .gitCommitId(commitId)
                                           .failedCount(1)
                                           .branchName(branchName)
                                           .repoId(repoUrl)
                                           .errorsForSummaryView(ImmutableList.of(gitSyncErrorDTO))
                                           .build();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void toGitSyncErrorDTOTest() {
    GitSyncErrorDTO dto = GitSyncErrorMapper.toGitSyncErrorDTO(gitSyncError);
    assertThat(dto).isEqualTo(gitSyncErrorDTO);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void toGitSyncErrorTest() {
    GitSyncError error = GitSyncErrorMapper.toGitSyncError(gitSyncErrorDTO, accountIdentifier);
    assertThat(error).isEqualTo(gitSyncError);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void toGitSyncErrorAggregateByCommitDTOTest() {
    GitSyncErrorAggregateByCommitDTO dto =
        GitSyncErrorMapper.toGitSyncErrorAggregateByCommitDTO(gitSyncErrorAggregateByCommit);
    assertThat(dto).isEqualTo(gitSyncErrorAggregateByCommitDTO);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void getFilePathUrlTest() {
    GitSyncError connectivityTypeGitSyncError = GitSyncError.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .repoUrl(repoUrl)
                                                    .branchName(branchName)
                                                    .errorType(GitSyncErrorType.CONNECTIVITY_ISSUE)
                                                    .build();
    String filePathUrl = GitSyncErrorMapper.getFilePathUrl(connectivityTypeGitSyncError);
    assertThat(filePathUrl).isEqualTo(null);

    GitSyncError fullSyncGitError = GitSyncError.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .repoUrl("https://github.com/harness/harness-core")
                                        .branchName("master")
                                        .completeFilePath(".harness/test/ConnectorDisconnectHandler.yaml")
                                        .errorType(GitSyncErrorType.FULL_SYNC)
                                        .repoProvider(GITHUB)
                                        .build();
    String filePathUrlForGithub = GitSyncErrorMapper.getFilePathUrl(fullSyncGitError);
    assertThat(filePathUrlForGithub)
        .isEqualTo("https://github.com/harness/harness-core/blob/master/.harness/test/connector.yaml");

    GitSyncError gittoHarnessError = GitSyncError.builder()
                                         .accountIdentifier(accountIdentifier)
                                         .repoUrl("https://bitbucket.org/harness/harness-core")
                                         .branchName("master")
                                         .completeFilePath(".harness/test/ConnectorDisconnectHandler.yaml")
                                         .errorType(GitSyncErrorType.GIT_TO_HARNESS)
                                         .repoProvider(BITBUCKET)
                                         .build();
    String filePathUrlForBitbucket = GitSyncErrorMapper.getFilePathUrl(gittoHarnessError);
    assertThat(filePathUrlForBitbucket)
        .isEqualTo("https://bitbucket.org/harness/harness-core/src/master/.harness/test/connector.yaml");
  }
}
