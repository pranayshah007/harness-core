/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.RunActionRequest;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class TerraformCloudClientImpl implements TerraformCloudClient {
  static final long TIME_OUT = 60;

  @Override
  public TerraformCloudResponse<List<OrganizationData>> listOrganizations(String url, String token, int page)
      throws IOException {
    Call<TerraformCloudResponse<List<OrganizationData>>> call =
        getRestClient(url).listOrganizations(getAuthorization(token), page);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<List<WorkspaceData>> listWorkspaces(
      String url, String token, String organization, int page) throws IOException {
    Call<TerraformCloudResponse<List<WorkspaceData>>> call =
        getRestClient(url).listWorkspaces(getAuthorization(token), organization, page);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<RunData> createRun(String url, String token, RunRequest request) throws IOException {
    Call<TerraformCloudResponse<RunData>> call = getRestClient(url).createRun(getAuthorization(token), request);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<RunData> getRun(String url, String token, String runId) throws IOException {
    Call<TerraformCloudResponse<RunData>> call = getRestClient(url).getRun(getAuthorization(token), runId);
    return executeRestCall(call);
  }

  @Override
  public void applyRun(String url, String token, String runId, RunActionRequest request) throws IOException {
    Call<Void> call = getRestClient(url).applyRun(getAuthorization(token), runId, request);
    executeRestCall(call);
  }

  @Override
  public void discardRun(String url, String token, String runId, RunActionRequest request) throws IOException {
    Call<Void> call = getRestClient(url).discardRun(getAuthorization(token), runId, request);
    executeRestCall(call);
  }

  @Override
  public void forceExecuteRun(String url, String token, String runId) throws IOException {
    Call<Void> call = getRestClient(url).forceExecuteRun(getAuthorization(token), runId);
    executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<PlanData> getPlan(String url, String token, String planId) throws IOException {
    Call<TerraformCloudResponse<PlanData>> call = getRestClient(url).getPlan(getAuthorization(token), planId);
    return executeRestCall(call);
  }

  @Override
  public String getPlanJsonOutput(String url, String token, String planId) throws IOException {
    Call<String> call = getRestClient(url).getPlanJsonOutput(getAuthorization(token), planId);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<ApplyData> getApply(String url, String token, String applyId) throws IOException {
    Call<TerraformCloudResponse<ApplyData>> call = getRestClient(url).getApply(getAuthorization(token), applyId);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<List<PolicyCheckData>> listPolicyChecks(
      String url, String token, String runId, int page) throws IOException {
    Call<TerraformCloudResponse<List<PolicyCheckData>>> call =
        getRestClient(url).listPolicyChecks(getAuthorization(token), runId, page);
    return executeRestCall(call);
  }

  @Override
  public String getPolicyCheckOutput(String url, String token, String policyCheckId) throws IOException {
    Call<String> call = getRestClient(url).getPolicyCheckOutput(getAuthorization(token), policyCheckId);
    return executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<List<StateVersionOutputData>> getStateVersionOutputs(
      String url, String token, String stateVersionId) throws IOException {
    Call<TerraformCloudResponse<List<StateVersionOutputData>>> call =
        getRestClient(url).getStateVersionOutputs(getAuthorization(token), stateVersionId);
    return executeRestCall(call);
  }

  @VisibleForTesting
  TerraformCloudRestClient getRestClient(String url) {
    Retrofit retrofit = new Retrofit.Builder()
                            .client(getHttpClient(url))
                            .baseUrl(url)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(TerraformCloudRestClient.class);
  }

  @NotNull
  private OkHttpClient getHttpClient(String url) {
    return getOkHttpClientBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .build();
  }

  private <T> T executeRestCall(Call<T> call) throws IOException {
    Response<T> response = null;
    try {
      log.info("Requesting: {}", call.request().url().url());
      response = call.execute();
      if (!response.isSuccessful() && response.errorBody() != null) {
        throw new TerraformCloudApiException(response.errorBody().string(), response.code());
      }
      return response.body();
    } catch (Exception e) {
      log.error("Error executing rest call", e);
      throw e;
    } finally {
      if (response != null && !response.isSuccessful() && response.errorBody() != null) {
        String errorResponse = response.errorBody().string();
        log.error("Received Error TerraformCloudResponse: {}", errorResponse);
        response.errorBody().close();
      }
    }
  }

  private String getAuthorization(@NonNull String token) {
    return "Bearer " + token;
  }
}
