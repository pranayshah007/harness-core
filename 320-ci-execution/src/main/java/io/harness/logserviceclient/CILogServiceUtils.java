/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.exception.GeneralException;

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
public class CILogServiceUtils {
  private final CILogServiceClient ciLogServiceClient;
  private final LogServiceConfig logServiceConfig;

  @Inject
  public CILogServiceUtils(CILogServiceClient logServiceClient, LogServiceConfig logServiceConfig) {
    this.ciLogServiceClient = logServiceClient;
    this.logServiceConfig = logServiceConfig;
  }

  @NotNull
  public String getLogServiceToken(String accountID) {
    log.info("Initiating token request to log service: {}", this.logServiceConfig.getBaseUrl());
    Call<String> tokenCall = ciLogServiceClient.generateToken(accountID, this.logServiceConfig.getGlobalToken());
    Response<String> response = null;
    try {
      response = tokenCall.execute();
    } catch (IOException e) {
      throw new GeneralException("Token request to log service call failed", e);
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
          "Could not fetch token from log service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }
    return response.body();
  }

  @NotNull
  public void closeLogStream(String accountID, String key, boolean snapshot, boolean prefix) {
    log.info("Calling log service to close stream for the key: {} with snapshot mode: {} and prefix: {}", key, snapshot,
        prefix);
    Call<Void> closeStreamCall =
        ciLogServiceClient.closeLogStream(accountID, key, snapshot, prefix, getLogServiceToken(accountID));

    Response<Void> response = null;
    try {
      response = closeStreamCall.execute();
    } catch (IOException e) {
      log.warn("Close call to log service failed", e);
      return;
    }

    // Received error from the server
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.warn("Could not read error body {}", response.errorBody());
      }

      log.warn(String.format("Response for log service close call: status code = %s, message = %s, response = %s",
          response.code(), response.message() == null ? "null" : response.message(),
          response.errorBody() == null ? "null" : errorBody));
    }
  }
}
