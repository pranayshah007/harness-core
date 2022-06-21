/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.azurerepo;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class AzureRepoServiceImpl implements AzureRepoService {
  private static final String STATE = "state";

  @Override
  public boolean sendStatus(AzureRepoConfig azureRepoConfig, String userName, String token, String sha, String org,
      String project, String repo, Map<String, Object> bodyObjectMap) {
    log.info("Sending status {} for sha {}", bodyObjectMap.get(STATE), sha);

    try {
      Response<StatusCreationResponse> statusCreationResponseResponse =
          getAzureRepoRestClient(azureRepoConfig)
              .createStatus(getAuthToken(token), org, project, repo, sha, bodyObjectMap)
              .execute();

      return statusCreationResponseResponse.isSuccessful();
    } catch (Exception e) {
      log.error("Failed to post commit status request to Azure repo with url {} and sha {} ",
          azureRepoConfig.getAzureRepoUrl(), sha, e);
      return false;
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
