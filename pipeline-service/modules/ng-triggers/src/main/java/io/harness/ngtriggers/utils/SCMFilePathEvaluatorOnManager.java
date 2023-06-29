/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.FindFilesInPRResponse;
import io.harness.product.ci.scm.proto.PRFile;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmServiceClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class SCMFilePathEvaluatorOnManager extends SCMFilePathEvaluator {
  private SecretDecryptor secretDecryptor;
  private SCMGrpc.SCMBlockingStub scmBlockingStub;
  private ScmServiceClient scmServiceClient;

  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  @Override
  public ScmPathFilterEvaluationTaskResponse execute(FilterRequestData filterRequestData,
      TriggerEventDataCondition pathCondition, ConnectorDetails connectorDetails, ScmConnector scmConnector) {
    try {
      ScmPathFilterEvaluationTaskParams params =
          getScmPathFilterEvaluationTaskParams(filterRequestData, pathCondition, connectorDetails, scmConnector);
      decrypt(scmConnector, connectorDetails.getEncryptedDataDetails());
      Set<String> changedFiles = getChangedFileset(params, scmConnector, connectorDetails.getIdentifier());

      for (String filepath : changedFiles) {
        if (ConditionEvaluator.evaluate(filepath, params.getStandard(), params.getOperator())) {
          return ScmPathFilterEvaluationTaskResponse.builder().matched(true).build();
        }
      }
      return ScmPathFilterEvaluationTaskResponse.builder().matched(false).build();
    } catch (Exception e) {
      return ScmPathFilterEvaluationTaskResponse.builder().errorMessage(e.getMessage()).matched(false).build();
    }
  }

  @VisibleForTesting
  public Set<String> getChangedFileset(
      ScmPathFilterEvaluationTaskParams params, ScmConnector connector, String connectorIdentifier) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorIdentifier),
        format("Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorIdentifier));

    Set<String> filePaths = new HashSet<>();

    if (params.getPrNumber() > 0) {
      FindFilesInPRResponse findFilesResponse =
          Failsafe.with(retryPolicy)
              .get(() -> scmServiceClient.findFilesInPR(connector, params.getPrNumber(), scmBlockingStub));

      if (findFilesResponse != null && findFilesResponse.getFilesCount() > 0) {
        filePaths = findFilesResponse.getFilesList().stream().map(PRFile::getPath).collect(toSet());
      }
    } else {
      CompareCommitsResponse compareCommitsResponse;
      if (isBitBucketOnPrem(connector)) {
        compareCommitsResponse = Failsafe.with(retryPolicy)
                                     .get(()
                                              -> scmServiceClient.compareCommits(connector, params.getLatestCommit(),
                                                  params.getPreviousCommit(), scmBlockingStub));
      } else {
        compareCommitsResponse = Failsafe.with(retryPolicy)
                                     .get(()
                                              -> scmServiceClient.compareCommits(connector, params.getPreviousCommit(),
                                                  params.getLatestCommit(), scmBlockingStub));
      }
      if (compareCommitsResponse != null && compareCommitsResponse.getFilesCount() > 0) {
        filePaths = compareCommitsResponse.getFilesList().stream().map(PRFile::getPath).collect(toSet());
      }
    }

    if (filePaths.isEmpty()) {
      log.warn(
          "there were 0 changedFiles for previous commit hash {} latest commit hash {} and  repo {} connectorIdentifier {}",
          params.getPreviousCommit(), params.getLatestCommit(), connector.getUrl(), connectorIdentifier);
    }
    return filePaths;
  }

  public void decrypt(ScmConnector connector, List<EncryptedDataDetail> encryptedDataDetails) {
    final DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(connector), encryptedDataDetails);
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(connector, decryptableEntity);
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

  public boolean isBitBucketOnPrem(ScmConnector connector) {
    return ConnectorType.BITBUCKET.equals(connector.getConnectorType())
        && !GitClientHelper.isBitBucketSAAS(connector.getUrl());
  }
}
