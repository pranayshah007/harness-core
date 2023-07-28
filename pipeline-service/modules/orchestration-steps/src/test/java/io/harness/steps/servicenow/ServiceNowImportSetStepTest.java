/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.servicenow.beans.ImportDataSpecType;
import io.harness.steps.servicenow.beans.ImportDataSpecWrapper;
import io.harness.steps.servicenow.beans.JsonImportDataSpec;
import io.harness.steps.servicenow.beans.KeyValuesImportDataSpec;
import io.harness.steps.servicenow.beans.ServiceNowField;
import io.harness.steps.servicenow.importset.ServiceNowImportSetSpecParameters;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ServiceNowImportSetStepTest extends CategoryTest {
  public static final String CONNECTOR = "CONNECTOR";
  public static final String STAGING_TABLE_NAME = "STAGING_TABLE_NAME";
  public static final String TIMEOUT = "2m";
  public static final String UUID = "UUID";
  public static final String FIELD_NAME = "FIELD_NAME";
  public static final String FIELD_VAL = "FIELD_VAL";
  public static final String jsonString = String.format("{\"%s\":\"%s\"}", FIELD_NAME, FIELD_VAL);
  private static final String DELEGATE_SELECTOR = "delegateSelector";
  private static final String DELEGATE_SELECTOR_2 = "delegateSelector2";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServiceNowStepHelperService serviceNowStepHelperService;
  @Mock private PipelineRbacHelper pipelineRbacHelper;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @InjectMocks private ServiceNowImportSetStep serviceNowImportSetStep;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  @Captor ArgumentCaptor<ServiceNowTaskNGParametersBuilder> paramsCaptor;

  private static final String accountId = "accountId";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String pipelineIdentifier = "pipelineIdentifier";
  private ILogStreamingStepClient logStreamingStepClient;

  private static final ParameterField DELEGATE_SELECTORS_PARAMETER = ParameterField.createValueField(
      Arrays.asList(new TaskSelectorYaml(DELEGATE_SELECTOR), new TaskSelectorYaml(DELEGATE_SELECTOR_2)));

  private static final List<TaskSelector> TASK_SELECTORS =
      TaskSelectorYaml.toTaskSelector(DELEGATE_SELECTORS_PARAMETER);

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithJsonImportData() {
    Ambiance ambiance = getAmbiance();
    ImportDataSpecWrapper jsonImportDataSpecWrapper = new ImportDataSpecWrapper(UUID, ImportDataSpecType.JSON,
        JsonImportDataSpec.builder().jsonBody(ParameterField.<String>builder().value(jsonString).build()).build());
    StepElementParameters parameters =
        getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, jsonImportDataSpecWrapper);
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(serviceNowStepHelperService.prepareTaskRequest(any(ServiceNowTaskNGParametersBuilder.class),
             any(Ambiance.class), anyString(), anyString(), anyString(), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);
    assertThat(serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, parameters, null)).isInstanceOf(TaskRequest.class);
    verify(serviceNowStepHelperService, times(1))
        .prepareTaskRequest(paramsCaptor.capture(), eq(ambiance), eq(CONNECTOR), eq(TIMEOUT),
            eq(String.format("ServiceNow Task: %s", ServiceNowActionNG.IMPORT_SET)), eq(TASK_SELECTORS));
    assertThat(paramsCaptor.getValue().build().getAction()).isEqualTo(ServiceNowActionNG.IMPORT_SET);
    assertThat(paramsCaptor.getValue().build().getStagingTableName()).isEqualTo(STAGING_TABLE_NAME);
    assertThat(paramsCaptor.getValue().build().getImportData()).isEqualTo(jsonString);

    StepElementParameters invalidParameters = getStepElementParameters(CONNECTOR, "  ", jsonImportDataSpecWrapper);
    assertThatThrownBy(() -> serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, invalidParameters, null))
        .isInstanceOf(InvalidRequestException.class);

    StepElementParameters invalidParameters1 = getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, null);
    assertThatThrownBy(() -> serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, invalidParameters1, null))
        .isInstanceOf(InvalidRequestException.class);
    verify(logStreamingStepClient, times(3)).openStream(ShellScriptTaskNG.COMMAND_UNIT);
    verify(logStreamingStepClient, times(2)).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithKeyValueImportData() {
    Ambiance ambiance = getAmbiance();
    ImportDataSpecWrapper keyValueImportDataSpecWrapper = new ImportDataSpecWrapper(UUID, ImportDataSpecType.KEY_VALUES,
        KeyValuesImportDataSpec.builder()
            .uuid(UUID)
            .fields(Collections.singletonList(ServiceNowField.builder()
                                                  .name(FIELD_NAME)
                                                  .value(ParameterField.<String>builder().value(FIELD_VAL).build())
                                                  .build()))
            .build());
    StepElementParameters parameters =
        getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, keyValueImportDataSpecWrapper);
    TaskRequest taskRequest = TaskRequest.newBuilder().build();
    when(serviceNowStepHelperService.prepareTaskRequest(any(ServiceNowTaskNGParametersBuilder.class),
             any(Ambiance.class), anyString(), anyString(), anyString(), eq(TASK_SELECTORS)))
        .thenReturn(taskRequest);
    assertThat(serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, parameters, null)).isInstanceOf(TaskRequest.class);
    verify(serviceNowStepHelperService, times(1))
        .prepareTaskRequest(paramsCaptor.capture(), eq(ambiance), eq(CONNECTOR), eq(TIMEOUT),
            eq(String.format("ServiceNow Task: %s", ServiceNowActionNG.IMPORT_SET)), eq(TASK_SELECTORS));
    assertThat(paramsCaptor.getValue().build().getAction()).isEqualTo(ServiceNowActionNG.IMPORT_SET);
    assertThat(paramsCaptor.getValue().build().getStagingTableName()).isEqualTo(STAGING_TABLE_NAME);
    assertThat(paramsCaptor.getValue().build().getImportData()).isEqualTo(jsonString);

    StepElementParameters invalidParameters = getStepElementParameters(CONNECTOR, "  ", keyValueImportDataSpecWrapper);
    assertThatThrownBy(() -> serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, invalidParameters, null))
        .isInstanceOf(InvalidRequestException.class);

    StepElementParameters invalidParameters1 = getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, null);
    assertThatThrownBy(() -> serviceNowImportSetStep.obtainTaskAfterRbac(ambiance, invalidParameters1, null))
        .isInstanceOf(InvalidRequestException.class);
    verify(logStreamingStepClient, times(3)).openStream(ShellScriptTaskNG.COMMAND_UNIT);
    verify(logStreamingStepClient, times(2)).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateResources() {
    Ambiance ambiance = getAmbiance();

    StepElementParameters parameters = getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, null);

    serviceNowImportSetStep.validateResources(ambiance, parameters);

    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));
    assertThat(captor.getValue().size()).isEqualTo(1);
    assertThat(captor.getValue().get(0).getType()).isEqualTo(EntityType.CONNECTORS);

    StepElementParameters parametersWithInvalidConnectorRef = getStepElementParameters(" ", STAGING_TABLE_NAME, null);
    assertThatThrownBy(() -> serviceNowImportSetStep.validateResources(ambiance, parametersWithInvalidConnectorRef))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(serviceNowImportSetStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    StepResponse stepResponse = StepResponse.builder().status(Status.NO_OP).build();
    when(serviceNowStepHelperService.prepareImportSetStepResponse(any())).thenReturn(stepResponse);
    assertThat(serviceNowImportSetStep.handleTaskResultWithSecurityContext(
                   getAmbiance(), getStepElementParameters(CONNECTOR, STAGING_TABLE_NAME, null), null))
        .isEqualTo(stepResponse);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  private StepElementParameters getStepElementParameters(
      String connectorRef, String stagingTable, ImportDataSpecWrapper importDataSpecWrapper) {
    return StepElementParameters.builder()
        .type("SERVICENOW_IMPORT_SET")
        .spec(ServiceNowImportSetSpecParameters.builder()
                  .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
                  .stagingTableName(ParameterField.<String>builder().value(stagingTable).build())
                  .importData(importDataSpecWrapper)
                  .delegateSelectors(DELEGATE_SELECTORS_PARAMETER)
                  .build())
        .timeout(ParameterField.createValueField(TIMEOUT))
        .build();
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", accountId)
        .putSetupAbstractions("orgIdentifier", orgIdentifier)
        .putSetupAbstractions("projectIdentifier", projectIdentifier)
        .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
        .build();
  }
}