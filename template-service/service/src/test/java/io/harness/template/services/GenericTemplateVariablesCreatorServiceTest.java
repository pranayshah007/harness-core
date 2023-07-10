/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.rule.Owner;
import io.harness.template.helpers.YamlVariablesUtils;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GenericTemplateVariablesCreatorServiceTest extends TemplateServiceTestBase {
  @InjectMocks GenericTemplateVariablesCreatorService genericTemplateVariablesCreatorService;

  @Mock YamlVariablesUtils yamlVariablesUtils;

  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  String yaml = "secretManager:\n"
      + "  shell: Bash\n"
      + "  executionTarget: {}\n"
      + "  onDelegate: false\n"
      + "  source:\n"
      + "    spec:\n"
      + "      script: ech\n"
      + "      type: Inline\n";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testSupportVariables() {
    boolean supportsVariables = genericTemplateVariablesCreatorService.supportsVariables();
    assertThat(supportsVariables).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetVariables() {
    VariableMergeServiceResponse variableMergeServiceResponse = VariableMergeServiceResponse.builder().build();
    mockStatic(YamlVariablesUtils.class);
    when(yamlVariablesUtils.getVariablesFromYaml(yaml, TemplateEntityType.STEP_TEMPLATE))
        .thenReturn(variableMergeServiceResponse);
    genericTemplateVariablesCreatorService.getVariables(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml, TemplateEntityType.STEP_TEMPLATE);
  }
}