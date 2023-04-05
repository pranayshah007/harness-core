/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.servicenow;

import static io.harness.rule.OwnerRule.AGORODETKI;

import static software.wings.beans.servicenow.ServiceNowTicketType.CHANGE_TASK;
import static software.wings.beans.servicenow.ServiceNowTicketType.INCIDENT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;

import software.wings.api.ServiceNowExecutionData;
import software.wings.api.ServiceNowImportSetResponse;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.servicenow.ServiceNowFields;
import software.wings.beans.servicenow.ServiceNowTaskParameters;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.helpers.ext.servicenow.ServiceNowRestClient;
import software.wings.service.intfc.security.EncryptionService;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ServicenowTaskTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String TABLE_NAME = "TABLE_NAME";
  private static final String ISSUE_ID = "ISSUE_ID";
  private static final String ISSUE_NUMBER = "ISSUE_NUMBER";
  private static final String JSON_BODY = "{\"import_set\":\"testImportSetValue\", \"result\":[]}";
  private static final String VALUE = "VALUE";

  @Mock ServiceNowRestClient snowClient;
  @Mock Call<JsonNode> request;
  @Mock Response<JsonNode> response;
  @Mock EncryptionService encryptionService;
  @Mock JsonNode jsonNode;
  @InjectMocks
  ServicenowTask snowTask =
      new ServicenowTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);
  @InjectMocks ServicenowTask spySnowTask = spy(snowTask);

  @Before
  public void setUp() throws IOException {
    doReturn(snowClient).when(spySnowTask).getRestClient(any());
    when(snowClient.createTicket(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(request);
    when(snowClient.createImportSet(anyString(), anyString(), anyString(), any())).thenReturn(request);
    when(snowClient.updateTicket(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(request);
    when(snowClient.createImportSet(anyString(), anyString(), anyString(), any())).thenReturn(request);
    when(snowClient.fetchChangeTasksFromCR(anyString(), anyString(), anyString(), anyString())).thenReturn(request);
    when(snowClient.getIssue(anyString(), anyString(), anyString(), anyString())).thenReturn(request);
    when(request.execute()).thenReturn(response);
    when(response.body()).thenReturn(jsonNode);
    when(jsonNode.get(anyString())).thenReturn(jsonNode);
    when(jsonNode.asText()).thenReturn(VALUE);
    when(jsonNode.textValue()).thenReturn(VALUE);
    when(jsonNode.toString()).thenReturn(JSON_BODY);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowServiceNowExceptionWhenResponseStatus401() {
    when(response.code()).thenReturn(401);
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.CREATE, INCIDENT);
    assertThatThrownBy(() -> spySnowTask.run(taskParams))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Invalid ServiceNow credentials");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowServiceNowExceptionWhenResponseStatus404() {
    when(response.code()).thenReturn(404);
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.CREATE, INCIDENT);
    assertThatThrownBy(() -> spySnowTask.run(taskParams))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("404 Not found");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowServiceNowExceptionWhenResponseStatusHasNoErrorBody() {
    when(response.code()).thenReturn(405);
    when(response.message()).thenReturn("");
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.CREATE, INCIDENT);
    assertThatThrownBy(() -> spySnowTask.run(taskParams))
        .isInstanceOf(ServiceNowException.class)
        .hasMessage("Failed to create ServiceNow ticket : ");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldCreateTicketAndReturnSuccessfulResponse() {
    when(response.isSuccessful()).thenReturn(true);
    when(response.body()).thenReturn(jsonNode);
    when(jsonNode.get("result")).thenReturn(jsonNode);
    when(jsonNode.get("number")).thenReturn(jsonNode);
    when(jsonNode.get("sys_id")).thenReturn(jsonNode);
    when(jsonNode.get("display_value")).thenReturn(jsonNode);
    when(jsonNode.asText()).thenReturn(VALUE);
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.CREATE, INCIDENT);

    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .issueNumber(VALUE)
                                                .issueId(VALUE)
                                                .ticketType(INCIDENT)
                                                .issueUrl("base.url/nav_to.do?uri=/incident.do?sys_id=VALUE")
                                                .responseMsg("Created ServiceNow ticket: VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParams, executionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionOnAttemptToUpdateAllChangeTaskTickets() {
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.UPDATE, CHANGE_TASK);
    taskParams.setUpdateMultiple(true);

    assertThatThrownBy(() -> spySnowTask.run(taskParams))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Change Request Number is required to update change tasks");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldUpdateTicketAndReturnSuccessfulResponse() {
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.UPDATE, INCIDENT);
    when(response.isSuccessful()).thenReturn(true);
    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .issueNumber(VALUE)
                                                .issueId(VALUE)
                                                .issueUrl("base.url/nav_to.do?uri=/incident.do?sys_id=VALUE")
                                                .ticketType(INCIDENT)
                                                .responseMsg("Updated ServiceNow ticket: VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParams, executionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldCreateImportSetAndReturnSuccessfulResponse() {
    ServiceNowTaskParameters taskParameters = getTaskParams(ServiceNowAction.IMPORT_SET, INCIDENT);
    ServiceNowImportSetResponse importSetResponse = new ServiceNowImportSetResponse();
    importSetResponse.setImportSet("testImportSetValue");
    importSetResponse.setResult(Collections.emptyList());
    when(response.isSuccessful()).thenReturn(true);
    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .ticketType(INCIDENT)
                                                .transformationDetails(importSetResponse)
                                                .transformationValues(Collections.emptyList())
                                                .responseMsg("Created import Set : VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParameters, executionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldUpdateAllChangeTaskTicketsAndReturnSuccessfulResponse() {
    ServiceNowTaskParameters taskParameters = getTaskParams(ServiceNowAction.UPDATE, CHANGE_TASK);
    taskParameters.setUpdateMultiple(true);
    Map<ServiceNowFields, String> fields = new HashMap<>();
    fields.put(ServiceNowFields.CHANGE_REQUEST_NUMBER, VALUE);
    fields.put(ServiceNowFields.CHANGE_TASK_TYPE, "");
    taskParameters.setFields(fields);
    Iterator<JsonNode> mockIterator = mock(Iterator.class);
    when(jsonNode.iterator()).thenReturn(mockIterator);
    when(mockIterator.hasNext()).thenReturn(true, false);
    when(mockIterator.next()).thenReturn(jsonNode);
    when(response.isSuccessful()).thenReturn(true);
    when(jsonNode.isArray()).thenReturn(true);

    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .issueNumber(VALUE)
                                                .issueUrl("base.url/nav_to.do?uri=/change_task.do?sys_id=VALUE")
                                                .ticketType(CHANGE_TASK)
                                                .responseMsg("Updated Service Now tasks VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParameters, executionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulResponseWhenThereNoTasksToUpdate() {
    ServiceNowTaskParameters taskParameters = getTaskParams(ServiceNowAction.UPDATE, CHANGE_TASK);
    taskParameters.setUpdateMultiple(true);
    Map<ServiceNowFields, String> fields = new HashMap<>();
    fields.put(ServiceNowFields.CHANGE_REQUEST_NUMBER, VALUE);
    fields.put(ServiceNowFields.CHANGE_TASK_TYPE, "");
    taskParameters.setFields(fields);
    Iterator<JsonNode> mockIterator = mock(Iterator.class);
    when(jsonNode.iterator()).thenReturn(mockIterator);
    when(mockIterator.hasNext()).thenReturn(false);
    when(response.isSuccessful()).thenReturn(true);
    when(jsonNode.isArray()).thenReturn(true);

    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .issueNumber(VALUE)
                                                .ticketType(CHANGE_TASK)
                                                .responseMsg("No change tasks to update for: VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParameters, executionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetIssueIdFromIssueNumberAndReturnSuccessfulResponse() {
    ServiceNowTaskParameters taskParams = getTaskParams(ServiceNowAction.UPDATE, INCIDENT);
    taskParams.setIssueId(null);
    when(response.isSuccessful()).thenReturn(true);
    when(jsonNode.isArray()).thenReturn(true);
    when(jsonNode.get(0)).thenReturn(jsonNode);
    ServiceNowExecutionData executionData = ServiceNowExecutionData.builder()
                                                .issueNumber(VALUE)
                                                .issueId(VALUE)
                                                .issueUrl("base.url/nav_to.do?uri=/incident.do?sys_id=VALUE")
                                                .ticketType(INCIDENT)
                                                .responseMsg("Updated ServiceNow ticket: VALUE")
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .build();

    runTaskAndAssertResponse(taskParams, executionData);
  }

  private ServiceNowTaskParameters getTaskParams(ServiceNowAction action, ServiceNowTicketType ticketType) {
    return ServiceNowTaskParameters.builder()
        .accountId(ACCOUNT_ID)
        .action(action)
        .additionalFields(Collections.singletonMap("key", "value"))
        .encryptionDetails(Collections.emptyList())
        .fields(Collections.emptyMap())
        .importSetTableName(TABLE_NAME)
        .issueId(ISSUE_ID)
        .issueNumber(ISSUE_NUMBER)
        .jsonBody(JSON_BODY)
        .serviceNowConfig(
            ServiceNowConfig.builder().username("user").baseUrl("base.url").password("".toCharArray()).build())
        .ticketType(ticketType)
        .build();
  }

  private void runTaskAndAssertResponse(
      ServiceNowTaskParameters taskParameters, ServiceNowExecutionData serviceNowExecutionData) {
    DelegateResponseData delegateResponseData = spySnowTask.run(taskParameters);
    assertThat(delegateResponseData).isEqualToComparingFieldByField(serviceNowExecutionData);
  }
}
