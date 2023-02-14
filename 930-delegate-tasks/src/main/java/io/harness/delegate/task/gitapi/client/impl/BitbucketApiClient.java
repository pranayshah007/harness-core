/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.cistatus.service.bitbucket.BitbucketConfig;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.delegate.task.gitapi.client.GitApiClient;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.git.GitTokenRetriever;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BitbucketApiClient implements GitApiClient {
  private static final String BITBUCKET_CLOUD_API_URL = "https://bitbucket.org/api/";
  private static final String PATH_SEPARATOR = "/";
  private static final String UNSUPPORTED_OPERATION = "Unsupported operation";
  private static final String USERNAME_ERR = "Unable to get username information from api access for identifier %s";
  private static final String HTTPS = "https://";
  private BitbucketService bitbucketService;
  private GitTokenRetriever tokenRetriever;

  @Override
  public DelegateResponseData findPullRequest(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException(UNSUPPORTED_OPERATION);
  }

  @Override
  public List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(GitHubPollingDelegateRequest attributesRequest) {
    throw new InvalidRequestException(UNSUPPORTED_OPERATION);
  }

  @Override
  public DelegateResponseData deleteRef(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException(UNSUPPORTED_OPERATION);
  }

  @Override
  public DelegateResponseData mergePR(GitApiTaskParams gitApiTaskParams) {
    ConnectorDetails gitConnector = gitApiTaskParams.getConnectorDetails();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
    String token = tokenRetriever.retrieveAuthToken(GitSCMType.BITBUCKET, gitConnector);
    String userName = fetchUserName(bitbucketConnectorDTO, gitConnector.getIdentifier());
    String repoSlug = gitApiTaskParams.getRepo();
    String prNumber = gitApiTaskParams.getPrNumber();
    String sha = gitApiTaskParams.getSha();
    String url = bitbucketConnectorDTO.getUrl();
    boolean isSaaS = GitClientHelper.isBitBucketSAAS(url);
    String apiURL = getBitbucketApiURL(url, isSaaS);
    BitbucketConfig bitbucketConfig = BitbucketConfig.builder().bitbucketUrl(apiURL).build();
    JSONObject mergePRResponse = bitbucketService.mergePR(bitbucketConfig, token, userName, gitApiTaskParams.getOwner(),
        repoSlug, prNumber, gitApiTaskParams.isDeleteSourceBranch(), gitApiTaskParams.getRef(), isSaaS);

    return prepareResponse(repoSlug, prNumber, sha, mergePRResponse).build();
  }

  GitApiTaskResponseBuilder prepareResponse(String repoSlug, String prNumber, String sha, JSONObject mergePRResponse) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    if (mergePRResponse == null) {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
          format("Merging PR encountered a problem. SHA:%s Repo:%s PrNumber:%s", sha, repoSlug, prNumber));
      return responseBuilder;
    }
    // if branch is not merged
    if (!(boolean) getValue(mergePRResponse, BitbucketServiceImpl.MERGED)) {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(format(
          "Merging PR encountered a problem. SHA:%s Repo:%s PrNumber:%s Message:%s Code:%s", sha, repoSlug, prNumber,
          getValue(mergePRResponse, BitbucketServiceImpl.ERROR), getValue(mergePRResponse, BitbucketServiceImpl.CODE)));
      return responseBuilder;
    }
    // when the branch is merged
    Object mergeCommitSha = getValue(mergePRResponse, BitbucketServiceImpl.SHA);
    if (mergeCommitSha == null) {
      log.error("Merge is successful. But, SHA is null. Please refer to the sha in the repo. Repo:%s PrNumber:%s",
          repoSlug, prNumber);
    }

    if (getValue(mergePRResponse, BitbucketServiceImpl.ERROR) != null) {
      // when branch is merged, but error occurred when deleting the source branch
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(format(
          "PR merged successfully, but encountered a problem while deleting the source branch. Merge Commit SHA:%s Repo:%s PrNumber:%s Message:%s Code:%s",
          mergeCommitSha, repoSlug, prNumber, getValue(mergePRResponse, BitbucketServiceImpl.ERROR),
          getValue(mergePRResponse, BitbucketServiceImpl.CODE)));
    } else {
      responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .gitApiResult(GitApiMergePRTaskResponse.builder().sha(String.valueOf(mergeCommitSha)).build());
    }
    return responseBuilder;
  }

  Object getValue(JSONObject jsonObject, String key) {
    if (jsonObject == null) {
      return null;
    }
    try {
      return jsonObject.get(key);
    } catch (Exception ex) {
      log.error("Failed to get key: {} in JsonObject: {}. Error: {}", key, jsonObject, ex.getMessage());
      return null;
    }
  }

  private static String getBitbucketApiURL(String url, boolean isSaaS) {
    if (isSaaS) {
      return BITBUCKET_CLOUD_API_URL;
    }
    String domain = GitClientHelper.getGitSCM(url);
    return HTTPS + domain + PATH_SEPARATOR;
  }

  private String fetchUserName(BitbucketConnectorDTO gitConfigDTO, String identifier) {
    try {
      if (gitConfigDTO.getApiAccess().getType() == BitbucketApiAccessType.USERNAME_AND_TOKEN) {
        return ((BitbucketUsernameTokenApiAccessDTO) gitConfigDTO.getApiAccess().getSpec()).getUsername();
      }
    } catch (Exception ex) {
      log.error(format(USERNAME_ERR, identifier), ex);
      throw new InvalidRequestException(format(USERNAME_ERR, identifier));
    }
    throw new InvalidRequestException(format(USERNAME_ERR, identifier));
  }
}
