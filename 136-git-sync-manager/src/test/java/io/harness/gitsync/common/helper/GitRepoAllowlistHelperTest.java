/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.HintException;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class GitRepoAllowlistHelperTest {
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @Spy GitRepoHelper gitRepoHelper = new GitRepoHelper();
  @Spy @InjectMocks GitRepoAllowlistHelper gitRepoAllowlistHelper;

  private final String ACCOUNT_IDENTIFIER = "accountId";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFilterAllowedReposInResponse() {
    Set<String> responseRepoList = new HashSet<>();
    responseRepoList.add("org/test-repo");

    List<String> repoAllowlist = new ArrayList<>();
    repoAllowlist.add("test-repo");
    doReturn(repoAllowlist).when(gitXSettingsHelper).getGitRepoAllowlist(any(), any(), any());

    Set<String> filteredResponse =
        gitRepoAllowlistHelper.filterRepoList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, responseRepoList);
    assertThat(filteredResponse.contains("org/test-repo")).isTrue();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFilterAllowedReposInResponseWithParentDirectoryInRepoAllowlist() {
    Set<String> responseRepoList = new HashSet<>();
    responseRepoList.add("org/test-repo");

    List<String> repoAllowlist = new ArrayList<>();
    repoAllowlist.add("org/test-repo");
    doReturn(repoAllowlist).when(gitXSettingsHelper).getGitRepoAllowlist(any(), any(), any());

    Set<String> filteredResponse =
        gitRepoAllowlistHelper.filterRepoList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, responseRepoList);
    assertThat(filteredResponse.contains("org/test-repo")).isTrue();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFilterAllowedReposInResponseWithMultipleParentDirectoryInRepoAllowlist() {
    Set<String> responseRepoList = new HashSet<>();
    responseRepoList.add("account/org/test-repo");

    List<String> repoAllowlist = new ArrayList<>();
    repoAllowlist.add("test-repo");
    doReturn(repoAllowlist).when(gitXSettingsHelper).getGitRepoAllowlist(any(), any(), any());

    Set<String> filteredResponse =
        gitRepoAllowlistHelper.filterRepoList(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, responseRepoList);
    assertThat(filteredResponse.contains("account/org/test-repo")).isTrue();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testValidateRepoWithInvalidRepo() {
    List<String> repoAllowlist = new ArrayList<>();
    repoAllowlist.add("test-repo");
    assertThatThrownBy(()
                           -> gitRepoAllowlistHelper.validateRepo(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, repoAllowlist, "invalidRepo"))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please check if repo [invalidRepo] is in allowed repository list under default setting for current scope: PROJECT with account Identifier [accountId], org identifier [orgId] and project identifier [projId]");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testValidateRepoWithValidRepo() {
    assertThatCode(()
                       -> gitRepoAllowlistHelper.validateRepo(
                           ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.EMPTY_LIST, "validRepo"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testValidateRepoWithNamespaceInRepoAllowlist() {
    ScmConnector scmConnector = GithubConnectorDTO.builder()
                                    .connectionType(GitConnectionType.REPO)
                                    .apiAccess(GithubApiAccessDTO.builder().build())
                                    .url("https://github.com/senjucanon2/test-repo")
                                    .build();

    List<String> repoAllowlist = new ArrayList<>();
    repoAllowlist.add("senjucanon2/test-repo");
    doReturn(repoAllowlist).when(gitXSettingsHelper).getGitRepoAllowlist(any(), any(), any());
    doNothing().when(gitRepoAllowlistHelper).validateRepo(any(), any(), any(), any(), any());

    gitRepoAllowlistHelper.validateRepo(Scope.builder()
                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .projectIdentifier(PROJ_IDENTIFIER)
                                            .build(),
        scmConnector, "test-repo");
    verify(gitRepoAllowlistHelper, times(1))
        .validateRepo(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, repoAllowlist, "senjucanon2/test-repo");
  }
}
