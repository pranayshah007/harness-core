/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.create;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class ServiceNowCreateStepPlanCreatorTest extends OrchestrationStepsTestBase {
  @Spy @InjectMocks private ServiceNowCreateStepPlanCreator serviceNowCreateStepPlanCreator;

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> set = serviceNowCreateStepPlanCreator.getSupportedStepTypes();
    assertEquals(set.size(), 1);
    assertTrue(set.contains(StepSpecTypeConstants.SERVICENOW_CREATE));
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertEquals(serviceNowCreateStepPlanCreator.getFieldClass(), ServiceNowCreateStepNode.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testValidateServiceNowTemplate() {
    ServiceNowCreateStepInfo serviceNowCreateStepInfoNormal = getServiceNowCreateStepInfo(
        ParameterField.createValueField(true), ParameterField.createValueField("templateName"));
    ServiceNowCreateStepInfo serviceNowCreateStepInfoNormal1 =
        getServiceNowCreateStepInfo(ParameterField.createValueField(false), null);
    ServiceNowCreateStepInfo serviceNowCreateStepInfoMalformed =
        getServiceNowCreateStepInfo(ParameterField.createValueField(true), null);
    ServiceNowCreateStepInfo serviceNowCreateStepInfoMalformed1 =
        getServiceNowCreateStepInfo(ParameterField.createValueField(true), ParameterField.createValueField("   "));
    serviceNowCreateStepPlanCreator.validateServiceNowTemplate(serviceNowCreateStepInfoNormal);
    serviceNowCreateStepPlanCreator.validateServiceNowTemplate(serviceNowCreateStepInfoNormal1);
    verify(serviceNowCreateStepPlanCreator, times(2)).validateServiceNowTemplate(any());
    assertThatThrownBy(
        () -> serviceNowCreateStepPlanCreator.validateServiceNowTemplate(serviceNowCreateStepInfoMalformed))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> serviceNowCreateStepPlanCreator.validateServiceNowTemplate(serviceNowCreateStepInfoMalformed1))
        .isInstanceOf(InvalidRequestException.class);
  }

  private ServiceNowCreateStepInfo getServiceNowCreateStepInfo(
      ParameterField<Boolean> useServiceNowTemplate, ParameterField<String> templateName) {
    return ServiceNowCreateStepInfo.builder()
        .useServiceNowTemplate(useServiceNowTemplate)
        .templateName(templateName)
        .connectorRef(ParameterField.createValueField("ConnectorRef"))
        .ticketType(ParameterField.createValueField("TicketType"))
        .build();
  }
}
