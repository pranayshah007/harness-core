/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import static java.lang.String.format;
import static org.joda.time.Minutes.minutes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.Execution;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.beans.entities.ResourceResponseData;
import io.harness.beans.entities.TerraformEndpointsData;
import io.harness.beans.entities.Workspace;
import io.harness.beans.entities.WorkspaceOutput;
import io.harness.beans.entities.WorkspaceVariables;
import io.harness.exception.GeneralException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.security.JWTTokenServiceUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IACM)
public class IACMServiceUtils {
  private final IACMServiceClient iacmServiceClient;
  private final IACMServiceConfig iacmServiceConfig;
  private final Duration retrySleepDuration = Duration.ofSeconds(2);
  private static final int maxAttempts = 3;
  @Inject
  public IACMServiceUtils(IACMServiceClient iacmServiceClient, IACMServiceConfig iacmServiceConfig) {
    this.iacmServiceClient = iacmServiceClient;
    this.iacmServiceConfig = iacmServiceConfig;
  }

  public String getCostEstimationToken() {
    return this.iacmServiceConfig.getCostEstimationToken();
  }

  public String getCostEstimationAPIEndpoint() {
    return this.iacmServiceConfig.getCostEstimationAPIIUrl();
  }

  public Workspace getIACMWorkspaceInfo(String org, String projectId, String accountId, String workspaceID) {
    log.info("Initiating token request to IACM service: {}", this.iacmServiceConfig.getBaseUrl());

    RetryPolicy<Response<JsonObject>> retryPolicy =
        getRetryPolicyJsonObject(format("[Retrying failed call to retrieve workspace info: {}"),
            format("Failed to retrieve workspace info after retrying {} times"));

    Response<JsonObject> response;

    try {
      response = Failsafe.with(retryPolicy)
                     .get(()
                              -> iacmServiceClient
                                     .getWorkspaceInfo(org, projectId, workspaceID,
                                         generateJWTToken(accountId, org, projectId), accountId)
                                     .execute());
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Workspace Info request to IACM service call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        if (response.errorBody() != null) {
          errorBody = response.errorBody().string();
        }
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      log.error("error querying the iac server{}", errorBody);

      throw new GeneralException(
          String.format("Could not retrieve IACM workspace info from the IACM service. status code = %s, message = %s",
              response.code(), response.message()));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM workspace info from the IACM service. Response body is null");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    Workspace workspace;
    try {
      workspace = objectMapper.readValue(response.body().toString(), Workspace.class);
    } catch (JsonProcessingException ex) {
      log.error("Could not parse json body {}", response.body().toString());
      throw new GeneralException(
          "Could not parse workspace response. Please contact Harness Support for more information");
    }
    if (workspace.getProvider_connector() == null) {
      throw new GeneralException("Could not retrieve IACM Connector from the IACM service. The WorkspaceID: "
          + workspace.getIdentifier() + " doesn't have a connector reference");
    }
    return workspace;
  }

  @NotNull
  public String getIACMServiceToken(String accountID) {
    log.info("Initiating token request to IACM service: " + this.iacmServiceConfig.getBaseUrl());
    Response<JsonObject> response;
    RetryPolicy<Response<JsonObject>> retryPolicy =
        getRetryPolicyJsonObject(format("[Retrying failed call to retrieve token from the IAC Server info: {}"),
            format("Failed to retrieve token from the IAC Server after retrying {} times"));
    try {
      response =
          Failsafe.with(retryPolicy)
              .get(() -> iacmServiceClient.generateToken(accountID, this.iacmServiceConfig.getGlobalToken()).execute());
    } catch (Exception e) {
      throw new GeneralException("Token request to IACM service call failed", e);
    }

    // Received error from the server
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        if (response.errorBody() != null) {
          errorBody = response.errorBody().string();
        }
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      log.error("error querying the iac server{}", errorBody);

      throw new GeneralException(
          String.format("Could not fetch token from IACM service. status code = %s, message = %s", response.code(),
              response.message()));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not fetch token from IACM service. Response body is null");
    }
    return response.body().get("token").getAsString();
  }

