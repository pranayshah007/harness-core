/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.PageRequestDTO;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.execution.buildstate.CodebaseUtils;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@Singleton
public class ScmGitRefManager {
  @Inject private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Inject private ScmServiceClient scmServiceClient;

  @Inject private SecretUtils secretUtils;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public ScmGitRefTaskResponseData fetchCodebaseMetadata(
      ScmConnector scmConnector, String connectorIdentifier, String branch, String prNumber, String tag) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorIdentifier),
        format("Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorIdentifier));

    if (isNotEmpty(branch)) {
      final GetLatestCommitResponse latestCommitResponse =
          Failsafe.with(retryPolicy)
              .get(() -> scmServiceClient.getLatestCommit(scmConnector, branch, null, scmBlockingStub));
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          latestCommitResponse.getStatus(), latestCommitResponse.getError());
      return ScmGitRefTaskResponseData.builder()
          .branch(branch)
          .repoUrl(scmConnector.getUrl())
          .gitRefType(GitRefType.LATEST_COMMIT_ID)
          .getLatestCommitResponse(latestCommitResponse.toByteArray())
          .build();
    } else if (isNotEmpty(prNumber)) {
      FindPRResponse findPRResponse =
          Failsafe.with(retryPolicy)
              .get(() -> scmServiceClient.findPR(scmConnector, Long.parseLong(prNumber), scmBlockingStub));
      ListCommitsInPRResponse listCommitsInPRResponse =
          Failsafe.with(retryPolicy)
              .get(() -> scmServiceClient.listCommitsInPR(scmConnector, Long.parseLong(prNumber), scmBlockingStub));
      return ScmGitRefTaskResponseData.builder()
          .gitRefType(GitRefType.PULL_REQUEST_WITH_COMMITS)
          .repoUrl(scmConnector.getUrl())
          .findPRResponse(findPRResponse.toByteArray())
          .listCommitsInPRResponse(listCommitsInPRResponse.toByteArray())
          .build();
    } else if (isNotEmpty(tag)) {
      final GetLatestCommitResponse latestCommitResponse =
          Failsafe.with(retryPolicy)
              .get(() -> scmServiceClient.getLatestCommit(scmConnector, null, tag, scmBlockingStub));
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          latestCommitResponse.getStatus(), latestCommitResponse.getError());
      return ScmGitRefTaskResponseData.builder()
          .repoUrl(scmConnector.getUrl())
          .gitRefType(GitRefType.LATEST_COMMIT_ID)
          .getLatestCommitResponse(latestCommitResponse.toByteArray())
          .build();
    } else {
      throw new CIStageExecutionException(
          "Manual codebase git task needs one of PR number, branch or tag. Please check if you are passing codebase inputs correctly.");
    }
  }

  public String getDefaultBranch(ScmConnector scmConnector, String connectorIdentifier) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to get default branch for connector: [%s], attempt: {}", connectorIdentifier),
        format("Failed call to get default branch for connector: [%s] after retrying {} times", connectorIdentifier));
    PageRequestDTO pageRequestDTO = PageRequestDTO.builder().build();
    final ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        Failsafe.with(retryPolicy)
            .get(() -> scmServiceClient.listBranchesWithDefault(scmConnector, pageRequestDTO, scmBlockingStub));
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
        listBranchesWithDefaultResponse.getStatus(), listBranchesWithDefaultResponse.getError());
    return listBranchesWithDefaultResponse.getDefaultBranch();
  }

  public ScmConnector getScmConnector(ConnectorDetails connectorDetails, String accountId, String repoName) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    String completeUrl = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, repoName);
    scmConnector.setUrl(completeUrl);

    final DecryptableEntity decryptedScmSpec =
        secretUtils.decryptViaManager(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
            connectorDetails.getEncryptedDataDetails(), accountId, connectorDetails.getIdentifier());
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptedScmSpec);
    return scmConnector;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
