/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.bitbucket;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class BitbucketServiceImpl implements BitbucketService {
  private static final String STATE = "state";

  @Override
  public boolean sendStatus(BitbucketConfig bitbucketConfig, String userName, String token,
      List<EncryptedDataDetail> encryptionDetails, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap) {
    log.info("Sending status {} for sha {}", bodyObjectMap.get(STATE), sha);

    try {
      Response<StatusCreationResponse> statusCreationResponseResponse;

      if (!GitClientHelper.isBitBucketSAAS(bitbucketConfig.getBitbucketUrl())) {
        statusCreationResponseResponse =
            getBitbucketClient(bitbucketConfig, encryptionDetails)
                .createOnPremStatus(getHeaderWithCredentials(token, userName), sha, bodyObjectMap)
                .execute();
      } else {
        statusCreationResponseResponse =
            getBitbucketClient(bitbucketConfig, encryptionDetails)
                .createStatus(getHeaderWithCredentials(token, userName), owner, repo, sha, bodyObjectMap)
                .execute();
      }

      return statusCreationResponseResponse.isSuccessful();

    } catch (Exception e) {
      log.error("Failed to send status for Bitbucket url {} and sha {} ", bitbucketConfig.getBitbucketUrl(), sha, e);
      return false;
    }
  }

  @VisibleForTesting
  public BitbucketRestClient getBitbucketClient(
      BitbucketConfig bitbucketConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      String bitbucketUrl = bitbucketConfig.getBitbucketUrl();
      Preconditions.checkNotNull(bitbucketUrl, "Bitbucket api url is null");
      if (!bitbucketUrl.endsWith("/")) {
        bitbucketUrl = bitbucketUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(bitbucketUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(bitbucketUrl))
                              .build();
      return retrofit.create(BitbucketRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to post commit status request to bitbucket server :" + bitbucketConfig.getBitbucketUrl(), e);
    }
  }

  private String getHeaderWithCredentials(String token, String userName) {
    return "Basic " + Base64.encodeBase64String(format("%s:%s", userName, token).getBytes(StandardCharsets.UTF_8));
  }
}