  public Map<String, String> getIacmWorkspaceOutputs(
      String org, String projectId, String accountId, String workspaceID) {
    log.info("Initiating request to IACM service for ouput retrieval: {}", this.iacmServiceConfig.getBaseUrl());
    RetryPolicy<Response<JsonObject>> retryPolicy = getRetryPolicyJsonObject(
        format("[Retrying failed call to retrieve output variables from the IAC Server info: {}"),
        format("Failed to retrieve output variables from the IAC Server after retrying {} times"));

    Response<JsonObject> response;

    try {
      response = Failsafe.with(retryPolicy)
                     .get(()
                              -> iacmServiceClient
                                     .getWorkspaceResoures(org, projectId, workspaceID,
                                         generateJWTToken(accountId, org, projectId), accountId)
                                     .execute());
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Error retrieving the resources from the IACM service. Call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        if (response.errorBody() != null) {
          errorBody = response.errorBody().string();
        }
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }
      log.error("error querying the iac server{}", errorBody);

      throw new GeneralException(String.format("Could not parse body for the env retrieval response = %s, message = %s",
          response.code(), response.message()));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM variables from the IACM service. Response body is null");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    ResourceResponseData responseData;
    Map<String, String> outputs = new HashMap<>();
    try {
      responseData = objectMapper.readValue(response.body().toString(), ResourceResponseData.class);
      for (WorkspaceOutput output : responseData.getOutputs()) {
        outputs.put(output.getName(), output.getValue());
      }
    } catch (JsonProcessingException ex) {
      log.error("Could not parse json body {}", response.body().toString());
      throw new GeneralException(
          "Could not parse variables response. Please contact Harness Support for more information");
    }
    if (outputs.size() == 0) {
      log.info("There are no outputs in this workspace");
    }
    return outputs;
  }

  public WorkspaceVariables[] getIacmWorkspaceEnvs(String org, String projectId, String accountId, String workspaceID) {
    log.info("Initiating request to IACM service for env retrieval: {}", this.iacmServiceConfig.getBaseUrl());
    RetryPolicy<Response<JsonArray>> retryPolicy =
        getRetryPolicyJsonArray(format("[Retrying failed call to retrieve envs variables from the IAC Server info: {}"),
            format("Failed to retrieve envs variables from the IAC Server after retrying {} times"));

    Response<JsonArray> response;

    try {
      response = Failsafe.with(retryPolicy)
                     .get(()
                              -> iacmServiceClient
                                     .getWorkspaceVariables(org, projectId, workspaceID,
                                         generateJWTToken(accountId, org, projectId), accountId)
                                     .execute());
    } catch (Exception e) {
      log.error("Error while trying to execute the query in the IACM Service: ", e);
      throw new GeneralException("Error retrieving the variables from the IACM service. Call failed", e);
    }
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        if (response.errorBody() != null) {
          errorBody = response.errorBody().string();
        }
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }
      log.error("error querying the iac server{}", errorBody);

