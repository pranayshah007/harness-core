/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmGetRepoUrlResponse;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class TemplateGitXHelperTest {
  @InjectMocks TemplateGitXHelper templateGitXHelper;

  @Mock SCMGitSyncHelper scmGitSyncHelper;

  private static final String BranchName = "branch";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String ENTITY_REPO_URL = "https://github.com/adivishy1/testRepo";

  private static final String PARENT_ENTITY_REPO = "testRepo";
  private static final String PARENT_ENTITY_CONNECTOR_REF = "account.github_connector";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetWorkingBranchRemote() {
    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .branch(BranchName)
                                   .parentEntityRepoName(PARENT_ENTITY_REPO)
                                   .parentEntityConnectorRef(PARENT_ENTITY_CONNECTOR_REF)
                                   .parentEntityAccountIdentifier(ACCOUNT_IDENTIFIER)
                                   .parentEntityOrgIdentifier(ORG_IDENTIFIER)
                                   .parentEntityProjectIdentifier(PROJECT_IDENTIFIER)
                                   .build();
    setupGitContext(branchInfo);
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    doReturn(ScmGetRepoUrlResponse.builder().repoUrl(ENTITY_REPO_URL).build())
        .when(scmGitSyncHelper)
        .getRepoUrl(any(), any(), any(), any());
    assertThat(templateGitXHelper.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);

    branchInfo = GitEntityInfo.builder()
                     .branch(BranchName)
                     .parentEntityRepoName(PARENT_ENTITY_REPO)
                     .parentEntityConnectorRef(PARENT_ENTITY_CONNECTOR_REF)
                     .build();
    setupGitContext(branchInfo);
    assertThat(templateGitXHelper.getWorkingBranch("random repo url")).isEqualTo("");
    branchInfo = GitEntityInfo.builder().branch(BranchName).parentEntityRepoUrl(ENTITY_REPO_URL).build();
    setupGitContext(branchInfo);
    assertThat(templateGitXHelper.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetWorkingBranchInline() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().branch(BranchName).build();
    setupGitContext(branchInfo);
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(templateGitXHelper.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
