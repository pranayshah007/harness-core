/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.FAILED;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.OVERRIDDEN;
import static io.harness.gitsync.core.beans.GitFullSyncEntityInfo.SyncStatus.QUEUED;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.FileChange;
import io.harness.gitsync.FileChanges;
import io.harness.gitsync.FullSyncEventRequest;
import io.harness.gitsync.FullSyncServiceGrpc.FullSyncServiceBlockingStub;
import io.harness.gitsync.ScopeDetails;
import io.harness.gitsync.common.beans.BranchSyncStatus;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.UserPrincipalMapper;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.fullsync.utils.FullSyncLogContextHelper;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.security.dto.UserPrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
@Slf4j
public class FullSyncAccumulatorServiceImpl implements FullSyncAccumulatorService {
  private final Map<Microservice, FullSyncServiceBlockingStub> fullSyncServiceBlockingStubMap;
  private final EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  private final GitFullSyncEntityService gitFullSyncEntityService;
  private final ScmOrchestratorService scmOrchestratorService;
  private final YamlGitConfigService yamlGitConfigService;
  private final FullSyncJobService fullSyncJobService;
  private final GitBranchService gitBranchService;

  @Override
  public void triggerFullSync(FullSyncEventRequest fullSyncEventRequest, String messageId) {
    log.info("Started triggering the git full sync job for the message Id {}", messageId);
    final EntityScopeInfo gitConfigScope = fullSyncEventRequest.getGitConfigScope();
    final ScopeDetails scopeDetails = getScopeDetails(gitConfigScope, messageId);
    String accountId = gitConfigScope.getAccountId();
    String projectId = StringValueUtils.getStringFromStringValue(gitConfigScope.getProjectId());
    String orgId = StringValueUtils.getStringFromStringValue(gitConfigScope.getOrgId());
    if (orgId == null || projectId == null) {
      throw new UnexpectedException("The orgId and projectId cannot be null");
    }
    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectId, orgId, accountId, gitConfigScope.getIdentifier());
    boolean isEntitiesAvailableForFullSync = false;

    for (Map.Entry<Microservice, FullSyncServiceBlockingStub> fullSyncStubEntry :
        fullSyncServiceBlockingStubMap.entrySet()) {
      FullSyncServiceBlockingStub fullSyncServiceBlockingStub = fullSyncStubEntry.getValue();
      Microservice microservice = fullSyncStubEntry.getKey();
      FileChanges entitiesForFullSync = null;
      try {
        // todo(abhinav): add retryInputSetReferenceProtoDTO
        log.info("Trying to get of the files for the message Id {} for the microservice {}", messageId, microservice);
        entitiesForFullSync = GitSyncGrpcClientUtils.retryAndProcessException(
            fullSyncServiceBlockingStub::getEntitiesForFullSync, scopeDetails);
      } catch (Exception e) {
        log.error("Error encountered while getting entities while full sync for msvc {}", microservice, e);
        continue;
      }
      if (entitiesForFullSync != null) {
        isEntitiesAvailableForFullSync =
            checkIfEntitiesAvailableForFullSync(entitiesForFullSync, microservice) || isEntitiesAvailableForFullSync;
        int fileProcessingSequenceNumber = 0;
        for (FileChange entityForFullSync : entitiesForFullSync.getFileChangesList()) {
          saveFullSyncEntityInfo(gitConfigScope, messageId, microservice, entityForFullSync,
              fullSyncEventRequest.getBranch(), fullSyncEventRequest.getRootFolder(), yamlGitConfigDTO,
              fileProcessingSequenceNumber);
          fileProcessingSequenceNumber++;
        }
      }
    }

