/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.azurerepo;

import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.pms.yaml.ParameterField;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class AzureRepoServiceImpl implements AzureRepoService {
  private static final String STATE = "state";
  private static final String MERGE_COMMIT_MESSAGE = "Harness: Updating config overrides";
  private static final String MERGE_STATUS = "completed";
  private static final String MERGED = "merged";
  public static final String BYPASS_POLICY = "bypassPolicy";
  public static final String BYPASS_REASON = "bypassReason";

  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 10;

  @Override
  public boolean sendStatus(AzureRepoConfig azureRepoConfig, String userName, String token, String sha, String prNumber,
      String org, String project, String repo, Map<String, Object> bodyObjectMap) {
    log.info("Sending status {} for sha {}", bodyObjectMap.get(STATE), sha);

    try {
      Response<StatusCreationResponse> statusCreationResponseResponse =
          getAzureRepoRestClient(azureRepoConfig)
              .createStatus(getAuthToken(token), org, project, repo, sha, bodyObjectMap)
              .execute();

      if (Strings.isNotBlank(prNumber)) {
        Response<StatusCreationResponse> prStatusCreationResponseResponse =
            getAzureRepoRestClient(azureRepoConfig)
                .createPRStatus(getAuthToken(token), org, project, repo, prNumber, bodyObjectMap)
                .execute();

        if (!prStatusCreationResponseResponse.isSuccessful()) {
          log.error("Failed to send status for AzureRepo url {} and prNumber {} error {}, message {}",
              azureRepoConfig.getAzureRepoUrl(), prNumber, statusCreationResponseResponse.errorBody().string(),
              statusCreationResponseResponse.message());
        }
      }

      if (!statusCreationResponseResponse.isSuccessful()) {
        log.error("Failed to send status for AzureRepo url {} and sha {} error {}, message {}",
            azureRepoConfig.getAzureRepoUrl(), sha, statusCreationResponseResponse.errorBody().string(),
            statusCreationResponseResponse.message());
      }

      return statusCreationResponseResponse.isSuccessful();
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Failed to send status for AzureRepo url %s and sha %s ", azureRepoConfig.getAzureRepoUrl(), sha), e);
    }
  }

  @Override
  public JSONObject mergePR(AzureRepoConfig azureRepoConfig, String token, String sha, String org, String project,
      String repo, String prNumber, boolean deleteSourceBranch, Map<String, Object> apiParamOptions) {
    log.info("Merging PR for sha {}", sha);

    JSONObject commitId = new JSONObject();
    commitId.put("commitId", sha);

    JSONObject mergeStrategy = new JSONObject();
    mergeStrategy.put("mergeStrategy", "1");
    mergeStrategy.put("mergeCommitMessage", MERGE_COMMIT_MESSAGE);
    mergeStrategy.put("deleteSourceBranch", deleteSourceBranch);

    addBypassParams(apiParamOptions, mergeStrategy);

    JSONObject lastMergeSourceCommit = new JSONObject();
    lastMergeSourceCommit.put("lastMergeSourceCommit", commitId);
    lastMergeSourceCommit.put("status", MERGE_STATUS);
    lastMergeSourceCommit.put("completionOptions", mergeStrategy);

    try {
      Response<Object> response = null;

      int i = MAX_ATTEMPTS;
      while (i > 0) {
        response = getAzureRepoRestClient(azureRepoConfig)
                       .mergePR(getAuthToken(token), org, project, repo, prNumber,
                           RequestBody.create(
                               MediaType.parse("application/json; charset=utf-8"), lastMergeSourceCommit.toString()))
                       .execute();
        i--;
        // This error code denotes that the base branch has been modified. This can happen if two merge requests
        // are sent for the same branch but the first one has not yet complete and second request reached the provider.
        if (response.code() != 405) {
          break;
        }
        log.info(format(
            "Received code %s, retrying attempt %s after sleeping for %s", response.code(), i, RETRY_SLEEP_DURATION));
        sleep(RETRY_SLEEP_DURATION);
      }

      JSONObject json = new JSONObject();
      if (response.isSuccessful()) {
        log.info("Response from Azure Repo Merge {}", response.body().toString());
        json.put("sha", ((LinkedHashMap) response.body()).get("mergeId"));
        json.put(MERGED, true);
      } else {
        JSONObject errObject = new JSONObject(response.errorBody().string());
        log.warn(
            "Merge Request for merging Azure repo with url {} and sha {} returned with response code {} and message {}",
            azureRepoConfig.getAzureRepoUrl(), sha, response.code(), errObject.get("message"));
        json.put("error", errObject.get("message"));
        json.put("code", response.code());
        json.put(MERGED, false);
      }
      return json;
    } catch (Exception e) {
      log.error("Failed to merge pull request to Azure repo with url {} and sha {} ", azureRepoConfig.getAzureRepoUrl(),
          sha, e);
      JSONObject json = new JSONObject();
      json.put(MERGED, false);
      json.put("error", e.getMessage());
      return json;
    }
  }

  @VisibleForTesting
  void addBypassParams(Map<String, Object> apiParamOptions, JSONObject mergeStrategy) {
    if (apiParamOptions.get(BYPASS_POLICY) != null) {
      mergeStrategy.put(
          BYPASS_POLICY, Boolean.valueOf((String) (((ParameterField) apiParamOptions.get(BYPASS_POLICY)).getValue())));
    }
    if (apiParamOptions.get(BYPASS_REASON) != null) {
      mergeStrategy.put(BYPASS_REASON, ((ParameterField) apiParamOptions.get(BYPASS_REASON)).getValue());
    }
  }

  @VisibleForTesting
  public AzureRepoRestClient getAzureRepoRestClient(AzureRepoConfig azureRepoConfig) {
    try {
      String azureRepoUrl = azureRepoConfig.getAzureRepoUrl();
      Preconditions.checkNotNull(azureRepoUrl, "Azure repo api url is null");
      if (!azureRepoUrl.endsWith("/")) {
        azureRepoUrl = azureRepoUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(azureRepoUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(azureRepoUrl))
                              .build();
      return retrofit.create(AzureRepoRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to post commit status request to Azure server :" + azureRepoConfig.getAzureRepoUrl(), e);
    }
  }

  private String getAuthToken(String authToken) {
    return format(
        "Basic %s", Base64.getEncoder().encodeToString(format(":%s", authToken).getBytes(StandardCharsets.UTF_8)));
  }
}
