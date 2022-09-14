package io.harness.template.services;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmGetRepoUrlResponse;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TemplateGitXServiceImplTest {
  @InjectMocks TemplateGitXServiceImpl templateGitXService;

  @Mock SCMGitSyncHelper scmGitSyncHelper;

  @Mock GitSyncSdkService gitSyncSdkService;

  @Mock NGTemplateFeatureFlagHelperService ngTemplateFeatureFlagHelperService;

  private static final String BranchName = "branch";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String ENTITY_REPO_URL = "https://github.com/adivishy1/testRepo";

  private static final String PARENT_ENTITY_REPO = "testRepo";
  private static final String PARENT_ENTITY_CONNECTOR_REF = "account.github_connector";

  private static final String TEMPLATE_ID = "templateID";

  private static final String PIPELINE_YAML = "pipeline: yaml";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    templateGitXService =
        new TemplateGitXServiceImpl(scmGitSyncHelper, ngTemplateFeatureFlagHelperService, gitSyncSdkService);
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
    assertThat(templateGitXService.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);

    branchInfo = GitEntityInfo.builder()
                     .branch(BranchName)
                     .parentEntityRepoName(PARENT_ENTITY_REPO)
                     .parentEntityConnectorRef(PARENT_ENTITY_CONNECTOR_REF)
                     .build();
    setupGitContext(branchInfo);
    assertThat(templateGitXService.getWorkingBranch("random repo url")).isEqualTo("");
    branchInfo = GitEntityInfo.builder().branch(BranchName).parentEntityRepoUrl(ENTITY_REPO_URL).build();
    setupGitContext(branchInfo);
    assertThat(templateGitXService.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetWorkingBranchInline() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().branch(BranchName).build();
    setupGitContext(branchInfo);
    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(templateGitXService.getWorkingBranch(ENTITY_REPO_URL)).isEqualTo(BranchName);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDPresent() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(ACCOUNT_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .identifier(TEMPLATE_ID)
                                        .yaml(PIPELINE_YAML)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(PARENT_ENTITY_CONNECTOR_REF)
                                   .repoName(PARENT_ENTITY_REPO)
                                   .branch(BranchName)
                                   .filePath(ENTITY_REPO_URL)
                                   .build();
    when(gitSyncSdkService.isGitSimplificationEnabled(any(), any(), any())).thenReturn(true);
    boolean isNewGitXEnabled = templateGitXService.isNewGitXEnabled(templateToSave, branchInfo);
    assertTrue(isNewGitXEnabled);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledWhenProjectIDMissingWithFeatureFlagEnabled() {
    TemplateEntity templateToSave = TemplateEntity.builder()
                                        .accountId(ACCOUNT_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .identifier(TEMPLATE_ID)
                                        .yaml(PIPELINE_YAML)
                                        .build();

    GitEntityInfo branchInfo = GitEntityInfo.builder()
                                   .storeType(StoreType.REMOTE)
                                   .connectorRef(PARENT_ENTITY_CONNECTOR_REF)
                                   .repoName(PARENT_ENTITY_REPO)
                                   .branch(BranchName)
                                   .filePath(ENTITY_REPO_URL)
                                   .build();

    Mockito
        .when(
            ngTemplateFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.NG_TEMPLATE_GITX_ACCOUNT_ORG))
        .thenReturn(true);

    boolean isNewGitXEnabled = templateGitXService.isNewGitXEnabled(templateToSave, branchInfo);
    assertTrue(isNewGitXEnabled);
  }
}
