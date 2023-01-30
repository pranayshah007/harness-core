/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDP)
public class TerraformCloudImplTest extends CategoryTest {
  private static String URL = "http://localhost";
  private static String TOKEN = "token";

  TerraformCloudClientImpl terraformCloudClient = spy(new TerraformCloudClientImpl());
  TerraformCloudRestClient terraformCloudRestClient = mock(TerraformCloudRestClient.class);

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(terraformCloudRestClient).when(terraformCloudClient).getRestClient(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListOrganizations() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse =
        TerraformCloudResponse.builder().data(Collections.singletonList(new OrganizationData())).build();
    doReturn(call).when(terraformCloudRestClient).listOrganizations(any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<List<OrganizationData>> response = terraformCloudClient.listOrganizations(URL, TOKEN);

    verify(terraformCloudRestClient).listOrganizations(eq("Bearer " + TOKEN));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListOrganizationsUnsuccessful() throws IOException {
    Call call = mock(Call.class);
    doReturn(call).when(terraformCloudRestClient).listOrganizations(any());
    doReturn(getErrorResponse(401, "errorContent")).when(call).execute();

    assertThatThrownBy(() -> terraformCloudClient.listOrganizations(URL, TOKEN))
        .isInstanceOf(TerraformCloudApiException.class)
        .hasMessage("errorContent");

    verify(terraformCloudRestClient).listOrganizations(eq("Bearer " + TOKEN));
    verify(call).execute();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListOrganizationsExceptionThrownDuringRequest() throws IOException {
    Exception exception = new RuntimeException("test");
    Call call = mock(Call.class);
    doReturn(call).when(terraformCloudRestClient).listOrganizations(any());
    doThrow(exception).when(call).execute();

    assertThatThrownBy(() -> terraformCloudClient.listOrganizations(URL, TOKEN)).isEqualTo(exception);

    verify(terraformCloudRestClient).listOrganizations(eq("Bearer " + TOKEN));
    verify(call).execute();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListWorkspaces() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse =
        TerraformCloudResponse.builder().data(Collections.singletonList(new WorkspaceData())).build();
    doReturn(call).when(terraformCloudRestClient).listWorkspaces(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<List<WorkspaceData>> response =
        terraformCloudClient.listWorkspaces(URL, TOKEN, "organization");

    verify(terraformCloudRestClient).listWorkspaces(eq("Bearer " + TOKEN), eq("organization"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCreateRun() throws IOException {
    RunRequest runRequest = RunRequest.builder().build();
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse = TerraformCloudResponse.builder().data(new RunData()).build();
    doReturn(call).when(terraformCloudRestClient).createRun(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<RunData> response = terraformCloudClient.createRun(URL, TOKEN, runRequest);

    verify(terraformCloudRestClient).createRun(eq("Bearer " + TOKEN), eq(runRequest));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRun() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse = TerraformCloudResponse.builder().data(new RunData()).build();
    doReturn(call).when(terraformCloudRestClient).getRun(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<RunData> response = terraformCloudClient.getRun(URL, TOKEN, "runId");

    verify(terraformCloudRestClient).getRun(eq("Bearer " + TOKEN), eq("runId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPlan() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse = TerraformCloudResponse.builder().data(new PlanData()).build();
    doReturn(call).when(terraformCloudRestClient).getPlan(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<PlanData> response = terraformCloudClient.getPlan(URL, TOKEN, "planId");

    verify(terraformCloudRestClient).getPlan(eq("Bearer " + TOKEN), eq("planId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPlanJsonOutput() throws IOException {
    Call call = mock(Call.class);
    String expectedResponse = "jsonOutput";
    doReturn(call).when(terraformCloudRestClient).getPlanJsonOutput(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    String response = terraformCloudClient.getPlanJsonOutput(URL, TOKEN, "planId");

    verify(terraformCloudRestClient).getPlanJsonOutput(eq("Bearer " + TOKEN), eq("planId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetApply() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse = TerraformCloudResponse.builder().data(new ApplyData()).build();
    doReturn(call).when(terraformCloudRestClient).getApply(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<ApplyData> response = terraformCloudClient.getApply(URL, TOKEN, "applyId");

    verify(terraformCloudRestClient).getApply(eq("Bearer " + TOKEN), eq("applyId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testListPolicyChecks() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse =
        TerraformCloudResponse.builder().data(Collections.singletonList(new PolicyCheckData())).build();
    doReturn(call).when(terraformCloudRestClient).listPolicyChecks(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<List<PolicyCheckData>> response = terraformCloudClient.listPolicyChecks(URL, TOKEN, "runId");

    verify(terraformCloudRestClient).listPolicyChecks(eq("Bearer " + TOKEN), eq("runId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPolicyCheckOutput() throws IOException {
    Call call = mock(Call.class);
    String expectedResponse = "policyCheckOutput";
    doReturn(call).when(terraformCloudRestClient).getPolicyCheckOutput(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    String response = terraformCloudClient.getPolicyCheckOutput(URL, TOKEN, "policyCheckId");

    verify(terraformCloudRestClient).getPolicyCheckOutput(eq("Bearer " + TOKEN), eq("policyCheckId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStateVersionOutputs() throws IOException {
    Call call = mock(Call.class);
    TerraformCloudResponse expectedResponse =
        TerraformCloudResponse.builder().data(Collections.singletonList(new StateVersionOutputData())).build();
    doReturn(call).when(terraformCloudRestClient).getStateVersionOutputs(any(), any());
    doReturn(Response.success(expectedResponse)).when(call).execute();

    TerraformCloudResponse<List<StateVersionOutputData>> response =
        terraformCloudClient.getStateVersionOutputs(URL, TOKEN, "stateVersionId");

    verify(terraformCloudRestClient).getStateVersionOutputs(eq("Bearer " + TOKEN), eq("stateVersionId"));
    verify(call).execute();
    assertThat(response).isEqualTo(expectedResponse);
  }

  @NotNull
  private Response<Object> getErrorResponse(int status, String content) {
    return Response.error(ResponseBody.create(MediaType.parse("application/vnd.api+json"), content),
        new okhttp3.Response.Builder()
            .message("message")
            .code(status)
            .protocol(Protocol.HTTP_1_1)
            .request(new Request.Builder().url("http://localhost/").build())
            .build());
  }
}