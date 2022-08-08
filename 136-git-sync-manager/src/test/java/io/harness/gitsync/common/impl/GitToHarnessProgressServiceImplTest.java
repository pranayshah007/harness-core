/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepStatus;
import io.harness.gitsync.common.beans.GitToHarnessProcessingStepType;
import io.harness.gitsync.common.beans.GitToHarnessProgressStatus;
import io.harness.gitsync.common.beans.YamlChangeSetEventType;
import io.harness.gitsync.common.dtos.GitToHarnessProgressDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class GitToHarnessProgressServiceImplTest extends GitSyncTestBase {
  @Inject GitToHarnessProgressServiceImpl gitToHarnessProgressService;

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testSave() {
    GitToHarnessProgressDTO gitToHarnessProgressToBeSaved = createGitToHarnessProgress();
    GitToHarnessProgressDTO savedDTO = gitToHarnessProgressService.save(gitToHarnessProgressToBeSaved);
    validateTheFieldsAreCorrect(savedDTO, gitToHarnessProgressToBeSaved);
  }

  private void validateTheFieldsAreCorrect(
      GitToHarnessProgressDTO gitToHarnessProgressSaved, GitToHarnessProgressDTO gitToHarnessProgressToBeSaved) {
    assertThat(gitToHarnessProgressSaved.getAccountIdentifier())
        .isEqualTo(gitToHarnessProgressSaved.getAccountIdentifier());
    assertThat(gitToHarnessProgressSaved.getBranch()).isEqualTo(gitToHarnessProgressSaved.getBranch());
    assertThat(gitToHarnessProgressSaved.getCommitId()).isEqualTo(gitToHarnessProgressSaved.getCommitId());
    assertThat(gitToHarnessProgressSaved.getEventType()).isEqualTo(gitToHarnessProgressSaved.getEventType());
    assertThat(gitToHarnessProgressSaved.getGitFileChanges()).isEqualTo(gitToHarnessProgressSaved.getGitFileChanges());
    assertThat(gitToHarnessProgressSaved.getRepoUrl()).isEqualTo(gitToHarnessProgressSaved.getRepoUrl());
    assertThat(gitToHarnessProgressSaved.getStepStartingTime())
        .isEqualTo(gitToHarnessProgressSaved.getStepStartingTime());
    assertThat(gitToHarnessProgressSaved.getStepStatus()).isEqualTo(gitToHarnessProgressSaved.getStepStatus());
    assertThat(gitToHarnessProgressSaved.getStepStatus()).isEqualTo(gitToHarnessProgressSaved.getStepStatus());
    assertThat(gitToHarnessProgressSaved.getGitToHarnessProgressStatus())
        .isEqualTo(gitToHarnessProgressSaved.getGitToHarnessProgressStatus());
    assertThat(gitToHarnessProgressSaved.getYamlChangeSetId())
        .isEqualTo(gitToHarnessProgressSaved.getYamlChangeSetId());
  }

  private GitToHarnessProgressDTO createGitToHarnessProgress() {
    return GitToHarnessProgressDTO.builder()
        .accountIdentifier("accountIdentifier")
        .branch("branch")
        .commitId("commitId")
        .eventType(YamlChangeSetEventType.BRANCH_SYNC)
        .gitFileChanges(Collections.emptyList())
        .repoUrl("repoUrl")
        .stepStartingTime(System.currentTimeMillis())
        .stepStatus(GitToHarnessProcessingStepStatus.DONE)
        .stepType(GitToHarnessProcessingStepType.GET_FILES)
        .gitToHarnessProgressStatus(GitToHarnessProgressStatus.DONE)
        .yamlChangeSetId("yamlChangeSetId")
        .build();
  }
}
