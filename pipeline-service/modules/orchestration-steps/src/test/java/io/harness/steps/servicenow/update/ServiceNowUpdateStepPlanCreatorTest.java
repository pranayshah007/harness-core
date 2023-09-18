/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.update;

import static io.harness.rule.OwnerRule.RAFAEL;

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
import io.harness.steps.servicenow.beans.ChangeTaskUpdateMultipleSpec;
import io.harness.steps.servicenow.beans.UpdateMultipleSpecType;
import io.harness.steps.servicenow.beans.UpdateMultipleTaskNode;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class ServiceNowUpdateStepPlanCreatorTest extends OrchestrationStepsTestBase {
  @Spy @InjectMocks private ServiceNowUpdateStepPlanCreator serviceNowUpdateStepPlanCreator;

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> set = serviceNowUpdateStepPlanCreator.getSupportedStepTypes();
    assertEquals(set.size(), 1);
    assertTrue(set.contains(StepSpecTypeConstants.SERVICENOW_UPDATE));
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertEquals(serviceNowUpdateStepPlanCreator.getFieldClass(), ServiceNowUpdateStepNode.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testValidateServiceNowSingleTask() {
    ServiceNowUpdateStepInfo serviceNowUpdateStepInfoNormal =
        getServiceNowUpdateStepInfoWithSingleTask("number", "type", false, null);

    ServiceNowUpdateStepInfo malformed1 = getServiceNowUpdateStepInfoWithSingleTask("number", "", false, null);
    ServiceNowUpdateStepInfo malformed2 = getServiceNowUpdateStepInfoWithSingleTask("", "type", false, null);

    List<ServiceNowUpdateStepInfo> serviceNowUpdateStepInfoList = List.of(malformed1, malformed2);

    serviceNowUpdateStepPlanCreator.validateServiceNowSingleTask(serviceNowUpdateStepInfoNormal);

    verify(serviceNowUpdateStepPlanCreator, times(1)).validateServiceNowSingleTask(any());
    for (ServiceNowUpdateStepInfo s : serviceNowUpdateStepInfoList) {
      assertThatThrownBy(() -> serviceNowUpdateStepPlanCreator.validateServiceNowSingleTask(s))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testValidateServiceNowMultipleTask() {
    ServiceNowUpdateStepInfo serviceNowUpdateStepInfoNormal =
        getChangeTaskUpdateMultiple("type", "number", false, null, "type");
    ServiceNowUpdateStepInfo serviceNowUpdateStepInfoNormal2 =
        getChangeTaskUpdateMultiple("", "number", false, null, "type");

    ServiceNowUpdateStepInfo malformed1 = getChangeTaskUpdateMultiple("type", "", false, null, "type");
    ServiceNowUpdateStepInfo malformed2 = getChangeTaskUpdateMultiple("", "", false, null, "type");
    ServiceNowUpdateStepInfo malformed3 = getChangeTaskUpdateMultiple("type", "number", false, null, "");
    ServiceNowUpdateStepInfo malformed4 = getChangeTaskUpdateMultiple("", "", false, null, "");

    List<ServiceNowUpdateStepInfo> serviceNowUpdateStepInfoList =
        List.of(malformed1, malformed2, malformed3, malformed4);

    serviceNowUpdateStepPlanCreator.validateServiceNowMultipleTask(serviceNowUpdateStepInfoNormal);
    serviceNowUpdateStepPlanCreator.validateServiceNowMultipleTask(serviceNowUpdateStepInfoNormal2);

    verify(serviceNowUpdateStepPlanCreator, times(2)).validateServiceNowMultipleTask(any());
    for (ServiceNowUpdateStepInfo s : serviceNowUpdateStepInfoList) {
      assertThatThrownBy(() -> serviceNowUpdateStepPlanCreator.validateServiceNowMultipleTask(s))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testValidateServiceNowUsingTemplate() {
    ServiceNowUpdateStepInfo normalMultiple1 =
        getChangeTaskUpdateMultiple("type", "number", true, "templateName", "type");
    ServiceNowUpdateStepInfo normalMultiple2 =
        getChangeTaskUpdateMultiple("type", "number", false, "templateName", "type");

    ServiceNowUpdateStepInfo normalSingle1 = getServiceNowUpdateStepInfoWithSingleTask("number", "type", false, null);
    ServiceNowUpdateStepInfo normalSingle2 =
        getServiceNowUpdateStepInfoWithSingleTask("number", "type", true, "templateName");

    ServiceNowUpdateStepInfo normalSingleMalformed =
        getServiceNowUpdateStepInfoWithSingleTask("number", "type", true, "");

    ServiceNowUpdateStepInfo normalUpdateMalformed = getChangeTaskUpdateMultiple("type", "number", true, "", "type");

    List<ServiceNowUpdateStepInfo> serviceNowUpdateStepInfoList = List.of(normalUpdateMalformed, normalSingleMalformed);

    serviceNowUpdateStepPlanCreator.validateServiceNowTemplate(normalMultiple1);
    serviceNowUpdateStepPlanCreator.validateServiceNowTemplate(normalSingle1);
    serviceNowUpdateStepPlanCreator.validateServiceNowTemplate(normalMultiple2);
    serviceNowUpdateStepPlanCreator.validateServiceNowTemplate(normalSingle2);

    verify(serviceNowUpdateStepPlanCreator, times(4)).validateServiceNowTemplate(any());
    for (ServiceNowUpdateStepInfo s : serviceNowUpdateStepInfoList) {
      assertThatThrownBy(() -> serviceNowUpdateStepPlanCreator.validateServiceNowTemplate(s))
          .isInstanceOf(InvalidRequestException.class);
    }
  }

  private ServiceNowUpdateStepInfo getServiceNowUpdateMultipleStepInfo(ParameterField<Boolean> useServiceNowTemplate,
      ParameterField<String> templateName, UpdateMultipleTaskNode updateMultipleTasksNode, String ticketType) {
    return ServiceNowUpdateStepInfo.builder()
        .useServiceNowTemplate(useServiceNowTemplate)
        .ticketType(ParameterField.createValueField(ticketType))
        .updateMultiple(updateMultipleTasksNode)
        .templateName(templateName)
        .connectorRef(ParameterField.createValueField("ConnectorRef"))
        .build();
  }

  public ServiceNowUpdateStepInfo getChangeTaskUpdateMultiple(
      String changeTaskType, String changeRequestNumber, boolean isTemplate, String templateName, String ticketType) {
    ChangeTaskUpdateMultipleSpec changeSpec =
        ChangeTaskUpdateMultipleSpec.builder()
            .changeTaskType(ParameterField.createValueField(changeTaskType))
            .changeRequestNumber(ParameterField.createValueField(changeRequestNumber))
            .build();

    UpdateMultipleTaskNode updateMultipleTasksNode =
        UpdateMultipleTaskNode.builder().spec(changeSpec).type(UpdateMultipleSpecType.CHANGE_TASK).build();

    return getServiceNowUpdateMultipleStepInfo(ParameterField.createValueField(isTemplate),
        ParameterField.createValueField(templateName), updateMultipleTasksNode, ticketType);
  }

  private ServiceNowUpdateStepInfo getServiceNowUpdateStepInfoWithSingleTask(
      String ticketNumber, String ticketType, boolean useServiceNowTemplate, String templateName) {
    return ServiceNowUpdateStepInfo.builder()
        .useServiceNowTemplate(ParameterField.createValueField(useServiceNowTemplate))
        .templateName(ParameterField.createValueField(templateName))
        .connectorRef(ParameterField.createValueField("ConnectorRef"))
        .ticketNumber(ParameterField.createValueField(ticketNumber))
        .ticketType(ParameterField.createValueField(ticketType))
        .build();
  }
}