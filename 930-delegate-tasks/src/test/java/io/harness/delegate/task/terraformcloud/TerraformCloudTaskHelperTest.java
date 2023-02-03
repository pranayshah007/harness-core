/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.terraformcloud.TerraformCloudApiException;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTaskHelperTest {
  private static String URL = "url";
  private static String TOKEN = "token";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private TerraformCloudClient terraformCloudClient;
  @InjectMocks private TerraformCloudTaskHelper taskHelper;

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
}
