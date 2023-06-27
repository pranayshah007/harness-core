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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitx.GitXSettingsHelper;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class GitRepoAllowlistHelperTest {
  @Mock GitXSettingsHelper gitXSettingsHelper;
  @InjectMocks GitRepoAllowlistHelper gitRepoAllowlistHelper;

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
}
