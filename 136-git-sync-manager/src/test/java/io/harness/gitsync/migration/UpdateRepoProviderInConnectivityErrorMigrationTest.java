/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.migration;

import static io.harness.gitsync.common.dtos.RepoProviders.BITBUCKET;
import static io.harness.gitsync.common.dtos.RepoProviders.GITHUB;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitSyncErrorType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.springframework.data.mongodb.core.MongoTemplate;

public class UpdateRepoProviderInConnectivityErrorMigrationTest extends GitSyncTestBase {
  @Inject MongoTemplate mongoTemplate;
  @InjectMocks @Inject UpdateRepoProviderInConnectivityErrorMigration updateRepoProviderInConnectivityErrorMigration;

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void migrateTest() {
    GitSyncError bitbucketError = GitSyncError.builder()
                                      .accountIdentifier("accountIdentifier")
                                      .repoUrl("https://bitbucket.org/harness/harness-core")
                                      .branchName("master")
                                      .completeFilePath(".harness/test/ConnectorDisconnectHandler.yaml")
                                      .errorType(GitSyncErrorType.GIT_TO_HARNESS)
                                      .build();
    GitSyncError githubError = GitSyncError.builder()
                                   .accountIdentifier("accountIdentifier")
                                   .repoUrl("https://github.com/harness/harness-core")
                                   .branchName("master")
                                   .completeFilePath(".harness/test/ConnectorDisconnectHandler.yaml")
                                   .errorType(GitSyncErrorType.GIT_TO_HARNESS)
                                   .build();
    GitSyncError githubSaaSConnectivityError = GitSyncError.builder()
                                                   .accountIdentifier("accountIdentifier")
                                                   .repoUrl("https://github.com/harness/harness-core")
                                                   .branchName("master")
                                                   .errorType(GitSyncErrorType.CONNECTIVITY_ISSUE)
                                                   .build();
    GitSyncError githubOnpremConnectivityError = GitSyncError.builder()
                                                     .accountIdentifier("accountIdentifier")
                                                     .repoUrl("https://22.45.108.67:80/harness/harness-core")
                                                     .branchName("master")
                                                     .errorType(GitSyncErrorType.CONNECTIVITY_ISSUE)
                                                     .build();
    GitSyncError bitbucketConnecitivityError = GitSyncError.builder()
                                                   .accountIdentifier("accountIdentifier")
                                                   .repoUrl("https://bitbucket.org/harness/harness-core")
                                                   .branchName("master")
                                                   .errorType(GitSyncErrorType.CONNECTIVITY_ISSUE)
                                                   .build();
    mongoTemplate.save(bitbucketError);
    mongoTemplate.save(githubError);
    mongoTemplate.save(githubSaaSConnectivityError);
    mongoTemplate.save(githubOnpremConnectivityError);
    mongoTemplate.save(bitbucketConnecitivityError);

    updateRepoProviderInConnectivityErrorMigration.migrate();

    List<GitSyncError> allGitSyncErrors = mongoTemplate.findAll(GitSyncError.class);
    assertThat(allGitSyncErrors.size()).isEqualTo(5);

    List<GitSyncError> connectivityErrors =
        allGitSyncErrors.stream()
            .filter(error -> error.getErrorType() == GitSyncErrorType.CONNECTIVITY_ISSUE)
            .collect(Collectors.toList());
    assertThat(connectivityErrors.size()).isEqualTo(3);

    List<GitSyncError> errorInBitbucketFiles =
        connectivityErrors.stream().filter(error -> error.getRepoProvider() == BITBUCKET).collect(Collectors.toList());
    assertThat(errorInBitbucketFiles.size()).isEqualTo(1);

    List<GitSyncError> errorInGithubFiles =
        connectivityErrors.stream().filter(error -> error.getRepoProvider() == GITHUB).collect(Collectors.toList());
    assertThat(errorInGithubFiles.size()).isEqualTo(2);
  }
}
