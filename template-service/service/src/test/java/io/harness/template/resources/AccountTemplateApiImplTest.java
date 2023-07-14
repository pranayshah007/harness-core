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
public class AccountTemplateApiImplTest extends CategoryTest {
  @InjectMocks AccountTemplateApiImpl accountTemplateApi;
  @Mock TemplateResourceApiUtils templateResourceApiUtils;
  private final String ACCOUNT_ID = "account_id";
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
  public void testCreateTemplatesAcc() {
    TemplateCreateRequestBody templateCreateRequestBody = new TemplateCreateRequestBody();
    templateCreateRequestBody.setTemplateYaml("yaml");
    templateCreateRequestBody.setIsStable(true);
    GitCreateDetails gitCreateDetails = new GitCreateDetails();
    gitCreateDetails.setStoreType(GitCreateDetails.StoreTypeEnum.INLINE);
    templateCreateRequestBody.setGitDetails(gitCreateDetails);
    accountTemplateApi.createTemplatesAcc(templateCreateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteTemplateAcc() {
    accountTemplateApi.deleteTemplateAcc(TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, ACCOUNT_ID, "", true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateAcc() {
    accountTemplateApi.getTemplateAcc(
        TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, ACCOUNT_ID, false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplateStableAcc() {
    accountTemplateApi.getTemplateStableAcc(TEMPLATE_IDENTIFIER, ACCOUNT_ID, false, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetTemplatesListAcc() {
    accountTemplateApi.getTemplatesListAcc(ACCOUNT_ID, 100, 25, "", "", "", "", true, null, null, null, null, null);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testImportTemplateAcc() {
    TemplateImportRequestBody templateImportRequestBody = new TemplateImportRequestBody();
    accountTemplateApi.importTemplateAcc(TEMPLATE_IDENTIFIER, templateImportRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateAcc() {
    TemplateUpdateRequestBody templateUpdateRequestBody = new TemplateUpdateRequestBody();
    accountTemplateApi.updateTemplateAcc(
        TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, templateUpdateRequestBody, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateTemplateStableAcc() {
    GitFindDetails gitFindDetails = new GitFindDetails();
    accountTemplateApi.updateTemplateStableAcc(TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, gitFindDetails, ACCOUNT_ID);
    // TODO :- Check why we are asking for Template Version Label here and Why are we using GitFindDetails Here instead
    // of TemplateUpdateRequestBody
  }
}