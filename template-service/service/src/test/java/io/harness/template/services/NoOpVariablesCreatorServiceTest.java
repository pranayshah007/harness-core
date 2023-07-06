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
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class NoOpVariablesCreatorServiceTest extends TemplateServiceTestBase {
  @Inject NoOpVariablesCreatorService noOpVariablesCreatorService;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testIsNewGitXEnabledAndIsRemoteEntity() {
    boolean supportsVariables = noOpVariablesCreatorService.supportsVariables();
    assertThat(supportsVariables).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetVariables() {
    noOpVariablesCreatorService.getVariables(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "yaml", TemplateEntityType.STEP_TEMPLATE);
  }
}