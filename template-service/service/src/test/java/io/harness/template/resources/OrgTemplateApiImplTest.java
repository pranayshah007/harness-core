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
public class OrgTemplateApiImplTest extends CategoryTest {
  @InjectMocks OrgTemplateApiImpl orgTemplateApi;
  @Mock TemplateResourceApiUtils templateResourceApiUtils;
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_ID = "org_id";
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
  public void testCreateTemplatesOrg() {
    TemplateCreateRequestBody templateCreateRequestBody = new TemplateCreateRequestBody();
    templateCreateRequestBody.setTemplateYaml("yaml");
    templateCreateRequestBody.setIsStable(true);
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    templateCreateRequestBody.setGitDetails(gitCreateDetails);
    orgTemplateApi.createTemplatesOrg(ORG_ID, templateCreateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteTemplateOrg() {
    orgTemplateApi.deleteTemplateOrg(TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, ACCOUNT_ID, "", true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateOrg() {
    orgTemplateApi.getTemplateOrg(
        TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, ACCOUNT_ID, false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateStableOrg() {
    orgTemplateApi.getTemplateStableOrg(
        ORG_ID, TEMPLATE_IDENTIFIER, ACCOUNT_ID, false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplatesListOrg() {
    orgTemplateApi.getTemplatesListOrg(ORG_ID, ACCOUNT_ID, 100, 25, "", "", "", "", true, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testImportTemplateOrg() {
    TemplateImportRequestBody templateImportRequestBody = new TemplateImportRequestBody();
    orgTemplateApi.importTemplateOrg(ORG_ID, TEMPLATE_IDENTIFIER, templateImportRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateOrg() {
    TemplateUpdateRequestBody templateUpdateRequestBody = new TemplateUpdateRequestBody();
    orgTemplateApi.updateTemplateOrg(
        TEMPLATE_IDENTIFIER, ORG_ID, TEMPLATE_VERSION_LABEL, templateUpdateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateStableOrg() {
    GitFindDetails gitFindDetails = new GitFindDetails();
    orgTemplateApi.updateTemplateStableOrg(
        ORG_ID, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, gitFindDetails, ACCOUNT_ID);
    // TODO :- Check why we are asking for Template Version Label here and Why are we using GitFindDetails Here instead
    // of TemplateUpdateRequestBody
  }
}