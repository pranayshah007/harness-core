/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.common.scmerrorhandling.ScmErrorCodeToHttpStatusCodeMapping.HTTP_500;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.gitsync.BranchDetails;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetBatchFilesRequest;
import io.harness.gitsync.GetBatchFilesResponse;
import io.harness.gitsync.GetBranchHeadCommitRequest;
import io.harness.gitsync.GetBranchHeadCommitResponse;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GetRepoUrlResponse;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceImplBase;
import io.harness.gitsync.IsGitSimplificationEnabled;
import io.harness.gitsync.IsGitSimplificationEnabledRequest;
import io.harness.gitsync.IsGitSyncEnabled;
import io.harness.gitsync.IsOldGitSyncEnabledForModule;
import io.harness.gitsync.IsOldGitSyncEnabledResponse;
import io.harness.gitsync.ListFilesRequest;
import io.harness.gitsync.ListFilesResponse;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.PushInfo;
import io.harness.gitsync.PushResponse;
import io.harness.gitsync.RepoDetails;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.UserDetailsResponse;
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.service.HarnessToGitHelperService;
import io.harness.logging.MdcContextSetter;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.security.Principal;
import io.harness.security.PrincipalContextData;
import io.harness.security.PrincipalProtoMapper;
import io.harness.security.SourcePrincipalContextData;
import io.harness.serializer.KryoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(DX)
public class HarnessToGitPushInfoGrpcService extends HarnessToGitPushInfoServiceImplBase {
  @Inject HarnessToGitHelperService harnessToGitHelperService;
  @Inject KryoSerializer kryoSerializer;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject ExceptionManager exceptionManager;
  private final String GIT_SERVICE = "Git Service";
  private final String OPERATION_INFO_LOG_FORMAT = "%s %s ops response : %s";
  private final String EMPTY_STRING = "";

  @Override
  public void pushFromHarness(PushInfo request, StreamObserver<PushResponse> responseObserver) {
    try (MdcContextSetter ignore1 = new MdcContextSetter(request.getContextMapMap())) {
      log.debug("Grpc request received for pushFromHarness");
      harnessToGitHelperService.postPushOperation(request);
      responseObserver.onNext(PushResponse.newBuilder().build());
      responseObserver.onCompleted();
      log.debug("Grpc request completed for pushFromHarness");
    }
  }

