/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.git.GitClientHelper.isBitBucketSAAS;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ContentType;
import io.harness.beans.FileContentBatchResponse;
import io.harness.beans.FileGitDetails;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.constants.Constants;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ConnectException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.GitClientHelper;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.logger.RepoBranchLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CompareCommitsRequest;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreateBranchRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRRequest;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookRequest;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileRequest;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.DeleteWebhookRequest;
import io.harness.product.ci.scm.proto.DeleteWebhookResponse;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.FileModifyRequest;
import io.harness.product.ci.scm.proto.FindCommitRequest;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.FindFilesInCommitRequest;
import io.harness.product.ci.scm.proto.FindFilesInCommitResponse;
import io.harness.product.ci.scm.proto.FindFilesInPRRequest;
import io.harness.product.ci.scm.proto.FindFilesInPRResponse;
import io.harness.product.ci.scm.proto.FindPRRequest;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GenerateYamlRequest;
import io.harness.product.ci.scm.proto.GenerateYamlResponse;
import io.harness.product.ci.scm.proto.GetAuthenticatedUserRequest;
import io.harness.product.ci.scm.proto.GetAuthenticatedUserResponse;
import io.harness.product.ci.scm.proto.GetBatchFileRequest;
import io.harness.product.ci.scm.proto.GetFileRequest;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileRequest;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitRequest;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetLatestFileRequest;
import io.harness.product.ci.scm.proto.GetUserRepoRequest;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposRequest;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.IsLatestFileRequest;
import io.harness.product.ci.scm.proto.IsLatestFileResponse;
import io.harness.product.ci.scm.proto.ListBranchesRequest;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultRequest;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRRequest;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ListCommitsRequest;
import io.harness.product.ci.scm.proto.ListCommitsResponse;
import io.harness.product.ci.scm.proto.ListWebhooksRequest;
import io.harness.product.ci.scm.proto.ListWebhooksResponse;
import io.harness.product.ci.scm.proto.NativeEvents;
import io.harness.product.ci.scm.proto.PRFile;
import io.harness.product.ci.scm.proto.PageRequest;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.RefreshTokenRequest;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.service.ScmServiceClient;
import io.harness.utils.FilePathUtils;
import io.harness.utils.ScmGrpcClientUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(DX)
public class ScmServiceClientImpl implements ScmServiceClient {
  ScmGitProviderMapper scmGitProviderMapper;
  ScmGitProviderHelper scmGitProviderHelper;

