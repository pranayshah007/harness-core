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
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
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
import io.harness.git.helper.BitbucketHelper;
import io.harness.git.model.MergePRResponse;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BitbucketApiClient implements GitApiClient {
  private static final String UNSUPPORTED_OPERATION = "Unsupported operation";
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
    String userName = BitbucketHelper.fetchUserName(bitbucketConnectorDTO, gitConnector.getIdentifier());
    String repoSlug = gitApiTaskParams.getRepo();
    String prNumber = gitApiTaskParams.getPrNumber();
    String sha = gitApiTaskParams.getSha();
    String url = bitbucketConnectorDTO.getUrl();
    String apiURL = GitClientHelper.getBitBucketApiURL(url);
    boolean deleteSourceBranch = gitApiTaskParams.isDeleteSourceBranch();
    BitbucketConfig bitbucketConfig = BitbucketConfig.builder().bitbucketUrl(apiURL).build();
    MergePRResponse mergePRResponse = bitbucketService.mergePR(bitbucketConfig, token, userName,
        gitApiTaskParams.getOwner(), repoSlug, prNumber, deleteSourceBranch, gitApiTaskParams.getRef());

    return prepareResponse(
        repoSlug, prNumber, sha, GitClientHelper.isBitBucketSAAS(url), deleteSourceBranch, mergePRResponse)
        .build();
  }

  GitApiTaskResponseBuilder prepareResponse(String repoSlug, String prNumber, String sha, boolean isSaaS,
      boolean deleteSourceBranch, MergePRResponse mergePRResponse) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    if (mergePRResponse == null) {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
          format("Merging PR encountered a problem. SHA:%s Repo:%s PrNumber:%s", sha, repoSlug, prNumber));
      return responseBuilder;
    }
    // if branch is not merged
    if (!mergePRResponse.isMerged()) {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
          format("Merging PR encountered a problem. SHA:%s Repo:%s PrNumber:%s Message:%s Code:%s", sha, repoSlug,
              prNumber, mergePRResponse.getErrorMessage(), mergePRResponse.getErrorCode()));
      return responseBuilder;
    }
    // when the branch is merged
    String mergeCommitSha = mergePRResponse.getSha();
    if (mergeCommitSha == null) {
      log.error("PR merged successfully, but SHA is null. Please refer the SHA in the repo. Repo:%s PrNumber:%s",
          repoSlug, prNumber);
    }

    // for onprem - when branch is merged, but error occurred when deleting the source branch
    if (!isSaaS && deleteSourceBranch && !mergePRResponse.isSourceBranchDeleted()) {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(format(
          "PR merged successfully, but encountered a problem while deleting the source branch. Merge Commit SHA:%s Repo:%s PrNumber:%s Message:%s Code:%s",
          mergeCommitSha, repoSlug, prNumber, mergePRResponse.getErrorMessage(), mergePRResponse.getErrorCode()));
      return responseBuilder;
    }
    responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .gitApiResult(GitApiMergePRTaskResponse.builder().sha(mergeCommitSha).build());

    return responseBuilder;
  }
}
