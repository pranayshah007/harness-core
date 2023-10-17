/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.rule.OwnerRule.vivekveman;
import static io.harness.servicenow.ServiceNowActionNG.CREATE_TICKET;
import static io.harness.servicenow.ServiceNowActionNG.CREATE_TICKET_USING_STANDARD_TEMPLATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.servicenow.beans.ServiceNowCreateType;
import io.harness.steps.servicenow.create.ServiceNowCreateSpecParameters;
import io.harness.steps.servicenow.create.ServiceNowCreateStep;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServiceNowCreateStepTest extends CategoryTest {
  public static final String TICKET_NUMBER = "TICKET_NUMBER";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String PROBLEM = "PROBLEM";
  public static final String INSTANCE_ID = "INSTANCE_ID";
  public static final String TEMPLATE_NAME = "TEMPLATE_NAME";
  public static final Boolean useServiceNowTemplate = true;
  private static final String DELEGATE_SELECTOR = "delegateSelector";
  private static final String DELEGATE_SELECTOR_2 = "delegateSelector2";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  ApprovalInstanceService approvalInstanceService;

  @Mock private ServiceNowStepHelperService serviceNowStepHelperService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private ServiceNowCreateStep serviceNowCreateStep;
  @InjectMocks private ServiceNowCreateSpecParameters serviceNowCreateSpecParameters;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock ILogStreamingStepClient logStreamingStepClient;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  private static final ParameterField DELEGATE_SELECTORS_PARAMETER = ParameterField.createValueField(
      Arrays.asList(new TaskSelectorYaml(DELEGATE_SELECTOR), new TaskSelectorYaml(DELEGATE_SELECTOR_2)));

  private static final List<TaskSelector> TASK_SELECTORS =
      TaskSelectorYaml.toTaskSelector(DELEGATE_SELECTORS_PARAMETER);

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    doNothing().when(mockNgLogCallback).saveExecutionLog(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(any());
    StepElementParameters parameters = getStepElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(serviceNowStepHelperService.prepareTaskRequest(any(ServiceNowTaskNGParametersBuilder.class),
             any(Ambiance.class), anyString(), anyString(), anyString(), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);
    assertThat(serviceNowCreateStep.obtainTaskAfterRbac(ambiance, parameters, null)).isSameAs(taskRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateResources() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();

    StepElementParameters parameters = getStepElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));

    serviceNowCreateStep.validateResources(ambiance, parameters);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetUseServiceNowTemplate() {
    assertThat(serviceNowCreateSpecParameters.getUseServiceNowTemplate(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.STANDARD).build()))
        .isTrue();

    assertThat(serviceNowCreateSpecParameters.getUseServiceNowTemplate(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.FORM).build()))
        .isTrue();

    assertThat(serviceNowCreateSpecParameters.getUseServiceNowTemplate(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.NORMAL).build()))
        .isFalse();

    assertThat(serviceNowCreateSpecParameters.getUseServiceNowTemplate(
                   ServiceNowCreateSpecParameters.builder()
                       .useServiceNowTemplate(ParameterField.createValueField(true))
                       .build()))
        .isTrue();

    assertThat(serviceNowCreateSpecParameters.getUseServiceNowTemplate(
                   ServiceNowCreateSpecParameters.builder()
                       .useServiceNowTemplate(ParameterField.createValueField(false))
                       .build()))
        .isFalse();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetAction() {
    assertThat(serviceNowCreateSpecParameters.getAction(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.STANDARD).build()))
        .isEqualTo(CREATE_TICKET_USING_STANDARD_TEMPLATE);

    assertThat(serviceNowCreateSpecParameters.getAction(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.FORM).build()))
        .isEqualTo(CREATE_TICKET);

    assertThat(serviceNowCreateSpecParameters.getAction(
                   ServiceNowCreateSpecParameters.builder().createType(ServiceNowCreateType.NORMAL).build()))
        .isEqualTo(CREATE_TICKET);

    assertThat(
        serviceNowCreateSpecParameters.getAction(ServiceNowCreateSpecParameters.builder()
                                                     .useServiceNowTemplate(ParameterField.createValueField(true))
                                                     .build()))
        .isEqualTo(CREATE_TICKET);

    assertThat(
        serviceNowCreateSpecParameters.getAction(ServiceNowCreateSpecParameters.builder()
                                                     .useServiceNowTemplate(ParameterField.createValueField(false))
                                                     .build()))
        .isEqualTo(CREATE_TICKET);
  }
  private StepElementParameters getStepElementParameters() {
    return StepElementParameters.builder()
        .type("SERVICENOW_CREATE")
        .spec(ServiceNowCreateSpecParameters.builder()
                  .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                  .ticketType(ParameterField.<String>builder().value(PROBLEM).build())
                  .templateName(ParameterField.<String>builder().value(TEMPLATE_NAME).build())
                  .useServiceNowTemplate(ParameterField.createValueField(true))
                  .delegateSelectors(DELEGATE_SELECTORS_PARAMETER)
                  .build())
        .build();
  }
}