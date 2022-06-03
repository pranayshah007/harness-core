/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stoserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.sto.beans.entities.STOServiceConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class STOServiceUtils {
  private final STOServiceClient stoServiceClient;
  private final STOServiceConfig stoServiceConfig;

  @Inject
  public STOServiceUtils(STOServiceClient stoServiceClient, STOServiceConfig stoServiceConfig) {
    this.stoServiceClient = stoServiceClient;
    this.stoServiceConfig = stoServiceConfig;
  }

  @NotNull
  public String getSTOServiceToken(String accountID) {
    log.info("Initiating token request to STO service: {}", this.stoServiceConfig.getBaseUrl());
    Call<String> tokenCall = stoServiceClient.generateToken(accountID, this.stoServiceConfig.getGlobalToken());
    Response<String> response = null;
    try {
      response = tokenCall.execute();
    } catch (IOException e) {
      throw new GeneralException("Token request to STO service call failed", e);
    }

    // Received error from the server
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not fetch token from STO service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }
    return response.body();
  }
}
