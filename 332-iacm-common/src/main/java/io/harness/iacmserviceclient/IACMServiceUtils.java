/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.entities.Stack;
import io.harness.beans.entities.StackVariables;
import io.harness.exception.GeneralException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
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

  public Stack getIACMStackInfo(String org, String projectId, String accountId, String stackID) {
    log.info("Initiating token request to IACM service: {}", this.iacmServiceConfig.getBaseUrl());
    String globalToken = this.iacmServiceConfig.getGlobalToken();
    Call<JsonObject> connectorCall = iacmServiceClient.getStackInfo(org, projectId, stackID, globalToken, accountId);
    Response<JsonObject> response;

    try {
      response = connectorCall.execute();
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Stack Info request to IACM service call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not retrieve IACM stack info from the IACM service. status code = %s, message = %s, response = %s",
          response.code(), response.message(), response.errorBody() == null ? "null" : errorBody));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM stack info from the IACM service. Response body is null");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    Stack stack;
    try {
      stack = objectMapper.readValue(response.body().toString(), Stack.class);
    } catch (JsonProcessingException ex) {
      log.error("Could not parse json body {}", response.body().toString());
      throw new GeneralException("Could not parse stack response. Please contact Harness Support for more information");
    }
    if (stack.getProvider_connector() == null) {
      throw new GeneralException("Could not retrieve IACM Connector from the IACM service. The StackID: "
          + stack.getSlug() + " doesn't have a connector reference");
    }
    return stack;
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

  public StackVariables[] getIacmStackEnvs(String org, String projectId, String accountId, String stackID) {
    log.info("Initiating request to IACM service for env retrieval: {}", this.iacmServiceConfig.getBaseUrl());
    Call<JsonArray> connectorCall = iacmServiceClient.getStackVariables(
        org, projectId, stackID, this.iacmServiceConfig.getGlobalToken(), accountId);
    Response<JsonArray> response;

    try {
      response = connectorCall.execute();
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Error retrieving the variables from the IACM service. Call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(
          String.format("Could not parse body for the env retrieval response = %s, message = %s, response = %s",
              response.code(), response.message(), response.errorBody() == null ? "null" : errorBody));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM variables from the IACM service. Response body is null");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    StackVariables[] vars;
    try {
      vars = objectMapper.readValue(response.body().toString(), StackVariables[].class);
    } catch (JsonProcessingException ex) {
      log.error("Could not parse json body {}", response.body().toString());
      throw new GeneralException(
          "Could not parse variables response. Please contact Harness Support for more information");
    }
    if (vars.length == 0) {
      log.info("Could not retrieve IACM variables from the IACM service.");
    }
    return vars;
  }
}
