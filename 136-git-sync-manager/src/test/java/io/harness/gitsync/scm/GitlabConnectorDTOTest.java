/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
@OwnedBy(PIPELINE)
public class GitlabConnectorDTOTest extends GitSyncTestBase {
  public static final String REPO_URL = "https://gitlab.com/gitlab160412/testRepo";
  public static final String ACCOUNT_URL = "https://gitlab.com/gitlab160412/";
  public static final String FILE_URL = "https://gitlab.com/gitlab160412/testRepo/-/blob/main/.harness/gitlabP4.yaml";
  public static final String BRANCH = "main";
  public static final String FILE_PATH = ".harness/gitlabP4.yaml";
  public static final String COMMIT_ID = "commitID";
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRepositoryDetailsForGitlab() {
    GitlabConnectorDTO gitlabConnectorDTO =
        GitlabConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .url(REPO_URL)
            .build();
    assertThat(gitlabConnectorDTO.getGitRepositoryDetails().getName()).isEqualTo("testRepo");
    assertThat(gitlabConnectorDTO.getGitRepositoryDetails().getOrg()).isEqualTo("gitlab160412");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetGitConnectionUrlForRepoConnector() {
    GitlabConnectorDTO gitlabConnectorDTO =
        GitlabConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .url(REPO_URL)
            .build();
    GitRepositoryDTO.builder().name("testRepo").build();
    assertEquals(REPO_URL, gitlabConnectorDTO.getGitConnectionUrl(GitRepositoryDTO.builder().name("testRepo").build()));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetGitConnectionUrlForAccountConnector() {
    GitlabConnectorDTO gitlabConnectorDTO =
        GitlabConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .url(ACCOUNT_URL)
            .build();
    GitRepositoryDTO.builder().name("testRepo").build();
    assertEquals(REPO_URL, gitlabConnectorDTO.getGitConnectionUrl(GitRepositoryDTO.builder().name("testRepo").build()));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetFileUrl() {
    GitlabConnectorDTO gitlabConnectorDTO =
        GitlabConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .authentication(GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .url(ACCOUNT_URL)
            .build();
    GitRepositoryDTO.builder().name("testRepo").build();
    assertEquals(FILE_URL,
        gitlabConnectorDTO.getFileUrl(
            BRANCH, FILE_PATH, COMMIT_ID, GitRepositoryDTO.builder().name("testRepo").build()));
  }
}
