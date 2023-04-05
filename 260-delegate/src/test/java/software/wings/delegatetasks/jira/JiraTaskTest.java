/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.jira;

import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JiraClientException;
import io.harness.jira.JiraAction;
import io.harness.jira.JiraCustomFieldValue;
import io.harness.jira.JiraFieldNG;
import io.harness.jira.JiraFieldSchemaNG;
import io.harness.jira.JiraFieldTypeNG;
import io.harness.jira.JiraInstanceData;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTypeNG;
import io.harness.jira.JiraProjectNG;
import io.harness.jira.JiraRestClient;
import io.harness.jira.JiraUserData;
import io.harness.rule.Owner;

import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.JiraConfig;
import software.wings.beans.jira.JiraTaskParameters;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.Issue.FluentCreate;
import net.rcarz.jiraclient.Issue.FluentTransition;
import net.rcarz.jiraclient.Issue.FluentUpdate;
import net.rcarz.jiraclient.Issue.SearchResult;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Project;
import net.rcarz.jiraclient.Resource;
import net.rcarz.jiraclient.RestClient;
import net.rcarz.jiraclient.RestException;
import net.rcarz.jiraclient.Status;
import net.rcarz.jiraclient.TimeTracking;
import net.rcarz.jiraclient.Transition;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class JiraTaskTest extends CategoryTest {
  private static final String BASE_URL = "http://jira.com";
  private static final String JIRA_ISSUE_ID = "JIRA_ISSUE_ID";
  private static final String PROJECT_KEY = "PROJECT_KEY";
  private static final String DESCRIPTION = "DESCRIPTION";
  private static final String COMMENT = "COMMENT";
  private static final String SUMMARY = "SUMMARY";
  private static final String LABEL = "LABEL";
  private static final String STATUS = "STATUS";
  private static final String PRIORITY = "PRIORITY";
  private static final String STORY = "Story";

  @Inject
  @InjectMocks
  JiraTask jiraTask =
      new JiraTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null, null, null);
  @InjectMocks JiraTask spyJiraTask = Mockito.spy(jiraTask);

  @Mock private EncryptionService encryptionService;
  @Mock private Issue issue;
  @Mock private JiraIssueNG issueNG;
  @Mock private JiraClient jiraClient;
  @Mock private JiraRestClient jiraRestClient;
  @Mock private io.harness.jira.JiraClient jiraNGClient;
  @Mock private Project project;
  @Mock private FluentUpdate update;
  @Mock private FluentCreate create;
  @Mock private RestClient restClient;
  @Mock private JSONObject json;
  @Mock private JSONArray jsonArray;
  @Mock private FluentTransition fluentTransition;
  @Mock private Transition transition;
  @Mock private Status status;

  @Captor ArgumentCaptor<String> fieldArgumentCaptor;
  @Captor ArgumentCaptor<TimeTracking> timeTrackingArgumentCaptor;
  @Captor ArgumentCaptor<Object> valuesArgumentCaptor;

  private JiraConfig jiraConfig =
      JiraConfig.builder().baseUrl(BASE_URL).username("username").password(new char['p']).build();

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnErrorWhenIssueIsNull() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    when(jiraNGClient.getIssue(JIRA_ISSUE_ID)).thenReturn(null);
    List<JiraUserData> userDataList = Arrays.asList(new JiraUserData("accountId", "Lucas", true, "id"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(userDataList).when(jiraNGClient).getUsers(anyString(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    JiraExecutionData executionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(String.format(
                "Wasn't able to find issue with provided issue identifier: \"%s\". Please, provide valid key or id.",
                JIRA_ISSUE_ID))
            .build();
    assertThat(delegateResponseData).isEqualToComparingFieldByField(executionData);
  }
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldFetchUserListInfo() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.SEARCH_USER);
    List<JiraUserData> mockUserList =
        new ArrayList<>(Arrays.asList(new JiraUserData("UserId", "User Name", true, "id")));
    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(mockUserList).when(jiraNGClient).getUsers(any(), any(), any());
    doReturn(new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD)).when(jiraNGClient).getInstanceData();

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).userSearchList(mockUserList).build();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
    verify(jiraNGClient).getUsers(eq(taskParameters.getUserQuery()), eq(taskParameters.getAccountId()), eq(null));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldFetchUserListInfoForJiraServer() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.SEARCH_USER);
    List<JiraUserData> mockUserList =
        new ArrayList<>(Arrays.asList(new JiraUserData("UserId", "User Name", true, "id")));
    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(mockUserList).when(jiraNGClient).getUsers(any(), any(), any());
    doReturn(new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER)).when(jiraNGClient).getInstanceData();

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).userSearchList(mockUserList).build();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
    verify(jiraNGClient).getUsers(eq(taskParameters.getUserQuery()), eq(taskParameters.getAccountId()), eq(null));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnFailedExecutionDataForUpdateTicketNG_failing() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    when(jiraNGClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issueNG);
    when(issueNG.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issueNG.getFields()).thenReturn(singletonMap("Project Key", PROJECT_KEY));
    List<JiraUserData> userDataList = Arrays.asList(new JiraUserData("accountId", "Lucas", true, "id"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(userDataList).when(jiraNGClient).getUsers(any(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();
    doThrow(new JiraClientException("error")).when(jiraNGClient).updateIssue(any(), any(), any(), anyMap());

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.FAILED);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldThrowErrorWhenJiraFetchesMoreThan_1_user() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    when(jiraNGClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issueNG);
    when(issueNG.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issueNG.getFields()).thenReturn(singletonMap("Project Key", PROJECT_KEY));
    List<JiraUserData> userDataList = Arrays.asList(
        new JiraUserData("accountId", "Lucas", true, "id1"), new JiraUserData("accountI2d", "Lucas", true, "id2"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(userDataList).when(jiraNGClient).getUsers(any(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();

    spyJiraTask.run(new Object[] {taskParameters});
  }
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnFailedExecutionDataForCreateTicketNG_failing() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    List<JiraUserData> userDataList = Arrays.asList(new JiraUserData("accountId", "Lucas", true, "id"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(issueNG).when(jiraNGClient).createIssue(any(), any(), anyMap(), anyBoolean(), anyBoolean(), anyBoolean());
    doReturn(userDataList).when(jiraNGClient).getUsers(any(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.CLOUD);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();
    doThrow(new JiraClientException("error"))
        .when(jiraNGClient)
        .createIssue(any(), any(), anyMap(), anyBoolean(), anyBoolean(), anyBoolean());

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionDataForCreateTicketNG() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    List<JiraUserData> userDataList = Arrays.asList(new JiraUserData("accountId", "Lucas", true, "id"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(issueNG).when(jiraNGClient).createIssue(any(), any(), anyMap(), anyBoolean(), anyBoolean(), anyBoolean());
    doReturn(userDataList).when(jiraNGClient).getUsers(any(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();
    doReturn(mock(JiraIssueNG.class))
        .when(jiraNGClient)
        .createIssue(any(), any(), anyMap(), anyBoolean(), anyBoolean(), anyBoolean());
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .jiraAction(JiraAction.CREATE_TICKET_NG)
            .executionStatus(ExecutionStatus.SUCCESS)
            .errorMessage("Created Jira ticket null")
            .issueUrl("https://some.attlasian.net/browse/null")
            .issueId(null)
            .issueKey(null)
            .jiraIssueData(JiraExecutionData.JiraIssueData.builder().description("").build())
            .build();
    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    verify(jiraNGClient).getUsers(taskParameters.getUserQuery(), null, null);
    verify(jiraNGClient)
        .createIssue(eq(taskParameters.getProject()), eq(taskParameters.getIssueType()), anyMap(), anyBoolean(),
            anyBoolean(), anyBoolean());
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
  }
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionDataForUpdateTicketNG() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET_NG);
    taskParameters.setCustomFields(
        singletonMap("customfield_10633", new JiraCustomFieldValue("user", taskParameters.getUserQuery())));
    List<JiraUserData> userDataList = Arrays.asList(new JiraUserData("accountId", "Lucas", true, "id"));

    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    when(jiraNGClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issueNG);
    when(issueNG.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issueNG.getFields()).thenReturn(singletonMap("Project Key", PROJECT_KEY));
    doReturn(mock(JiraIssueNG.class)).when(jiraNGClient).updateIssue(any(), any(), any(), any());
    doReturn(userDataList).when(jiraNGClient).getUsers(any(), any(), any());
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .errorMessage("Updated Jira ticket JIRA_ISSUE_ID")
            .issueUrl("https://some.attlasian.net/browse/JIRA_ISSUE_ID")
            .issueId(JIRA_ISSUE_ID)
            .issueKey(JIRA_ISSUE_ID)
            .jiraIssueData(
                JiraExecutionData.JiraIssueData.builder().description(taskParameters.getDescription()).build())
            .build();
    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    Mockito.verify(jiraNGClient).updateIssue(eq(JIRA_ISSUE_ID), eq(taskParameters.getStatus()), eq(null), anyMap());
    verify(jiraNGClient).getUsers(taskParameters.getUserQuery(), null, null);
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_newCreateMetaEndPointWithoutIssueTypeFailed() {
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .jiraAction(JiraAction.GET_CREATE_METADATA)
                                            .createmetaExpandParam("projects.issuetypes")
                                            .project("TES")
                                            .issueType(null)
                                            .useNewMeta(true)
                                            .build();
    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doThrow(new JiraClientException("error"))
        .when(jiraNGClient)
        .getIssueCreateMetadata(eq("TES"), any(), eq("projects.issuetypes"), eq(false), eq(false), eq(true), eq(true));
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).isInstanceOf(JiraExecutionData.class);
    assertThat(delegateResponseData).hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_newCreateMetaEndPointWithoutIssueTypeSuccess() {
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .jiraAction(JiraAction.GET_CREATE_METADATA)
                                            .createmetaExpandParam("projects.issuetypes")
                                            .project("TES")
                                            .issueType(null)
                                            .useNewMeta(true)
                                            .build();
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = new JiraIssueCreateMetadataNG();
    JiraProjectNG jiraProjectNG = new JiraProjectNG();
    jiraProjectNG.setKey("TES");
    jiraProjectNG.setId("10101");
    jiraProjectNG.setName("TestTask");
    JiraIssueTypeNG jiraIssueTypeNG1 = new JiraIssueTypeNG();
    jiraIssueTypeNG1.setName("Task");
    jiraIssueTypeNG1.setId("10003");
    jiraIssueTypeNG1.setSubTask(false);
    JiraIssueTypeNG jiraIssueTypeNG2 = new JiraIssueTypeNG();
    jiraIssueTypeNG2.setName("Sub-Task");
    jiraIssueTypeNG2.setId("10000");
    jiraIssueTypeNG2.setSubTask(true);
    Map<String, JiraIssueTypeNG> issueTypes = new HashMap<>();
    issueTypes.put("Task", jiraIssueTypeNG1);
    issueTypes.put("Sub-Task", jiraIssueTypeNG2);
    jiraProjectNG.setIssueTypes(issueTypes);
    Map<String, JiraProjectNG> projects = new HashMap<>();
    projects.put("TES", jiraProjectNG);
    jiraIssueCreateMetadataNG.setProjects(projects);
    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(jiraIssueCreateMetadataNG)
        .when(jiraNGClient)
        .getIssueCreateMetadata(eq("TES"), any(), eq("projects.issuetypes"), eq(false), eq(false), eq(true), eq(true));
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
    assertThat(delegateResponseData).isInstanceOf(JiraExecutionData.class);
    JiraExecutionData jiraExecutionData = (JiraExecutionData) delegateResponseData;
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().size()).isEqualTo(1);
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getKey()).isEqualTo("TES");
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().size()).isEqualTo(2);
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().get(0).getId())
        .isEqualTo("10003");
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().get(1).getId())
        .isEqualTo("10000");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_newCreateMetaEndPointWithIssueType() {
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .jiraAction(JiraAction.GET_CREATE_METADATA)
                                            .createmetaExpandParam("projects.issuetypes")
                                            .project("TES")
                                            .issueType(null)
                                            .useNewMeta(true)
                                            .build();
    JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG = new JiraIssueCreateMetadataNG();
    JiraProjectNG jiraProjectNG = new JiraProjectNG();
    jiraProjectNG.setKey("TES");
    jiraProjectNG.setId("10101");
    jiraProjectNG.setName("TestTask");
    JiraIssueTypeNG jiraIssueTypeNG1 = new JiraIssueTypeNG();
    jiraIssueTypeNG1.setName("Task");
    jiraIssueTypeNG1.setId("10003");
    jiraIssueTypeNG1.setSubTask(false);
    JiraFieldNG jiraFieldNG1 = new JiraFieldNG();
    jiraFieldNG1.setRequired(true);
    jiraFieldNG1.setKey("summary");
    jiraFieldNG1.setName("Summary");
    jiraFieldNG1.setCustom(false);
    jiraFieldNG1.setSchema(JiraFieldSchemaNG.builder().typeStr("string").type(JiraFieldTypeNG.STRING).build());
    JiraFieldNG jiraFieldNG2 = new JiraFieldNG();
    jiraFieldNG2.setRequired(true);
    jiraFieldNG2.setKey("labels");
    jiraFieldNG2.setName("Labels");
    jiraFieldNG2.setCustom(false);
    jiraFieldNG2.setSchema(
        JiraFieldSchemaNG.builder().typeStr("string").array(true).type(JiraFieldTypeNG.STRING).build());
    Map<String, JiraFieldNG> fields = new HashMap<>();
    fields.put("summary", jiraFieldNG1);
    fields.put("labels", jiraFieldNG2);
    jiraIssueTypeNG1.setFields(fields);
    Map<String, JiraIssueTypeNG> issueTypes = new HashMap<>();
    issueTypes.put("Task", jiraIssueTypeNG1);
    jiraProjectNG.setIssueTypes(issueTypes);
    Map<String, JiraProjectNG> projects = new HashMap<>();
    projects.put("TES", jiraProjectNG);
    jiraIssueCreateMetadataNG.setProjects(projects);
    doReturn(jiraNGClient).when(spyJiraTask).getNGJiraClient(taskParameters);
    doReturn(jiraIssueCreateMetadataNG)
        .when(jiraNGClient)
        .getIssueCreateMetadata(eq("TES"), any(), eq("projects.issuetypes"), eq(false), eq(false), eq(true), eq(true));
    JiraInstanceData jiraInstanceData = new JiraInstanceData(JiraInstanceData.JiraDeploymentType.SERVER);
    doReturn(jiraInstanceData).when(jiraNGClient).getInstanceData();

    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
    assertThat(delegateResponseData).isInstanceOf(JiraExecutionData.class);
    JiraExecutionData jiraExecutionData = (JiraExecutionData) delegateResponseData;
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().size()).isEqualTo(1);
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getKey()).isEqualTo("TES");
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().size()).isEqualTo(1);
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().get(0).getId())
        .isEqualTo("10003");
    assertThat(jiraExecutionData.getCreateMetadata().getProjects().get(0).getIssueTypes().get(0).getJiraFields().size())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldExtractTimeTrackingSuccessfully() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET_NG);
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    Map<String, String> userTypeFields = null;
    customFields.put("TimeTracking:OriginalEstimate", new JiraCustomFieldValue("timetracking", "1d 2h"));
    customFields.put("TimeTracking:RemainingEstimate", new JiraCustomFieldValue("timetracking", "4h"));
    taskParameters.setCustomFields(customFields);

    jiraTask.setCustomFieldsOnCreate(taskParameters, create);
    Map<String, String> extracted = jiraTask.extractFieldsFromCGParameters(taskParameters, userTypeFields);

    assertThat(extracted.get("Original Estimate")).isEqualTo("1d 2h");
    assertThat(extracted.get("Remaining Estimate")).isEqualTo("4h");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetTimeTrackingPropertiesOnTicketCreate() {
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put("TimeTracking:OriginalEstimate", new JiraCustomFieldValue("timetracking", "1d 2h"));
    customFields.put("TimeTracking:RemainingEstimate", new JiraCustomFieldValue("timetracking", "4h"));
    JiraTaskParameters parameters = JiraTaskParameters.builder().customFields(customFields).build();

    jiraTask.setCustomFieldsOnCreate(parameters, create);
    Mockito.verify(create).field(fieldArgumentCaptor.capture(), timeTrackingArgumentCaptor.capture());

    String capturedField = fieldArgumentCaptor.getValue();
    TimeTracking capturedTimeTracking = timeTrackingArgumentCaptor.getValue();

    assertThat(capturedField).isEqualTo(Field.TIME_TRACKING);
    assertThat(capturedTimeTracking.getOriginalEstimate())
        .isEqualTo(customFields.get("TimeTracking:OriginalEstimate").getFieldValue());
    assertThat(capturedTimeTracking.getRemainingEstimate())
        .isEqualTo(customFields.get("TimeTracking:RemainingEstimate").getFieldValue());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetAllCustomFieldsOnTicketCreate() {
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put("TimeTracking:OriginalEstimate", new JiraCustomFieldValue("timetracking", "1d 2h"));
    customFields.put("TimeTracking:RemainingEstimate", new JiraCustomFieldValue("timetracking", "4h"));
    customFields.put("someCustomField", new JiraCustomFieldValue("multiselect", "first,second"));
    customFields.put("otherCustomField", new JiraCustomFieldValue("any", "value"));

    JiraTaskParameters parameters = JiraTaskParameters.builder().customFields(customFields).build();

    jiraTask.setCustomFieldsOnCreate(parameters, create);
    Mockito.verify(create, Mockito.times(3)).field(fieldArgumentCaptor.capture(), valuesArgumentCaptor.capture());

    List<String> capturedFields = fieldArgumentCaptor.getAllValues();
    List<Object> capturedValues = valuesArgumentCaptor.getAllValues();

    assertThat(capturedFields).containsExactlyInAnyOrder(Field.TIME_TRACKING, "someCustomField", "otherCustomField");
    assertThat(capturedValues.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetTimeTrackingPropertiesOnTicketUpdate() {
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put("TimeTracking:OriginalEstimate", new JiraCustomFieldValue("timetracking", "1d 2h"));
    customFields.put("TimeTracking:RemainingEstimate", new JiraCustomFieldValue("timetracking", "4h"));

    JiraTaskParameters parameters = JiraTaskParameters.builder().customFields(customFields).build();

    jiraTask.setCustomFieldsOnUpdate(parameters, update);
    Mockito.verify(update).field(fieldArgumentCaptor.capture(), timeTrackingArgumentCaptor.capture());

    String capturedField = fieldArgumentCaptor.getValue();
    TimeTracking capturedTimeTracking = timeTrackingArgumentCaptor.getValue();

    assertThat(capturedField).isEqualTo(Field.TIME_TRACKING);
    assertThat(capturedTimeTracking.getOriginalEstimate())
        .isEqualTo(customFields.get("TimeTracking:OriginalEstimate").getFieldValue());
    assertThat(capturedTimeTracking.getRemainingEstimate())
        .isEqualTo(customFields.get("TimeTracking:RemainingEstimate").getFieldValue());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetAllCustomFieldsOnTicketUpdate() {
    Map<String, JiraCustomFieldValue> customFields = new HashMap<>();
    customFields.put("TimeTracking:OriginalEstimate", new JiraCustomFieldValue("timetracking", "1d 2h"));
    customFields.put("TimeTracking:RemainingEstimate", new JiraCustomFieldValue("timetracking", "4h"));
    customFields.put("someCustomField", new JiraCustomFieldValue("multiselect", "first,second"));

    JiraTaskParameters parameters = JiraTaskParameters.builder().customFields(customFields).build();

    jiraTask.setCustomFieldsOnUpdate(parameters, update);
    Mockito.verify(update, Mockito.times(2)).field(fieldArgumentCaptor.capture(), valuesArgumentCaptor.capture());

    List<String> capturedFields = fieldArgumentCaptor.getAllValues();
    List<Object> capturedValues = valuesArgumentCaptor.getAllValues();

    assertThat(capturedFields).containsExactlyInAnyOrder(Field.TIME_TRACKING, "someCustomField");
    assertThat(capturedValues.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotFailJiraApprovalOnException() {
    JiraTaskParameters parameters = getJiraTaskParametersForApproval();
    JiraTask spyJiraTask = spy(jiraTask);
    spyJiraTask.checkJiraApproval(parameters);
    JiraExecutionData jiraExecutionData = (JiraExecutionData) jiraTask.run(parameters);
    assertThat(jiraExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.PAUSED);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category({UnitTests.class})
  public void shouldGetProxyEnabledJiraClient() throws JiraException {
    JiraTaskParameters jiraTaskParameters = getJiraTaskParametersForAUTH();

    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "80");
    JiraClient jiraClient = jiraTask.getJiraClient(jiraTaskParameters);
    assertThat(jiraClient.getRestClient().getHttpClient()).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category({UnitTests.class})
  public void shouldGetProxyEnabledJiraClientWithAuth() throws JiraException {
    JiraTaskParameters jiraTaskParameters = getJiraTaskParametersForAUTH();

    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "80");
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "user");
    JiraClient jiraClient = jiraTask.getJiraClient(jiraTaskParameters);
    assertThat(jiraClient.getRestClient().getHttpClient()).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category({UnitTests.class})
  public void shouldThrowProxyAuthErrorOnValidateCredentials() {
    JiraTaskParameters jiraTaskParameters = getJiraTaskParametersForAUTH();

    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "80");
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "user");

    Mockito
        .doAnswer(invocation -> {
          throw new JiraException("", new RestException("Proxy Authentication Required", 407, "", new Header['a']));
        })
        .when(encryptionService)
        .decrypt(jiraConfig, jiraTaskParameters.getEncryptionDetails(), false);

    JiraExecutionData jiraExecutionData = (JiraExecutionData) jiraTask.validateCredentials(jiraTaskParameters);
    assertThat(jiraExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(jiraExecutionData.getErrorMessage())
        .isEqualTo(
            "Failed to fetch projects during credential validation. Reason: Proxy Authentication Required. Error Code: 407");
  }

  @Test
  @Owner(developers = {ROHITKARELIA, UTKARSH_CHOUBEY})
  @Category({UnitTests.class})
  public void shouldThrowProxyAuthErrorOnGetProjects() {
    JiraTaskParameters jiraTaskParameters = getJiraTaskParametersForAUTH();

    System.setProperty("http.proxyHost", "testProxyHost");
    System.setProperty("http.proxyPort", "80");
    System.setProperty("http.proxyUser", "user");
    System.setProperty("http.proxyPassword", "user");

    Mockito
        .doAnswer(invocation -> { throw new RestException("Proxy Authentication Required", 407, "", new Header['a']); })
        .when(encryptionService)
        .decrypt(jiraConfig, jiraTaskParameters.getEncryptionDetails(), false);

    JiraExecutionData jiraExecutionData = (JiraExecutionData) jiraTask.getProjects(jiraTaskParameters);
    assertThat(jiraExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(jiraExecutionData.getErrorMessage())
        .isEqualTo(
            "Failed to fetch projects from Jira server, Uri for GET PROJECTS - /rest/api/latest/project  Reason: Proxy Authentication Required. Error Code: 407");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailUpdateTicketWhenFailToCreateJiraClient() throws JiraException {
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .updateIssueIds(Collections.singletonList(JIRA_ISSUE_ID))
                                            .jiraAction(JiraAction.UPDATE_TICKET)
                                            .build();
    Mockito.doThrow(new JiraException("")).when(spyJiraTask).getJiraClient(taskParameters);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Failed to create jira client while trying to update : [JIRA_ISSUE_ID]")
            .jiraServerResponse("")
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnFailedExecutionDataWhenWrongTicketIdIsProvided() throws JiraException {
    JiraTaskParameters taskParameters = JiraTaskParameters.builder()
                                            .updateIssueIds(Collections.singletonList(JIRA_ISSUE_ID))
                                            .jiraAction(JiraAction.UPDATE_TICKET)
                                            .project(PROJECT_KEY)
                                            .build();
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getProject()).thenReturn(project);
    when(project.getKey()).thenReturn("");
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(String.format(
                "Provided issue identifier: \"%s\" does not correspond to Project: \"%s\". Please, provide valid key or id.",
                JIRA_ISSUE_ID, taskParameters.getProject()))
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionDataForUpdateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET);

    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issue.getProject()).thenReturn(project);
    when(issue.update()).thenReturn(update);
    when(project.getKey()).thenReturn(PROJECT_KEY);
    when(update.field(any(), any())).thenReturn(null);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .errorMessage("Updated Jira ticket JIRA_ISSUE_ID")
            .issueUrl("https://some.attlasian.net/browse/JIRA_ISSUE_ID")
            .issueId(JIRA_ISSUE_ID)
            .issueKey(JIRA_ISSUE_ID)
            .jiraIssueData(
                JiraExecutionData.JiraIssueData.builder().description(taskParameters.getDescription()).build())
            .build();
    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    Mockito.verify(update).execute();
    Mockito.verify(issue).addComment(COMMENT);
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionWhenJiraExceptionOccurredWhileTryingToUpdateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET);

    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenThrow(new JiraException(""));

    JiraExecutionData jiraExecutionData = JiraExecutionData.builder()
                                              .executionStatus(ExecutionStatus.FAILED)
                                              .errorMessage("Failed to update Jira Issue for Id: JIRA_ISSUE_ID. ")
                                              .jiraServerResponse("")
                                              .build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionWhenWingsExceptionOccurredWhileTryingToUpdateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.UPDATE_TICKET);

    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenThrow(new InvalidRequestException(""));

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage("INVALID_REQUEST").build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionDataAfterForCreateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.createIssue(PROJECT_KEY, STORY)).thenReturn(create);
    when(create.field(any(), any())).thenReturn(create);
    when(create.execute()).thenReturn(issue);
    when(issue.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issue.getId()).thenReturn(JIRA_ISSUE_ID);
    when(issue.getDescription()).thenReturn(DESCRIPTION);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .jiraAction(JiraAction.CREATE_TICKET)
            .errorMessage("Created Jira ticket JIRA_ISSUE_ID")
            .issueUrl("https://some.attlasian.net/browse/JIRA_ISSUE_ID")
            .issueId(JIRA_ISSUE_ID)
            .issueKey(JIRA_ISSUE_ID)
            .jiraIssueData(
                JiraExecutionData.JiraIssueData.builder().description(taskParameters.getDescription()).build())
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnJiraExceptionWhenCreateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET);
    Mockito.doThrow(new JiraException("")).when(spyJiraTask).getJiraClient(taskParameters);
    JiraExecutionData jiraExecutionData = JiraExecutionData.builder()
                                              .executionStatus(ExecutionStatus.FAILED)
                                              .errorMessage("Unable to create a new Jira ticket. JiraException:  ")
                                              .jiraServerResponse("")
                                              .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnWingsExceptionWhenCreateTicket() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CREATE_TICKET);
    Mockito.doThrow(new InvalidRequestException("")).when(spyJiraTask).getJiraClient(taskParameters);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage("INVALID_REQUEST").build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldParseEmptyBodyFromJiraIssueWhenFailing() throws JiraException {
    JiraException exception =
        new JiraException("error", new RestException("error", HttpStatus.SC_METHOD_NOT_ALLOWED, "{}", new Header['a']));
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.FETCH_ISSUE);
    when(encryptionService.decrypt(taskParameters.getJiraConfig(), taskParameters.getEncryptionDetails(), false))
        .thenReturn(null);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenThrow(exception);

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(
                "Unable to fetch Jira Issue for Id: JIRA_ISSUE_ID  Failed to parse json response from Jira: RestException: 405 error: {}")
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnJiraExceptionForFetchIssue() {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.FETCH_ISSUE);
    when(encryptionService.decrypt(taskParameters.getJiraConfig(), taskParameters.getEncryptionDetails(), false))
        .thenReturn(null);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Unable to fetch Jira Issue for Id: JIRA_ISSUE_ID  Failed to retrieve issue JIRA_ISSUE_ID")
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFetchIssueAndReturnPausedExecutionForFetchIssue() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.FETCH_ISSUE);
    when(encryptionService.decrypt(taskParameters.getJiraConfig(), taskParameters.getEncryptionDetails(), false))
        .thenReturn(null);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getField(STATUS)).thenReturn(singletonMap("name", "To Do"));
    when(issue.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(issue.getId()).thenReturn(JIRA_ISSUE_ID);
    when(issue.getDescription()).thenReturn(DESCRIPTION);

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .executionStatus(ExecutionStatus.PAUSED)
            .issueUrl("https://some.attlasian.net/browse/JIRA_ISSUE_ID")
            .issueKey(JIRA_ISSUE_ID)
            .currentStatus("To Do")
            .errorMessage("Waiting for Approval on ticket: JIRA_ISSUE_ID")
            .jiraIssueData(JiraExecutionData.JiraIssueData.builder().description(DESCRIPTION).build())
            .build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFetchProjectsAndReturnSuccessfulExecution() throws JiraException, IOException, RestException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_PROJECTS);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    JSONArray jsonArray = new JSONArray();
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.get(nullable(URI.class))).thenReturn(json);
    try (MockedStatic<JSONArray> jsonArrayMockedStatic = Mockito.mockStatic(JSONArray.class)) {
      when(JSONArray.fromObject(json)).thenReturn(jsonArray);
      JiraExecutionData jiraExecutionData =
          JiraExecutionData.builder().projects(jsonArray).executionStatus(ExecutionStatus.SUCCESS).build();
      runTaskAndAssertResponse(taskParameters, jiraExecutionData);
    }
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnJiraExceptionForGetFieldsAndOptions()
      throws JiraException, URISyntaxException, IOException, RestException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_FIELDS_OPTIONS);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.searchIssues("project = PROJECT_KEY", 1)).thenThrow(new JiraException(""));
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .errorMessage("Failed to fetch issues from Jira server for project - PROJECT_KEY")
            .executionStatus(ExecutionStatus.FAILED)
            .build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnRestExceptionForGetFieldsAndOptions()
      throws JiraException, URISyntaxException, IOException, RestException, IllegalAccessException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_FIELDS_OPTIONS);
    SearchResult issues = mock(SearchResult.class);
    FieldUtils.writeField(issues, "issues", singletonList(issue), true);
    URI uri = new URI(Resource.getBaseUri() + "issue/" + JIRA_ISSUE_ID + "/editmeta");
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.searchIssues("project = PROJECT_KEY", 1)).thenReturn(issues);
    when(issue.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.buildURI(Resource.getBaseUri() + "issue/" + JIRA_ISSUE_ID + "/editmeta")).thenReturn(uri);
    when(restClient.get(uri)).thenThrow(new RestException("", 500, "", null));

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .errorMessage("Failed to fetch editmeta from Jira server. Issue - JIRA_ISSUE_ID")
            .executionStatus(ExecutionStatus.FAILED)
            .build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulResponseForGetFieldsAndOptions()
      throws URISyntaxException, JiraException, IOException, RestException, IllegalAccessException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_FIELDS_OPTIONS);
    SearchResult issues = mock(SearchResult.class);
    FieldUtils.writeField(issues, "issues", singletonList(issue), true);
    URI uri = new URI(Resource.getBaseUri() + "issue/" + JIRA_ISSUE_ID + "/editmeta");
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.searchIssues("project = PROJECT_KEY", 1)).thenReturn(issues);
    when(issue.getKey()).thenReturn(JIRA_ISSUE_ID);
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.buildURI(Resource.getBaseUri() + "issue/" + JIRA_ISSUE_ID + "/editmeta")).thenReturn(uri);
    when(restClient.get(uri)).thenReturn(json);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().fields((JSONObject) json).executionStatus(ExecutionStatus.SUCCESS).build();

    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnJiraExceptionForGetStatuses() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_STATUSES);
    Mockito.doThrow(new JiraException("")).when(spyJiraTask).getJiraClient(taskParameters);
    JiraExecutionData jiraExecutionData = JiraExecutionData.builder()
                                              .errorMessage("Failed to fetch statuses from Jira server.")
                                              .executionStatus(ExecutionStatus.FAILED)
                                              .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionForGetStatuses()
      throws URISyntaxException, JiraException, IOException, RestException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_STATUSES);
    URI uri = new URI(Resource.getBaseUri() + "project/PROJECT_KEY/statuses");
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.buildURI(Resource.getBaseUri() + "project/PROJECT_KEY/statuses")).thenReturn(uri);
    when(restClient.get(uri)).thenReturn(jsonArray);

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).statuses(jsonArray).build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailExecutionOnJiraExceptionForGetCreateMetadata() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_CREATE_METADATA);
    Mockito.doThrow(new JiraException("")).when(spyJiraTask).getJiraClient(taskParameters);
    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder()
            .errorMessage(
                "Failed to fetch issue metadata from Jira server, Uri for GET_CREATE_METADATA - /rest/api/latest/issue/createmeta ")
            .executionStatus(ExecutionStatus.FAILED)
            .build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionForGetCreateMetadataNew()
      throws JiraException, URISyntaxException, IOException, RestException {
    doReturn(false).when(spyJiraTask).getDisableOptimizationFlag();
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_CREATE_METADATA);
    URI uri = new URI(Resource.getBaseUri() + "issue/createmeta");
    URI resolutionUri = new URI(Resource.getBaseUri() + "resolution");
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("expand", "projects.issuetypes.fields");
    queryParams.put("projectKeys", PROJECT_KEY);
    queryParams.put("issuetypeNames", STORY);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.buildURI(Resource.getBaseUri() + "issue/createmeta", queryParams)).thenReturn(uri);
    when(restClient.get(uri)).thenReturn(json);
    when(restClient.buildURI(Resource.getBaseUri() + "resolution")).thenReturn(resolutionUri);
    when(restClient.get(resolutionUri)).thenReturn(jsonArray);
    when(json.getString("expand")).thenReturn("");
    when(json.getJSONArray("projects")).thenReturn(jsonArray);
    when(jsonArray.size()).thenReturn(1);
    when(jsonArray.getJSONObject(0)).thenReturn(json);
    when(json.getString("id")).thenReturn(PROJECT_KEY);
    when(json.getString("key")).thenReturn(PROJECT_KEY);
    when(json.getString("name")).thenReturn(PROJECT_KEY);
    when(json.getJSONArray("issuetypes")).thenReturn(jsonArray);
    when(json.containsKey("description")).thenReturn(false);
    when(json.getBoolean("subtask")).thenReturn(false);
    when(json.getJSONObject("fields")).thenReturn(json);
    when(json.keySet()).thenReturn(emptySet());
    when(json.containsKey("statuses")).thenReturn(false);
    when(json.getBoolean("required")).thenReturn(false);
    when(json.getJSONObject("schema")).thenReturn(json);
    when(json.containsKey("allowedValues")).thenReturn(false);
    when(json.get("key")).thenReturn("");
    try (MockedStatic<JSONObject> jsonArrayMockedStatic = Mockito.mockStatic(JSONObject.class)) {
      when(JSONObject.fromObject(any())).thenReturn(json);
      DelegateResponseData responseData = spyJiraTask.run(new Object[] {taskParameters});
      assertThat(((JiraExecutionData) responseData).getCreateMetadata()).isNotNull();
      assertThat(((JiraExecutionData) responseData).getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
  }
  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionForGetCreateMetadata()
      throws JiraException, URISyntaxException, IOException, RestException {
    doReturn(true).when(spyJiraTask).getDisableOptimizationFlag();
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.GET_CREATE_METADATA);
    URI uri = new URI(Resource.getBaseUri() + "issue/createmeta");
    URI resolutionUri = new URI(Resource.getBaseUri() + "resolution");
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("expand", "projects.issuetypes.fields");
    queryParams.put("projectKeys", PROJECT_KEY);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getRestClient()).thenReturn(restClient);
    when(restClient.buildURI(Resource.getBaseUri() + "issue/createmeta", queryParams)).thenReturn(uri);
    when(restClient.get(uri)).thenReturn(json);
    when(restClient.buildURI(Resource.getBaseUri() + "resolution")).thenReturn(resolutionUri);
    when(restClient.get(resolutionUri)).thenReturn(jsonArray);
    when(json.getString("expand")).thenReturn("");
    when(json.getJSONArray("projects")).thenReturn(jsonArray);
    when(jsonArray.size()).thenReturn(1);
    when(jsonArray.getJSONObject(0)).thenReturn(json);
    when(json.getString("id")).thenReturn(PROJECT_KEY);
    when(json.getString("key")).thenReturn(PROJECT_KEY);
    when(json.getString("name")).thenReturn(PROJECT_KEY);
    when(json.getJSONArray("issuetypes")).thenReturn(jsonArray);
    when(json.containsKey("description")).thenReturn(false);
    when(json.getBoolean("subtask")).thenReturn(false);
    when(json.getJSONObject("fields")).thenReturn(json);
    when(json.keySet()).thenReturn(emptySet());
    when(json.containsKey("statuses")).thenReturn(false);
    when(json.getBoolean("required")).thenReturn(false);
    when(json.getJSONObject("schema")).thenReturn(json);
    when(json.containsKey("allowedValues")).thenReturn(false);
    when(json.get("key")).thenReturn("");
    try (MockedStatic<JSONObject> jsonArrayMockedStatic = Mockito.mockStatic(JSONObject.class)) {
      when(JSONObject.fromObject(any())).thenReturn(json);
      DelegateResponseData responseData = spyJiraTask.run(new Object[] {taskParameters});
      assertThat(((JiraExecutionData) responseData).getCreateMetadata()).isNotNull();
      assertThat(((JiraExecutionData) responseData).getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessfulExecutionForCheckJiraApproval() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CHECK_APPROVAL);

    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getField(STATUS)).thenReturn(singletonMap("name", "To Do"));

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().currentStatus("To Do").executionStatus(ExecutionStatus.SUCCESS).build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRejectExecutionForCheckJiraApproval() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CHECK_APPROVAL);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getField(STATUS)).thenReturn(singletonMap("name", "Done"));

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().currentStatus("Done").executionStatus(ExecutionStatus.REJECTED).build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldPauseExecutionWhileWaitingJiraStatusToMatchApprovalOrRejectionValue() throws JiraException {
    JiraTaskParameters taskParameters = getTaskParams(JiraAction.CHECK_APPROVAL);
    doReturn(jiraClient).when(spyJiraTask).getJiraClient(taskParameters);
    when(jiraClient.getIssue(JIRA_ISSUE_ID)).thenReturn(issue);
    when(issue.getField(STATUS)).thenReturn(singletonMap("name", "In Progress"));

    JiraExecutionData jiraExecutionData =
        JiraExecutionData.builder().currentStatus("In Progress").executionStatus(ExecutionStatus.PAUSED).build();
    runTaskAndAssertResponse(taskParameters, jiraExecutionData);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowJiraExceptionWhenFailedToGetTransitions() throws JiraException {
    when(issue.getTransitions()).thenThrow(new JiraException(""));
    assertThatThrownBy(() -> jiraTask.updateStatus(issue, "")).isInstanceOf(JiraException.class).hasMessage("");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowJiraExceptionWhenNoTransitionsFound() throws JiraException {
    when(issue.getTransitions()).thenReturn(emptyList());
    when(issue.getStatus()).thenReturn(status);
    when(status.getName()).thenReturn("Unattainable");
    assertThatThrownBy(() -> jiraTask.updateStatus(issue, "To Do"))
        .isInstanceOf(JiraException.class)
        .hasMessage("No transition found from [Unattainable] to [To Do]");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowExceptionOccurredWhileTryingToUpdateStatus() throws JiraException {
    when(issue.getTransitions()).thenReturn(singletonList(transition));
    when(transition.getToStatus()).thenReturn(status);
    when(status.getName()).thenReturn("Done");
    when(issue.transition()).thenReturn(fluentTransition);
    doThrow(new JiraException("Exception while trying to update status to Done"))
        .when(fluentTransition)
        .execute(transition);

    assertThatThrownBy(() -> jiraTask.updateStatus(issue, "Done"))
        .isInstanceOf(JiraException.class)
        .hasMessage("Exception while trying to update status to Done");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteTransitionToStatus() throws JiraException {
    when(issue.getTransitions()).thenReturn(singletonList(transition));
    when(transition.getToStatus()).thenReturn(status);
    when(status.getName()).thenReturn("Done");
    when(issue.transition()).thenReturn(fluentTransition);
    jiraTask.updateStatus(issue, "Done");
    Mockito.verify(fluentTransition).execute(transition);
  }

  private JiraTaskParameters getTaskParams(JiraAction action) {
    return JiraTaskParameters.builder()
        .updateIssueIds(Collections.singletonList(JIRA_ISSUE_ID))
        .jiraAction(action)
        .approvalField(STATUS)
        .approvalValue("To Do")
        .rejectionField(STATUS)
        .rejectionValue("Done")
        .issueType(STORY)
        .issueId(JIRA_ISSUE_ID)
        .project(PROJECT_KEY)
        .priority(PRIORITY)
        .description(DESCRIPTION)
        .customFields(singletonMap("field", new JiraCustomFieldValue("string", "value")))
        .comment(COMMENT)
        .jiraConfig(
            JiraConfig.builder().baseUrl("https://some.attlasian.net").password("password".toCharArray()).build())
        .summary(SUMMARY)
        .userQuery("user@harness.io")
        .labels(Collections.singletonList(LABEL))
        .build();
  }

  private void runTaskAndAssertResponse(JiraTaskParameters taskParameters, JiraExecutionData jiraExecutionData) {
    DelegateResponseData delegateResponseData = spyJiraTask.run(new Object[] {taskParameters});
    assertThat(delegateResponseData).isEqualToComparingFieldByField(jiraExecutionData);
  }

  private JiraTaskParameters getJiraTaskParametersForAUTH() {
    return JiraTaskParameters.builder().jiraAction(JiraAction.AUTH).jiraConfig(jiraConfig).build();
  }

  private JiraTaskParameters getJiraTaskParametersForApproval() {
    return JiraTaskParameters.builder()
        .jiraAction(JiraAction.CHECK_APPROVAL)
        .issueId("ISSUE-10012")
        .jiraConfig(jiraConfig)
        .build();
  }
}
