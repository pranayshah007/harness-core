/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import static io.harness.rule.OwnerRule.NGONZALEZ;

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
import io.harness.MockableTestMixin;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.entities.Workspace;
import io.harness.beans.entities.WorkspaceVariables;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.rule.LifecycleRule;
import io.harness.rule.Owner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

public class IACMServiceUtilsTest extends CategoryTest implements MockableTestMixin {
  @Mock private IACMServiceClient iacmServiceClient;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String ACCOUNT_ID = "account";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMServiceTokenSuccess() throws Exception {
    String globalToken = "token";
    JsonObject iacmServiceTokenResponse = new JsonObject();
    iacmServiceTokenResponse.addProperty("token", "iacm-token");
    String iacmServiceToken = "iacm-token";
    Call<JsonObject> iacmServiceTokenCall = mock(Call.class);
    when(iacmServiceTokenCall.execute()).thenReturn(Response.success(iacmServiceTokenResponse));
    when(iacmServiceClient.generateToken(eq(ACCOUNT_ID), eq(globalToken))).thenReturn(iacmServiceTokenCall);
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());

    String token = iacmServiceUtils.getIACMServiceToken(ACCOUNT_ID);
    assertThat(token).isEqualTo(iacmServiceToken);
    verify(iacmServiceTokenCall, times(1)).execute();
    verify(iacmServiceClient, times(1)).generateToken(eq(ACCOUNT_ID), eq(globalToken));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMServiceTokenFailure() throws Exception {
    String globalToken = "token";
    Call<JsonObject> iacmServiceTokenCall = mock(Call.class);
    when(iacmServiceTokenCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    when(iacmServiceClient.generateToken(eq(ACCOUNT_ID), eq(globalToken))).thenReturn(iacmServiceTokenCall);
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    assertThatThrownBy(() -> iacmServiceUtils.getIACMServiceToken(ACCOUNT_ID)).isInstanceOf(GeneralException.class);
    verify(iacmServiceTokenCall, times(3)).execute();
    verify(iacmServiceClient, times(3)).generateToken(eq(ACCOUNT_ID), eq(globalToken));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExceptionsInGetIACMWorkspaceInfo() throws IOException {
    Call<JsonObject> connectorCall = mock(Call.class);
    when(iacmServiceClient.getWorkspaceInfo(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(connectorCall);
    when(connectorCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    assertThatThrownBy(() -> iacmServiceUtils.getIACMWorkspaceInfo("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Workspace Info request to IACM service call failed");

    Call<JsonObject> connectorCall2 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceInfo(any(), any(), any(), any(), any())).thenReturn(connectorCall2);
    Response<JsonObject> response = Response.error(500, getResponse(500));
    when(connectorCall2.execute()).thenReturn(response);
    assertThatThrownBy(() -> iacmServiceUtils.getIACMWorkspaceInfo("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Could not retrieve IACM workspace info from the IACM service. status code");

    Call<JsonObject> connectorCall3 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceInfo(any(), any(), any(), any(), any())).thenReturn(connectorCall3);
    response = Response.success(200, null);
    when(connectorCall3.execute()).thenReturn(response);
    assertThatThrownBy(() -> iacmServiceUtils.getIACMWorkspaceInfo("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Could not retrieve IACM workspace info from the IACM service. Response body is null");

    Call<JsonObject> connectorCall5 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceInfo(any(), any(), any(), any(), any())).thenReturn(connectorCall5);
    response = Response.success(200, JsonParser.parseString("{name:bar}").getAsJsonObject());
    when(connectorCall5.execute()).thenReturn(response);
    assertThatThrownBy(() -> iacmServiceUtils.getIACMWorkspaceInfo("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Could not retrieve IACM Connector from the IACM service. The WorkspaceID:");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMWorkspaceInfo() throws IOException {
    Call<JsonObject> connectorCall5 = mock(Call.class);
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    when(iacmServiceClient.getWorkspaceInfo(any(), any(), any(), any(), any())).thenReturn(connectorCall5);
    Response<JsonObject> response =
        Response.success(200, JsonParser.parseString("{provider_connector:bar}").getAsJsonObject());
    when(connectorCall5.execute()).thenReturn(response);
    Workspace workspace = iacmServiceUtils.getIACMWorkspaceInfo("a", "b", "c", "d");
    assertThat(workspace.getProvider_connector()).isEqualTo("bar");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExceptionsInGetIacmWorkspaceEnvs() throws IOException {
    Call<JsonArray> connectorCall = mock(Call.class);
    when(iacmServiceClient.getWorkspaceVariables(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(connectorCall);
    when(connectorCall.execute()).thenThrow(new IOException("Got error while trying to process!"));
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    assertThatThrownBy(() -> iacmServiceUtils.getIacmWorkspaceEnvs("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Error retrieving the variables from the IACM service. Call failed");

    Call<JsonArray> connectorCall2 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceVariables(any(), any(), any(), any(), any())).thenReturn(connectorCall2);
    Response<JsonArray> response = Response.error(500, getResponse(500));
    when(connectorCall2.execute()).thenReturn(response);
    assertThatThrownBy(() -> iacmServiceUtils.getIacmWorkspaceEnvs("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Could not parse body for the env retrieval response ");

    Call<JsonArray> connectorCall3 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceVariables(any(), any(), any(), any(), any())).thenReturn(connectorCall3);
    response = Response.success(200, null);
    when(connectorCall3.execute()).thenReturn(response);
    assertThatThrownBy(() -> iacmServiceUtils.getIacmWorkspaceEnvs("a", "b", "c", "d"))
        .isInstanceOf(GeneralException.class)
        .hasMessageContaining("Could not retrieve IACM variables from the IACM service. Response body is null");

    Call<JsonArray> connectorCall5 = mock(Call.class);
    when(iacmServiceClient.getWorkspaceVariables(any(), any(), any(), any(), any())).thenReturn(connectorCall5);
    response = Response.success(200, JsonParser.parseString("[]").getAsJsonArray());
    when(connectorCall5.execute()).thenReturn(response);
    iacmServiceUtils.getIacmWorkspaceEnvs("a", "b", "c", "d");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmWorkspaceEnvs() throws IOException {
    Call<JsonArray> connectorCall5 = mock(Call.class);
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    when(iacmServiceClient.getWorkspaceVariables(any(), any(), any(), any(), any())).thenReturn(connectorCall5);
    Response<JsonArray> response =
        Response.success(200, JsonParser.parseString("[{key:\"value\"}, {key:\"value2\"}]").getAsJsonArray());
    when(connectorCall5.execute()).thenReturn(response);
    WorkspaceVariables[] vars = iacmServiceUtils.getIacmWorkspaceEnvs("a", "b", "c", "d");
    assertThat(vars.length).isEqualTo(2);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIACMResources() throws IOException {
    Call<JsonObject> connectorCall5 = mock(Call.class);
    IACMServiceUtils iacmServiceUtils = new IACMServiceUtils(iacmServiceClient, createServiceConfig());
    when(iacmServiceClient.getWorkspaceResoures(any(), any(), any(), any(), any())).thenReturn(connectorCall5);
    Response<JsonObject> response = Response.success(200,
        JsonParser
            .parseString("{\n"
                + "    \"resources\": [\n"
                + "        {}\n"
                + "    ],\n"
                + "    \"outputs\": [\n"
                + "        {\n"
                + "            \"name\": \"app1\",\n"
                + "            \"value\": \"ami-1\",\n"
                + "            \"sensitive\": true\n"
                + "        },\n"
                + "        {\n"
                + "            \"name\": \"app2\",\n"
                + "            \"value\": \"ami-2\",\n"
                + "            \"sensitive\": false\n"
                + "        },\n"
                + "        {\n"
                + "            \"name\": \"app3\",\n"
                + "            \"value\": \"ami-3\",\n"
                + "            \"sensitive\": false\n"
                + "        }\n"
                + "    ]\n"
                + "}")
            .getAsJsonObject());
    when(connectorCall5.execute()).thenReturn(response);
    Map<String, String> vars = iacmServiceUtils.getIacmWorkspaceOutputs("a", "b", "c", "d");
    assertThat(vars.size()).isEqualTo(3);
    assertThat(vars.get("app1")).isEqualTo("ami-1");
    assertThat(vars.get("app2")).isEqualTo("ami-2");
    assertThat(vars.get("app3")).isEqualTo("ami-3");
  }

  private ResponseBody getResponse(int code) {
    Request request = new Request.Builder().url("https://dummyurl.com").method("GET", null).build();
    okhttp3.Response response = new okhttp3.Response.Builder()
                                    .code(code)
                                    .body(ResponseBody.create(MediaType.parse("text/plain"), "aaaa"))
                                    .request(request)
                                    .protocol(Protocol.HTTP_1_1)
                                    .message("OK")
                                    .build();
    return response.body();
  }

  private IACMServiceConfig createServiceConfig() {
    String baseUrl = "http://localhost:4000";
    String globalToken = "token";
    return IACMServiceConfig.builder().globalToken(globalToken).baseUrl(baseUrl).build();
  }
}