/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.git.GitClientV2;
import io.harness.git.model.ChangeType;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.CommitResult;
import io.harness.git.model.PushResultGit;
import io.harness.git.model.PushResultGit.RefUpdate;
import io.harness.rule.Owner;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitCommitResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitPushResult;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitServiceImplTest extends CategoryTest {
  @Mock GitClientV2 gitClient;
  @Inject @InjectMocks GitServiceImpl gitService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testCommitAndPush() {
    final String repoUrl = "http://sample.com";
    final String password = "password";
    final String username = "username";
    final String commitId = "commitId";
    final String commitMsg = "commitMsg";
    final int commitTime = 123;
    final String filePath = "filePath";
    final String content = "content";
    final GitCommitRequest gcr = GitCommitRequest.builder()
                                     .forcePush(false)
                                     .pushOnlyIfHeadSeen(true)
                                     .gitFileChanges(Collections.singletonList(
                                         GitFileChange.Builder.aGitFileChange().withChangeType(ChangeType.ADD).build()))
                                     .build();
    final GitOperationContext goc =
        GitOperationContext.builder()
            .gitConfig(GitConfig.builder().username(username).password(password.toCharArray()).repoUrl(repoUrl).build())
            .gitCommitRequest(gcr)
            .build();
    final CommitAndPushResult commitAndPushResult =
        CommitAndPushResult.builder()
            .gitCommitResult(
                CommitResult.builder().commitId(commitId).commitMessage(commitMsg).commitTime(commitTime).build())
            .filesCommittedToGit(Collections.singletonList(io.harness.git.model.GitFileChange.builder()
                                                               .changeType(ChangeType.ADD)
                                                               .filePath(filePath)
                                                               .fileContent(content)
                                                               .commitMessage(commitMsg)
                                                               .commitId(commitId)
                                                               .build()))
            .gitPushResult(PushResultGit.pushResultBuilder().refUpdate(RefUpdate.builder().build()).build())
            .build();
    final GitCommitAndPushResult gitCommitAndPushResult =
        GitCommitAndPushResult.builder()
            .filesCommitedToGit(Collections.singletonList(GitFileChange.Builder.aGitFileChange()
                                                              .withCommitMessage(commitMsg)
                                                              .withChangeType(ChangeType.ADD)
                                                              .withFilePath(filePath)
                                                              .withFileContent(content)
                                                              .withCommitId(commitId)
                                                              .withCommitMessage(commitMsg)
                                                              .build()))
            .gitCommitResult(
                GitCommitResult.builder().commitId(commitId).commitMessage(commitMsg).commitTime(commitTime).build())
            .gitPushResult(GitPushResult.builder().refUpdate(GitPushResult.RefUpdate.builder().build()).build())
            .build();

    doReturn(commitAndPushResult).when(gitClient).commitAndPush(any());
    final GitCommitAndPushResult gitCommitAndPushResultRet = gitService.commitAndPush(goc);

    assertThat(gitCommitAndPushResultRet).isNotNull();
    assertThat(gitCommitAndPushResultRet).isEqualTo(gitCommitAndPushResult);
  }
}
