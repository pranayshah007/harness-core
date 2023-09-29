/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.runnable;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.common.dtos.GitDiffResultFileDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent.GitXWebhookEventKeys;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.gitsync.gitxwebhooks.utils.GitXWebhookUtils;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class GitXWebhookProcessorRunnable implements Runnable {
  @Inject private GitXWebhookService gitXWebhookService;
  @Inject private GitXWebhookEventsRepository gitXWebhookEventsRepository;
  @Inject private GitXWebhookCacheUpdateHelper gitXWebhookCacheUpdateHelper;
  @Inject private GitXWebhookEventService gitXWebhookEventService;
  @Inject private ScmOrchestratorService scmOrchestratorService;
  @Inject private GitSyncConnectorHelper gitSyncConnectorHelper;
  @Inject private GitRepoHelper gitRepoHelper;

  @Override
  public void run() {
    try {
      List<GitXWebhookEvent> queuedEvents = getQueuedGitXWebhookEvents();

      if (!queuedEvents.isEmpty()) {
        queuedEvents.forEach(this::processQueuedEvent);
      }

    } catch (Exception exception) {
      log.error("Faced exception while polling for the queued webhook events", exception);
    }
  }

  private List<GitXWebhookEvent> getQueuedGitXWebhookEvents() {
    Criteria criteria = Criteria.where(GitXWebhookEventKeys.eventStatus).is(GitXWebhookEventStatus.QUEUED);
    return gitXWebhookEventsRepository.list(new Query(criteria));
  }

  private void processQueuedEvent(GitXWebhookEvent gitXWebhookEvent) {
    GitXWebhook gitXWebhook = getGitXWebhook(gitXWebhookEvent);
    if (gitXWebhook == null) {
      gitXWebhookEventService.updateEvent(gitXWebhookEvent.getAccountIdentifier(),
          gitXWebhookEvent.getEventIdentifier(),
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.SKIPPED).build());
      return;
    }
    ScmConnector scmConnector =
        getScmConnector(gitXWebhook.getAccountIdentifier(), gitXWebhook.getConnectorRef(), gitXWebhook.getRepoName());
    List<String> modifiedFilePaths = parsePayloadAndGetModifiedFilePaths(gitXWebhook, gitXWebhookEvent, scmConnector);
    List<String> processingFilePaths = getMatchingFilePaths(modifiedFilePaths, gitXWebhook);
    if (isEmpty(processingFilePaths)) {
      log.info("The webhook event will be SKIPPED as the webhook is disabled or the folder paths don't match.");
      gitXWebhookEventService.updateEvent(gitXWebhookEvent.getAccountIdentifier(),
          gitXWebhookEvent.getEventIdentifier(),
          GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.SKIPPED).build());
    } else {
      gitXWebhookCacheUpdateHelper.submitTask(gitXWebhookEvent.getEventIdentifier(),
          buildScmGetBatchFilesByBranchRequestDTO(gitXWebhook, gitXWebhookEvent, modifiedFilePaths, scmConnector));
      gitXWebhookEventService.updateEvent(gitXWebhookEvent.getAccountIdentifier(),
          gitXWebhookEvent.getEventIdentifier(),
          GitXEventUpdateRequestDTO.builder()
              .gitXWebhookEventStatus(GitXWebhookEventStatus.PROCESSING)
              .processedFilePaths(processingFilePaths)
              .build());
    }
  }

  private ScmGetBatchFilesByBranchRequestDTO buildScmGetBatchFilesByBranchRequestDTO(GitXWebhook gitXWebhook,
      GitXWebhookEvent gitXWebhookEvent, List<String> modifiedFilePaths, ScmConnector scmConnector) {
    return ScmGetBatchFilesByBranchRequestDTO.builder()
        .accountIdentifier(gitXWebhook.getAccountIdentifier())
        .scmGetFileByBranchRequestDTOMap(
            buildScmGetFileByBranchRequestDTOMap(gitXWebhook, gitXWebhookEvent, modifiedFilePaths, scmConnector))
        .build();
  }

  private Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> buildScmGetFileByBranchRequestDTOMap(
      GitXWebhook gitXWebhook, GitXWebhookEvent gitXWebhookEvent, List<String> modifiedFilePaths,
      ScmConnector scmConnector) {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileByBranchRequestDTO> scmGetFileByBranchRequestDTOMap =
        new HashMap<>();
    modifiedFilePaths.forEach(modifiedFilePath -> {
      String uniqueIdentifier = buildUniqueIdentifier(gitXWebhookEvent, modifiedFilePath);
      ScmGetBatchFileRequestIdentifier scmGetBatchFileRequestIdentifier =
          ScmGetBatchFileRequestIdentifier.builder().identifier(uniqueIdentifier).build();
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO =
          ScmGetFileByBranchRequestDTO.builder()
              .scope(Scope.of(gitXWebhook.getAccountIdentifier()))
              .scmConnector(scmConnector)
              .repoName(gitXWebhook.getRepoName())
              .branchName(gitXWebhookEvent.getBranch())
              .filePath(modifiedFilePath)
              .connectorRef(gitXWebhook.getConnectorRef())
              .useCache(false)
              .build();
      scmGetFileByBranchRequestDTOMap.put(scmGetBatchFileRequestIdentifier, scmGetFileByBranchRequestDTO);
    });
    return scmGetFileByBranchRequestDTOMap;
  }

  private String buildUniqueIdentifier(GitXWebhookEvent gitXWebhookEvent, String modifiedFilePath) {
    return gitXWebhookEvent.getAccountIdentifier() + "/" + gitXWebhookEvent.getEventIdentifier() + "/"
        + modifiedFilePath;
  }

  private List<String> parsePayloadAndGetModifiedFilePaths(
      GitXWebhook gitXWebhook, GitXWebhookEvent gitXWebhookEvent, ScmConnector scmConnector) {
    if (gitXWebhook.getIsEnabled()) {
      log.info(String.format(
          "The webhook with identifier [%s] is enabled. Checking for the folder paths.", gitXWebhook.getIdentifier()));
      List<String> modifiedFilePaths = getDiffFilesUsingSCM(gitXWebhook.getAccountIdentifier(), scmConnector,
          gitXWebhookEvent.getBeforeCommitId(), gitXWebhookEvent.getAfterCommitId());

      log.info(String.format("Successfully fetched %d of modified file paths", modifiedFilePaths.size()));
      return modifiedFilePaths;
    }
    return new ArrayList<>();
  }

  private List<String> getMatchingFilePaths(List<String> modifiedFilePaths, GitXWebhook gitXWebhook) {
    return GitXWebhookUtils.compareFolderPaths(gitXWebhook.getFolderPaths(), modifiedFilePaths);
  }

  private GitXWebhook getGitXWebhook(GitXWebhookEvent gitXWebhookEvent) {
    Optional<GitXWebhook> optionalGitXWebhook = gitXWebhookService.getGitXWebhook(
        gitXWebhookEvent.getAccountIdentifier(), gitXWebhookEvent.getWebhookIdentifier(), null);
    if (optionalGitXWebhook.isEmpty()) {
      return null;
    }
    return optionalGitXWebhook.get();
  }

  public List<String> getDiffFilesUsingSCM(
      String accountIdentifier, ScmConnector scmConnector, String initialCommitId, String finalCommitId) {
    GitDiffResultFileListDTO gitDiffResultFileListDTO =
        scmOrchestratorService.processScmRequest(scmClientFacilitatorService
            -> scmClientFacilitatorService.listCommitsDiffFiles(
                Scope.of(accountIdentifier, "", ""), scmConnector, initialCommitId, finalCommitId),
            "", "", accountIdentifier);

    StringBuilder gitDiffResultFileList =
        new StringBuilder(String.format("Compare Commits Response from %s to %s :: ", initialCommitId, finalCommitId));
    gitDiffResultFileListDTO.getPrFileList().forEach(
        prFile -> gitDiffResultFileList.append(prFile.toString()).append(" :::: "));
    log.info(gitDiffResultFileList.toString());

    return emptyIfNull(gitDiffResultFileListDTO.getPrFileList())
        .stream()
        .map(GitDiffResultFileDTO::getPath)
        .distinct()
        .collect(Collectors.toList());
  }

  public ScmConnector getScmConnector(String accountIdentifier, String connectorRef, String repoName) {
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnector(accountIdentifier, "", "", connectorRef);
    scmConnector.setUrl(gitRepoHelper.getRepoUrl(scmConnector, repoName));
    return scmConnector;
  }
}
