/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.iacm.beans.entities.IACMServiceConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
@OwnedBy(HarnessTeam.IACM)
public class IACMServiceUtils {
  private final IACMServiceClient iacmServiceClient;
  private final IACMServiceConfig iacmServiceConfig;

  @Inject
  public IACMServiceUtils(IACMServiceClient iacmServiceClient, IACMServiceConfig iacmServiceConfig) {
    this.iacmServiceClient = iacmServiceClient;
    this.iacmServiceConfig = iacmServiceConfig;
  }

  public String getIACMConnector(String stackID) {
    log.info("Initiating token request to IACM service: {}", this.iacmServiceConfig.getBaseUrl());
    Call<JsonObject> connectorCall = iacmServiceClient.getStackInfo(stackID, this.iacmServiceConfig.getGlobalToken());
    Response<JsonObject> response = null;

    try {
      response = connectorCall.execute();
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Connector request to IACM service call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not retrieve IACM Connector from the IACM service. status code = %s, message = %s, response = %s",
          response.code(), response.message(), response.errorBody() == null ? "null" : errorBody));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM Connector from the IACM service. Response body is null");
    }
    JsonElement providerConnector = response.body().get("provider_connector");
    if (providerConnector == null) {
      throw new GeneralException("Could not retrieve IACM Connector from the IACM service. The StackID: " + stackID
          + " doesn't have a connector reference");
    }
    return providerConnector.getAsString();
  }

  @NotNull
  public String getIACMServiceToken(String accountID) {
    log.info("Initiating token request to IACM service: {}", this.iacmServiceConfig.getBaseUrl());
    Call<JsonObject> tokenCall = iacmServiceClient.generateToken(accountID, this.iacmServiceConfig.getGlobalToken());
    Response<JsonObject> response = null;
    try {
      response = tokenCall.execute();
    } catch (IOException e) {
      throw new GeneralException("Token request to IACM service call failed", e);
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
          "Could not fetch token from IACM service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not fetch token from IACM service. Response body is null");
    }
    return response.body().get("token").getAsString();
  }
}
