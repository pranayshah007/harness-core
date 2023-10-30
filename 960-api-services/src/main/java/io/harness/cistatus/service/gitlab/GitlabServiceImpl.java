/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.gitlab;

import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class GitlabServiceImpl implements GitlabService {
  public static final String DESC = "description";
  public static final String STATE = "state";
  public static final String CONTEXT = "context";
  public static final String TARGET_URL = "target_url";
  private static final String SEPARATOR = "/";

  public static final String MERGED = "merged";
  public static final String MESSAGE = "message";
  public static final String MERGE_COMMIT_SHA = "merge_commit_sha";
  public static final String SHA = "sha";
  public static final String CODE = "code";
  public static final String ERROR = "error";
  public static final String EMPTY_RESPONSE = "Received empty response from Merge API. Please retry after some time.";

  public static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(3);
  public static final int MAX_ATTEMPTS = 10;

  @Override
  public JSONObject mergePR(String apiUrl, String slug, String token, String prNumber, Boolean deleteSourceBranch) {
    JSONObject responseJson = new JSONObject();
    try {
      Response<Object> response = null;

      int i = MAX_ATTEMPTS;
      while (i > 0) {
        response = getGitlabRestClient(GitlabConfig.builder().gitlabUrl(apiUrl).build())
                       .mergePR(token, slug, prNumber, deleteSourceBranch)
                       .execute();
        i--;

        if (response == null) {
          sleepAndRetry(response, i);
          continue;
        }
        // Possible status codes https://docs.gitlab.com/ee/api/merge_requests.html#merge-a-merge-request
        // 422 could happen intermittently in Gitlab
        // https://platform.harness.internal/ng/account/W50osoQJS42JItbue3ddhA/cd/orgs/default/projects/meenatest/pipelines/test/deployments/xVWJQ9D0RTeIzVgmtixYXA/pipeline?storeType=INLINE
        if (response.code() != 405 && response.code() != 422) {
          break;
        }
        // no need of exponential backoff for status 405 or 422, because this retry mechanism is mainly focussed at
        // handling temporary failures
        sleepAndRetry(response, i);
      }

      if (response == null) {
        log.info(EMPTY_RESPONSE);
        responseJson.put(ERROR, EMPTY_RESPONSE);
        responseJson.put(MERGED, false);
        responseJson.put(CODE, 500);
        return responseJson;
      }

      if (response.isSuccessful()) {
        responseJson.put(SHA, ((LinkedHashMap) response.body()).get(MERGE_COMMIT_SHA));
        responseJson.put(MERGED, true);
      } else {
        JSONObject errObject = new JSONObject(response.errorBody().string());
        log.error("Failed to merge PR, gitlab url {}, prNum {}, error {}, code {}", apiUrl, prNumber,
            errObject.get(MESSAGE), response.code());
        responseJson.put(ERROR, errObject.get(MESSAGE));
        responseJson.put(CODE, response.code());
        responseJson.put(MERGED, false);
      }
    } catch (Exception e) {
      log.error("Failed to merge PR for gitlab url {} and prNum {} ", apiUrl, prNumber, e);
      responseJson.put(ERROR, e.getMessage());
      responseJson.put(CODE, 500);
      responseJson.put(MERGED, false);
    }
    return responseJson;
  }

  private void sleepAndRetry(Response<Object> response, int i) {
    log.info(format("Received code %s, retrying attempt %s after sleeping for %s seconds",
        response != null ? response.code() : null, i, RETRY_SLEEP_DURATION.getSeconds()));
    sleep(RETRY_SLEEP_DURATION);
  }

  @Override
  public boolean sendStatus(GitlabConfig gitlabConfig, String userName, String token,
      List<EncryptedDataDetail> encryptionDetails, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap) {
    log.info("Sending status {} for sha {}", bodyObjectMap.get(STATE), sha);

    try {
      Response<StatusCreationResponse> statusCreationResponseResponse =
          getGitlabRestClient(gitlabConfig)
              .createStatus(getAuthToken(token), owner + SEPARATOR + repo, sha, (String) bodyObjectMap.get(STATE),
                  (String) bodyObjectMap.get(CONTEXT), (String) bodyObjectMap.get(DESC),
                  (String) bodyObjectMap.get(TARGET_URL))
              .execute();

      if (!statusCreationResponseResponse.isSuccessful()) {
        log.error("Failed to send status for gitlab url {} and sha {} error {}, message {}",
            gitlabConfig.getGitlabUrl(), sha, statusCreationResponseResponse.errorBody().string(),
            statusCreationResponseResponse.message());
      }

      return statusCreationResponseResponse.isSuccessful();
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Failed to send status for Gitlab url %s and sha %s ", gitlabConfig.getGitlabUrl(), sha), e);
    }
  }

  @VisibleForTesting
  public GitlabRestClient getGitlabRestClient(GitlabConfig gitlabConfig) {
    try {
      String gitlabUrl = gitlabConfig.getGitlabUrl();
      Preconditions.checkNotNull(gitlabUrl, "Gitlab api url is null");
      if (!gitlabUrl.endsWith("/")) {
        gitlabUrl = gitlabUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(gitlabUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(gitlabUrl))
                              .build();
      return retrofit.create(GitlabRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to post commit status request to gitlab :" + gitlabConfig.getGitlabUrl(), e);
    }
  }

  private String getAuthToken(String authToken) {
    return format("Bearer %s", authToken);
  }
}
