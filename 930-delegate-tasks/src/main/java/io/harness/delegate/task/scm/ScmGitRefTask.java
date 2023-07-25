/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.RepoFilterParamsDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ListCommitsResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.DX)
public class ScmGitRefTask extends AbstractDelegateRunnableTask {
  @Inject SecretDecryptionService secretDecryptionService;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;

  public ScmGitRefTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ScmGitRefTaskParams scmGitRefTaskParams = (ScmGitRefTaskParams) parameters;
    secretDecryptionService.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmGitRefTaskParams.getScmConnector()),
        scmGitRefTaskParams.getEncryptedDataDetails());
    switch (scmGitRefTaskParams.getGitRefType()) {
      case BRANCH: {
        ListBranchesResponse listBranchesResponse = scmDelegateClient.processScmRequest(
            c -> scmServiceClient.listBranches(scmGitRefTaskParams.getScmConnector(), SCMGrpc.newBlockingStub(c)));
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
            listBranchesResponse.getStatus(), listBranchesResponse.getError());
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .listBranchesResponse(listBranchesResponse.toByteArray())
            .build();
      }
      case COMMIT: {
        ListCommitsResponse listCommitsResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listCommits(
                scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getBranch(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .branch(scmGitRefTaskParams.getBranch())
            .listCommitsResponse(listCommitsResponse.toByteArray())
            .build();
      }
      case PULL_REQUEST_COMMITS: {
        ListCommitsInPRResponse listCommitsInPRResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listCommitsInPR(
                scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getPrNumber(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .listCommitsInPRResponse(listCommitsInPRResponse.toByteArray())
            .build();
      }
      case PULL_REQUEST_WITH_COMMITS: {
        FindPRResponse findPRResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.findPR(
                scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getPrNumber(), SCMGrpc.newBlockingStub(c)));
        ListCommitsInPRResponse listCommitsInPRResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listCommitsInPR(
                scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getPrNumber(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .repoUrl(scmGitRefTaskParams.getScmConnector().getUrl())
            .findPRResponse(findPRResponse.toByteArray())
            .listCommitsInPRResponse(listCommitsInPRResponse.toByteArray())
            .build();
      }
      case COMPARE_COMMITS: {
        CompareCommitsResponse compareCommitsResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.compareCommits(scmGitRefTaskParams.getScmConnector(),
                scmGitRefTaskParams.getInitialCommitId(), scmGitRefTaskParams.getFinalCommitId(),
                SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .compareCommitsResponse(compareCommitsResponse.toByteArray())
            .build();
      }
      case LATEST_COMMIT_ID: {
        final GetLatestCommitResponse latestCommitResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getLatestCommit(scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getBranch(),
                scmGitRefTaskParams.getRef(), SCMGrpc.newBlockingStub(c)));
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
            latestCommitResponse.getStatus(), latestCommitResponse.getError());
        return ScmGitRefTaskResponseData.builder()
            .branch(scmGitRefTaskParams.getBranch())
            .repoUrl(scmGitRefTaskParams.getScmConnector().getUrl())
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getLatestCommitResponse(latestCommitResponse.toByteArray())
            .build();
      }
      case FIND_COMMIT: {
        final FindCommitResponse commitResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.findCommit(
                scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getRef(), SCMGrpc.newBlockingStub(c)));
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
            commitResponse.getStatus(), commitResponse.getError());
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .findCommitResponse(commitResponse.toByteArray())
            .build();
      }
      case CREATE_BRANCH: {
        final CreateBranchResponse createBranchResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createNewBranch(scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getBranch(),
                scmGitRefTaskParams.getBaseBranch(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .createBranchResponse(createBranchResponse.toByteArray())
            .build();
      }
      case REPOSITORY_LIST: {
        final GetUserReposResponse getUserReposResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getUserRepos(scmGitRefTaskParams.getScmConnector(),
                scmGitRefTaskParams.getPageRequest(), SCMGrpc.newBlockingStub(c),
                scmGitRefTaskParams.getRepoFilterDelegateTaskParams() != null
                    ? RepoFilterParamsDTO.builder()
                          .repoName(scmGitRefTaskParams.getRepoFilterDelegateTaskParams().getRepoName())
                          .userName(scmGitRefTaskParams.getRepoFilterDelegateTaskParams().getUserName())
                          .build()
                    : RepoFilterParamsDTO.builder().build()));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getUserReposResponse(getUserReposResponse.toByteArray())
            .build();
      }
      case BRANCH_LIST_WITH_DEFAULT_BRANCH: {
        final ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.listBranchesWithDefault(scmGitRefTaskParams.getScmConnector(),
                scmGitRefTaskParams.getPageRequest(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getListBranchesWithDefaultResponse(listBranchesWithDefaultResponse.toByteArray())
            .build();
      }
      case REPOSITORY_DETAILS: {
        final GetUserRepoResponse getUserRepoResponse = scmDelegateClient.processScmRequest(
            c -> scmServiceClient.getRepoDetails(scmGitRefTaskParams.getScmConnector(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getUserRepoResponse(getUserRepoResponse.toByteArray())
            .build();
      }
      case CREATE_BRANCH_V2: {
        final CreateBranchResponse createBranchResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.createNewBranchV2(scmGitRefTaskParams.getScmConnector(),
                scmGitRefTaskParams.getBranch(), scmGitRefTaskParams.getBaseBranch(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .createBranchResponse(createBranchResponse.toByteArray())
            .build();
      }
      case GET_LATEST_COMMIT_ON_FILE: {
        final GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getLatestCommitOnFile(scmGitRefTaskParams.getScmConnector(),
                scmGitRefTaskParams.getBranch(), scmGitRefTaskParams.getFilePath(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getLatestCommitOnFileResponse(getLatestCommitOnFileResponse.toByteArray())
            .build();
      }
      case LATEST_COMMIT_V2: {
        GetLatestCommitResponse latestCommitResponse = scmDelegateClient.processScmRequest(c
            -> scmServiceClient.getLatestCommit(scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getBranch(),
                scmGitRefTaskParams.getRef(), SCMGrpc.newBlockingStub(c)));
        return ScmGitRefTaskResponseData.builder()
            .gitRefType(scmGitRefTaskParams.getGitRefType())
            .getLatestCommitResponse(latestCommitResponse.toByteArray())
            .build();
      }
      default:
        throw new UnknownEnumTypeException("GitRefType", scmGitRefTaskParams.getGitRefType().toString());
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
