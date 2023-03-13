/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.bitbucket;

import static java.lang.String.format;

import io.harness.cistatus.BitbucketOnPremErrorResponse;
import io.harness.cistatus.BitbucketOnPremMergeResponse;
import io.harness.cistatus.BitbucketSaaSErrorResponse;
import io.harness.cistatus.BitbucketSaaSMergeResponse;
import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.git.model.MergePRResponse;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class BitbucketServiceImpl implements BitbucketService {
  private static final String STATE = "state";
  private static final String NAME = "name";
  private static final String DRY_RUN = "dryRun";
  private static final String CLOSE_SOURCE_BRANCH = "close_source_branch";
  private static final String ON_PREM_MERGE_FAILURE =
      "Failed to merge PR for Bitbucket Server. URL {} and PR number {}. Response {} ";
  private static final String SAAS_MERGE_FAILURE =
      "Failed to merge PR for Bitbucket Cloud. URL {} and PR number {}. Response {} ";
  private static final String FAILED_TO_GET_ERR = "Failed to get error message from merge response";
  private static final String ON_PREM_FAILURE_TO_DELETE_REF =
      "Failed to delete ref for Bitbucket Server. URL {}, ref {}, Error {}";

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

      if (!statusCreationResponseResponse.isSuccessful()) {
        log.error("Failed to send status for bitbucket url {} and sha {} error {}, message {}",
            bitbucketConfig.getBitbucketUrl(), sha, statusCreationResponseResponse.errorBody().string(),
            statusCreationResponseResponse.message());
      }

      return statusCreationResponseResponse.isSuccessful();

    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Failed to send status for Bitbucket url %s and sha %s ", bitbucketConfig.getBitbucketUrl(), sha), e);
    }
  }

  @Override
  public MergePRResponse mergePR(BitbucketConfig bitbucketConfig, String token, String userName, String org,
      String repoSlug, String prNumber, boolean deleteSourceBranch, String ref) {
    String authToken = getHeaderWithCredentials(token, userName);
    MergePRResponse mergeResponse;
    if (GitClientHelper.isBitBucketSAAS(bitbucketConfig.getBitbucketUrl())) {
      mergeResponse = mergeSaaSPR(bitbucketConfig, authToken, org, repoSlug, prNumber, deleteSourceBranch);
    } else {
      mergeResponse = mergeOnPremPR(bitbucketConfig, authToken, org, repoSlug, prNumber, ref, deleteSourceBranch);
    }
    return mergeResponse;
  }

  private MergePRResponse mergeOnPremPR(BitbucketConfig bitbucketConfig, String authToken, String org, String repoSlug,
      String prNumber, String ref, boolean deleteSourceBranch) {
    MergePRResponse mergeResponse = new MergePRResponse();
    Map<String, Object> parameters = new HashMap<>();
    String url = bitbucketConfig.getBitbucketUrl();

    try {
      Response<BitbucketOnPremMergeResponse> mergePRResponse =
          getBitbucketClient(bitbucketConfig, null)
              .mergeOnPremPR(authToken, org, repoSlug, prNumber, parameters)
              .execute();
      BitbucketOnPremMergeResponse mergeResponseBody = mergePRResponse.body();
      ResponseBody errorBody = mergePRResponse.errorBody();

      /* when the merge operation is called in parallel from the pipeline as well as externally by the user, the
       Bitbucket API returns empty response (this is one scenario I observed).
       In this case, we will not know the SHA or any other status from the response.
       */
      if (mergeResponseBody == null && errorBody == null) {
        handleInterruptedOperation(bitbucketConfig.getBitbucketUrl(), repoSlug, prNumber,
            mergePRResponse.isSuccessful(), mergePRResponse.message(), mergePRResponse.code(), mergeResponse);
        return mergeResponse;
      }

      if (!mergePRResponse.isSuccessful()) {
        log.error(ON_PREM_MERGE_FAILURE, url, prNumber, errorBody);
        setErrorMergeResponse(getOnPremErrorMessage(errorBody.string()), mergePRResponse.code(), mergeResponse);
        return mergeResponse;
      }

      setSuccessfulMergeResponse(mergeResponseBody.getProperties().getMergeCommit().getId(), mergeResponse);
      if (deleteSourceBranch) {
        // if merge is successful, delete source branch when deleteSourceBranch is true
        boolean isBranchDeleted = deleteRef(bitbucketConfig, authToken, ref, repoSlug, org, mergeResponse);
        if (!isBranchDeleted) {
          log.error(
              "Error encountered when deleting source branch {} of the pull request {}. URL {}", ref, prNumber, url);
          mergeResponse.setSourceBranchDeleted(false);
        }
      }
    } catch (Exception e) {
      log.error(ON_PREM_MERGE_FAILURE, url, prNumber, e);
      setErrorMergeResponse(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, mergeResponse);
    }
    return mergeResponse;
  }

  private void setSuccessfulMergeResponse(String sha, MergePRResponse mergeResponse) {
    mergeResponse.setSha(sha);
    mergeResponse.setMerged(true);
  }

  private void setErrorMergeResponse(String errorMessage, int errorCode, MergePRResponse mergeResponse) {
    mergeResponse.setErrorMessage(errorMessage);
    mergeResponse.setErrorCode(errorCode);
    mergeResponse.setMerged(false);
  }

  private String getOnPremErrorMessage(String errorBody) {
    try {
      Gson gson = new Gson();
      BitbucketOnPremErrorResponse error = gson.fromJson(errorBody, BitbucketOnPremErrorResponse.class);
      return error.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(", "));
    } catch (Exception e) {
      log.error(FAILED_TO_GET_ERR + " Error {}", e.getMessage());
      return FAILED_TO_GET_ERR;
    }
  }

  private String getSaaSErrorMessage(String errorBody) {
    try {
      Gson gson = new Gson();
      BitbucketSaaSErrorResponse error = gson.fromJson(errorBody, BitbucketSaaSErrorResponse.class);
      return error.getError().getMessage();
    } catch (Exception e) {
      log.error(FAILED_TO_GET_ERR + " Error {}", e.getMessage());
      return FAILED_TO_GET_ERR;
    }
  }

  private MergePRResponse mergeSaaSPR(BitbucketConfig bitbucketConfig, String authToken, String org, String repoSlug,
      String prNumber, boolean deleteSourceBranch) {
    MergePRResponse mergeResponse = new MergePRResponse();
    Map<String, Object> parameters = new HashMap<>();
    if (deleteSourceBranch) {
      parameters.put(CLOSE_SOURCE_BRANCH, true);
    }
    try {
      Response<BitbucketSaaSMergeResponse> bitbucketMergePRResponse =
          getBitbucketClient(bitbucketConfig, null)
              .mergeSaaSPR(authToken, org, repoSlug, prNumber, parameters)
              .execute();

      BitbucketSaaSMergeResponse mergeResponseBody = bitbucketMergePRResponse.body();
      ResponseBody errorBody = bitbucketMergePRResponse.errorBody();

      /* when the merge operation is called in parallel from the pipeline as well as externally by the user, the
       Bitbucket API returns empty response (this is one scenario I observed).
       In this case, we will not know the SHA or any other status from the response.
       */
      if (mergeResponseBody == null && errorBody == null) {
        handleInterruptedOperation(bitbucketConfig.getBitbucketUrl(), repoSlug, prNumber,
            bitbucketMergePRResponse.isSuccessful(), bitbucketMergePRResponse.message(),
            bitbucketMergePRResponse.code(), mergeResponse);
        return mergeResponse;
      }

      if (!bitbucketMergePRResponse.isSuccessful()) {
        log.error(SAAS_MERGE_FAILURE, bitbucketConfig.getBitbucketUrl(), prNumber, errorBody);
        setErrorMergeResponse(getSaaSErrorMessage(errorBody.string()), bitbucketMergePRResponse.code(), mergeResponse);
        return mergeResponse;
      }

      setSuccessfulMergeResponse(mergeResponseBody.getMergeCommit().getHash(), mergeResponse);
    } catch (Exception e) {
      log.error(SAAS_MERGE_FAILURE, bitbucketConfig.getBitbucketUrl(), prNumber, e);
      setErrorMergeResponse(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, mergeResponse);
    }
    return mergeResponse;
  }

  private void handleInterruptedOperation(String url, String repoSlug, String prNumber, boolean isSuccessful,
      String msg, int code, MergePRResponse mergeResponse) {
    log.error("Interrupted operation while merging PR. URL {}, PR number {}, response status {}, message {}, code {}",
        url, prNumber, isSuccessful, msg, code);
    setErrorMergeResponse(
        "Unknown status. Please refer to the PR status and SHA in the repo " + repoSlug + ". PR number " + prNumber,
        HttpStatus.SC_INTERNAL_SERVER_ERROR, mergeResponse);
  }

  @Override
  public boolean deleteRef(BitbucketConfig bitbucketConfig, String authToken, String ref, String repoSlug, String org,
      MergePRResponse mergeResponse) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(NAME, ref);
    parameters.put(DRY_RUN, false);
    try {
      Response<Object> response =
          getBitbucketClient(bitbucketConfig, null).deleteOnPremRef(authToken, org, repoSlug, parameters).execute();

      if (response.isSuccessful()) {
        mergeResponse.setSourceBranchDeleted(true);
        return true;
      }
      String errMsg = getOnPremErrorMessage(response.errorBody().string());
      setDeleteRefErrInResponse(mergeResponse, errMsg, response.code());
      log.error(ON_PREM_FAILURE_TO_DELETE_REF, bitbucketConfig.getBitbucketUrl(), ref, errMsg);

    } catch (Exception e) {
      setDeleteRefErrInResponse(mergeResponse, e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
      log.error(ON_PREM_FAILURE_TO_DELETE_REF, bitbucketConfig.getBitbucketUrl(), ref, e);
    }
    return false;
  }

  private void setDeleteRefErrInResponse(MergePRResponse mergeResponse, String errMsg, int errCode) {
    if (mergeResponse != null) {
      mergeResponse.setErrorMessage(errMsg);
      mergeResponse.setErrorCode(errCode);
    }
  }

  public BitbucketRestClient getBitbucketClient(
      BitbucketConfig bitbucketConfig, List<EncryptedDataDetail> encryptionDetails) {
    try {
      String bitbucketUrl = bitbucketConfig.getBitbucketUrl();
      Preconditions.checkNotNull(bitbucketUrl, "Bitbucket API URL is null");
      if (!bitbucketUrl.endsWith("/")) {
        bitbucketUrl = bitbucketUrl + "/";
      }

      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new GuavaModule());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new NGHarnessJacksonModule());

      /* when the merge operation is called in parallel from the pipeline as well as externally by the user, the
       Bitbucket API returns empty string as response, leading to parsing exception. This sets the response as null
       whenever the API returns empty string
       */
      mapper.coercionConfigFor(BitbucketSaaSMergeResponse.class)
          .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
      mapper.coercionConfigFor(BitbucketOnPremMergeResponse.class)
          .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
      mapper.coercionConfigFor(BitbucketSaaSErrorResponse.class)
          .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
      mapper.coercionConfigFor(BitbucketOnPremErrorResponse.class)
          .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(bitbucketUrl)
                              .addConverterFactory(JacksonConverterFactory.create(mapper))
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