    if (!isEntitiesAvailableForFullSync) {
      log.info("No entities to perform full-sync for message id {}", messageId);
      markTheQueuedFilesAsSuccessfullySynced(accountId, orgId, projectId, messageId);
      return;
    }
    if (fullSyncEventRequest.getIsNewBranch()) {
      createNewBranch(fullSyncEventRequest, yamlGitConfigDTO.getRepo());
    }
    GitFullSyncJob gitFullSyncJob = saveTheFullSyncJob(fullSyncEventRequest, messageId);
    if (gitFullSyncJob == null) {
      log.info("The job is not created for the message id {}, as a job with id already exists", messageId);
      return;
    }
  }

  @VisibleForTesting
  void markTheQueuedFilesAsSuccessfullySynced(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String messageId) {
    try {
      List<GitFullSyncEntityInfo> entitiesBelongingToPrevJobs =
          gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(
              accountIdentifier, orgIdentifier, projectIdentifier, messageId);
      if (isEmpty(entitiesBelongingToPrevJobs)) {
        log.info("No previous full sync entity to be marked as successful");
        return;
      }
      for (GitFullSyncEntityInfo gitFullSyncEntityInfo : entitiesBelongingToPrevJobs) {
        gitFullSyncEntityService.updateStatus(
            accountIdentifier, gitFullSyncEntityInfo.getUuid(), GitFullSyncEntityInfo.SyncStatus.SUCCESS, null);
        log.info("Marking the full sync entity with uuid {} as successful", gitFullSyncEntityInfo.getUuid());
      }
    } catch (Exception ex) {
      log.error("Marking the previous queued files as successful failed", ex);
    }
  }

  private boolean checkIfEntitiesAvailableForFullSync(FileChanges entitiesForFullSync, Microservice microservice) {
    int fileNumber = emptyIfNull(entitiesForFullSync.getFileChangesList()).size();
    log.info("Saving {} files for the microservice {}", fileNumber, microservice);
    if (fileNumber > 0) {
      return true;
    }
    return false;
  }

  private GitFullSyncJob saveTheFullSyncJob(FullSyncEventRequest fullSyncEventRequest, String messageId) {
    final EntityScopeInfo gitConfigScope = fullSyncEventRequest.getGitConfigScope();
    final UserPrincipal userPrincipal =
        UserPrincipalMapper.toRest(fullSyncEventRequest.getUserPrincipal(), gitConfigScope.getAccountId());
    GitFullSyncJob fullSyncJob = GitFullSyncJob.builder()
                                     .accountIdentifier(gitConfigScope.getAccountId())
                                     .orgIdentifier(getStringValueFromProtoString(gitConfigScope.getOrgId()))
                                     .projectIdentifier(getStringValueFromProtoString(gitConfigScope.getProjectId()))
                                     .yamlGitConfigIdentifier(gitConfigScope.getIdentifier())
                                     .syncStatus(QUEUED.name())
                                     .messageId(messageId)
                                     .retryCount(0)
                                     .createPullRequest(fullSyncEventRequest.getCreatePr())
                                     .targetBranch(fullSyncEventRequest.getTargetBranch())
                                     .branch(fullSyncEventRequest.getBranch())
                                     .prTitle(fullSyncEventRequest.getPrTitle())
                                     .triggeredBy(userPrincipal)
                                     .build();
    try {
      return fullSyncJobService.save(fullSyncJob);
    } catch (DuplicateKeyException ex) {
      log.error("A full sync job already exists for the message {}", messageId, ex);
    }
    return null;
  }

  private void saveFullSyncEntityInfo(EntityScopeInfo entityScopeInfo, String messageId, Microservice microservice,
      FileChange entityForFullSync, String branchName, String rootFolder, YamlGitConfigDTO yamlGitConfigDTO,
      int fileProcessingSequenceNumber) {
    String projectIdentifier = getStringValueFromProtoString(entityScopeInfo.getProjectId());
    String orgIdentifier = getStringValueFromProtoString(entityScopeInfo.getOrgId());
    final GitFullSyncEntityInfo gitFullSyncEntityInfo =
        GitFullSyncEntityInfo.builder()
            .accountIdentifier(entityScopeInfo.getAccountId())
            .filePath(entityForFullSync.getFilePath())
            .projectIdentifier(projectIdentifier)
            .orgIdentifier(orgIdentifier)
            .microservice(microservice.name())
            .messageId(messageId)
            .entityDetail(entityDetailProtoToRestMapper.createEntityDetailDTO(entityForFullSync.getEntityDetail()))
            .syncStatus(QUEUED.name())
            .yamlGitConfigId(entityScopeInfo.getIdentifier())
            .repoName(yamlGitConfigDTO.getName())
            .repoUrl(yamlGitConfigDTO.getRepo())
            .branchName(branchName)
            .rootFolder(rootFolder)
            .retryCount(0)
            .fileProcessingSequenceNumber(fileProcessingSequenceNumber)
            .build();
    markOverriddenEntities(
        entityScopeInfo.getAccountId(), orgIdentifier, projectIdentifier, entityForFullSync.getFilePath());
    gitFullSyncEntityService.save(gitFullSyncEntityInfo);
  }

  private void markOverriddenEntities(
      String accountId, String orgIdentifier, String projectIdentifier, String filePath) {
    List<GitFullSyncEntityInfo.SyncStatus> oldStatus = Arrays.asList(QUEUED, FAILED);
    gitFullSyncEntityService.updateStatus(accountId, orgIdentifier, projectIdentifier, filePath, oldStatus, OVERRIDDEN);
  }

  private ScopeDetails getScopeDetails(EntityScopeInfo entityScopeInfo, String messageId) {
    String projectIdentifier = getStringValueFromProtoString(entityScopeInfo.getProjectId());
    String orgIdentifier = getStringValueFromProtoString(entityScopeInfo.getOrgId());
    Map<String, String> logContext = FullSyncLogContextHelper.getContext(
        entityScopeInfo.getAccountId(), orgIdentifier, projectIdentifier, messageId);
    return ScopeDetails.newBuilder().setEntityScope(entityScopeInfo).putAllLogContext(logContext).build();
  }

  private String getStringValueFromProtoString(StringValue stringValue) {
    if (stringValue != null) {
      return stringValue.getValue();
    }
    return null;
  }

  private void createNewBranch(FullSyncEventRequest fullSyncEventRequest, String repoUrl) {
    final EntityScopeInfo gitConfigScope = fullSyncEventRequest.getGitConfigScope();
    String projectIdentifier = getStringValueFromProtoString(gitConfigScope.getProjectId());
    String orgIdentifier = getStringValueFromProtoString(gitConfigScope.getOrgId());
    GitBranch gitBranch =
        gitBranchService.get(gitConfigScope.getAccountId(), repoUrl, fullSyncEventRequest.getBranch());
    if (gitBranch != null) {
      throw new InvalidRequestException(
          String.format("A branch with name [%s] already exists in Harness", fullSyncEventRequest.getBranch()));
    }
    InfoForGitPush infoForGitPush = InfoForGitPush.builder()
                                        .accountId(gitConfigScope.getAccountId())
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .branch(fullSyncEventRequest.getBranch())
                                        .baseBranch(fullSyncEventRequest.getBaseBranch())
                                        .build();

    scmOrchestratorService.processScmRequest(scmClientFacilitatorService
        -> scmClientFacilitatorService.createBranch(infoForGitPush, gitConfigScope.getIdentifier()),
        projectIdentifier, orgIdentifier, gitConfigScope.getAccountId());
    gitBranch = GitBranch.builder()
                    .accountIdentifier(gitConfigScope.getAccountId())
                    .branchName(fullSyncEventRequest.getBranch())
                    .branchSyncStatus(BranchSyncStatus.UNSYNCED)
                    .repoURL(repoUrl)
                    .build();
    gitBranchService.save(gitBranch);
  }
}
