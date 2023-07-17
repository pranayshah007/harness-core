/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitFindDetails;
import io.harness.spec.server.template.v1.model.TemplateCreateRequestBody;
import io.harness.spec.server.template.v1.model.TemplateImportRequestBody;
import io.harness.spec.server.template.v1.model.TemplateUpdateRequestBody;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class ProjectTemplateApiImplTest extends CategoryTest {
  @InjectMocks ProjectTemplateApiImpl projectTemplateApi;
  @Mock TemplateResourceApiUtils templateResourceApiUtils;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_ID = "org_id";
  private final String PROJECT_ID = "project_id";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private AutoCloseable mocks;

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
  public void testCreateTemplatesProject() {
    TemplateCreateRequestBody templateCreateRequestBody = new TemplateCreateRequestBody();
    templateCreateRequestBody.setTemplateYaml("yaml");
    templateCreateRequestBody.setIsStable(true);
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    templateCreateRequestBody.setGitDetails(gitCreateDetails);
    projectTemplateApi.createTemplatesProject(ORG_ID, PROJECT_ID, templateCreateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteTemplateProject() {
    projectTemplateApi.deleteTemplateProject(
        PROJECT_ID, TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, ACCOUNT_ID, "", true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateProject() {
    projectTemplateApi.getTemplateProject(PROJECT_ID, TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, ACCOUNT_ID,
        false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateStableProject() {
    projectTemplateApi.getTemplateStableProject(
        ORG_ID, PROJECT_ID, TEMPLATE_IDENTIFIER, ACCOUNT_ID, false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplatesListProject() {
    projectTemplateApi.getTemplatesListProject(
        ORG_ID, PROJECT_ID, ACCOUNT_ID, 100, 25, "", "", "", "", true, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testImportTemplateProject() {
    TemplateImportRequestBody templateImportRequestBody = new TemplateImportRequestBody();
    projectTemplateApi.importTemplateProject(
        ORG_ID, PROJECT_ID, TEMPLATE_IDENTIFIER, templateImportRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateProject() {
    TemplateUpdateRequestBody templateUpdateRequestBody = new TemplateUpdateRequestBody();
    projectTemplateApi.updateTemplateProject(
        PROJECT_ID, TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, templateUpdateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateStableProject() {
    GitFindDetails gitFindDetails = new GitFindDetails();
    projectTemplateApi.updateTemplateStableProject(
        ORG_ID, PROJECT_ID, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, gitFindDetails, ACCOUNT_ID);
  }
}