      throw new GeneralException(String.format("Could not parse body for the env retrieval response = %s, message = %s",
          response.code(), response.message()));
    }

    if (response.body() == null) {
      throw new GeneralException("Could not retrieve IACM variables from the IACM service. Response body is null");
    }
    ObjectMapper objectMapper = new ObjectMapper();
    WorkspaceVariables[] vars;
    try {
      vars = objectMapper.readValue(response.body().toString(), WorkspaceVariables[].class);
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

  public String GetTerraformEndpointsData(String account, String org, String project, String workspaceId) {
    TerraformEndpointsData tfEndpointsData = TerraformEndpointsData.builder()
                                                 .org_id(org)
                                                 .base_url(iacmServiceConfig.getExternalUrl())
                                                 .account_id(account)
                                                 .project_id(project)
                                                 .workspace_id(workspaceId)
                                                 .token(generateJWTToken(account, org, project))
                                                 .build();
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(tfEndpointsData);
    } catch (Exception ex) {
      throw new GeneralException(
          "Could not parse Endpoint information. Please contact Harness Support for more information");
    }
  }

  public void createIACMExecution(Ambiance ambiance, String workspaceId) {
    log.info("Initiating post request to IACM service for execution creation: {}", this.iacmServiceConfig.getBaseUrl());
    RetryPolicy<Response<JsonObject>> retryPolicy =
        getRetryPolicyJsonObject(format("[Retrying failed call to create an execution in the IAC Server info: {}"),
            format("Failed to create an execution in the IAC Server after retrying {} times"));

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    Execution execution = Execution.builder()
                              .org(ngAccess.getOrgIdentifier())
                              .account(ngAccess.getAccountIdentifier())
                              .project(ngAccess.getProjectIdentifier())
                              .workspace(workspaceId)
                              .pipeline_execution_id(ambiance.getPlanExecutionId())
                              .pipeline(ambiance.getMetadata().getPipelineIdentifier())
                              .pipeline_stage_id(ambiance.getStageExecutionId())
                              .build();

    Response<JsonObject> response;

    try {
      response = Failsafe.with(retryPolicy)
                     .get(()
                              -> iacmServiceClient
                                     .postIACMExecution(ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(),
                                         execution,
                                         generateJWTToken(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
                                             ngAccess.getProjectIdentifier()),
                                         ngAccess.getAccountIdentifier())
                                     .execute());
    } catch (Exception e) {
      log.error("Error while trying to execute the request in the IACM Service: ", e);
      throw new GeneralException("Error creating the execution from the IACM service. Call failed", e);
    }
    if (!response.isSuccessful()) {
      if (response.code() == 409) {
        return;
      }
      String errorBody = null;
      try {
        if (response.errorBody() != null) {
          errorBody = response.errorBody().string();
        }
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }
      log.error("error querying the iac server{}", errorBody);

      throw new GeneralException(String.format(
          "Could not parse body for the execution response = %s, message = %s", response.code(), response.message()));
    }
  }

  private String generateJWTToken(String accountId, String orgId, String projectId) {
    return JWTTokenServiceUtils.generateJWTToken(
        ImmutableMap.of("accountId", accountId, "orgId", orgId, "projectId", projectId),
        minutes(120).toStandardDuration().getMillis(), iacmServiceConfig.getGlobalToken());
  }

  private RetryPolicy<Response<JsonObject>> getRetryPolicyJsonObject(
      String failedAttemptMessage, String failureMessage) {
    Random random = new Random();
    return new RetryPolicy<Response<JsonObject>>()
        .handleResultIf(event -> !event.isSuccessful() && event.code() != 409)
        .onRetry(event -> {
          log.warn("Retrying again");
          long minDelay = 3000; // Minimum delay in milliseconds (3 seconds)
          long maxDelay = 10000; // Maximum delay in milliseconds (10 seconds)
          long delay = minDelay + random.nextInt((int) (maxDelay - minDelay + 1));
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        })
        .withMaxAttempts(maxAttempts)
        .onFailedAttempt(event -> {
          log.warn(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure());
          log.warn("Error code: {}", event.getLastResult().code());
        })
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private RetryPolicy<Response<JsonArray>> getRetryPolicyJsonArray(String failedAttemptMessage, String failureMessage) {
    Random random = new Random();
    return new RetryPolicy<Response<JsonArray>>()
        .handleResultIf(event -> !event.isSuccessful() && event.code() != 409)
        .onRetry(event -> {
          log.info("Retrying again");
          long minDelay = 3000; // Minimum delay in milliseconds (3 seconds)
          long maxDelay = 10000; // Maximum delay in milliseconds (10 seconds)
          long delay = minDelay + random.nextInt((int) (maxDelay - minDelay + 1));
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        })
        .withMaxAttempts(maxAttempts)
        .onFailedAttempt(event -> {
          log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure());
          log.info("Error code: {}", event.getLastResult().code());
        })
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
