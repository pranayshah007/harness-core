/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.rule.Owner;
import io.harness.template.resources.beans.PermissionTypes;
import io.harness.template.services.TemplateRefreshService;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class NGTemplateRefreshResourceImplTest extends CategoryTest {
  @InjectMocks NGTemplateRefreshResourceImpl ngTemplateRefreshResource;
  @Mock AccessControlClient accessControlClient;
  @Mock TemplateRefreshService templateRefreshService;
  private AutoCloseable mocks;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";

  @Before
  public void setUp() throws IOException {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRefreshAndUpdateTemplate() {
    ngTemplateRefreshResource.refreshAndUpdateTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        TEMPLATE_VERSION_LABEL, TEMPLATE_VERSION_LABEL, "false", null);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetRefreshedYaml() {
    ngTemplateRefreshResource.getRefreshedYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", RefreshRequestDTO.builder().build());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForTemplate() {
    ngTemplateRefreshResource.validateTemplateInputsForTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "false", null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForYaml() {
    ngTemplateRefreshResource.validateTemplateInputsForYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", RefreshRequestDTO.builder().build());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetYamlDiff() {
    ngTemplateRefreshResource.getYamlDiff(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "false", null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRefreshAllTemplates() {
    ngTemplateRefreshResource.refreshAllTemplates(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "false", null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRefreshAllTemplatesForYaml() {
    ngTemplateRefreshResource.refreshAllTemplatesForYaml(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", RefreshRequestDTO.builder().build());
  }
}