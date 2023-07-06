/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TemplateVariableCreatorFactoryTest extends TemplateServiceTestBase {
  @InjectMocks private TemplateVariableCreatorFactory templateVariableCreatorFactory;

  @Mock PipelineTemplateVariablesCreatorService pipelineTemplateVariablesCreatorService;
  @Mock CustomDeploymentTemplateVariablesCreatorService customDeploymentTemplateVariablesCreatorService;
  @Mock GenericTemplateVariablesCreatorService genericTemplateVariablesCreatorService;
  @Mock NoOpVariablesCreatorService noOpVariablesCreatorService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getVariableCreatorFactory() {
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.STEP_TEMPLATE))
        .isExactlyInstanceOf(PipelineTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.STAGE_TEMPLATE))
        .isExactlyInstanceOf(PipelineTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.PIPELINE_TEMPLATE))
        .isExactlyInstanceOf(PipelineTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.STEPGROUP_TEMPLATE))
        .isExactlyInstanceOf(PipelineTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
        .isExactlyInstanceOf(CustomDeploymentTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE))
        .isExactlyInstanceOf(GenericTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.MONITORED_SERVICE_TEMPLATE))
        .isExactlyInstanceOf(GenericTemplateVariablesCreatorService.class);
    assertThat(templateVariableCreatorFactory.getVariablesService(TemplateEntityType.SECRET_MANAGER_TEMPLATE))
        .isExactlyInstanceOf(GenericTemplateVariablesCreatorService.class);
  }
}