/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.GITOPS)
public class NGGitOpsCommandTaskTest extends CategoryTest {
  private static final String TEST_INPUT_ID = generateUuid();
  @InjectMocks
  NGGitOpsCommandTask ngGitOpsCommandTask = new NGGitOpsCommandTask(
      DelegateTaskPackage.builder()
          .delegateId(TEST_INPUT_ID)
          .delegateTaskId(TEST_INPUT_ID)
          .data(TaskData.builder().parameters(new Object[] {}).taskType(TEST_INPUT_ID).async(false).build())
          .accountId(TEST_INPUT_ID)
          .build(),
      null, null, null);
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testUpdateFilesNotFoundWithEmptyContent() {
    List<String> paths = Arrays.asList("path1", "path2");
    List<GitFile> gitfiles = Arrays.asList(GitFile.builder().filePath("path1").fileContent("content1").build());
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().files(gitfiles).build();

    ngGitOpsCommandTask.updateFilesNotFoundWithEmptyContent(fetchFilesResult, paths);
    assertThat(fetchFilesResult.getFiles()).hasSize(2);
    assertThat(fetchFilesResult.getFiles().get(0).getFilePath()).isEqualTo("path1");
    assertThat(fetchFilesResult.getFiles().get(0).getFileContent()).isEqualTo("content1");
    assertThat(fetchFilesResult.getFiles().get(1).getFilePath()).isEqualTo("path2");
    assertThat(fetchFilesResult.getFiles().get(1).getFileContent()).isEqualTo("");
  }
}
