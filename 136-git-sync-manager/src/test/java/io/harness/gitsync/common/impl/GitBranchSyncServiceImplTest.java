/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.scm.ScmGitUtils;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitBranchSyncServiceImplTest extends GitSyncTestBase {
  @InjectMocks GitBranchSyncServiceImpl gitBranchSyncService;
  @Mock ScmOrchestratorService scmOrchestratorService;
  @Mock YamlGitConfigDTO yamlGitConfigDTO;
  public static final String commitId = "commitId";
  public static final String accountIdentifier = "accountIdentifier";
  public static final String message = "message";
  public static final String rootFolder = "abc/.harness";
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetCommitMessage() {
    assert (gitBranchSyncService.getCommitMessage(yamlGitConfigDTO, null, accountIdentifier)) == null;
    Commit commit = Commit.newBuilder().setMessage(message).build();
    when(scmOrchestratorService.processScmRequest(any(Function.class), any(), any(), any())).thenReturn(commit);
    assertThat(gitBranchSyncService.getCommitMessage(yamlGitConfigDTO, commitId, accountIdentifier)).isEqualTo(message);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFilteredFiles() {
    String filePath1 = "xyz.yaml";
    String filePath2 = "ttt.yaml";
    List<GitFileChangeDTO> gitFileChangeDTOList = new ArrayList<>();
    gitFileChangeDTOList.add(GitFileChangeDTO.builder().path(rootFolder + "/" + filePath1).build());
    gitFileChangeDTOList.add(GitFileChangeDTO.builder().path(rootFolder + "/" + filePath2).build());
    List<String> filesToExclude = Arrays.asList(ScmGitUtils.createFilePath(rootFolder, filePath1));
    // full sync sends file with createFilePath method and GitFileChangeDTO contains path that is send directly by Git.
    List<GitFileChangeDTO> filteredFiles = gitBranchSyncService.getFilteredFiles(gitFileChangeDTOList, filesToExclude);
    assertThat(filteredFiles).isNotNull();
    assertThat(filteredFiles.size()).isEqualTo(1);
    assertThat(filteredFiles.get(0).getPath()).isEqualTo(rootFolder + "/" + filePath2);
  }
}
