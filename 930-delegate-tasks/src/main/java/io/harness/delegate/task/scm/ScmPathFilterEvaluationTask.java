/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.FindFilesInPRResponse;
import io.harness.product.ci.scm.proto.PRFile;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ScmPathFilterEvaluationTask extends AbstractDelegateRunnableTask {
  private static final ScmPathFilterEvaluationTaskResponse NOT_A_MATCH =
      ScmPathFilterEvaluationTaskResponse.builder().matched(false).build();
  private static final ScmPathFilterEvaluationTaskResponse MATCHED_ALL =
      ScmPathFilterEvaluationTaskResponse.builder().matched(false).build();

  @Inject ScmServiceClient scmServiceClient;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject SecretDecryptionService secretDecryptionService;

  public ScmPathFilterEvaluationTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmPathFilterEvaluationTaskParams filterQueryParams = (ScmPathFilterEvaluationTaskParams) parameters;

    try {
      decrypt(filterQueryParams);
      Set<String> changedFiles = getChangedFileset(filterQueryParams, filterQueryParams.getScmConnector());

      for (String filepath : changedFiles) {
        if (ConditionEvaluator.evaluate(filepath, filterQueryParams.getStandard(), filterQueryParams.getOperator())) {
          return ScmPathFilterEvaluationTaskResponse.builder().matched(true).build();
        }
      }
      return ScmPathFilterEvaluationTaskResponse.builder().matched(false).build();
    } catch (Exception e) {
      return ScmPathFilterEvaluationTaskResponse.builder().errorMessage(e.getMessage()).matched(false).build();
    }
  }

  private void decrypt(ScmPathFilterEvaluationTaskParams filterQueryParams) {
    ScmConnector connector = filterQueryParams.getScmConnector();
    if (GithubConnectorDTO.class.isAssignableFrom(connector.getClass())) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connector;
      secretDecryptionService.decrypt(
          githubConnectorDTO.getApiAccess().getSpec(), filterQueryParams.getEncryptedDataDetails());
    } else if (GitlabConnectorDTO.class.isAssignableFrom(connector.getClass())) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connector;
      secretDecryptionService.decrypt(
          gitlabConnectorDTO.getApiAccess().getSpec(), filterQueryParams.getEncryptedDataDetails());
    } else if (BitbucketConnectorDTO.class.isAssignableFrom(connector.getClass())) {
      BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connector;
      secretDecryptionService.decrypt(
          bitbucketConnectorDTO.getApiAccess().getSpec(), filterQueryParams.getEncryptedDataDetails());
    }
  }

  private Set<String> getChangedFileset(ScmPathFilterEvaluationTaskParams params, ScmConnector connector) {
    if (params.getPrNumber() != 0) {
      // PR case
      FindFilesInPRResponse findFilesResponse = scmDelegateClient.processScmRequest(
          c -> scmServiceClient.findFilesInPR(connector, params.getPrNumber(), SCMGrpc.newBlockingStub(c)));
      Set<String> filepaths = new HashSet<>();
      for (PRFile prfile : findFilesResponse.getFilesList()) {
        filepaths.add(prfile.getPath());
      }
      return filepaths;
    } else {
      CompareCommitsResponse compareCommitsResponse;
      if (isBitBucketOnPrem(connector)) {
        // push case
        compareCommitsResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.compareCommits(
                connector, params.getLatestCommit(), params.getPreviousCommit(), SCMGrpc.newBlockingStub(c)));
      } else {
        // push case
        compareCommitsResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.compareCommits(
                connector, params.getPreviousCommit(), params.getLatestCommit(), SCMGrpc.newBlockingStub(c)));
      }
      Set<String> filepaths = emptySet();
      if (compareCommitsResponse.getFilesCount() > 0) {
        filepaths = compareCommitsResponse.getFilesList().stream().map(prFile -> prFile.getPath()).collect(toSet());
      }

      if (filepaths.isEmpty()) {
        log.warn("there were 0 changedFiles for previous commit hash {} latest commit hash {} and  repo {}",
            params.getPreviousCommit(), params.getLatestCommit(), connector.getUrl());
      }
      return filepaths;
    }
  }

  private boolean isBitBucketOnPrem(ScmConnector connector) {
    return ConnectorType.BITBUCKET.equals(connector.getConnectorType())
        && !GitClientHelper.isBitBucketSAAS(connector.getUrl());
  }
}
