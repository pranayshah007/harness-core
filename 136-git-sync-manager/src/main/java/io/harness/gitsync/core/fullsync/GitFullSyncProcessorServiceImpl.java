/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.authorization.AuthorizationServiceHeader.GIT_SYNC_SERVICE;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.gitsync.core.fullsync.entity.GitFullSyncJob.SyncStatus.COMPLETED;
import static io.harness.gitsync.core.fullsync.entity.GitFullSyncJob.SyncStatus.FAILED;
import static io.harness.gitsync.core.fullsync.entity.GitFullSyncJob.SyncStatus.FAILED_WITH_RETRIES_LEFT;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.util.stream.Collectors.toList;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.Scope;
import io.harness.gitsync.FullSyncChangeSet;
import io.harness.gitsync.FullSyncFileResponse;
import io.harness.gitsync.FullSyncRequest;
import io.harness.gitsync.FullSyncResponse;
import io.harness.gitsync.FullSyncServiceGrpc;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.FullSyncMsvcProcessingResponse;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.beans.FullSyncFilesGroupedByMsvc;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.fullsync.utils.FullSyncLogContextHelper;
import io.harness.gitsync.scm.ScmGitUtils;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class GitFullSyncProcessorServiceImpl implements GitFullSyncProcessorService {
  private final Map<Microservice, FullSyncServiceGrpc.FullSyncServiceBlockingStub> fullSyncServiceBlockingStubMap;
  YamlGitConfigService yamlGitConfigService;
  EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  GitFullSyncEntityService gitFullSyncEntityService;
  FullSyncJobService fullSyncJobService;
  ScmOrchestratorService scmOrchestratorService;
  List<Microservice> microservicesProcessingOrder;
  GitBranchSyncService gitBranchSyncService;

  private static int MAX_RETRY_COUNT = 2;
  private static int MAX_BATCH_SIZE = 20;

  @Override
  public FullSyncMsvcProcessingResponse processFilesInBatches(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList) {
    List<FullSyncFileResponse> fullSyncFileResponses = new ArrayList<>();
    boolean isSyncFailed = false;
    List<List<GitFullSyncEntityInfo>> batchList = Lists.partition(emptyIfNull(entityInfoList), MAX_BATCH_SIZE);
    int batchNumber = 1;

    for (List<GitFullSyncEntityInfo> gitEntityInfoList : batchList) {
      FullSyncMsvcProcessingResponse fullSyncMsvcProcessingResponse = processFiles(microservice, gitEntityInfoList);
      fullSyncFileResponses.addAll(fullSyncMsvcProcessingResponse.getFullSyncFileResponses());
      isSyncFailed = fullSyncMsvcProcessingResponse.isSyncFailed() || isSyncFailed;
      log.info("Processed [{}] batch files for Msvc [{}] having response [{}]", batchNumber++, microservice,
          fullSyncMsvcProcessingResponse.isSyncFailed());
    }

    return FullSyncMsvcProcessingResponse.builder()
        .fullSyncFileResponses(fullSyncFileResponses)
        .isSyncFailed(isSyncFailed)
        .build();
  }

  private FullSyncMsvcProcessingResponse processFiles(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList) {
    boolean requestfailed = false;
    FullSyncResponse fullSyncResponse = null;
    try {
      fullSyncResponse = performSyncForEntities(microservice, entityInfoList);
    } catch (Exception e) {
      requestfailed = true;
    }
    boolean isFileProcessingFailed = setTheProcessingStatusOfFiles(fullSyncResponse, entityInfoList);
    return FullSyncMsvcProcessingResponse.builder()
        .fullSyncFileResponses(
            fullSyncResponse == null ? Collections.emptyList() : fullSyncResponse.getFileResponseList())
        .isSyncFailed(isFileProcessingFailed || requestfailed)
        .build();
  }

  private boolean setTheProcessingStatusOfFiles(
      FullSyncResponse fullSyncResponse, List<GitFullSyncEntityInfo> entityInfoList) {
    boolean anyFilesFailed = false;
    if (fullSyncResponse == null) {
      return anyFilesFailed;
    }
    for (FullSyncFileResponse fullSyncFileResponse : emptyIfNull(fullSyncResponse.getFileResponseList())) {
      if (fullSyncFileResponse == null) {
        continue;
      }
      GitFullSyncEntityInfo fullSyncEntityInfo =
          emptyIfNull(entityInfoList)
              .stream()
              .filter(x -> x.getFilePath().equals(fullSyncFileResponse.getFilePath()))
              .findFirst()
              .get();
      String errorMsg = "";
      errorMsg = fullSyncFileResponse.getErrorMsg();
      if (isNotEmpty(errorMsg)) {
        anyFilesFailed = true;
        gitFullSyncEntityService.updateStatus(fullSyncEntityInfo.getAccountIdentifier(), fullSyncEntityInfo.getUuid(),
            GitFullSyncEntityInfo.SyncStatus.FAILED, errorMsg);
      } else {
        gitFullSyncEntityService.markSuccessful(
            fullSyncEntityInfo.getUuid(), fullSyncEntityInfo.getAccountIdentifier());
      }
    }
    return anyFilesFailed;
  }

  private FullSyncResponse performSyncForEntities(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList) {
    if (isEmpty(entityInfoList)) {
      return FullSyncResponse.newBuilder().build();
    }
    final FullSyncServiceGrpc.FullSyncServiceBlockingStub fullSyncServiceBlockingStub =
        fullSyncServiceBlockingStubMap.get(microservice);
    GitFullSyncEntityInfo gitFullSyncEntityInfo = entityInfoList.get(0);
    final YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigService.getByProjectIdAndRepo(
        gitFullSyncEntityInfo.getAccountIdentifier(), gitFullSyncEntityInfo.getOrgIdentifier(),
        gitFullSyncEntityInfo.getProjectIdentifier(), gitFullSyncEntityInfo.getRepoUrl());
    List<FullSyncChangeSet> fullSyncChangeSets = new ArrayList<>();
    for (GitFullSyncEntityInfo fullSyncEntityInfo : entityInfoList) {
      fullSyncChangeSets.add(getFullSyncChangeSet(fullSyncEntityInfo, yamlGitConfigDTO));
    }
    log.info("Performing full sync for microservice [{}]", microservice);
    Map<String, String> logContext = FullSyncLogContextHelper.getContext(gitFullSyncEntityInfo.getAccountIdentifier(),
        gitFullSyncEntityInfo.getOrgIdentifier(), gitFullSyncEntityInfo.getProjectIdentifier(),
        gitFullSyncEntityInfo.getMessageId());
    return GitSyncGrpcClientUtils.retryAndProcessException(fullSyncServiceBlockingStub::performEntitySync,
        FullSyncRequest.newBuilder().putAllLogContext(logContext).addAllFileChanges(fullSyncChangeSets).build());
  }

  private FullSyncChangeSet getFullSyncChangeSet(GitFullSyncEntityInfo entityInfo, YamlGitConfigDTO yamlGitConfigDTO) {
    return FullSyncChangeSet.newBuilder()
        .setBranchName(entityInfo.getBranchName())
        .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(entityInfo.getEntityDetail()))
        .setFilePath(entityInfo.getFilePath())
        .setYamlGitConfigIdentifier(yamlGitConfigDTO.getIdentifier())
        .setAccountIdentifier(entityInfo.getAccountIdentifier())
        .setFolderPath(yamlGitConfigDTO.getDefaultRootFolder().getRootFolder())
        .setCommitMessage(getCommitMessageForTheFullSyncFlow(entityInfo.getFilePath()))
        .build();
  }

  private String getCommitMessageForTheFullSyncFlow(String filePath) {
    return "Harness Full Sync: "
        + "Add file " + filePath;
  }

  @Override
  public void performFullSync(GitFullSyncJob fullSyncJob) {
    Map<String, String> logContext = FullSyncLogContextHelper.getContext(fullSyncJob.getAccountIdentifier(),
        fullSyncJob.getOrgIdentifier(), fullSyncJob.getProjectIdentifier(), fullSyncJob.getMessageId());
    try (AutoLogContext ignore1 = new AutoLogContext(logContext, OVERRIDE_ERROR)) {
      log.info("Started full sync for the job {}", fullSyncJob.getUuid());
      List<GitFullSyncEntityInfo> allEntitiesToBeSynced = new ArrayList<>();
      GitFullSyncJob.SyncStatus currentJobStatus = GitFullSyncJob.SyncStatus.QUEUED;
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(GIT_SYNC_SERVICE.getServiceId()));
        UpdateResult updateResult =
            fullSyncJobService.markJobAsRunning(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid());
        if (updateResult.getModifiedCount() == 0L) {
          log.warn(
              "There is no job to run for the id {}, maybe the other thread is running it or maybe the job has reached terminating state",
              fullSyncJob.getUuid());
          return;
        }

        allEntitiesToBeSynced = gitFullSyncEntityService.listEntitiesToBeSynced(
            fullSyncJob.getAccountIdentifier(), fullSyncJob.getMessageId());
        markAlreadyProcessedFilesAsSuccess(fullSyncJob.getAccountIdentifier(), fullSyncJob.getOrgIdentifier(),
            fullSyncJob.getProjectIdentifier(), fullSyncJob.getMessageId(), allEntitiesToBeSynced);
        markTheGitFullSyncEntityAsQueued(allEntitiesToBeSynced);
        boolean processingFailed = false;
        final List<FullSyncFilesGroupedByMsvc> fullSyncFilesGroupedByMsvcs =
            sortTheFilesInTheProcessingOrder(allEntitiesToBeSynced);
        for (FullSyncFilesGroupedByMsvc fullSyncFilesGroupedByMsvc : fullSyncFilesGroupedByMsvcs) {
          log.info("Number of files is {} for the microservice {}",
              emptyIfNull(fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList()).size(),
              fullSyncFilesGroupedByMsvc.getMicroservice());
          FullSyncMsvcProcessingResponse fullSyncMsvcProcessingResponse = processFilesInBatches(
              fullSyncFilesGroupedByMsvc.getMicroservice(), fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList());
          processingFailed = fullSyncMsvcProcessingResponse.isSyncFailed() || processingFailed;
          if (fullSyncFilesGroupedByMsvc.getMicroservice() == Microservice.CORE) {
            setTheRepoBranchForTheGitSyncConnector(fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList(),
                fullSyncMsvcProcessingResponse.getFullSyncFileResponses());
          }
        }

        currentJobStatus = getTheCurrentStatusOfJob(processingFailed, fullSyncJob);
        updateTheStatusOfJob(currentJobStatus, fullSyncJob);
        if (fullSyncJob.isCreatePullRequest()) {
          log.info("Started creating pull request");
          createAPullRequest(fullSyncJob);
        }
        log.info("Completed full sync for the job");
      } catch (Exception ex) {
        log.error("Encountered error while doing full sync", ex);
        currentJobStatus = getTheCurrentStatusOfJob(true, fullSyncJob);
        try {
          updateTheStatusOfJob(currentJobStatus, fullSyncJob);
        } catch (Exception exception) {
          log.error(
              String.format("Attention! Not able to update the status of job {}", fullSyncJob.getUuid()), exception);
        }
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
      }
      if (FAILED.equals(currentJobStatus) || COMPLETED.equals(currentJobStatus)) {
        log.info("Initialised branch sync event for branch {} and job status {}", fullSyncJob.getBranch(),
            currentJobStatus.toString());
        syncBranch(allEntitiesToBeSynced, fullSyncJob);
      }
    }
  }

  @VisibleForTesting
  void markAlreadyProcessedFilesAsSuccess(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String messageId, List<GitFullSyncEntityInfo> allNewEntitiesToBeSynced) {
    try {
      List<GitFullSyncEntityInfo> entitiesBelongingToPrevJobs =
          gitFullSyncEntityService.getQueuedEntitiesFromPreviousJobs(
              accountIdentifier, orgIdentifier, projectIdentifier, messageId);
      if (isEmpty(entitiesBelongingToPrevJobs)) {
        return;
      }
      Set<String> filePathsToBeProcessed = emptyIfNull(allNewEntitiesToBeSynced)
                                               .stream()
                                               .map(GitFullSyncEntityInfo::getFilePath)
                                               .collect(Collectors.toSet());
      for (GitFullSyncEntityInfo gitFullSyncEntityInfo : entitiesBelongingToPrevJobs) {
        if (!filePathsToBeProcessed.contains(gitFullSyncEntityInfo.getFilePath())) {
          gitFullSyncEntityService.updateStatus(
              accountIdentifier, gitFullSyncEntityInfo.getUuid(), GitFullSyncEntityInfo.SyncStatus.SUCCESS, null);
          log.info("Marking the full sync entity with uuid {} as successful", gitFullSyncEntityInfo.getUuid());
        }
      }
    } catch (Exception ex) {
      log.error("Marking the previous queued files as successful, failed", ex);
    }
  }

  private void syncBranch(List<GitFullSyncEntityInfo> allEntitiesToBeSynced, GitFullSyncJob fullSyncJob) {
    try {
      YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigService.get(fullSyncJob.getProjectIdentifier(),
          fullSyncJob.getOrgIdentifier(), fullSyncJob.getAccountIdentifier(), fullSyncJob.getYamlGitConfigIdentifier());
      List<String> filePathsToBeExcluded = new ArrayList<>();
      for (GitFullSyncEntityInfo entityToBeSynced : emptyIfNull(allEntitiesToBeSynced)) {
        filePathsToBeExcluded.add(
            ScmGitUtils.createFilePath(entityToBeSynced.getRootFolder(), entityToBeSynced.getFilePath()));
      }
      gitBranchSyncService.createBranchSyncEvent(fullSyncJob.getAccountIdentifier(), fullSyncJob.getOrgIdentifier(),
          fullSyncJob.getProjectIdentifier(), fullSyncJob.getYamlGitConfigIdentifier(), yamlGitConfigDTO.getRepo(),
          fullSyncJob.getBranch(), filePathsToBeExcluded);
    } catch (Exception ex) {
      log.error(String.format("Not able to perform branch sync for branch {}", fullSyncJob.getBranch()), ex);
    }
  }

  private void markTheGitFullSyncEntityAsQueued(List<GitFullSyncEntityInfo> allEntitiesToBeSynced) {
    for (GitFullSyncEntityInfo gitFullSyncEntityInfo : emptyIfNull(allEntitiesToBeSynced)) {
      gitFullSyncEntityService.updateStatus(gitFullSyncEntityInfo.getAccountIdentifier(),
          gitFullSyncEntityInfo.getUuid(), GitFullSyncEntityInfo.SyncStatus.QUEUED, null);
    }
  }

  private void setTheRepoBranchForTheGitSyncConnector(
      List<GitFullSyncEntityInfo> ngCoreFullSyncFiles, List<FullSyncFileResponse> fullSyncMsvcProcessingResponses) {
    List<GitFullSyncEntityInfo> connectors = ngCoreFullSyncFiles.stream()
                                                 .filter(x -> x.getEntityDetail().getType() == EntityType.CONNECTORS)
                                                 .collect(toList());
    if (isEmpty(connectors)) {
      return;
    }
    GitFullSyncEntityInfo gitFullSyncEntityInfo = ngCoreFullSyncFiles.get(0);
    String accountIdentifier = gitFullSyncEntityInfo.getAccountIdentifier();
    String orgIdentifier = gitFullSyncEntityInfo.getOrgIdentifier();
    String projectIdentifier = gitFullSyncEntityInfo.getProjectIdentifier();
    List<YamlGitConfigDTO> yamlGitConfigDTOS =
        yamlGitConfigService.list(projectIdentifier, orgIdentifier, accountIdentifier);
    populateRepoAndBranchForTheConnectorRef(accountIdentifier, orgIdentifier, projectIdentifier, ngCoreFullSyncFiles,
        fullSyncMsvcProcessingResponses, yamlGitConfigDTOS);
  }

  private void populateRepoAndBranchForTheConnectorRef(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<GitFullSyncEntityInfo> ngCoreFullSyncFiles,
      List<FullSyncFileResponse> fullSyncMsvcProcessingResponses, List<YamlGitConfigDTO> yamlGitConfigDTOS) {
    if (isEmpty(yamlGitConfigDTOS)) {
      return;
    }
    for (YamlGitConfigDTO yamlGitConfigDTO : yamlGitConfigDTOS) {
      String gitConnectorRef = yamlGitConfigDTO.getGitConnectorRef();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          gitConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier, null);
      Optional<GitFullSyncEntityInfo> gitFullSyncEntityInfoOptional =
          getConnectorFullSyncEntity(identifierRef, ngCoreFullSyncFiles, fullSyncMsvcProcessingResponses);
      if (gitFullSyncEntityInfoOptional.isPresent()) {
        GitFullSyncEntityInfo gitFullSyncEntityInfo = gitFullSyncEntityInfoOptional.get();
        saveTheRepoAndBranchInYamlGitConfig(
            yamlGitConfigDTO, gitFullSyncEntityInfo.getYamlGitConfigId(), gitFullSyncEntityInfo.getBranchName());
      }
    }
  }

  private void saveTheRepoAndBranchInYamlGitConfig(
      YamlGitConfigDTO yamlGitConfigDTO, String yamlGitConfigId, String branchName) {
    yamlGitConfigService.updateTheConnectorRepoAndBranch(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getIdentifier(), yamlGitConfigId, branchName);
  }

  private Optional<GitFullSyncEntityInfo> getConnectorFullSyncEntity(IdentifierRef identifierRef,
      List<GitFullSyncEntityInfo> ngCoreFullSyncFiles, List<FullSyncFileResponse> fullSyncMsvcProcessingResponses) {
    if (isEmpty(fullSyncMsvcProcessingResponses)) {
      return Optional.empty();
    }

    if (identifierRef.getScope() == Scope.ACCOUNT || identifierRef.getScope() == Scope.ORG) {
      return Optional.empty();
    }

    return emptyIfNull(ngCoreFullSyncFiles)
        .stream()
        .filter(x -> checkIfEntityMatchesTheConnector(x, identifierRef))
        .findFirst();
  }

  private boolean checkIfEntityMatchesTheConnector(
      GitFullSyncEntityInfo gitFullSyncEntityInfo, IdentifierRef identifierRef) {
    if (identifierRef == null) {
      return false;
    }

    IdentifierRef entityRef = (IdentifierRef) gitFullSyncEntityInfo.getEntityDetail().getEntityRef();
    return identifierRef.getIdentifier().equals(entityRef.getIdentifier());
  }

  private void createAPullRequest(GitFullSyncJob fullSyncJob) {
    String projectIdentifier = fullSyncJob.getProjectIdentifier();
    String orgIdentifier = fullSyncJob.getOrgIdentifier();
    String accountIdentifier = fullSyncJob.getAccountIdentifier();
    String yamlGitConfigIdentifier = fullSyncJob.getYamlGitConfigIdentifier();
    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
    GitPRCreateRequest createPRRequest = GitPRCreateRequest.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .yamlGitConfigRef(yamlGitConfigIdentifier)
                                             .title(fullSyncJob.getPrTitle())
                                             .sourceBranch(fullSyncJob.getBranch())
                                             .targetBranch(fullSyncJob.getTargetBranch())
                                             .build();
    scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.createPullRequest(createPRRequest),
        projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigDTO.getGitConnectorRef(),
        yamlGitConfigDTO.getGitConnectorsRepo(), yamlGitConfigDTO.getGitConnectorsBranch());
  }

  @VisibleForTesting
  protected List<FullSyncFilesGroupedByMsvc> sortTheFilesInTheProcessingOrder(
      List<GitFullSyncEntityInfo> allEntitiesToBeSynced) {
    List<FullSyncFilesGroupedByMsvc> filesGroupedByMicroservices = new ArrayList<>();
    Map<String, List<GitFullSyncEntityInfo>> filesGroupedByMsvc =
        emptyIfNull(allEntitiesToBeSynced)
            .stream()
            .filter(x -> !x.getSyncStatus().equals(GitFullSyncEntityInfo.SyncStatus.SUCCESS.toString()))
            .collect(Collectors.groupingBy(GitFullSyncEntityInfo::getMicroservice));
    for (Map.Entry<String, List<GitFullSyncEntityInfo>> entry : filesGroupedByMsvc.entrySet()) {
      Microservice microservice = Microservice.fromString(entry.getKey());
      List<GitFullSyncEntityInfo> filesForThisMicroservice = entry.getValue();
      if (isNotEmpty(filesForThisMicroservice)) {
        filesForThisMicroservice.sort(Comparator.comparingInt(GitFullSyncEntityInfo::getFileProcessingSequenceNumber));
      }
      if (microservice == Microservice.CORE) {
        keepTheFullSyncConnectorAtTheLast(filesForThisMicroservice);
      }
      FullSyncFilesGroupedByMsvc fullSyncFilesGroupedByMsvc = FullSyncFilesGroupedByMsvc.builder()
                                                                  .microservice(microservice)
                                                                  .gitFullSyncEntityInfoList(filesForThisMicroservice)
                                                                  .build();
      filesGroupedByMicroservices.add(fullSyncFilesGroupedByMsvc);
    }
    filesGroupedByMicroservices.sort(
        Comparator.comparingInt(x -> microservicesProcessingOrder.indexOf(x.getMicroservice())));
    return filesGroupedByMicroservices;
  }

  private void keepTheFullSyncConnectorAtTheLast(List<GitFullSyncEntityInfo> filesForThisMicroservice) {
    // When we are doing the full sync of the entities, we do a get call of the full sync config ConnectorDisconnectHandler.
    // If the git ConnectorDisconnectHandler itself is full synced before any entity then we won't be able to find this
    // git ConnectorDisconnectHandler in other repo and branch. Hence we are first syncing all other files and at last we are
    // syncing this git ConnectorDisconnectHandler.
    if (isEmpty(filesForThisMicroservice)) {
      return;
    }
    GitFullSyncEntityInfo gitFullSyncEntityInfo = filesForThisMicroservice.get(0);
    String yamlGitConfigId = gitFullSyncEntityInfo.getYamlGitConfigId();
    String accountIdentifier = gitFullSyncEntityInfo.getAccountIdentifier();
    String orgIdentifier = gitFullSyncEntityInfo.getOrgIdentifier();
    String projectIdentifier = gitFullSyncEntityInfo.getProjectIdentifier();

    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigId);
    String gitConnectorRef = yamlGitConfigDTO.getGitConnectorRef();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        gitConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier, null);

    if (identifierRef.getScope() == Scope.ACCOUNT || identifierRef.getScope() == Scope.ORG) {
      return;
    }

    final OptionalInt posOfConnectorFullSyncEntityOpt =
        IntStream.range(0, filesForThisMicroservice.size())
            .filter(x -> checkIfEntityMatchesTheConnector(filesForThisMicroservice.get(x), identifierRef))
            .findFirst();

    if (posOfConnectorFullSyncEntityOpt.isPresent()) {
      final int positionOfConnectorFullSyncEntity = posOfConnectorFullSyncEntityOpt.getAsInt();
      GitFullSyncEntityInfo connectorFullSyncEntity = filesForThisMicroservice.get(positionOfConnectorFullSyncEntity);
      filesForThisMicroservice.remove(positionOfConnectorFullSyncEntity);
      filesForThisMicroservice.add(connectorFullSyncEntity);
    }
  }

  private void updateTheStatusOfJob(GitFullSyncJob.SyncStatus status, GitFullSyncJob fullSyncJob) {
    if (status.equals(FAILED) || status.equals(FAILED_WITH_RETRIES_LEFT)) {
      fullSyncJobService.markFullSyncJobAsFailed(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid(), status);
    } else if (status.equals(COMPLETED)) {
      fullSyncJobService.markFullSyncJobAsSuccess(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid());
    }
  }

  private GitFullSyncJob.SyncStatus getTheCurrentStatusOfJob(boolean processingFailed, GitFullSyncJob fullSyncJob) {
    if (processingFailed) {
      if (fullSyncJob.getRetryCount() >= MAX_RETRY_COUNT) {
        return FAILED;
      } else {
        return FAILED_WITH_RETRIES_LEFT;
      }
    } else {
      return COMPLETED;
    }
  }
}
