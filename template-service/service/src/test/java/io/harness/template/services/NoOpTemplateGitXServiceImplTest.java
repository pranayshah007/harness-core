/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.resources.beans.TemplateImportRequestDTO;

import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class NoOpTemplateGitXServiceImplTest extends TemplateServiceTestBase {
  @Inject NoOpTemplateGitXServiceImpl noOpTemplateGitXService;

  TemplateEntity template;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    template = TemplateEntity.builder()
                   .accountId(ACCOUNT_ID)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJ_IDENTIFIER)
                   .identifier(TEMPLATE_IDENTIFIER)
                   .name(TEMPLATE_IDENTIFIER)
                   .versionLabel(TEMPLATE_VERSION_LABEL)
                   .storeType(StoreType.REMOTE)
                   .templateEntityType(TemplateEntityType.MONITORED_SERVICE_TEMPLATE)
                   .templateScope(Scope.PROJECT)
                   .build();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledAndIsRemoteEntity() {
    GitEntityInfo branchInfo = GitEntityInfo.builder().storeType(StoreType.REMOTE).commitMsg("test").build();
    boolean isNewGitXEnabled = noOpTemplateGitXService.isNewGitXEnabledAndIsRemoteEntity(template, branchInfo);
    assertThat(isNewGitXEnabled).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabled() {
    boolean isNewGitXEnabled = noOpTemplateGitXService.isNewGitXEnabled(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
    assertThat(isNewGitXEnabled).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCheckForFileUniquenessAndGetRepoURL() {
    noOpTemplateGitXService.checkForFileUniquenessAndGetRepoURL(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testImportTemplateFromRemote() {
    noOpTemplateGitXService.importTemplateFromRemote(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testPerformImportFlowYamlValidations() {
    noOpTemplateGitXService.performImportFlowYamlValidations(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TemplateImportRequestDTO.builder().build(), "");
  }
}