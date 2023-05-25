/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ssca.beans.SscaExecutionConstants;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.ssca.client.beans.SscaAuthToken;
import io.harness.ssca.client.beans.enforcement.SscaEnforcementSummary;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.SSCA)
public class SSCAServiceUtils {
  private final SSCAServiceConfig sscaServiceConfig;
  private final SSCAServiceClient sscaServiceClient;

  @Inject
  public SSCAServiceUtils(SSCAServiceConfig sscaServiceConfig, SSCAServiceClient sscaServiceClient) {
    this.sscaServiceConfig = sscaServiceConfig;
    this.sscaServiceClient = sscaServiceClient;
  }

  public String getSscaServiceToken(String accountId, String orgId, String projectId) {
    Call<SscaAuthToken> call = sscaServiceClient.generateAuthToken(accountId, orgId, projectId);
    Response<SscaAuthToken> response;
    try {
      response = call.execute();
    } catch (IOException ex) {
      throw new GeneralException("Token request to ssca core service failed", ex);
    }

    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not fetch token from SSCA service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }
    if (response.body() == null) {
      throw new GeneralException("Could not fetch token from SSCA service. Response body is null");
    }

    return response.body().getToken();
  }

  public Map<String, String> getSSCAServiceEnvVariables(String accountId, String orgId, String projectId) {
    Map<String, String> envVars = new HashMap<>();
    final String sscaServiceBaseUrl = sscaServiceConfig.getHttpClientConfig().getBaseUrl();

    String sscaServiceToken = "token";

    // Make a call to the SSCA service and get back the token.
    try {
      sscaServiceToken = getSscaServiceToken(accountId, orgId, projectId);
    } catch (Exception e) {
      log.error("Could not call token endpoint for SSCA service", e);
    }

    envVars.put(SscaExecutionConstants.SSCA_SERVICE_TOKEN_VARIABLE, sscaServiceToken);
    envVars.put(SscaExecutionConstants.SSCA_SERVICE_ENDPOINT_VARIABLE, sscaServiceBaseUrl);

    return envVars;
  }

  public SscaEnforcementSummary getEnforcementSummary(String stepExecutionId) {
    Call<SscaEnforcementSummary> call = sscaServiceClient.getEnforcementSummary(stepExecutionId);
    Response<SscaEnforcementSummary> response;
    try {
      response = call.execute();
    } catch (IOException ex) {
      throw new GeneralException("Enforcement Summary Request to SSCA core service failed", ex);
    }

    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new CIStageExecutionException(String.format(
          "Could not fetch enforcement summary from SSCA service. status code = %s, message = %s, response = %s",
          response.code(), response.message() == null ? "null" : response.message(),
          response.errorBody() == null ? "null" : errorBody));
    }
    if (response.body() == null) {
      throw new CIStageExecutionException(
          "Could not fetch enforcement summary from SSCA service. Response body is null");
    }

    return response.body();
  }
}
