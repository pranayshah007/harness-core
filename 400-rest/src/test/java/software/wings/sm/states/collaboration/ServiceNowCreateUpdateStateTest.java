/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.collaboration;

import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.DESCRIPTION_VALUE;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.servicenow.ServiceNowTicketTypeNG.INCIDENT;

import static software.wings.beans.TaskType.SERVICENOW_ASYNC;
import static software.wings.delegatetasks.servicenow.ServiceNowAction.CREATE;
import static software.wings.delegatetasks.servicenow.ServiceNowAction.IMPORT_SET;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.VARIABLE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.api.ServiceNowExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.servicenow.ServiceNowCreateUpdateParams;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.delegatetasks.servicenow.ServiceNowAction;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ServiceNowCreateUpdateStateTest extends CategoryTest {
  private static final String WORKFLOW_VARIABLE_NAME = "testVariable";
  private static final String WORKFLOW_VARIABLE_VALUE = "testValue";
  @Mock ActivityHelperService activityHelperService;
  @Mock ExecutionContextImpl context;
  @Mock SettingsService settingsService;
  @Mock SecretManager secretManager;
  @Mock DelegateService delegateService;
  @Mock SweepingOutputService sweepingOutputService;
  @Mock StateExecutionService stateExecutionService;
  @Mock FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks ServiceNowCreateUpdateState serviceNowCreateUpdateState = new ServiceNowCreateUpdateState(STATE_NAME);

  @Before
  public void setUp() {
    when(sweepingOutputService.save(any())).thenReturn(null);
    when(
        activityHelperService.createAndSaveActivity(context, Activity.Type.Command,
            serviceNowCreateUpdateState.getName(), serviceNowCreateUpdateState.getStateType(), Collections.emptyList()))
        .thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    when(context.fetchRequiredApp())
        .thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).appId(APP_ID).build());
    when(settingsService.getByAccountAndId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(ServiceNowConfig.builder().password(PASSWORD).build())
                        .build());
    when(context.renderExpression(anyString()))
        .thenAnswer(invocation
            -> ((String) invocation.getArguments()[0])
                   .replace(format("${%s}", WORKFLOW_VARIABLE_NAME), WORKFLOW_VARIABLE_VALUE));
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(secretManager.getEncryptionDetails(
             ServiceNowConfig.builder().password(PASSWORD).build(), APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn(Collections.emptyList());
    when(delegateService.queueTaskV2(any(DelegateTask.class))).thenReturn(UUID);
    when(featureFlagService.isEnabled(eq(FeatureName.HONOR_DELEGATE_SCOPING), anyString())).thenReturn(true);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldQueueDelegateTaskForCreateAndReturnExecutionResponse() {
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(getParamsForAction(CREATE));
    ExecutionResponse executionResponse = serviceNowCreateUpdateState.execute(context);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", SERVICENOW_ASYNC.name());
    assertThat(delegateTaskArgumentCaptor.getValue().isSelectionLogsTrackingEnabled()).isTrue();
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
    assertExecutionResponseOnExecute(executionResponse);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldQueueDelegateTaskForImportSetAndReturnExecutionResponse() {
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(getParamsForAction(IMPORT_SET));
    ExecutionResponse executionResponse = serviceNowCreateUpdateState.execute(context);

    assertExecutionResponseOnExecute(executionResponse);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenInvalidJsonBodyIsProvided() {
    ServiceNowCreateUpdateParams params = getParamsForAction(IMPORT_SET);
    params.setJsonBody("invalidJson");
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(params);
    assertThatThrownBy(() -> serviceNowCreateUpdateState.execute(context))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Json Body is not a valid Json: invalidJson");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldRenderExpressionForImportSetParams() {
    ServiceNowCreateUpdateParams params = getParamsForAction(IMPORT_SET);
    params.setImportSetTableName(format("${%s}", WORKFLOW_VARIABLE_NAME));
    params.setJsonBody(format("{\"${%s}\":\"${%s}\"}", WORKFLOW_VARIABLE_NAME, WORKFLOW_VARIABLE_NAME));
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(params);
    serviceNowCreateUpdateState.renderImportSetExpressions(context, params);
    assertThat(serviceNowCreateUpdateState.getServiceNowCreateUpdateParams())
        .hasFieldOrPropertyWithValue("importSetTableName", WORKFLOW_VARIABLE_VALUE);
    assertThat(serviceNowCreateUpdateState.getServiceNowCreateUpdateParams())
        .hasFieldOrPropertyWithValue(
            "jsonBody", format("{\"%s\":\"%s\"}", WORKFLOW_VARIABLE_VALUE, WORKFLOW_VARIABLE_VALUE));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenSnowConfigIsInvalid() {
    when(settingsService.getByAccountAndId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(JenkinsConfig.builder().build()).build());
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(getParamsForAction(CREATE));
    assertThatThrownBy(() -> serviceNowCreateUpdateState.execute(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Type of Setting Attribute Value is not SnowConfig");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponse() {
    ServiceNowExecutionData responseData = ServiceNowExecutionData.builder()
                                               .executionStatus(ExecutionStatus.SUCCESS)
                                               .issueId(UUID)
                                               .issueNumber(UUID)
                                               .transformationValues(Collections.singletonList(VARIABLE_NAME))
                                               .build();
    ExecutionResponse expectedResponse =
        ExecutionResponse.builder().stateExecutionData(responseData).executionStatus(ExecutionStatus.SUCCESS).build();
    ExecutionResponse response =
        serviceNowCreateUpdateState.handleAsyncResponse(context, Collections.singletonMap(ACTIVITY_ID, responseData));
    assertThat(response).isEqualTo(expectedResponse);
  }

  private void assertExecutionResponseOnExecute(ExecutionResponse executionResponse) {
    ExecutionResponse expectedResponse =
        ExecutionResponse.builder()
            .async(true)
            .correlationIds(Collections.singletonList(ACTIVITY_ID))
            .delegateTaskId(UUID)
            .stateExecutionData(ServiceNowExecutionData.builder().activityId(ACTIVITY_ID).build())
            .build();
    assertThat(executionResponse).isEqualTo(expectedResponse);
  }

  private ServiceNowCreateUpdateParams getParamsForAction(ServiceNowAction serviceNowAction) {
    ServiceNowCreateUpdateParams params = new ServiceNowCreateUpdateParams();
    params.setSnowConnectorId(SETTING_ID);
    params.setAction(serviceNowAction);
    params.setIssueNumber(UUID);
    params.setTicketId(UUID);
    params.setTicketType(INCIDENT.name());
    params.setUpdateMultiple(true);
    params.setFields(Collections.singletonMap(ServiceNowFields.DESCRIPTION, DESCRIPTION_VALUE));
    params.setAdditionalFields(new HashMap<>(Map.of("key", "value")));
    params.setImportSetTableName(VARIABLE_NAME);
    params.setJsonBody("{\"key\": \"value\"}");
    return params;
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldQueueDelegateTaskWithCorrectSetupAbstractionsForEnvType() {
    when(context.getEnvType()).thenReturn("PROD");
    serviceNowCreateUpdateState.setServiceNowCreateUpdateParams(getParamsForAction(CREATE));
    ExecutionResponse executionResponse = serviceNowCreateUpdateState.execute(context);

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("setupAbstractions.envType", "PROD");
    verify(stateExecutionService).appendDelegateTaskDetails(eq(null), any(DelegateTaskDetails.class));
  }
}