  @Override
  public CreateFileResponse createFile(ScmConnector scmConnector, GitFileDetails gitFileDetails,
      SCMGrpc.SCMBlockingStub scmBlockingStub, boolean useGitClient) {
    FileModifyRequest fileModifyRequest =
        getFileModifyRequest(scmConnector, gitFileDetails).setUseGitClient(useGitClient).build();
    CreateFileResponse createFileResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createFile, fileModifyRequest);
    if (ScmResponseStatusUtils.isSuccessfulCreateResponse(createFileResponse.getStatus())
        && isEmpty(createFileResponse.getCommitId())) {
      GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = getLatestCommitOnFile(
          scmConnector, scmBlockingStub, gitFileDetails.getBranch(), gitFileDetails.getFilePath());
      if (isEmpty(getLatestCommitOnFileResponse.getError())) {
        return CreateFileResponse.newBuilder(createFileResponse)
            .setCommitId(getLatestCommitOnFileResponse.getCommitId())
            .build();
      } else {
        // In case commit id is empty for any reason, we treat this as an error case even if file got created on git
        return CreateFileResponse.newBuilder()
            .setStatus(Constants.SCM_INTERNAL_SERVER_ERROR_CODE)
            .setError(Constants.SCM_INTERNAL_SERVER_ERROR_MESSAGE)
            .build();
      }
    }
    return createFileResponse;
  }

  private FileModifyRequest.Builder getFileModifyRequest(ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector, true);
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    return FileModifyRequest.newBuilder()
        .setBranch(gitFileDetails.getBranch())
        .setSlug(slug)
        .setPath(gitFileDetails.getFilePath())
        .setBranch(gitFileDetails.getBranch())
        .setContent(gitFileDetails.getFileContent())
        .setMessage(gitFileDetails.getCommitMessage())
        .setProvider(gitProvider)
        .setSignature(Signature.newBuilder()
                          .setEmail(gitFileDetails.getUserEmail())
                          .setName(gitFileDetails.getUserName())
                          .build());
  }

  @Override
  public UpdateFileResponse updateFile(ScmConnector scmConnector, GitFileDetails gitFileDetails,
      SCMGrpc.SCMBlockingStub scmBlockingStub, boolean useGitClient) {
    Optional<UpdateFileResponse> preChecksStatus =
        runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub, gitFileDetails);
    if (preChecksStatus.isPresent()) {
      return preChecksStatus.get();
    }

    final FileModifyRequest.Builder fileModifyRequestBuilder = getFileModifyRequest(scmConnector, gitFileDetails);
    handleUpdateFileRequestIfBBOnPrem(fileModifyRequestBuilder, scmConnector, gitFileDetails);
    final FileModifyRequest fileModifyRequest =
        fileModifyRequestBuilder.setBlobId(Strings.nullToEmpty(gitFileDetails.getOldFileSha()))
            .setCommitId(Strings.nullToEmpty(gitFileDetails.getCommitId()))
            .setUseGitClient(useGitClient)
            .build();
    UpdateFileResponse updateFileResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::updateFile, fileModifyRequest);

    if (ScmResponseStatusUtils.isSuccessResponse(updateFileResponse.getStatus())
        && isEmpty(updateFileResponse.getCommitId())) {
      GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = getLatestCommitOnFile(
          scmConnector, scmBlockingStub, gitFileDetails.getBranch(), gitFileDetails.getFilePath());
      if (isNotEmpty(getLatestCommitOnFileResponse.getError())) {
        return UpdateFileResponse.newBuilder()
            .setStatus(Constants.SCM_BAD_RESPONSE_ERROR_CODE)
            .setError(getLatestCommitOnFileResponse.getError())
            .build();
      }
      return UpdateFileResponse.newBuilder(updateFileResponse)
          .setCommitId(getLatestCommitOnFileResponse.getCommitId())
          .build();
    }
    return updateFileResponse;
  }

  @Override
  public DeleteFileResponse deleteFile(
      ScmConnector scmConnector, GitFileDetails gitFileDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector, true);
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    final DeleteFileRequest deleteFileRequest = DeleteFileRequest.newBuilder()
                                                    .setBranch(gitFileDetails.getBranch())
                                                    .setPath(gitFileDetails.getFilePath())
                                                    .setProvider(gitProvider)
                                                    .setSlug(slug)
                                                    .setBlobId(gitFileDetails.getOldFileSha())
                                                    .setBranch(gitFileDetails.getBranch())
                                                    .setMessage(gitFileDetails.getCommitMessage())
                                                    .setSignature(Signature.newBuilder()
                                                                      .setEmail(gitFileDetails.getUserEmail())
                                                                      .setName(gitFileDetails.getUserName())
                                                                      .build())
                                                    .build();

    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::deleteFile, deleteFileRequest);
  }

  @Override
  public FileContent getFileContent(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("getFileContent")) {
      Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
      String slug = scmGitProviderHelper.getSlug(scmConnector);
      final GetFileRequest.Builder gitFileRequestBuilder =
          GetFileRequest.newBuilder().setPath(gitFilePathDetails.getFilePath()).setProvider(gitProvider).setSlug(slug);
      if (isNotEmpty(gitFilePathDetails.getBranch())) {
        if (checkIfBranchIsHavingSlashForBB(scmConnector, gitFilePathDetails.getBranch())) {
          GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = getLatestCommitOnFile(
              scmConnector, scmBlockingStub, gitFilePathDetails.getBranch(), gitFilePathDetails.getFilePath());
          if (isNotEmpty(getLatestCommitOnFileResponse.getError())) {
            return FileContent.newBuilder().setStatus(400).setError(getLatestCommitOnFileResponse.getError()).build();
          }
          gitFileRequestBuilder.setRef(getLatestCommitOnFileResponse.getCommitId());
        } else {
          gitFileRequestBuilder.setBranch(gitFilePathDetails.getBranch());
        }
      } else if (isNotEmpty(gitFilePathDetails.getRef())) {
        gitFileRequestBuilder.setRef(gitFilePathDetails.getRef());
      }
      return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getFile, gitFileRequestBuilder.build());
    }
  }

  private boolean checkIfBranchIsHavingSlashForBB(ScmConnector scmConnector, String branchName) {
    return ConnectorType.BITBUCKET.equals(scmConnector.getConnectorType()) && branchName.contains("/");
  }

  private FileBatchContentResponse getContentOfFiles(List<String> filePaths, String slug, Provider gitProvider,
      String ref, SCMGrpc.SCMBlockingStub scmBlockingStub, boolean base64Encoding) {
    GetBatchFileRequest batchFileRequest = createBatchFileRequest(filePaths, slug, ref, gitProvider, base64Encoding);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getBatchFile, batchFileRequest);
  }

  private FileBatchContentResponse getContentOfFilesV2(
      List<String> filePaths, String slug, Provider gitProvider, String ref, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    GetBatchFileRequest batchFileRequest = createBatchFileRequest(filePaths, slug, ref, gitProvider, true);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getBatchFile, batchFileRequest);
  }

  @Override
  public FileContent getLatestFile(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    GetLatestFileRequest getLatestFileRequest = getLatestFileRequestObject(scmConnector, gitFilePathDetails);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestFile, getLatestFileRequest);
  }

  @Override
  public IsLatestFileResponse isLatestFile(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails,
      FileContent fileContent, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    IsLatestFileRequest isLatestFileRequest = getIsLatestFileRequest(scmConnector, gitFilePathDetails, fileContent);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::isLatestFile, isLatestFileRequest);
  }

  @Override
  public FileContent pushFile(
      ScmConnector scmConnector, GitFileDetails gitFileDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FileModifyRequest fileModifyRequest = getFileModifyRequest(scmConnector, gitFileDetails).build();
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::pushFile, fileModifyRequest);
  }

  @Override
  public FindFilesInBranchResponse findFilesInBranch(
      ScmConnector scmConnector, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FindFilesInBranchRequest findFilesInBranchRequest = getFindFilesInBranchRequest(scmConnector, branch);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findFilesInBranch, findFilesInBranchRequest);
  }

  @Override
  public FindFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FindFilesInCommitRequest findFilesInCommitRequest = getFindFilesInCommitRequest(scmConnector, gitFilePathDetails);
    // still to be resolved
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findFilesInCommit, findFilesInCommitRequest);
  }

  @Override
  public ListFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, ListFilesInCommitRequest request, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FindFilesInCommitResponse response = null;
    FindFilesInCommitRequest.Builder findFilesInCommitRequestBuilder =
        initFindFilesInCommitRequest(scmConnector, request);
    List<FileGitDetails> fileGitDetailsList = new ArrayList<>();

    do {
      if (response != null) {
        findFilesInCommitRequestBuilder.setPagination(PageRequest.newBuilder()
                                                          .setPage(response.getPagination().getNext())
                                                          .setUrl(response.getPagination().getNextUrl())
                                                          .build());
      }
      response = ScmGrpcClientUtils.retryAndProcessException(
          scmBlockingStub::findFilesInCommit, findFilesInCommitRequestBuilder.build());
      response.getFileList().forEach(file
          -> fileGitDetailsList.add(FileGitDetails.builder()
                                        .blobId(file.getBlobId())
                                        .commitId(file.getCommitId())
                                        .contentType(ContentType.mapFromScmProtoValue(file.getContentType()))
                                        .path(file.getPath())
                                        .build()));
      if (isFailureResponse(response.getStatus())) {
        return ListFilesInCommitResponse.builder().statusCode(response.getStatus()).error(response.getError()).build();
      }
    } while (response.getPagination().getNext() != 0 || isNotEmpty(response.getPagination().getNextUrl()));

    return ListFilesInCommitResponse.builder()
        .statusCode(Constants.HTTP_SUCCESS_STATUS_CODE)
        .fileGitDetailsList(fileGitDetailsList)
        .build();
  }

  @Override
  public FindFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, String commitHash, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FindFilesInCommitRequest findFilesInCommitRequest = getFindFilesInCommitRequest(scmConnector, commitHash);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findFilesInCommit, findFilesInCommitRequest);
  }

  @Override
  public FindFilesInPRResponse findFilesInPR(
      ScmConnector scmConnector, int prNumber, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    FindFilesInPRRequest findFilesInPRRequest = getFindFilesInPRRequest(scmConnector, prNumber);
    // still to be resolved
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findFilesInPR, findFilesInPRRequest);
  }

  @Override
  public GetLatestCommitResponse getLatestCommit(
      ScmConnector scmConnector, String branch, String ref, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    GetLatestCommitRequest getLatestCommitRequest = getLatestCommitRequestObject(scmConnector, branch, ref);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommit, getLatestCommitRequest);
  }

  @Override
  public ListBranchesResponse listBranches(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    final String slug = scmGitProviderHelper.getSlug(scmConnector);
    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    int pageNumber = 1;
    ListBranchesResponse branchListResponse = null;
    List<String> branchesList = new ArrayList<>();
    do {
      ListBranchesRequest listBranchesRequest = ListBranchesRequest.newBuilder()
                                                    .setSlug(slug)
                                                    .setProvider(provider)
                                                    .setPagination(PageRequest.newBuilder().setPage(pageNumber).build())
                                                    .build();
      branchListResponse =
          ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::listBranches, listBranchesRequest);
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          branchListResponse.getStatus(), branchListResponse.getError());
      branchesList.addAll(branchListResponse.getBranchesList());
      pageNumber = branchListResponse.getPagination().getNext();
    } while (hasMoreBranches(branchListResponse));
    return ListBranchesResponse.newBuilder().addAllBranches(branchesList).build();
  }

  private boolean hasMoreBranches(ListBranchesResponse branchList) {
    return branchList != null && branchList.getPagination() != null && branchList.getPagination().getNext() != 0;
  }

  @Override
  public ListBranchesWithDefaultResponse listBranchesWithDefault(
      ScmConnector scmConnector, PageRequestDTO pageRequest, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    final String slug = scmGitProviderHelper.getSlug(scmConnector);
    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    int pageNumber = 1;
    ListBranchesWithDefaultRequest listBranchesWithDefaultRequest =
        ListBranchesWithDefaultRequest.newBuilder()
            .setSlug(slug)
            .setProvider(provider)
            .setPagination(PageRequest.newBuilder().setPage(pageNumber).build())
            .build();
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse = ScmGrpcClientUtils.retryAndProcessException(
        scmBlockingStub::listBranchesWithDefault, listBranchesWithDefaultRequest);
    if (isNotEmpty(listBranchesWithDefaultResponse.getError())) {
      return listBranchesWithDefaultResponse;
    }
    return listMoreBranches(scmConnector, listBranchesWithDefaultResponse, pageRequest.getPageSize(), scmBlockingStub);
  }

  @VisibleForTesting
  ListBranchesWithDefaultResponse listMoreBranches(ScmConnector scmConnector,
      ListBranchesWithDefaultResponse listBranchesWithDefaultResponse, int pageSize,
      SCMGrpc.SCMBlockingStub scmBlockingStub) {
    final String slug = scmGitProviderHelper.getSlug(scmConnector);
    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    ListBranchesResponse listBranchesResponse = null;
    int branchCount = listBranchesWithDefaultResponse.getBranchesCount();
    List<String> branchesList = new ArrayList<>();

    branchesList.addAll(new ArrayList<>(listBranchesWithDefaultResponse.getBranchesList()));
    int pageNumber = listBranchesWithDefaultResponse.getPagination().getNext();
    while (pageNumber != 0 && branchCount <= pageSize) {
      ListBranchesRequest listBranchesRequest = ListBranchesRequest.newBuilder()
                                                    .setSlug(slug)
                                                    .setProvider(provider)
                                                    .setPagination(PageRequest.newBuilder().setPage(pageNumber).build())
                                                    .build();
      listBranchesResponse =
          ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::listBranches, listBranchesRequest);
      if (isNotEmpty(listBranchesResponse.getError())) {
        return ListBranchesWithDefaultResponse.newBuilder()
            .setStatus(listBranchesResponse.getStatus())
            .setError(listBranchesResponse.getError())
            .build();
      }
      branchesList.addAll(new ArrayList<>(listBranchesResponse.getBranchesList()));
      pageNumber = listBranchesResponse.getPagination().getNext();
      branchCount += listBranchesResponse.getBranchesCount();
    }
    return prepareListBranchResponse(listBranchesWithDefaultResponse.getDefaultBranch(), branchesList, pageSize);
  }

  private ListBranchesWithDefaultResponse prepareListBranchResponse(
      String defaultBranch, List<String> branchesList, int pageSize) {
    if (branchesList.size() > pageSize) {
      return ListBranchesWithDefaultResponse.newBuilder()
          .setStatus(200)
          .setDefaultBranch(defaultBranch)
          .addAllBranches(branchesList.subList(0, pageSize))
          .build();
    }
    return ListBranchesWithDefaultResponse.newBuilder()
        .setStatus(200)
        .setDefaultBranch(defaultBranch)
        .addAllBranches(branchesList)
        .build();
  }

  @Override
  public ListCommitsResponse listCommits(
      ScmConnector scmConnector, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    ListCommitsRequest listCommitsRequest = getListCommitsRequest(scmConnector, branch);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::listCommits, listCommitsRequest);
  }

  @Override
  public ListCommitsInPRResponse listCommitsInPR(
      ScmConnector scmConnector, long prNumber, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    final String slug = scmGitProviderHelper.getSlug(scmConnector);
    final Provider provider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    int pageNumber = 1;
    if (isBitBucketSAAS(scmConnector.getUrl())) {
      pageNumber = 0;
    }
    ListCommitsInPRResponse commitsInPRResponse;
    List<Commit> commitList = new ArrayList<>();
    do {
      ListCommitsInPRRequest listCommitsInPRRequest =
          ListCommitsInPRRequest.newBuilder()
              .setSlug(slug)
              .setNumber(prNumber)
              .setProvider(provider)
              .setPagination(PageRequest.newBuilder().setPage(pageNumber).build())
              .build();
      commitsInPRResponse =
          ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::listCommitsInPR, listCommitsInPRRequest);
      commitList.addAll(commitsInPRResponse.getCommitsList());
      pageNumber = commitsInPRResponse.getPagination().getNext();
    } while (pageNumber != 0);
    return ListCommitsInPRResponse.newBuilder().addAllCommits(commitList).build();
  }

  private GetBatchFileRequest createBatchFileRequest(
      List<String> harnessRelatedFilePaths, String slug, String ref, Provider gitProvider, boolean getBase64Content) {
    List<GetFileRequest> getBatchFileRequests = new ArrayList<>();
    // todo @deepak: Add the pagination logic to get the list of file content, once scm provides support
    for (String path : emptyIfNull(harnessRelatedFilePaths)) {
      GetFileRequest getFileRequest = GetFileRequest.newBuilder()
                                          .setSlug(slug)
                                          .setProvider(gitProvider)
                                          .setRef(ref)
                                          .setPath(path)
                                          .setBase64Encoding(getBase64Content)
                                          .build();
      getBatchFileRequests.add(getFileRequest);
    }
    return GetBatchFileRequest.newBuilder().addAllFindRequest(getBatchFileRequests).build();
  }

  private List<String> getPathsOfFilesBelongingToHarness(FindFilesInBranchResponse filesInBranch) {
    List<FileChange> fileList = filesInBranch == null ? Collections.emptyList() : filesInBranch.getFileList();
    if (isEmpty(fileList)) {
      return Collections.emptyList();
    }
    // todo @deepak: Filter and only get files which belongs to harness
    return fileList.stream().map(fileChange -> fileChange.getPath()).collect(toList());
  }

  private GetLatestFileRequest getLatestFileRequestObject(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return GetLatestFileRequest.newBuilder()
        .setBranch(gitFilePathDetails.getBranch())
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .setPath(gitFilePathDetails.getFilePath())
        .build();
  }

  private IsLatestFileRequest getIsLatestFileRequest(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, FileContent fileContent) {
    return IsLatestFileRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setPath(gitFilePathDetails.getFilePath())
        .setBranch(gitFilePathDetails.getBranch())
        .setBlobId(fileContent.getBlobId())
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private FindFilesInBranchRequest getFindFilesInBranchRequest(ScmConnector scmConnector, String branch) {
    return FindFilesInBranchRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setBranch(branch)
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private FindFilesInPRRequest getFindFilesInPRRequest(ScmConnector scmConnector, int prNumber) {
    return FindFilesInPRRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setNumber(prNumber)
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private FindFilesInCommitRequest getFindFilesInCommitRequest(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return FindFilesInCommitRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setRef(gitFilePathDetails.getBranch()) // How to get Ref for files????????????
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private FindFilesInCommitRequest.Builder getFindFilesInCommitRequestBuilder(
      ScmConnector scmConnector, ListFilesInCommitRequest request) {
    return FindFilesInCommitRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setRef(request.getRef())
        .setPath(request.getFileDirectoryPath())
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector));
  }

  private FindFilesInCommitRequest getFindFilesInCommitRequest(ScmConnector scmConnector, String commitHash) {
    return FindFilesInCommitRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setRef(commitHash)
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private GetLatestCommitRequest getLatestCommitRequestObject(ScmConnector scmConnector, String branch, String ref) {
    final GetLatestCommitRequest.Builder getLatestCommitRequestBuilder =
        GetLatestCommitRequest.newBuilder()
            .setSlug(scmGitProviderHelper.getSlug(scmConnector))
            .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector));

    if (isNotEmpty(branch)) {
      getLatestCommitRequestBuilder.setBranch(branch);
    } else if (isNotEmpty(ref)) {
      getLatestCommitRequestBuilder.setRef(ref);
    }

    return getLatestCommitRequestBuilder.build();
  }

  private ListBranchesRequest getListBranchesRequest(ScmConnector scmConnector) {
    return ListBranchesRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private ListCommitsRequest getListCommitsRequest(ScmConnector scmConnector, String branch) {
    return ListCommitsRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setBranch(branch)
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  private ListCommitsInPRRequest getListCommitsInPRRequest(ScmConnector scmConnector, long prNumber) {
    return ListCommitsInPRRequest.newBuilder()
        .setSlug(scmGitProviderHelper.getSlug(scmConnector))
        .setNumber(prNumber)
        .setProvider(scmGitProviderMapper.mapToSCMGitProvider(scmConnector))
        .build();
  }

  @Override
  public FileContentBatchResponse listFiles(
      ScmConnector connector, Set<String> foldersList, String branchName, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(connector);
    String slug = scmGitProviderHelper.getSlug(connector);
    final GetLatestCommitResponse latestCommitResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommit,
            GetLatestCommitRequest.newBuilder().setBranch(branchName).setProvider(gitProvider).setSlug(slug).build());
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
        latestCommitResponse.getStatus(), latestCommitResponse.getError());
    String latestCommitId = latestCommitResponse.getCommitId();
    try (AutoLogContext ignore = new RepoBranchLogContext(slug, branchName, latestCommitId, OVERRIDE_ERROR)) {
      List<String> getFilesWhichArePartOfHarness =
          getFileNames(foldersList, slug, gitProvider, branchName, latestCommitId, scmBlockingStub);
      final FileBatchContentResponse contentOfFiles =
          getContentOfFiles(getFilesWhichArePartOfHarness, slug, gitProvider, latestCommitId, scmBlockingStub, false);
      return FileContentBatchResponse.builder()
          .fileBatchContentResponse(contentOfFiles)
          .commitId(latestCommitId)
          .build();
    }
  }

  @Override
  public FileContentBatchResponse listFilesV2(
      ScmConnector connector, Set<String> foldersList, String branchName, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    log.info("Fetching Optimized files via V2 API and base64 encoded data");
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(connector);
    String slug = scmGitProviderHelper.getSlug(connector);
    final GetLatestCommitResponse latestCommitResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommit,
            GetLatestCommitRequest.newBuilder().setBranch(branchName).setProvider(gitProvider).setSlug(slug).build());
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
        latestCommitResponse.getStatus(), latestCommitResponse.getError());
    String latestCommitId = latestCommitResponse.getCommitId();
    try (AutoLogContext ignore = new RepoBranchLogContext(slug, branchName, latestCommitId, OVERRIDE_ERROR)) {
      List<String> getFilesWhichArePartOfHarness =
          getFileNames(foldersList, slug, gitProvider, branchName, latestCommitId, scmBlockingStub);
      final FileBatchContentResponse contentOfFiles =
          getContentOfFilesV2(getFilesWhichArePartOfHarness, slug, gitProvider, latestCommitId, scmBlockingStub);
      return FileContentBatchResponse.builder()
          .fileBatchContentResponse(contentOfFiles)
          .commitId(latestCommitId)
          .build();
    }
  }

  @Override
  public FileContentBatchResponse listFoldersFilesByCommitId(
      ScmConnector connector, Set<String> foldersList, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(connector);
    String slug = scmGitProviderHelper.getSlug(connector);

    try (AutoLogContext ignore = new RepoBranchLogContext(slug, null, commitId, OVERRIDE_ERROR)) {
      List<String> getFilesWhichArePartOfHarness =
          getFileNames(foldersList, slug, gitProvider, null, commitId, scmBlockingStub);
      final FileBatchContentResponse contentOfFiles =
          getContentOfFilesV2(getFilesWhichArePartOfHarness, slug, gitProvider, commitId, scmBlockingStub);
      return FileContentBatchResponse.builder().fileBatchContentResponse(contentOfFiles).commitId(commitId).build();
    }
  }

  private List<String> getFileNames(Set<String> foldersList, String slug, Provider gitProvider, String branch,
      String ref, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    GetFilesInFolderForkTask getFilesInFolderTask = GetFilesInFolderForkTask.builder()
                                                        .provider(gitProvider)
                                                        .scmBlockingStub(scmBlockingStub)
                                                        .slug(slug)
                                                        .ref(ref)
                                                        .build();
    List<FileChange> forkJoinTask = getFilesInFolderTask.createForkJoinTask(foldersList);
    return emptyIfNull(forkJoinTask).stream().map(FileChange::getPath).collect(toList());
  }

  // Find content of files for given files paths in the branch at latest commit
  @Override
  public FileContentBatchResponse listFilesByFilePaths(
      ScmConnector connector, List<String> filePaths, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(connector);
    String slug = scmGitProviderHelper.getSlug(connector);
    final GetLatestCommitResponse latestCommit =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommit,
            GetLatestCommitRequest.newBuilder().setBranch(branch).setProvider(gitProvider).setSlug(slug).build());
    return processListFilesByFilePaths(connector, filePaths, branch, latestCommit.getCommitId(), scmBlockingStub);
  }

  // Find content of files for given files paths in the branch at given commit
  @Override
  public FileContentBatchResponse listFilesByCommitId(
      ScmConnector connector, List<String> filePaths, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    return processListFilesByFilePaths(connector, filePaths, null, commitId, scmBlockingStub);
  }

  @Override
  public CreateBranchResponse createNewBranch(
      ScmConnector scmConnector, String branch, String baseBranchName, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    branch = FilePathUtils.removeStartingAndEndingSlash(branch);
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    String latestShaOfBranch = getLatestShaOfBranch(slug, gitProvider, baseBranchName, scmBlockingStub);
    final CreateBranchResponse createBranchResponse =
        createNewBranchFromDefault(slug, gitProvider, branch, latestShaOfBranch, scmBlockingStub);
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          createBranchResponse.getStatus(), createBranchResponse.getError());
    } catch (WingsException e) {
      log.error("SCM create branch ops error : {}", e.getMessage());
      final WingsException cause = ExceptionUtils.cause(ErrorCode.SCM_UNPROCESSABLE_ENTITY, e);
      if (cause != null) {
        throw new InvalidRequestException(String.format("Action could not be completed. Possible reasons can be:\n"
                + "1. A branch with name %s already exists in the remote Git repository\n"
                + "2. The branch name %s is invalid\n",
            branch, branch));
      } else {
        throw new ExplanationException(String.format("Failed to create branch %s", branch), e);
      }
    }
    return createBranchResponse;
  }

  @Override
  public CreatePRResponse createPullRequest(
      ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    CreatePRRequest createPRRequest = CreatePRRequest.newBuilder()
                                          .setSlug(slug)
                                          .setTitle(gitPRCreateRequest.getTitle())
                                          .setProvider(gitProvider)
                                          .setSource(gitPRCreateRequest.getSourceBranch())
                                          .setTarget(gitPRCreateRequest.getTargetBranch())
                                          .build();
    final CreatePRResponse prResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createPR, createPRRequest);
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(prResponse.getStatus(), prResponse.getError());
    } catch (WingsException e) {
      final WingsException cause = ExceptionUtils.cause(ErrorCode.SCM_NOT_MODIFIED, e);
      if (cause != null) {
        throw new ExplanationException("A PR already exist for given branches", e);
      } else {
        throw new ExplanationException("Failed to create PR", e);
      }
    }
    return prResponse;
  }

  @Override
  public FindPRResponse findPR(ScmConnector scmConnector, long number, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    FindPRRequest findPRRequest =
        FindPRRequest.newBuilder().setSlug(slug).setNumber(number).setProvider(gitProvider).build();
    final FindPRResponse prResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findPR, findPRRequest);
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(prResponse.getStatus(), prResponse.getError());
    return prResponse;
  }

  @Override
  public FindCommitResponse findCommit(
      ScmConnector scmConnector, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    FindCommitRequest findCommitRequest =
        FindCommitRequest.newBuilder().setSlug(slug).setRef(commitId).setProvider(gitProvider).build();
    final FindCommitResponse commitResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findCommit, findCommitRequest);
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
        commitResponse.getStatus(), commitResponse.getError());
    return commitResponse;
  }

  @Override
  public CreateWebhookResponse createWebhook(
      ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    CreateWebhookRequest createWebhookRequest =
        getCreateWebhookRequest(slug, gitProvider, gitWebhookDetails, scmConnector, null, Collections.emptyList());
    CreateWebhookResponse createWebhookResponse =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createWebhook, createWebhookRequest);
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
        createWebhookResponse.getStatus(), createWebhookResponse.getError());
    return createWebhookResponse;
  }

  private CreateWebhookResponse createWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails,
      SCMGrpc.SCMBlockingStub scmBlockingStub, WebhookResponse exisitingWebhook,
      List<NativeEvents> existingNativeEventsList) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    CreateWebhookRequest createWebhookRequest = getCreateWebhookRequest(
        slug, gitProvider, gitWebhookDetails, scmConnector, exisitingWebhook, existingNativeEventsList);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createWebhook, createWebhookRequest);
  }

  private CreateWebhookRequest getCreateWebhookRequest(String slug, Provider gitProvider,
      GitWebhookDetails gitWebhookDetails, ScmConnector scmConnector, WebhookResponse identicalTarget,
      List<NativeEvents> existingNativeEventsList) {
    final CreateWebhookRequest.Builder createWebhookRequestBuilder = CreateWebhookRequest.newBuilder()
                                                                         .setSlug(slug)
                                                                         .setProvider(gitProvider)
                                                                         .setTarget(gitWebhookDetails.getTarget());
    if (isNotEmpty(gitWebhookDetails.getSecret())) {
      createWebhookRequestBuilder.setSecret(gitWebhookDetails.getSecret());
    }
    return ScmGitWebhookHelper.getCreateWebhookRequest(
        createWebhookRequestBuilder, gitWebhookDetails, scmConnector, identicalTarget, existingNativeEventsList);
  }

  @Override
  public DeleteWebhookResponse deleteWebhook(
      ScmConnector scmConnector, String id, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    DeleteWebhookRequest deleteWebhookRequest = getDeleteWebhookRequest(slug, gitProvider, id);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::deleteWebhook, deleteWebhookRequest);
  }

  private DeleteWebhookRequest getDeleteWebhookRequest(String slug, Provider gitProvider, String id) {
    return DeleteWebhookRequest.newBuilder().setSlug(slug).setProvider(gitProvider).setId(id).build();
  }

  @Override
  public ListWebhooksResponse listWebhook(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    ListWebhooksRequest listWebhooksRequest = getListWebhookRequest(slug, gitProvider);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::listWebhooks, listWebhooksRequest);
  }

  @Override
  public CreateWebhookResponse upsertWebhook(
      ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    ListWebhooksResponse listWebhooksResponse = listWebhook(scmConnector, scmBlockingStub);
    final List<WebhookResponse> webhooksList = listWebhooksResponse.getWebhooksList();
    WebhookResponse existingWebhook = null;

    List<NativeEvents> allNativeEventsList = isNotEmpty(webhooksList)
        ? webhooksList.stream().map(WebhookResponse::getNativeEvents).collect(toList())
        : Collections.emptyList();
    // This is being used only for Azure since it supports only single event per hook.
    List<NativeEvents> existingNativeEventsList = new ArrayList<>();

    for (WebhookResponse webhookResponse : webhooksList) {
      if (isIdentical(webhookResponse, gitWebhookDetails, scmConnector, allNativeEventsList)) {
        return CreateWebhookResponse.newBuilder().setWebhook(webhookResponse).setStatus(200).build();
      }
      if (isIdenticalTarget(webhookResponse, gitWebhookDetails)) {
        existingWebhook = webhookResponse;
        existingNativeEventsList.add(webhookResponse.getNativeEvents());
        final DeleteWebhookResponse deleteWebhookResponse =
            deleteWebhook(scmConnector, webhookResponse.getId(), scmBlockingStub);
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(deleteWebhookResponse.getStatus(), null);
      }
    }

    CreateWebhookResponse createWebhookResponse =
        createWebhook(scmConnector, gitWebhookDetails, scmBlockingStub, existingWebhook, existingNativeEventsList);
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowExceptionForUpsertWebhook(
        createWebhookResponse.getStatus(), createWebhookResponse.getError());
    return createWebhookResponse;
  }

  @Override
  public CompareCommitsResponse compareCommits(ScmConnector scmConnector, String initialCommitId, String finalCommitId,
      SCMGrpc.SCMBlockingStub scmBlockingStub) {
    List<PRFile> prFiles = new ArrayList<>();
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    CompareCommitsRequest.Builder request = CompareCommitsRequest.newBuilder()
                                                .setSource(initialCommitId)
                                                .setTarget(finalCommitId)
                                                .setSlug(slug)
                                                .setProvider(gitProvider)
                                                .setPagination(PageRequest.newBuilder().setPage(1).build());
    CompareCommitsResponse response;
    // process request in pagination manner
    do {
      response = ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::compareCommits, request.build());
      prFiles.addAll(response.getFilesList());
      // Set next page in the request
      request.setPagination(PageRequest.newBuilder().setPage(response.getPagination().getNext()).build());
    } while (response.getPagination().getNext() != 0);

    return CompareCommitsResponse.newBuilder().addAllFiles(prFiles).build();
  }

  private ListWebhooksRequest getListWebhookRequest(String slug, Provider gitProvider) {
    // Do pagination if webhook goes beyond single page
    return ListWebhooksRequest.newBuilder()
        .setSlug(slug)
        .setProvider(gitProvider)
        .setPagination(PageRequest.newBuilder().setPage(1).build())
        .build();
  }

  @Override
  public GetAuthenticatedUserResponse getAuthenticatedUser(
      ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getAuthenticatedUser,
        GetAuthenticatedUserRequest.newBuilder().setProvider(gitProvider).build());
  }

  @Override
  public GetUserReposResponse getUserRepos(
      ScmConnector scmConnector, io.harness.beans.PageRequestDTO pageRequest, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getUserRepos,
        GetUserReposRequest.newBuilder()
            .setPagination(PageRequest.newBuilder().setPage(pageRequest.getPageIndex() + 1).build())
            .setProvider(gitProvider)
            .setFetchAllRepos(pageRequest.isFetchAll())
            .build());
  }

  @Override
  public GetUserReposResponse getAllUserRepos(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getUserRepos,
        GetUserReposRequest.newBuilder()
            .setPagination(PageRequest.newBuilder().build())
            .setProvider(gitProvider)
            .setFetchAllRepos(true)
            .build());
  }

  @Override
  public GetUserRepoResponse getRepoDetails(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("getRepoDetails")) {
      String slug = scmGitProviderHelper.getSlug(scmConnector);
      Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
      return ScmGrpcClientUtils.retryAndProcessException(
          scmBlockingStub::getUserRepo, GetUserRepoRequest.newBuilder().setSlug(slug).setProvider(gitProvider).build());
    }
  }

  @Override
  public CreateBranchResponse createNewBranchV2(
      ScmConnector scmConnector, String newBranchName, String baseBranchName, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    GetLatestCommitResponse latestCommitResponse = ScmGrpcClientUtils.retryAndProcessException(
        scmBlockingStub::getLatestCommit,
        GetLatestCommitRequest.newBuilder().setBranch(baseBranchName).setSlug(slug).setProvider(gitProvider).build());
    if (isFailureResponse(latestCommitResponse.getStatus())) {
      log.error(String.format("Error while getting latest commit of branch [%s], %s: %s", baseBranchName,
          latestCommitResponse.getStatus(), latestCommitResponse.getError()));
      return CreateBranchResponse.newBuilder()
          .setStatus(latestCommitResponse.getStatus())
          .setError(latestCommitResponse.getError())
          .build();
    }
    return createNewBranchFromDefault(
        slug, gitProvider, newBranchName, latestCommitResponse.getCommit().getSha(), scmBlockingStub);
  }

  @Override
  public CreatePRResponse createPullRequestV2(ScmConnector scmConnector, String sourceBranchName,
      String targetBranchName, String prTitle, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    String slug = scmGitProviderHelper.getSlug(scmConnector);
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector);
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createPR,
        CreatePRRequest.newBuilder()
            .setSlug(slug)
            .setTitle(prTitle)
            .setProvider(gitProvider)
            .setSource(sourceBranchName)
            .setTarget(targetBranchName)
            .build());
  }

  @Override
  public RefreshTokenResponse refreshToken(ScmConnector scmConnector, String clientId, String clientSecret,
      String endpoint, String refreshToken, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    return scmBlockingStub.refreshToken(RefreshTokenRequest.newBuilder()
                                            .setClientID(clientId)
                                            .setClientSecret(clientSecret)
                                            .setRefreshToken(refreshToken)
                                            .setEndpoint(endpoint)
                                            .build());
  }

  @Override
  public GenerateYamlResponse autogenerateStageYamlForCI(
      String cloneUrl, String yamlVersion, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    return scmBlockingStub.generateStageYamlForCI(
        GenerateYamlRequest.newBuilder().setUrl(cloneUrl).setYamlVersion(yamlVersion).build());
  }

  public GetLatestCommitOnFileResponse getLatestCommitOnFile(
      ScmConnector scmConnector, String branchName, String filepath, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    return getLatestCommitOnFile(scmConnector, scmBlockingStub, branchName, filepath);
  }

  @SneakyThrows
  @Override
  public GitFileResponse getFile(
      ScmConnector scmConnector, GitFileRequest gitFileRequest, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    log.info("getOnlyFileContent :: {}", gitFileRequest.isGetOnlyFileContent());
    String commitId = gitFileRequest.getCommitId();
    String branch = gitFileRequest.getBranch();
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("getFile")) {
      // give higher precedence to commit id if not empty
      if (isNotEmpty(commitId)) {
        branch = null;
      } else if (isEmpty(branch)) {
        GetUserRepoResponse getUserRepoResponse = getRepoDetails(scmConnector, scmBlockingStub);
        if (isFailureResponse(getUserRepoResponse.getStatus())) {
          return GitFileResponse.builder()
              .error(getUserRepoResponse.getError())
              .statusCode(getUserRepoResponse.getStatus())
              .build();
        }
        branch = getUserRepoResponse.getRepo().getBranch();
      }

      FileContent fileContent = getFileContent(scmConnector,
          GitFilePathDetails.builder()
              .filePath(gitFileRequest.getFilepath())
              .ref(gitFileRequest.getCommitId())
              .branch(branch)
              .build(),
          scmBlockingStub);
      if (isFailureResponse(fileContent.getStatus())) {
        return GitFileResponse.builder()
            .error(fileContent.getError())
            .statusCode(fileContent.getStatus())
            .branch(branch)
            .build();
      }

      if (!gitFileRequest.isGetOnlyFileContent() && isEmpty(commitId)) {
        GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
            getLatestCommitOnFile(scmConnector, scmBlockingStub, branch, gitFileRequest.getFilepath());
        if (isNotEmpty(getLatestCommitOnFileResponse.getError())) {
          return GitFileResponse.builder()
              .error(getLatestCommitOnFileResponse.getError())
              .statusCode(Constants.SCM_BAD_RESPONSE_ERROR_CODE)
              .branch(branch)
              .build();
        }
        commitId = getLatestCommitOnFileResponse.getCommitId();
      }

      return GitFileResponse.builder()
          .commitId(commitId)
          .filepath(gitFileRequest.getFilepath())
          .content(fileContent.getContent())
          .objectId(fileContent.getBlobId())
          .branch(branch)
          .statusCode(Constants.HTTP_SUCCESS_STATUS_CODE)
          .build();
    } catch (Exception exception) {
      checkAndRethrowExceptionIfApplicable(exception);
      log.error("Faced exception in getFile operation: ", exception);
      return GitFileResponse.builder()
          .error(exception.getMessage())
          .statusCode(Constants.SCM_INTERNAL_SERVER_ERROR_CODE)
          .branch(branch)
          .commitId(commitId)
          .build();
    }
  }

  @Override
  public GitFileBatchResponse getBatchFile(
      GitFileBatchRequest gitFileBatchRequest, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Map<GetBatchFileRequestIdentifier, GitFileResponse> getBatchFileRequestIdentifierGitFileResponseMap =
        new HashMap<>();
    gitFileBatchRequest.getGetBatchFileRequestIdentifierGitFileRequestV2Map().forEach((identifier, request) -> {
      GitFileResponse gitFileResponse = getFile(request.getScmConnector(),
          GitFileRequest.builder()
              .commitId(request.getCommitId())
              .filepath(request.getFilepath())
              .branch(request.getBranch())
              .getOnlyFileContent(request.isGetOnlyFileContent())
              .build(),
          scmBlockingStub);
      getBatchFileRequestIdentifierGitFileResponseMap.put(identifier, gitFileResponse);
    });
    return GitFileBatchResponse.builder()
        .getBatchFileRequestIdentifierGitFileResponseMap(getBatchFileRequestIdentifierGitFileResponseMap)
        .build();
  }

  private FileContentBatchResponse processListFilesByFilePaths(ScmConnector connector, List<String> filePaths,
      String branch, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(connector);
    String slug = scmGitProviderHelper.getSlug(connector);
    try (AutoLogContext ignore = new RepoBranchLogContext(slug, branch, commitId, OVERRIDE_ERROR)) {
      final FileBatchContentResponse contentOfFiles =
          getContentOfFiles(filePaths, slug, gitProvider, commitId, scmBlockingStub, false);
      return FileContentBatchResponse.builder().fileBatchContentResponse(contentOfFiles).commitId(commitId).build();
    }
  }

  private CreateBranchResponse createNewBranchFromDefault(String slug, Provider gitProvider, String branch,
      String latestShaOfBranch, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::createBranch,
        CreateBranchRequest.newBuilder()
            .setName(branch)
            .setCommitId(latestShaOfBranch)
            .setProvider(gitProvider)
            .setSlug(slug)
            .build());
  }

  public String getLatestShaOfBranch(
      String slug, Provider gitProvider, String defaultBranchName, SCMGrpc.SCMBlockingStub scmBlockingStub) {
    try {
      GetLatestCommitResponse latestCommit =
          ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommit,
              GetLatestCommitRequest.newBuilder()
                  .setBranch(defaultBranchName)
                  .setSlug(slug)
                  .setProvider(gitProvider)
                  .build());
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(latestCommit.getStatus(), latestCommit.getError());
      return latestCommit.getCommit().getSha();
    } catch (Exception ex) {
      log.error(
          "Error encountered while getting latest commit of branch [{}] in slug [{}]", defaultBranchName, slug, ex);
      throw ex;
    }
  }

  private boolean isIdenticalTarget(WebhookResponse webhookResponse, GitWebhookDetails gitWebhookDetails) {
    // Currently we don't add secret however we receive it in response with empty value
    return webhookResponse.getTarget().replace("&secret=", "").equals(gitWebhookDetails.getTarget());
  }

  private boolean isIdentical(WebhookResponse webhookResponse, GitWebhookDetails gitWebhookDetails,
      ScmConnector scmConnector, List<NativeEvents> allNativeEventsList) {
    return isIdenticalTarget(webhookResponse, gitWebhookDetails)
        && ScmGitWebhookHelper.isIdenticalEvents(
            webhookResponse, gitWebhookDetails.getHookEventType(), scmConnector, allNativeEventsList);
  }

  private GetLatestCommitOnFileResponse getLatestCommitOnFile(
      ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub, String branch, String filepath) {
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("getLatestCommitOnFile")) {
      Provider gitProvider = scmGitProviderMapper.mapToSCMGitProvider(scmConnector, true);
      String slug = scmGitProviderHelper.getSlug(scmConnector);
      return ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::getLatestCommitOnFile,
          GetLatestCommitOnFileRequest.newBuilder()
              .setProvider(gitProvider)
              .setSlug(slug)
              .setBranch(branch)
              .setFilePath(filepath)
              .build());
    }
  }

  @VisibleForTesting
  protected Optional<UpdateFileResponse> runUpdateFileOpsPreChecks(
      ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub, GitFileDetails gitFileDetails) {
    // Check if current file commit is same as latest commit on file on remote
    if (ConnectorType.BITBUCKET.equals(scmConnector.getConnectorType())) {
      GetLatestCommitOnFileResponse latestCommitResponse = getLatestCommitOnFile(
          scmConnector, scmBlockingStub, gitFileDetails.getBranch(), gitFileDetails.getFilePath());
      if (isEmpty(latestCommitResponse.getCommitId()) && isBitbucketOnPrem(scmConnector)) {
        return Optional.of(UpdateFileResponse.newBuilder()
                               .setStatus(Constants.SCM_INTERNAL_SERVER_ERROR_CODE)
                               .setError(Constants.SCM_GIT_PROVIDER_ERROR_MESSAGE)
                               .build());
      }
      if (!latestCommitResponse.getCommitId().equals(gitFileDetails.getCommitId())) {
        return Optional.of(UpdateFileResponse.newBuilder()
                               .setStatus(Constants.SCM_CONFLICT_ERROR_CODE)
                               .setError(Constants.SCM_CONFLICT_ERROR_MESSAGE)
                               .setCommitId(latestCommitResponse.getCommitId())
                               .build());
      }
    } else if (ConnectorType.AZURE_REPO.equals(scmConnector.getConnectorType())) {
      GetLatestCommitOnFileResponse latestCommitResponse = getLatestCommitOnFile(
          scmConnector, scmBlockingStub, gitFileDetails.getBranch(), gitFileDetails.getFilePath());
      if (!latestCommitResponse.getCommitId().equals(gitFileDetails.getCommitId())) {
        return Optional.of(UpdateFileResponse.newBuilder()
                               .setStatus(Constants.SCM_CONFLICT_ERROR_CODE)
                               .setError(Constants.SCM_CONFLICT_ERROR_MESSAGE)
                               .setCommitId(latestCommitResponse.getCommitId())
                               .build());
      }
    }
    return Optional.empty();
  }

  private void handleUpdateFileRequestIfBBOnPrem(
      FileModifyRequest.Builder fileModifyRequestBuilder, ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    if (isBitbucketOnPrem(scmConnector)) {
      fileModifyRequestBuilder.setCommitId(gitFileDetails.getCommitId());
    }
  }

  private boolean isFailureResponse(int statusCode) {
    return statusCode >= 300;
  }

  private boolean isBitbucketOnPrem(ScmConnector scmConnector) {
    return ConnectorType.BITBUCKET.equals(scmConnector.getConnectorType())
        && !GitClientHelper.isBitBucketSAAS(scmConnector.getUrl());
  }

  private FindFilesInCommitRequest.Builder initFindFilesInCommitRequest(
      ScmConnector scmConnector, ListFilesInCommitRequest listFilesInCommitRequest) {
    FindFilesInCommitRequest.Builder findFilesInCommitRequestBuilder =
        getFindFilesInCommitRequestBuilder(scmConnector, listFilesInCommitRequest);
    int page = 1;
    if (isBitbucket(scmConnector)) {
      page = 0;
    }
    findFilesInCommitRequestBuilder.setPagination(PageRequest.newBuilder().setPage(page).build());
    return findFilesInCommitRequestBuilder;
  }

  private boolean isBitbucket(ScmConnector scmConnector) {
    return ConnectorType.BITBUCKET.equals(scmConnector.getConnectorType());
  }

  // Need to not process and rethrow exceptions defined in SCM GRPC Utils so that ScmDelegateClient is able to handle
  // them
  // TODO:
  //  We should fix the exceptions in SCM GRPC Utils and also correspondingly ScmDelegateClient to use correct
  //  exceptions with proper error codes
  private void checkAndRethrowExceptionIfApplicable(Exception exception) throws Exception {
    if (exception instanceof ConnectException || exception instanceof GeneralException) {
      throw exception;
    }
  }
}
