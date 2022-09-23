/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gittoharness;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.gitsync.ChangeSet;
import io.harness.gitsync.ChangeSets;
import io.harness.gitsync.EntityInfo;
import io.harness.gitsync.EntityInfos;
import io.harness.gitsync.FileProcessingResponse;
import io.harness.gitsync.FileProcessingStatus;
import io.harness.gitsync.GitToHarnessInfo;
import io.harness.gitsync.GitToHarnessProcessRequest;
import io.harness.gitsync.MarkEntityInvalidRequest;
import io.harness.gitsync.MarkEntityInvalidResponse;
import io.harness.gitsync.ProcessingFailureStage;
import io.harness.gitsync.ProcessingResponse;
import io.harness.gitsync.ResetGitSyncSDKCacheRequest;
import io.harness.gitsync.ResetGitSyncSDKCacheResponse;
import io.harness.gitsync.beans.GitProcessRequest;
import io.harness.gitsync.common.YamlProcessingLogContext;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.dao.GitProcessingRequestService;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.interceptor.GitSyncThreadDecorator;
import io.harness.gitsync.logger.GitProcessingLogContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.manage.GlobalContextManager;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.ng.core.ValidationError;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(DX)
public class GitToHarnessSdkProcessorImpl implements GitToHarnessSdkProcessor {
  ChangeSetInterceptorService changeSetInterceptorService;
  GitSdkInterface changeSetHelperService;
  Supplier<List<EntityType>> sortOrder;
  GitSyncThreadDecorator gitSyncThreadDecorator;
  GitProcessingRequestService gitProcessingRequestDao;
  PersistentLocker persistentLocker;
  GitSyncSdkService gitSyncSdkService;

  @Inject
  public GitToHarnessSdkProcessorImpl(ChangeSetInterceptorService changeSetInterceptorService,
      GitSdkInterface changeSetHelperService, @Named("GitSyncSortOrder") Supplier<List<EntityType>> sortOrder,
      GitSyncThreadDecorator gitSyncThreadDecorator, GitProcessingRequestService gitProcessingRequestDao,
      PersistentLocker persistentLocker, GitSyncSdkService gitSyncSdkService) {
    this.changeSetInterceptorService = changeSetInterceptorService;
    this.changeSetHelperService = changeSetHelperService;
    this.sortOrder = sortOrder;
    this.gitSyncThreadDecorator = gitSyncThreadDecorator;
    this.gitProcessingRequestDao = gitProcessingRequestDao;
    this.persistentLocker = persistentLocker;
    this.gitSyncSdkService = gitSyncSdkService;
  }

  /**
   * Processing is a 4 step process.
   * <li><b>Preprocess step.</b> Generally a no op stage.</li>
   * <li><b>Sort step.</b> Changesets are sorted as per sort order.</li>
   * <li><b>Process step.</b> Change sets are processed by calling various service layers.</li>
   * <li><b>Post process step.</b> Collection of all the return data happens.</li>
   */
  @Override
  public ProcessingResponse gitToHarnessProcessingRequest(GitToHarnessProcessRequest gitToHarnessRequest) {
    ChangeSets changeSets = gitToHarnessRequest.getChangeSets();
    String accountId = gitToHarnessRequest.getAccountId();
    String commitId = gitToHarnessRequest.getCommitId().getValue();
    try (AutoLogContext ignore1 = new GitProcessingLogContext(accountId, commitId, OVERRIDE_ERROR);
         AcquiredLock lock = persistentLocker.waitToAcquireLock(
             GitProcessRequest.class, commitId, Duration.ofMinutes(2), Duration.ofSeconds(10))) {
      final Map<String, FileProcessingResponse> fileProcessingStatusMap =
          initializeProcessingResponse(gitToHarnessRequest);

      if (isEmpty(changeSets.getChangeSetList())) {
        return ProcessingResponse.newBuilder().setAccountId(gitToHarnessRequest.getAccountId()).build();
      }

      if (preProcessStage(changeSets, fileProcessingStatusMap, commitId, accountId)) {
        return flattenProcessingResponse(fileProcessingStatusMap, accountId, ProcessingFailureStage.RECEIVE_STAGE);
      }

      if (sortStage(changeSets, fileProcessingStatusMap, commitId, accountId)) {
        return flattenProcessingResponse(fileProcessingStatusMap, accountId, ProcessingFailureStage.SORT_STAGE);
      }
      if (processStage(changeSets, fileProcessingStatusMap, gitToHarnessRequest, commitId, accountId)) {
        return flattenProcessingResponse(fileProcessingStatusMap, accountId, ProcessingFailureStage.PROCESS_STAGE);
      }

      if (postProcessStage(changeSets, fileProcessingStatusMap, commitId, accountId)) {
        return flattenProcessingResponse(fileProcessingStatusMap, accountId, ProcessingFailureStage.PROCESS_STAGE);
      }

      return flattenProcessingResponse(fileProcessingStatusMap, accountId, null);
    }
  }

