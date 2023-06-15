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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final String DEFAULT_PAGE_SIZE = "100";
  private final STOServiceClient stoServiceClient;
  private final STOServiceConfig stoServiceConfig;

  @Inject
  public STOServiceUtils(STOServiceClient stoServiceClient, STOServiceConfig stoServiceConfig) {
    this.stoServiceClient = stoServiceClient;
    this.stoServiceConfig = stoServiceConfig;
  }

  @NotNull
  public String getSTOServiceToken(String accountId) {
    log.info("Initiating token request to STO service: {}", this.stoServiceConfig.getInternalUrl());
    JsonObject responseBody;
    if (accountId == null) {
      responseBody = makeAPICall(stoServiceClient.generateTokenAllAccounts(this.stoServiceConfig.getGlobalToken()));
    } else {
      responseBody = makeAPICall(stoServiceClient.generateToken(accountId, this.stoServiceConfig.getGlobalToken()));
    }

    if (responseBody.has("token")) {
      return responseBody.get("token").getAsString();
    }

    log.error("Response from STO service doesn't contain token information: {}", responseBody);

    return "";
  }

  @NotNull
  public Map<String, String> getOutputVariables(
      String accountId, String orgId, String projectId, String executionId, String stageId, String stepId) {
    log.info("Initiating output variables request to STO service: {}", this.stoServiceConfig.getInternalUrl());

    Map<String, String> result = new HashMap<>();
    String token = getSTOServiceToken(accountId);
    String accessToken = "ApiKey " + token;

    if ("".equals(token)) {
      result.put("ERROR_MESSAGE", "Failed to authenticate with STO");
      return result;
    }

    int totalPages = 0;
    JsonObject matchingScan = null;

    for (int page = 0; page <= totalPages && matchingScan == null; page++) {
      JsonObject scansResponseBody = makeAPICall(
          stoServiceClient.getScans(accessToken, accountId, executionId, String.valueOf(page), DEFAULT_PAGE_SIZE));

      if (scansResponseBody == null) {
        break;
      }

      // Update totalPages after first API call
      if (page == 0) {
        if (scansResponseBody.has("pagination")) {
          JsonObject paginationObject = scansResponseBody.getAsJsonObject("pagination");
          if (paginationObject != null && paginationObject.has("totalPages")) {
            JsonElement totalPagesElement = paginationObject.get("totalPages");
            if (totalPagesElement.isJsonPrimitive() && ((JsonPrimitive) totalPagesElement).isNumber()) {
              totalPages = totalPagesElement.getAsInt() - 1;
            }
          }
        }
      }

      JsonArray scanResultsArray = scansResponseBody.getAsJsonArray("results");

      for (JsonElement jsonElement : scanResultsArray) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        if (jsonObject.has("stepId") && jsonObject.has("stageId")) {
          String scanStepId = jsonObject.get("stepId").getAsString();
          String scanStageId = jsonObject.get("stageId").getAsString();
          String scanExecutionId = jsonObject.get("executionId").getAsString();
          if (scanStageId.equals(stageId) && scanStepId.equals(stepId) && scanExecutionId.equals(executionId)) {
            matchingScan = jsonObject;
            break;
          }
        }
      }
    }

    if (matchingScan == null) {
      result.put("ERROR_MESSAGE", "Scan results were not found");
      return result;
    }

    String scanId = matchingScan.get("id").getAsString();
    String scanStatus = matchingScan.get("status").getAsString();

    JsonObject responseBody =
        makeAPICall(stoServiceClient.getOutputVariables(accessToken, scanId, accountId, orgId, projectId));

    result.put("JOB_ID", scanId);
    result.put("JOB_STATUS", scanStatus);
    result.put("CRITICAL", responseBody.get("critical").getAsString());
    result.put("HIGH", responseBody.get("high").getAsString());
    result.put("MEDIUM", responseBody.get("medium").getAsString());
    result.put("LOW", responseBody.get("low").getAsString());
    result.put("INFO", responseBody.get("info").getAsString());
    result.put("UNASSIGNED", responseBody.get("unassigned").getAsString());
    result.put("TOTAL",
        getTotalFromResponse(responseBody, Arrays.asList("critical", "high", "medium", "low", "info", "unassigned")));
    result.put("NEW_CRITICAL", responseBody.get("newCritical").getAsString());
    result.put("NEW_HIGH", responseBody.get("newHigh").getAsString());
    result.put("NEW_MEDIUM", responseBody.get("newMedium").getAsString());
    result.put("NEW_LOW", responseBody.get("newLow").getAsString());
    result.put("NEW_INFO", responseBody.get("newInfo").getAsString());
    result.put("NEW_UNASSIGNED", responseBody.get("newUnassigned").getAsString());
    result.put("NEW_TOTAL",
        getTotalFromResponse(
            responseBody, Arrays.asList("newCritical", "newHigh", "newMedium", "newLow", "newInfo", "newUnassigned")));

    return result;
  }

  @NotNull
  public String getUsageAllAccounts(long timestamp) {
    String token = getSTOServiceToken(null);
    String accessToken = "ApiKey " + token;

    return makeAPICall(stoServiceClient.getUsageAllAccounts(accessToken, String.valueOf(timestamp)))
        .get("usage")
        .toString();
  }

  private JsonObject makeAPICall(Call<JsonObject> apiCall) {
    Response<JsonObject> response = null;
    try {
      response = apiCall.execute();
    } catch (IOException e) {
      throw new GeneralException("API request to STO service call failed", e);
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
          "API call to STO service failed. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }

    if (response.body() == null) {
      throw new GeneralException("Cannot compleete API call to STO service. Response body is null");
    }

    return response.body();
  }

  private String getTotalFromResponse(JsonObject responseBody, List<String> severities) {
    Integer total = severities.stream()
                        .filter(severity -> responseBody.has(severity) && !responseBody.get(severity).isJsonNull())
                        .map(severity -> parseSeverityValue(responseBody.get(severity).getAsString()))
                        .reduce(0, Integer::sum);

    return total.toString();
  }

  private static Integer parseSeverityValue(String severity) {
    try {
      return Integer.parseInt(severity);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
