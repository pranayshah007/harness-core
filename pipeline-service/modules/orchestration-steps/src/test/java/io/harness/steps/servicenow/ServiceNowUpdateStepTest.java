/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.steps.servicenow.beans.ChangeTaskUpdateMultipleSpec;
import io.harness.steps.servicenow.beans.UpdateMultipleSpec;
import io.harness.steps.servicenow.beans.UpdateMultipleSpecType;
import io.harness.steps.servicenow.beans.UpdateMultipleTaskNode;
import io.harness.steps.servicenow.update.ServiceNowUpdateSpecParameters;
import io.harness.steps.servicenow.update.ServiceNowUpdateStep;

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

public class ServiceNowUpdateStepTest extends CategoryTest {
  public static final String TICKET_NUMBER = "TICKET_NUMBER";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String PROBLEM = "PROBLEM";
  public static final String INSTANCE_ID = "INSTANCE_ID";
  public static final String TEMPLATE_NAME = "TEMPLATE_NAME";
  public static final String ticketNumber = "ticketNumber";
  public static final Boolean useServiceNowTemplate = true;
  private static final String DELEGATE_SELECTOR = "delegateSelector";
  private static final String DELEGATE_SELECTOR_2 = "delegateSelector2";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  ApprovalInstanceService approvalInstanceService;
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock ILogStreamingStepClient logStreamingStepClient;
  @Mock private ServiceNowStepHelperService serviceNowStepHelperService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private ServiceNowUpdateStep serviceNowUpdateStep;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";

  private static final ParameterField DELEGATE_SELECTORS_PARAMETER = ParameterField.createValueField(
      Arrays.asList(new TaskSelectorYaml(DELEGATE_SELECTOR), new TaskSelectorYaml(DELEGATE_SELECTOR_2)));
  private static final UpdateMultipleSpec changeTask =
      ChangeTaskUpdateMultipleSpec.builder()
          .changeTaskType(ParameterField.createValueField("type"))
          .changeRequestNumber(ParameterField.createValueField(TICKET_NUMBER))
          .build();

  private static final List<TaskSelector> TASK_SELECTORS =
      TaskSelectorYaml.toTaskSelector(DELEGATE_SELECTORS_PARAMETER);

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacSingleTask() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(any());
    StepElementParameters parameters = getStepSingleElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(serviceNowStepHelperService.prepareTaskRequest(any(ServiceNowTaskNGParametersBuilder.class),
             any(Ambiance.class), anyString(), anyString(), anyString(), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);

    assertThat(serviceNowUpdateStep.obtainTaskAfterRbac(ambiance, parameters, null)).isSameAs(taskRequest);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacMultipleTask() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    doReturn(logStreamingStepClient).when(logStreamingStepClientFactory).getLogStreamingStepClient(any());
    StepElementParameters parameters = getStepMultipleTaskElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(serviceNowStepHelperService.prepareTaskRequest(any(ServiceNowTaskNGParametersBuilder.class),
             any(Ambiance.class), anyString(), anyString(), anyString(), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);

    assertThat(serviceNowUpdateStep.obtainTaskAfterRbac(ambiance, parameters, null)).isSameAs(taskRequest);
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

    StepElementParameters parameters = getStepSingleElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));

    serviceNowUpdateStep.validateResources(ambiance, parameters);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testValidateResourcesMultipleTask() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();

    StepElementParameters parameters = getStepMultipleTaskElementParameters();
    parameters.setTimeout(ParameterField.createValueField(CONNECTOR));

    serviceNowUpdateStep.validateResources(ambiance, parameters);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));
  }

  private StepElementParameters getStepSingleElementParameters() {
    return StepElementParameters.builder()
        .type("SERVICENOW_UPDATE")
        .spec(ServiceNowUpdateSpecParameters.builder()
                  .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                  .ticketType(ParameterField.<String>builder().value(PROBLEM).build())
                  .ticketNumber(ParameterField.<String>builder().value(ticketNumber).build())
                  .templateName(ParameterField.<String>builder().value(TEMPLATE_NAME).build())
                  .useServiceNowTemplate(ParameterField.createValueField(true))
                  .delegateSelectors(DELEGATE_SELECTORS_PARAMETER)
                  .build())
        .build();
  }

  private StepElementParameters getStepMultipleTaskElementParameters() {
    return StepElementParameters.builder()
        .type("SERVICENOW_UPDATE")
        .spec(
            ServiceNowUpdateSpecParameters.builder()
                .connectorRef(ParameterField.<String>builder().value(CONNECTOR).build())
                .templateName(ParameterField.<String>builder().value(TEMPLATE_NAME).build())
                .useServiceNowTemplate(ParameterField.createValueField(true))
                .updateMultiple(
                    UpdateMultipleTaskNode.builder().type(UpdateMultipleSpecType.CHANGE_TASK).spec(changeTask).build())
                .delegateSelectors(DELEGATE_SELECTORS_PARAMETER)
                .build())
        .build();
  }
}
