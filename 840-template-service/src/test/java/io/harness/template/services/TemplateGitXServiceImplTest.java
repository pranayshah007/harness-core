/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.YOGESH;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.repositories.NGTemplateRepository;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.TemplateImportRequestDTO;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class TemplateGitXServiceImplTest {
  @InjectMocks TemplateGitXServiceImpl templateGitXService;

  @Mock SCMGitSyncHelper scmGitSyncHelper;

  @Mock GitSyncSdkService gitSyncSdkService;

  @Mock NGTemplateRepository templateRepository;

  @Mock GitAwareEntityHelper gitAwareEntityHelper;

  private static final String BranchName = "branch";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String ENTITY_REPO_URL = "https://github.com/adivishy1/testRepo";

  private static final String PARENT_ENTITY_REPO = "testRepo";
  private static final String PARENT_ENTITY_CONNECTOR_REF = "account.github_connector";

  private static final String TEMPLATE_ID = "templateID";

  private static final String PIPELINE_YAML = "pipeline: yaml";

  private static final String IMPORTED_YAML = "template:\n"
      + "  name: importT7\n"
      + "  identifier: importT7\n"
      + "  versionLabel: v2\n"
      + "  type: Step\n"
      + "  projectIdentifier: GitX_Remote\n"
      + "  orgIdentifier: default\n"
      + "  tags: {}\n"
      + "  spec:\n"
      + "    timeout: 10m\n"
      + "    type: ShellScript\n"
      + "    spec:\n"
      + "      shell: Bash\n"
      + "      onDelegate: true\n"
      + "      source:\n"
      + "        type: Inline\n"
      + "        spec:\n"
      + "          script: echo \"import Template v2\"\n"
      + "      environmentVariables: []\n"
      + "      outputVariables: []\n";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    templateGitXService =
        new TemplateGitXServiceImpl(scmGitSyncHelper, gitSyncSdkService, templateRepository, gitAwareEntityHelper);
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
    boolean isNewGitXEnabled = templateGitXService.isNewGitXEnabledAndIsRemoteEntity(templateToSave, branchInfo);
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

    boolean isNewGitXEnabled = templateGitXService.isNewGitXEnabledAndIsRemoteEntity(templateToSave, branchInfo);
    assertTrue(isNewGitXEnabled);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportTemplateFromRemote() {
    InvalidRequestException thrownException = new InvalidRequestException(
        "Requested metadata params do not match the values found in the YAML on Git for these fields: [identifier, versionLabel, orgIdentifier, projectIdentifier]");
    TemplateImportRequestDTO templateImportRequestDTO =
        TemplateImportRequestDTO.builder().templateName("importT7").templateVersion("v1").build();

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      templateGitXService.performImportFlowYamlValidations(
          ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, templateImportRequestDTO, IMPORTED_YAML);
    });

    assertEquals(thrownException.getMessage(), exception.getMessage());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testImportTemplateFromRemoteValidations() {
    TemplateImportRequestDTO templateImportRequestDTO = TemplateImportRequestDTO.builder()
                                                            .templateName("importT7")
                                                            .templateVersion("v2")
                                                            .templateDescription("foobar")
                                                            .build();

    templateGitXService.performImportFlowYamlValidations(
        "default", "GitX_Remote", "importT7", templateImportRequestDTO, IMPORTED_YAML);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRepoUrlAndCheckForFileUniqueness() {
    String repoUrl = "repoUrl";
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath("filePath").build();
    MockedStatic<GitAwareContextHelper> utilities = mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);

    doReturn(repoUrl).when(gitAwareEntityHelper).getRepoUrl(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    doReturn(10L).when(templateRepository).countFileInstances(ACCOUNT_IDENTIFIER, repoUrl, gitEntityInfo.getFilePath());
    assertThatThrownBy(()
                           -> templateGitXService.checkForFileUniquenessAndGetRepoURL(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, TEMPLATE_ID, false))
        .isInstanceOf(DuplicateFileImportException.class);

    assertThat(templateGitXService.checkForFileUniquenessAndGetRepoURL(
                   ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, TEMPLATE_ID, true))
        .isEqualTo(repoUrl);
  }
}