  @Override
  public MarkEntityInvalidResponse markEntitiesInvalid(MarkEntityInvalidRequest markEntityInvalidRequest) {
    String accountId = markEntityInvalidRequest.getAccountId();
    String commitId = markEntityInvalidRequest.getCommitId().getValue();
    GitToHarnessInfo branchInfo = markEntityInvalidRequest.getBranchInfo();
    List<EntityInfo> entities = markEntityInvalidRequest.getEntityInfoList().getEntityInfoListList();

    List<EntityInfo> successfullyMarkedInvalid = new ArrayList<>();
    entities.forEach(entityInfo -> {
      try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
        GlobalContextManager.upsertGlobalContextRecord(
            createGitEntityInfo(branchInfo.getBranch(), entityInfo.getFilePath(), entityInfo.getYamlGitConfigId(),
                entityInfo.getLastObjectId().getValue(), commitId));
        try {
          if (changeSetHelperService.markEntityInvalid(accountId, entityInfo)) {
            successfullyMarkedInvalid.add(entityInfo);
          }
        } catch (Exception exception) {
          log.error("Exception while marking entity {} invalid, skipping it", entityInfo, exception);
        }
      }
    });

    return MarkEntityInvalidResponse.newBuilder()
        .setEntityInfos(EntityInfos.newBuilder().addAllEntityInfoList(successfullyMarkedInvalid).build())
        .build();
  }

  @Override
  public ResetGitSyncSDKCacheResponse resetGitSyncSDKCache(ResetGitSyncSDKCacheRequest request) {
    try {
      gitSyncSdkService.resetGitSyncSDKCache(
          request.getAccountIdentifier(), request.getOrgIdentifier(), request.getProjectIdentifier());
      return ResetGitSyncSDKCacheResponse.newBuilder().build();
    } catch (Exception ex) {
      return ResetGitSyncSDKCacheResponse.newBuilder().setError(ex.getMessage()).build();
    }
  }

  private boolean postProcessStage(ChangeSets changeSets, Map<String, FileProcessingResponse> processingResponseMap,
      String commitId, String accountId) {
    try {
      final ProcessingResponse processingResponse = flattenProcessingResponse(processingResponseMap, accountId, null);
      changeSetInterceptorService.postChangeSetProcessing(processingResponse, accountId);
    } catch (Exception e) {
      return true;
    }
    return false;
  }

  private boolean processStage(ChangeSets changeSets, Map<String, FileProcessingResponse> processingResponseMap,
      GitToHarnessProcessRequest gitToHarnessRequest, String commitId, String accountId) {
    try {
      // todo(abhinav): Do parallel processing.
      for (ChangeSet changeSet : changeSets.getChangeSetList()) {
        log.info("Processing the file [{}]", changeSet.getFilePath());
        final FileProcessingStatus status = processingResponseMap.get(changeSet.getFilePath()).getStatus();
        if (!status.equals(FileProcessingStatus.UNPROCESSED)) {
          continue;
        }
        try (GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
             AutoLogContext ignore1 = YamlProcessingLogContext.builder()
                                          .changeSetId(changeSet.getChangeSetId())
                                          .changeType(changeSet.getChangeType().name())
                                          .build(OVERRIDE_ERROR);) {
          GlobalContextManager.upsertGlobalContextRecord(
              createGitEntityInfo(gitToHarnessRequest.getGitToHarnessBranchInfo(), changeSet,
                  gitToHarnessRequest.getCommitId().getValue()));
          changeSetHelperService.process(changeSet);
          updateFileProcessingResponse(
              FileProcessingStatus.SUCCESS, null, processingResponseMap, changeSet.getFilePath(), commitId, accountId);
        } catch (ConstraintViolationException ex) {
          updateFileProcessingResponse(FileProcessingStatus.FAILURE, getErrorMessage(ex), processingResponseMap,
              changeSet.getFilePath(), commitId, accountId);
        } catch (Exception e) {
          log.error("Exception in processing [{}]", changeSet.getFilePath(), e);
          updateFileProcessingResponse(FileProcessingStatus.FAILURE, getErrorMessage(e), processingResponseMap,
              changeSet.getFilePath(), commitId, accountId);
        }
      }
    } catch (Exception e) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  protected GitSyncBranchContext createGitEntityInfo(
      String branch, String filePath, String yamlGitConfigId, String lastObjectId, String commitId) {
    GitEntityFilePath rootFolderAndFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(filePath);
    return GitSyncBranchContext.builder()
        .gitBranchInfo(GitEntityInfo.builder()
                           .branch(branch)
                           .folderPath(rootFolderAndFilePath.getRootFolder())
                           .filePath(rootFolderAndFilePath.getFilePath())
                           .yamlGitConfigId(yamlGitConfigId)
                           .lastObjectId(lastObjectId)
                           .isSyncFromGit(true)
                           .commitId(commitId)
                           .build())
        .build();
  }

  private GitSyncBranchContext createGitEntityInfo(
      GitToHarnessInfo gitToHarnessBranchInfo, ChangeSet changeSet, String commitId) {
    return createGitEntityInfo(gitToHarnessBranchInfo.getBranch(), changeSet.getFilePath(),
        changeSet.getYamlGitConfigInfo().getYamlGitConfigId(),
        changeSet.getObjectId() == null ? null : changeSet.getObjectId().getValue(), commitId);
  }

  private boolean sortStage(ChangeSets changeSets, Map<String, FileProcessingResponse> processingResponseMap,
      String commitId, String accountId) {
    try {
      final List<ChangeSet> sortedChangeSets = sortChangeSets(sortOrder, changeSets.getChangeSetList());
      changeSetInterceptorService.postChangeSetSort(sortedChangeSets, accountId);
    } catch (Exception e) {
      updateFileProcessingResponseForAllChangeSets(
          processingResponseMap, FileProcessingStatus.SKIPPED, e.getMessage(), commitId, accountId);
      return true;
    }
    return false;
  }

  private boolean preProcessStage(ChangeSets changeSets, Map<String, FileProcessingResponse> processingResponseMap,
      String commitId, String accountId) {
    try {
      changeSetInterceptorService.onChangeSetReceive(changeSets, accountId);
    } catch (Exception e) {
      updateFileProcessingResponseForAllChangeSets(
          processingResponseMap, FileProcessingStatus.SKIPPED, e.getMessage(), commitId, accountId);
      return true;
    }
    return false;
  }

  private ProcessingResponse flattenProcessingResponse(Map<String, FileProcessingResponse> processingResponseMap,
      String accountId, ProcessingFailureStage processingFailureStage) {
    final List<FileProcessingResponse> fileProcessingResponses = new ArrayList<>(processingResponseMap.values());
    final ProcessingResponse.Builder processingResponseBuilder = ProcessingResponse.newBuilder()
                                                                     .addAllResponse(fileProcessingResponses)
                                                                     .setAccountId(accountId)
                                                                     .setIsError(false);
    if (processingFailureStage != null) {
      processingResponseBuilder.setProcessingFailureStage(processingFailureStage);
      processingResponseBuilder.setIsError(true);
    }
    return processingResponseBuilder.build();
  }

  private void updateFileProcessingResponseForAllChangeSets(Map<String, FileProcessingResponse> gitProcessRequest,
      FileProcessingStatus status, String message, String commitId, String accountId) {
    gitProcessRequest.forEach(
        (key, value) -> updateFileProcessingResponse(status, message, gitProcessRequest, key, commitId, accountId));
  }

  private void updateFileProcessingResponse(FileProcessingStatus status, String message,
      Map<String, FileProcessingResponse> responseMap, String filePath, String commitId, String accountId) {
    gitProcessingRequestDao.updateFileStatus(commitId, filePath, status, message, accountId);
    final FileProcessingResponse responseValue = responseMap.get(filePath);
    final FileProcessingResponse.Builder fileProcessingResponseBuilder =
        FileProcessingResponse.newBuilder().setStatus(status).setFilePath(responseValue.getFilePath());
    if (!isEmpty(message)) {
      fileProcessingResponseBuilder.setErrorMsg(StringValue.of(message));
    }
    responseMap.put(filePath, fileProcessingResponseBuilder.build());
  }

  private Map<String, FileProcessingResponse> initializeProcessingResponse(
      GitToHarnessProcessRequest gitToHarnessProcessRequest) {
    return gitProcessingRequestDao.upsert(gitToHarnessProcessRequest);
  }

  private List<ChangeSet> sortChangeSets(Supplier<List<EntityType>> sortOrder, List<ChangeSet> changeSetList) {
    return changeSetList.stream().sorted(new ChangeSetSortComparator(sortOrder.get())).collect(Collectors.toList());
  }

  private String getErrorMessage(Exception ex) {
    if (ex instanceof ConstraintViolationException) {
      ConstraintViolationException exception = (ConstraintViolationException) ex;
      Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
      List<ValidationError> validationErrors = new ArrayList<>();
      constraintViolations.forEach(constraintViolation -> {
        String field = null;
        for (Path.Node node : constraintViolation.getPropertyPath()) {
          field = node.getName();
        }
        validationErrors.add(ValidationError.of(field, constraintViolation.getMessage()));
      });
      return validationErrors.size() == 0
          ? "wrong field in entity"
          : validationErrors.get(0).getFieldId() + " " + validationErrors.get(0).getError();
    } else {
      return ExceptionUtils.getMessage(ex);
    }
  }
}
