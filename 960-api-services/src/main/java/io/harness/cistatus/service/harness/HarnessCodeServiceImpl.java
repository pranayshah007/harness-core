/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.harness;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class HarnessCodeServiceImpl implements HarnessCodeService {
  @Override
  public boolean sendStatus(HarnessCodeConfig harnessCodePayload, String token, String sha, String repoRef) {
    log.info("Sending status {} for sha {} and repo {}", harnessCodePayload.getHarnessCodePayload().getStatus(), sha,
        repoRef);
    try {
      Response<StatusCreationResponse> statusCreationResponseResponse =
          getHarnessRestClient(harnessCodePayload)
              .sendStatus(token, repoRef, sha, harnessCodePayload.getHarnessCodePayload())
              .execute();

      if (!statusCreationResponseResponse.isSuccessful()) {
        log.error("Failed to send status for harness url {} and sha {} error {}, message {}",
            harnessCodePayload.getHarnessCodeBaseUrl(), sha, statusCreationResponseResponse.errorBody().string(),
            statusCreationResponseResponse.message());
      }

      return statusCreationResponseResponse.isSuccessful();
    } catch (Exception e) {
      throw new InvalidRequestException(format("Failed to send status for Harness url %s and sha %s ",
                                            harnessCodePayload.getHarnessCodeBaseUrl(), sha),
          e);
    }
  }

  @VisibleForTesting
  public HarnessCodeRestClient getHarnessRestClient(HarnessCodeConfig harnessCodeConfig) {
    try {
      String harnessApiUrl = harnessCodeConfig.getHarnessCodeBaseUrl();
      Preconditions.checkNotNull(harnessApiUrl, "Harness api url is null");
      if (!harnessApiUrl.endsWith("/")) {
        harnessApiUrl = harnessApiUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(harnessApiUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(harnessApiUrl))
                              .build();
      return retrofit.create(HarnessCodeRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to post commit status request to harness :" + harnessCodeConfig.getHarnessCodeBaseUrl(), e);
    }
  }
}
