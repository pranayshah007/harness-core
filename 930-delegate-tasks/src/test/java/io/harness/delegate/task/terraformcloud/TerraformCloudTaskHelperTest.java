/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.TMACARI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.terraformcloud.PlanType;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.terraformcloud.TerraformCloudApiException;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.Relationship;
import io.harness.terraformcloud.model.ResourceLinkage;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.RunStatus;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTaskHelperTest {
  private static String URL = "url";
  private static String TOKEN = "token";
  private static final String WORKSPACE = "ws-123";
  private static final String ORG = "org-123";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private TerraformCloudClient terraformCloudClient;
  @InjectMocks RunRequestCreator runRequestCreator;
  @Mock DelegateFileManagerBase delegateFileManager;
  @Mock LogCallback logCallback;

  @InjectMocks private TerraformCloudTaskHelper taskHelper;

  @Before
  public void setUp() {
    on(taskHelper).set("runRequestCreator", runRequestCreator);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getAllOrganizationsOnePage() throws IOException {
    OrganizationData organization1 = new OrganizationData();
    organization1.setId("id1");
    organization1.setAttributes(OrganizationData.Attributes.builder().name("org1").build());
    OrganizationData organization2 = new OrganizationData();
    organization2.setId("id2");
    organization2.setAttributes(OrganizationData.Attributes.builder().name("org2").build());
    JsonNode jsonNode = JsonUtils.asObject("{\"next\": null}", JsonNode.class);

    doReturn(TerraformCloudResponse.builder().data(Arrays.asList(organization1, organization2)).links(jsonNode).build())
        .when(terraformCloudClient)
        .listOrganizations(any(), any(), anyInt());
    List<OrganizationData> organizations =
        taskHelper.getAllOrganizations(TerraformCloudApiTokenCredentials.builder().url(URL).token(TOKEN).build());

    verify(terraformCloudClient, times(1)).listOrganizations(URL, TOKEN, 1);
    assertThat(organizations.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getAllOrganizationsMultiplePages() throws IOException {
    OrganizationData organization1 = new OrganizationData();
    organization1.setId("id1");
    organization1.setAttributes(OrganizationData.Attributes.builder().name("org1").build());
    OrganizationData organization2 = new OrganizationData();
    organization2.setId("id2");
    organization2.setAttributes(OrganizationData.Attributes.builder().name("org2").build());
    JsonNode jsonNode1 = JsonUtils.asObject("{\"next\": \"http://localhost\"}", JsonNode.class);
    JsonNode jsonNode2 = JsonUtils.asObject("{\"next\": null}", JsonNode.class);

    doReturn(TerraformCloudResponse.builder().data(List.of(organization1)).links(jsonNode1).build())
        .when(terraformCloudClient)
        .listOrganizations(any(), any(), eq(1));
    doReturn(TerraformCloudResponse.builder().data(List.of(organization2)).links(jsonNode2).build())
        .when(terraformCloudClient)
        .listOrganizations(any(), any(), eq(2));

    List<OrganizationData> organizations =
        taskHelper.getAllOrganizations(TerraformCloudApiTokenCredentials.builder().url(URL).token(TOKEN).build());

    verify(terraformCloudClient, times(1)).listOrganizations(URL, TOKEN, 1);
    verify(terraformCloudClient, times(1)).listOrganizations(URL, TOKEN, 2);
    assertThat(organizations.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getAllWorkspaces() throws IOException {
    WorkspaceData workspaceData1 = new WorkspaceData();
    workspaceData1.setId("id1");
    workspaceData1.setAttributes(WorkspaceData.Attributes.builder().name("ws1").build());
    WorkspaceData workspaceData2 = new WorkspaceData();
    workspaceData2.setId("id2");
    workspaceData2.setAttributes(WorkspaceData.Attributes.builder().name("ws2").build());
    JsonNode jsonNode1 = JsonUtils.asObject("{\"next\": \"http://localhost\"}", JsonNode.class);
    JsonNode jsonNode2 = JsonUtils.asObject("{\"next\": null}", JsonNode.class);

    doReturn(TerraformCloudResponse.builder().data(List.of(workspaceData1)).links(jsonNode1).build())
        .when(terraformCloudClient)
        .listWorkspaces(any(), any(), any(), eq(1));
    doReturn(TerraformCloudResponse.builder().data(List.of(workspaceData2)).links(jsonNode2).build())
        .when(terraformCloudClient)
        .listWorkspaces(any(), any(), any(), eq(2));

    List<WorkspaceData> workspaces =
        taskHelper.getAllWorkspaces(TerraformCloudApiTokenCredentials.builder().url(URL).token(TOKEN).build(), "org");

    verify(terraformCloudClient, times(1)).listWorkspaces(URL, TOKEN, "org", 1);
    verify(terraformCloudClient, times(1)).listWorkspaces(URL, TOKEN, "org", 2);
    assertThat(workspaces.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getWorkspacesMap() throws IOException {
    TerraformCloudTaskHelper terraformCloudTaskHelper = spy(taskHelper);
    TerraformCloudApiTokenCredentials credentials =
        TerraformCloudApiTokenCredentials.builder().url(URL).token(TOKEN).build();
    WorkspaceData workspaceData1 = new WorkspaceData();
    workspaceData1.setId("id1");
    workspaceData1.setAttributes(WorkspaceData.Attributes.builder().name("ws1").build());
    WorkspaceData workspaceData2 = new WorkspaceData();
    workspaceData2.setId("id2");
    workspaceData2.setAttributes(WorkspaceData.Attributes.builder().name("ws2").build());

    doReturn(Arrays.asList(workspaceData1, workspaceData2))
        .when(terraformCloudTaskHelper)
        .getAllWorkspaces(any(), any());

    Map<String, String> workspacesMap = terraformCloudTaskHelper.getWorkspacesMap(
        TerraformCloudConfig.builder().terraformCloudCredentials(credentials).build(), "org");

    verify(terraformCloudTaskHelper).getAllWorkspaces(credentials, "org");
    assertThat(workspacesMap.get("id1")).isEqualTo("ws1");
    assertThat(workspacesMap.get("id2")).isEqualTo("ws2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getOrganizationsMap() throws IOException {
    TerraformCloudTaskHelper terraformCloudTaskHelper = spy(taskHelper);
    TerraformCloudApiTokenCredentials credentials =
        TerraformCloudApiTokenCredentials.builder().url(URL).token(TOKEN).build();
    OrganizationData organization1 = new OrganizationData();
    organization1.setId("id1");
    organization1.setAttributes(OrganizationData.Attributes.builder().name("org1").build());
    OrganizationData organization2 = new OrganizationData();
    organization2.setId("id2");
    organization2.setAttributes(OrganizationData.Attributes.builder().name("org2").build());

    doReturn(Arrays.asList(organization1, organization2)).when(terraformCloudTaskHelper).getAllOrganizations(any());

    Map<String, String> organizationsMap = terraformCloudTaskHelper.getOrganizationsMap(
        TerraformCloudConfig.builder().terraformCloudCredentials(credentials).build());

    verify(terraformCloudTaskHelper).getAllOrganizations(credentials);
    assertThat(organizationsMap.get("id1")).isEqualTo("org1");
    assertThat(organizationsMap.get("id2")).isEqualTo("org2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getLogs() throws IOException {
    LogCallback logCallback = mock(LogCallback.class);
    when(terraformCloudClient.getLogs(any(), anyInt(), anyInt()))
        .thenReturn("logLine1\nlogLine2\nlog")
        .thenReturn("Line3\nlogLine4\nlog")
        .thenReturn("line5" + (char) 3);

    taskHelper.streamLogs(logCallback, URL);

    verify(logCallback, times(5)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getLogsRequestFailsThenSuccessfulRetry() throws IOException {
    LogCallback logCallback = mock(LogCallback.class);
    when(terraformCloudClient.getLogs(any(), anyInt(), anyInt()))
        .thenReturn("logLine1\nlogLine2\nlog")
        .thenReturn("Line3\nlogLine4\nlog")
        .thenThrow(new TerraformCloudApiException("errorMessage", 400))
        .thenReturn("line5" + (char) 3);

    taskHelper.streamLogs(logCallback, URL);

    verify(logCallback, times(5)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void getLogsRequestFailsFiveTimes() throws IOException {
    LogCallback logCallback = mock(LogCallback.class);
    when(terraformCloudClient.getLogs(any(), anyInt(), anyInt()))
        .thenReturn("logLine1\nlogLine2\nlog")
        .thenReturn("Line3\nlogLine4\nlog")
        .thenThrow(new TerraformCloudApiException("errorMessage1", 400))
        .thenThrow(new TerraformCloudApiException("errorMessage2", 400))
        .thenThrow(new TerraformCloudApiException("errorMessage3", 400))
        .thenThrow(new TerraformCloudApiException("errorMessage4", 400))
        .thenThrow(new TerraformCloudApiException("errorMessage5", 400))
        .thenReturn("line5" + (char) 3);

    assertThatThrownBy(() -> taskHelper.streamLogs(logCallback, URL))
        .isInstanceOf(TerraformCloudApiException.class)
        .hasMessage("errorMessage5");

    verify(logCallback, times(4)).saveExecutionLog(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void createRunRefreshState() throws IOException {
    TerraformCloudTaskParams terraformCloudTaskParams =
        TerraformCloudTaskParams.builder()
            .workspace(WORKSPACE)
            .organization(ORG)
            .terraformCloudTaskType(TerraformCloudTaskType.RUN_REFRESH_STATE)
            .build();
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(getCreateRunResponse())
        .when(terraformCloudClient)
        .createRun(any(), any(), runRequestArgumentCaptor.capture());
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getPlanResponse()).when(terraformCloudClient).getPlan(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());

    RunData runData = taskHelper.createRun("url", "token", terraformCloudTaskParams, logCallback);

    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isRefreshOnly());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isAutoApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(runData.getId()).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateRunPlanOnly() throws IOException {
    TerraformCloudTaskParams terraformCloudTaskParams =
        TerraformCloudTaskParams.builder()
            .workspace(WORKSPACE)
            .organization(ORG)
            .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_ONLY)
            .build();

    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(getCreateRunResponse())
        .when(terraformCloudClient)
        .createRun(any(), any(), runRequestArgumentCaptor.capture());
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getPlanResponse()).when(terraformCloudClient).getPlan(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());

    RunData runData = taskHelper.createRun("url", "token", terraformCloudTaskParams, logCallback);

    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanOnly());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(runData.getId()).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateRunPlanAndApply() throws IOException {
    TerraformCloudTaskParams terraformCloudTaskParams =
        TerraformCloudTaskParams.builder()
            .workspace(WORKSPACE)
            .organization(ORG)
            .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_AND_APPLY)
            .build();
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(getCreateRunResponse())
        .when(terraformCloudClient)
        .createRun(any(), any(), runRequestArgumentCaptor.capture());
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getPlanResponse()).when(terraformCloudClient).getPlan(any(), any(), any());
    doReturn(getApplyResponse()).when(terraformCloudClient).getApply(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());

    RunData runData = taskHelper.createRun("url", "token", terraformCloudTaskParams, logCallback);

    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(runData.getId()).isEqualTo("run-123");
    verify(terraformCloudClient, times(1)).getApply(any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateRunPlanAndDestroy() throws IOException {
    TerraformCloudTaskParams terraformCloudTaskParams =
        TerraformCloudTaskParams.builder()
            .workspace(WORKSPACE)
            .organization(ORG)
            .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN_AND_DESTROY)
            .build();
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(getCreateRunResponse())
        .when(terraformCloudClient)
        .createRun(any(), any(), runRequestArgumentCaptor.capture());
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getPlanResponse()).when(terraformCloudClient).getPlan(any(), any(), any());
    doReturn(getApplyResponse()).when(terraformCloudClient).getApply(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());

    RunData runData = taskHelper.createRun("url", "token", terraformCloudTaskParams, logCallback);

    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(runData.getId()).isEqualTo("run-123");
    verify(terraformCloudClient, times(1)).getApply(any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateRunPlanWithForceExecuteRun() throws IOException {
    TerraformCloudTaskParams terraformCloudTaskParams = TerraformCloudTaskParams.builder()
                                                            .workspace(WORKSPACE)
                                                            .organization(ORG)
                                                            .accountId("accountId")
                                                            .entityId("entityId")
                                                            .planType(PlanType.APPLY)
                                                            .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN)
                                                            .build();
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(getCreateRunResponse())
        .when(terraformCloudClient)
        .createRun(any(), any(), runRequestArgumentCaptor.capture());
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getPlanResponse()).when(terraformCloudClient).getPlan(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());
    doReturn("json plan dummy").when(terraformCloudClient).getPlanJsonOutput(any(), any(), any());

    RunData runData = taskHelper.createRun("url", "token", terraformCloudTaskParams, logCallback);

    assertFalse(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(runData.getId()).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testApply() throws IOException {
    doReturn(getCreateRunResponse()).when(terraformCloudClient).getRun(any(), any(), any());
    doReturn(getApplyResponse()).when(terraformCloudClient).getApply(any(), any(), any());
    doReturn("log" + (char) 3).when(terraformCloudClient).getLogs(any(), anyInt(), anyInt());
    doReturn(getOutputResponse()).when(terraformCloudClient).getStateVersionOutputs(any(), any(), any(), anyInt());

    String output = taskHelper.applyRun("url", "token", "run-123", "message", logCallback);

    verify(terraformCloudClient, times(1)).applyRun(any(), any(), any(), any());

    assertThat(output).isEqualTo("{ \"x1\" : { \"value\" : {\"x1\":\"y1\"}, \"sensitive\" : false } }");
  }

  private TerraformCloudResponse getCreateRunResponse() {
    RunData runData = new RunData();
    runData.setRelationships(new HashMap<>());
    runData.setId("run-123");
    Relationship relationshipPlan = new Relationship();
    relationshipPlan.setData(Collections.singletonList(ResourceLinkage.builder().id("planId").build()));
    runData.getRelationships().put("plan", relationshipPlan);

    Relationship relationshipApply = new Relationship();
    relationshipApply.setData(Collections.singletonList(ResourceLinkage.builder().id("applyId").build()));
    runData.getRelationships().put("apply", relationshipApply);
    runData.setAttributes(Attributes.builder().status(RunStatus.policy_checked).build());
    return TerraformCloudResponse.builder().data(runData).build();
  }

  private TerraformCloudResponse getPlanResponse() {
    PlanData planData = new PlanData();
    planData.setAttributes(PlanData.Attributes.builder().logReadUrl("logUrl").build());
    return TerraformCloudResponse.builder().data(planData).build();
  }

  private TerraformCloudResponse getApplyResponse() {
    ApplyData applyData = new ApplyData();
    applyData.setRelationships(new HashMap<>());
    applyData.setAttributes(ApplyData.Attributes.builder().status("finished").build());
    Relationship relationshipCv = new Relationship();
    relationshipCv.setData(Collections.singletonList(ResourceLinkage.builder().id("cv-123").build()));
    applyData.getRelationships().put("state-versions", relationshipCv);
    return TerraformCloudResponse.builder().data(applyData).build();
  }

  private TerraformCloudResponse getOutputResponse() {
    StateVersionOutputData stateVersionOutputData = new StateVersionOutputData();
    stateVersionOutputData.setAttributes(
        StateVersionOutputData.Attributes.builder().name("x1").value(JsonUtils.readTree("{\"x1\" : \"y1\"}")).build());
    return TerraformCloudResponse.builder()
        .data(Collections.singletonList(stateVersionOutputData))
        .links(JsonUtils.readTree("{\"self\" : \"https:some.io\"}"))
        .build();
  }
}
