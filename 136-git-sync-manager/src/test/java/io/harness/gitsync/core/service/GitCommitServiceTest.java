/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.service;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.common.beans.GitSyncDirection;
import io.harness.gitsync.core.dtos.GitCommitDTO;
import io.harness.gitsync.core.impl.GitCommitServiceImpl;
import io.harness.gitsync.gitfileactivity.beans.GitFileProcessingSummary;
import io.harness.repositories.gitCommit.GitCommitRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class GitCommitServiceTest extends CategoryTest {
  @Mock GitCommitRepository gitCommitRepository;
  @InjectMocks @Inject GitCommitServiceImpl gitCommitService;
  @Mock MongoTemplate mongoTemplate;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testSave() {
    // TODO add a fix here
    //    gitCommitService.save(GitCommitDTO.builder().build());
    //    verify(gitCommitRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testUpsertOnCommitIdAndRepoUrlAndGitSyncDirection_WithFileSummary() {
    GitCommitDTO gitCommitDTO = GitCommitDTO.builder()
                                    .commitId("commitId")
                                    .commitMessage("commit message")
                                    .accountIdentifier("accountIdentifier")
                                    .fileProcessingSummary(GitFileProcessingSummary.builder().build())
                                    .repoURL("repourl")
                                    .gitSyncDirection(GitSyncDirection.GIT_TO_HARNESS)
                                    .build();
    when(mongoTemplate.upsert(any(), any(), any(Class.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
    UpdateResult updateResult = gitCommitService.upsertOnCommitIdAndRepoUrlAndGitSyncDirection(gitCommitDTO);
    assertThat(updateResult.getModifiedCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testUpsertOnCommitIdAndRepoUrlAndGitSyncDirection_WithoutFileSummary() {
    GitCommitDTO gitCommitDTO = GitCommitDTO.builder()
                                    .commitId("commitId")
                                    .commitMessage("commit message")
                                    .accountIdentifier("accountIGitCommitServiceTestdentifier")
                                    .fileProcessingSummary(null)
                                    .build();
    when(mongoTemplate.upsert(any(), any(), any(Class.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
    UpdateResult updateResult = gitCommitService.upsertOnCommitIdAndRepoUrlAndGitSyncDirection(gitCommitDTO);
    assertThat(updateResult.getModifiedCount()).isEqualTo(1);
  }
}
