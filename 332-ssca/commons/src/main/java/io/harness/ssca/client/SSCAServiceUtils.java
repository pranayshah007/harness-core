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
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.ssca.client.beans.SscaAuthToken;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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
}
