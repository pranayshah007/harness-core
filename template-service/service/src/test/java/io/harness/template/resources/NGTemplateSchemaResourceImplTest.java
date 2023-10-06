/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.individualschema.TemplateSchemaParserFactory;
import io.harness.pms.yaml.individualschema.TemplateSchemaParserV0;
import io.harness.rule.Owner;
import io.harness.template.services.NGTemplateSchemaService;
import io.harness.template.utils.TemplateSchemaFetcher;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class NGTemplateSchemaResourceImplTest extends CategoryTest {
  @InjectMocks NGTemplateSchemaResourceImpl ngTemplateSchemaResource;
  @Mock NGTemplateSchemaService ngTemplateSchemaService;
  @Mock TemplateSchemaFetcher templateSchemaFetcher;
  @Mock TemplateSchemaParserV0 templateSchemaParserV0;
  @Mock TemplateSchemaParserFactory templateSchemaParserFactory;
  private AutoCloseable mocks;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() throws IOException {
    mocks = MockitoAnnotations.openMocks(this);
    when(templateSchemaParserFactory.getTemplateSchemaParser("v0")).thenReturn(templateSchemaParserV0);
    when(templateSchemaParserV0.getIndividualSchema(any())).thenReturn(null);
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
  public void testGetTemplateSchema() {
    ngTemplateSchemaResource.getTemplateSchema(
        TemplateEntityType.STEP_TEMPLATE, PROJ_IDENTIFIER, ORG_IDENTIFIER, Scope.PROJECT, ACCOUNT_ID, "ShellScript");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStaticYamlSchema() {
    ngTemplateSchemaResource.getStaticYamlSchema(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "ShellScript",
        TemplateEntityType.STEP_TEMPLATE, Scope.PROJECT, "ShellScript");
  }
}