  @Override
  public void pushFile(FileInfo request, StreamObserver<PushFileResponse> responseObserver) {
    PushFileResponse pushFileResponse;
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(request.getContextMapMap())) {
      log.debug("Grpc request received for pushFile");
      setPrincipal(request);
      pushFileResponse = harnessToGitHelperService.pushFile(request);
      log.debug("Grpc request completed for pushFile");
    } catch (Exception e) {
      log.error("Push to git failed with exception", e);
      final String message = ExceptionUtils.getMessage(e);
      pushFileResponse = PushFileResponse.newBuilder()
                             .setDefaultBranchName("")
                             .setIsDefault(false)
                             .setScmResponseCode(-1)
                             .setCommitId("")
                             .setError(message)
                             .setStatus(0)
                             .setDefaultBranchName("")
                             .build();
    }
    responseObserver.onNext(pushFileResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void getFile(GetFileRequest request, StreamObserver<GetFileResponse> responseObserver) {
    GetFileResponse getFileResponse;
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
        ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(),
        request.getBranchName(), request.getFilePath(), GitOperation.GET_FILE, request.getContextMapMap());
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for getFile ops %s", GIT_SERVICE, request));
      long startTime = currentTimeMillis();
      try {
        setPrincipal(request.getScopeIdentifiers().getAccountIdentifier(), request.getPrincipal());
        getFileResponse = harnessToGitHelperService.getFileByBranch(request);
        log.info(String.format("%s getFile ops response : %s", GIT_SERVICE, getFileResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.GET_FILE);
        log.error(errorMessage, ex);
        getFileResponse = GetFileResponse.newBuilder()
                              .setStatusCode(HTTP_500)
                              .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                              .build();
      } finally {
        logResponseTime(startTime, GitOperation.GET_FILE);
      }
    }
    responseObserver.onNext(getFileResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void createFile(CreateFileRequest request, StreamObserver<CreateFileResponse> responseObserver) {
    CreateFileResponse createFileResponse;
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
        ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(),
        request.getBranchName(), request.getFilePath(), GitOperation.CREATE_FILE, request.getContextMapMap());
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for createFile ops %s", GIT_SERVICE, request));
      long startTime = currentTimeMillis();
      try {
        setPrincipal(request.getScopeIdentifiers().getAccountIdentifier(), request.getPrincipal());
        createFileResponse = harnessToGitHelperService.createFile(request);
        log.info(String.format("%s createFile ops response : %s", GIT_SERVICE, createFileResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.CREATE_FILE);
        log.error(errorMessage, ex);
        createFileResponse = CreateFileResponse.newBuilder()
                                 .setStatusCode(HTTP_500)
                                 .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                                 .build();
      } finally {
        logResponseTime(startTime, GitOperation.CREATE_FILE);
      }
    }
    responseObserver.onNext(createFileResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void getUserDetails(io.harness.gitsync.UserDetailsRequest request,
      io.grpc.stub.StreamObserver<io.harness.gitsync.UserDetailsResponse> responseObserver) {
    UserDetailsResponse userDetailsResponse;
    Map<String, String> contextMap =
        GitSyncLogContextHelper.setContextMap(Scope.builder().accountIdentifier(request.getAccountIdentifier()).build(),
            EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, GitOperation.GET_USER_DETAILS, null);

    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for getUserDetails ops %s", GIT_SERVICE, request));
      long startTime = currentTimeMillis();
      try {
        userDetailsResponse = harnessToGitHelperService.getUserDetails(request);
        log.info(String.format("%s getUserDetails ops response : %s", GIT_SERVICE, userDetailsResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.GET_USER_DETAILS);
        log.error(errorMessage, ex);
        userDetailsResponse = UserDetailsResponse.newBuilder()
                                  .setStatusCode(HTTP_500)
                                  .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                                  .build();
      } finally {
        logResponseTime(startTime, GitOperation.GET_USER_DETAILS);
      }
    }

    responseObserver.onNext(userDetailsResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void updateFile(UpdateFileRequest request, StreamObserver<UpdateFileResponse> responseObserver) {
    UpdateFileResponse updateFileResponse;
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
        ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(),
        request.getBranchName(), request.getFilePath(), GitOperation.UPDATE_FILE, request.getContextMapMap());
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for updateFile ops %s", GIT_SERVICE, request));
      long startTime = currentTimeMillis();
      try {
        setPrincipal(request.getScopeIdentifiers().getAccountIdentifier(), request.getPrincipal());
        updateFileResponse = harnessToGitHelperService.updateFile(request);
        log.info(String.format("%s updateFile ops response : %s", GIT_SERVICE, updateFileResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.UPDATE_FILE);
        log.error(errorMessage, ex);
        updateFileResponse = UpdateFileResponse.newBuilder()
                                 .setStatusCode(HTTP_500)
                                 .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                                 .build();
      } finally {
        logResponseTime(startTime, GitOperation.UPDATE_FILE);
      }
    }
    responseObserver.onNext(updateFileResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void createPullRequest(CreatePRRequest request, StreamObserver<CreatePRResponse> responseObserver) {
    CreatePRResponse createPRResponse;
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(request.getContextMapMap())) {
      setPrincipal(request.getScopeIdentifiers().getAccountIdentifier(), request.getPrincipal());
      createPRResponse = harnessToGitHelperService.createPullRequest(request);
      log.info("Git Sync Service createPullRequest ops response : {}", createPRResponse);
    } catch (Exception ex) {
      log.error("Faced exception during createPullRequest GIT call", ex);
      final String errorMessage = ExceptionUtils.getMessage(ex);
      createPRResponse = CreatePRResponse.newBuilder()
                             .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                             .build();
    }
    responseObserver.onNext(createPRResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void getRepoUrl(GetRepoUrlRequest request, StreamObserver<GetRepoUrlResponse> responseObserver) {
    GetRepoUrlResponse getRepoUrlResponse;
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
        ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(), "",
        "", GitOperation.GET_REPO_URL, request.getContextMapMap());
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for getRepoUrl ops %s", GIT_SERVICE, request));
      try {
        getRepoUrlResponse = harnessToGitHelperService.getRepoUrl(request);
        log.info(String.format("%s getRepoUrl ops response : %s", GIT_SERVICE, getRepoUrlResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.GET_REPO_URL);
        log.error(errorMessage, ex);
        getRepoUrlResponse = GetRepoUrlResponse.newBuilder()
                                 .setStatusCode(HTTP_500)
                                 .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                                 .build();
      }
    }
    responseObserver.onNext(getRepoUrlResponse);
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  void setPrincipal(FileInfo request) {
    final Principal principalFromProto = request.getPrincipal();
    final io.harness.security.dto.Principal principal;
    if (request.getIsFullSyncFlow()) {
      principal = harnessToGitHelperService.getFullSyncUser(request);
    } else {
      principal = PrincipalProtoMapper.toPrincipalDTO(request.getAccountId(), principalFromProto);
    }
    GlobalContextManager.upsertGlobalContextRecord(PrincipalContextData.builder().principal(principal).build());
    GlobalContextManager.upsertGlobalContextRecord(SourcePrincipalContextData.builder().principal(principal).build());
  }

  @Override
  public void isGitSyncEnabledForScope(EntityScopeInfo request, StreamObserver<IsGitSyncEnabled> responseObserver) {
    log.debug("Grpc request received for isGitSyncEnabledForScope");
    final Boolean gitSyncEnabled = harnessToGitHelperService.isGitSyncEnabled(request);
    responseObserver.onNext(IsGitSyncEnabled.newBuilder().setEnabled(gitSyncEnabled).build());
    responseObserver.onCompleted();
    log.debug("Grpc request completed for isGitSyncEnabledForScope");
  }

  @Override
  public void isGitSimplificationEnabledForScope(
      IsGitSimplificationEnabledRequest request, StreamObserver<IsGitSimplificationEnabled> responseObserver) {
    final Boolean isGitSimplificationEnabled = harnessToGitHelperService.isGitSimplificationEnabled(request);
    responseObserver.onNext(IsGitSimplificationEnabled.newBuilder().setEnabled(isGitSimplificationEnabled).build());
    responseObserver.onCompleted();
  }

  @Override
  public void isOldGitSyncEnabledForModule(
      IsOldGitSyncEnabledForModule request, StreamObserver<IsOldGitSyncEnabledResponse> responseObserver) {
    final Boolean isOldGitSyncEnabledForModule = harnessToGitHelperService.isOldGitSyncEnabledForModule(
        request.getEntityScopeInfo(), request.getIsNotFFModule());
    responseObserver.onNext(
        IsOldGitSyncEnabledResponse.newBuilder().setIsEnabled(isOldGitSyncEnabledForModule).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBranchHeadCommitDetails(
      GetBranchHeadCommitRequest request, StreamObserver<GetBranchHeadCommitResponse> responseObserver) {
    GetBranchHeadCommitResponse showBranchResponse;
    Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
        ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(),
        request.getBranchName(), "", GitOperation.GET_BRANCH_HEAD_COMMIT, request.getContextMapMap());
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      showBranchResponse = harnessToGitHelperService.getBranchHeadCommitDetails(request);
      log.info(String.format(
          OPERATION_INFO_LOG_FORMAT, GIT_SERVICE, GitOperation.GET_BRANCH_HEAD_COMMIT, showBranchResponse));
    } catch (Exception ex) {
      final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.GET_BRANCH_HEAD_COMMIT);
      log.error(errorMessage, ex);
      showBranchResponse = GetBranchHeadCommitResponse.newBuilder()
                               .setStatusCode(HTTP_500)
                               .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                               .build();
    }
    responseObserver.onNext(showBranchResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void listFiles(ListFilesRequest request, StreamObserver<ListFilesResponse> responseObserver) {
    Map<String, String> contextMap = null;
    ListFilesResponse response;
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      response = harnessToGitHelperService.listFiles(request);
      log.info(String.format(OPERATION_INFO_LOG_FORMAT, GIT_SERVICE, GitOperation.LIST_FILES, response));
    } catch (Exception ex) {
      final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.LIST_FILES);
      log.error(errorMessage, ex);
      response = ListFilesResponse.newBuilder()
                     .setStatusCode(HTTP_500)
                     .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                     .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getBatchFiles(GetBatchFilesRequest request, StreamObserver<GetBatchFilesResponse> responseObserver) {
    GetBatchFilesResponse getBatchFilesResponse;
    Map<String, String> contextMap = getContextMapForBatchFileRequest(request);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      log.info(String.format("%s Grpc request received for getBatchFiles ops %s", GIT_SERVICE, request));
      long startTime = currentTimeMillis();
      try {
        getBatchFilesResponse = harnessToGitHelperService.getBatchFiles(request);
        log.info(String.format("%s getBatchFiles ops response : %s", GIT_SERVICE, getBatchFilesResponse));
      } catch (Exception ex) {
        final String errorMessage = getErrorMessageForRuntimeExceptions(GitOperation.GET_BATCH_FILES);
        log.error(errorMessage, ex);
        getBatchFilesResponse = GetBatchFilesResponse.newBuilder()
                                    .setStatusCode(HTTP_500)
                                    .setError(ErrorDetails.newBuilder().setErrorMessage(errorMessage).build())
                                    .build();
      } finally {
        logResponseTime(startTime, GitOperation.GET_BATCH_FILES);
      }
    }
    responseObserver.onNext(getBatchFilesResponse);
    responseObserver.onCompleted();
  }

  @Override
  public void getDefaultBranch(RepoDetails request, StreamObserver<BranchDetails> responseObserver) {
    try (MdcContextSetter ignore1 = new MdcContextSetter(request.getContextMapMap())) {
      log.info("Grpc request received for getDefaultBranch");
      final BranchDetails branchDetails = harnessToGitHelperService.getBranchDetails(request);
      log.info("Grpc request completed for getDefaultBranch");
      responseObserver.onNext(branchDetails);
    } catch (Exception ex) {
      final String errorMessage = ExceptionUtils.getMessage(ex);
      responseObserver.onError(Status.fromThrowable(ex).withDescription(errorMessage).asRuntimeException());
    }
    responseObserver.onCompleted();
  }

  private void setPrincipal(String accountIdentifier, Principal requestPrincipal) {
    final io.harness.security.dto.Principal principal =
        PrincipalProtoMapper.toPrincipalDTO(accountIdentifier, requestPrincipal);
    GlobalContextManager.upsertGlobalContextRecord(PrincipalContextData.builder().principal(principal).build());
    GlobalContextManager.upsertGlobalContextRecord(SourcePrincipalContextData.builder().principal(principal).build());
  }

  protected String getErrorMessageForRuntimeExceptions(GitOperation gitOperation) {
    return String.format("Unexpected error occurred while performing %s git operation. Please contact Harness Support.",
        gitOperation.getValue());
  }

  private Map<String, String> getContextMapForBatchFileRequest(GetBatchFilesRequest getBatchFilesRequest) {
    Map<String, String> globalContextMap = new HashMap<>(getBatchFilesRequest.getContextMapMap());
    getBatchFilesRequest.getGetFileRequestMapMap().forEach((requestIdentifier, request) -> {
      Map<String, String> contextMap = GitSyncLogContextHelper.setContextMap(
          ScopeIdentifierMapper.getScopeFromScopeIdentifiers(request.getScopeIdentifiers()), request.getRepoName(),
          request.getBranchName(), request.getFilePath(), GitOperation.GET_BATCH_FILES, request.getContextMapMap());
      globalContextMap.put(requestIdentifier, contextMap.toString());
    });
    return globalContextMap;
  }

  private void logResponseTime(long startTime, GitOperation gitOperation) {
    log.info("Total Time Taken (in ms) for Git Operation {} : {}", gitOperation, currentTimeMillis() - startTime);
  }
}
