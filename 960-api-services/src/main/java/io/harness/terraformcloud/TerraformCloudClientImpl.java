/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
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
  private static final String JSON_PLAN_OUTPUT_URL_PATTERN = "%s/api/v2/plans/%s/json-output";
  private static final String POLICY_CHECK_OUTPUT_URL_PATTERN = "%s/api/v2/policy-checks/%s/output";
  private static final String LOG_STREAM_URL_PATTERN = "%s?offset=%s&limit=%s";
  private static final long TIME_OUT = 30;

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
    HttpGet request = new HttpGet(String.format(JSON_PLAN_OUTPUT_URL_PATTERN, url, planId));
    request.addHeader("Authorization", getAuthorization(token));
    return executeHttpCall(request);
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
    HttpGet request = new HttpGet(String.format(POLICY_CHECK_OUTPUT_URL_PATTERN, url, policyCheckId));
    request.addHeader("Authorization", getAuthorization(token));
    return executeHttpCall(request);
  }

  @Override
  public TerraformCloudResponse<List<StateVersionOutputData>> getStateVersionOutputs(
      String url, String token, String stateVersionId, int page) throws IOException {
    Call<TerraformCloudResponse<List<StateVersionOutputData>>> call =
        getRestClient(url).getStateVersionOutputs(getAuthorization(token), stateVersionId, page);
    return executeRestCall(call);
  }

  @Override
  public String getLogs(String logsReadUrl, int offset, int limit) throws IOException {
    String url = String.format(LOG_STREAM_URL_PATTERN, logsReadUrl, offset, limit);
    return executeHttpCall(new HttpGet(url));
  }

  @Override
  public void overridePolicyChecks(String url, String token, String policyChecksId) throws IOException {
    Call<Void> call = getRestClient(url).overridePolicyChecks(getAuthorization(token), policyChecksId);
    executeRestCall(call);
  }

  @Override
  public TerraformCloudResponse<List<RunData>> getAppliedRuns(String url, String token, String workspaceId)
      throws IOException {
    Call<TerraformCloudResponse<List<RunData>>> call =
        getRestClient(url).getRunsByStatus(getAuthorization(token), workspaceId, "applied");
    return executeRestCall(call);
  }

  @VisibleForTesting
  TerraformCloudRestClient getRestClient(String url) {
    Retrofit retrofit = new Retrofit.Builder()
                            .client(getOkHttpClient(url))
                            .baseUrl(url)
                            .addConverterFactory(ScalarsConverterFactory.create())
                            .addConverterFactory(JacksonConverterFactory.create(
                                new ObjectMapper().enable(READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)))
                            .build();
    return retrofit.create(TerraformCloudRestClient.class);
  }

  @NotNull
  private OkHttpClient getOkHttpClient(String url) {
    return getOkHttpClientBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .retryOnConnectionFailure(true)
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

  private String executeHttpCall(HttpRequestBase request) throws IOException {
    String url = request.getURI().toURL().toString();
    log.info("Requesting: {}", url);
    String content;
    try (CloseableHttpClient httpClient = getHttpClient(url);
         CloseableHttpResponse response = httpClient.execute(request)) {
      HttpEntity entity = response.getEntity();
      if (response.getStatusLine().getStatusCode() == 200) {
        content = EntityUtils.toString(entity);
      } else {
        throw new TerraformCloudApiException(EntityUtils.toString(entity), response.getStatusLine().getStatusCode());
      }
    } catch (IOException e) {
      log.error("Error executing http call", e);
      throw e;
    }
    return content;
  }

  @VisibleForTesting
  CloseableHttpClient getHttpClient(String url) {
    RequestConfig requestConfig = RequestConfig.custom()
                                      .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(TIME_OUT))
                                      .setSocketTimeout((int) TimeUnit.SECONDS.toMillis(TIME_OUT))
                                      .build();
    HttpClientBuilder httpClientBuilder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
    setProxyIfRequired(url, httpClientBuilder);
    return httpClientBuilder.build();
  }

  private void setProxyIfRequired(String url, HttpClientBuilder httpClientBuilder) {
    HttpHost proxyHost = Http.getHttpProxyHost();
    if (proxyHost != null && !Http.shouldUseNonProxy(url)) {
      if (isNotEmpty(Http.getProxyUserName())) {
        httpClientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost),
            new UsernamePasswordCredentials(Http.getProxyUserName(), Http.getProxyPassword()));
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
      }
      httpClientBuilder.setProxy(proxyHost);
    }
  }

  private String getAuthorization(@NonNull String token) {
    return "Bearer " + token;
  }
}
