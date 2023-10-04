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
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.gitxwebhooks.dtos.GitXCacheUpdateHelperRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent.GitXWebhookEventKeys;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookEventLogContext;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.gitsync.gitxwebhooks.utils.GitXWebhookUtils;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
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
      log.info("Fetching the Queued items");
      List<GitXWebhookEvent> queuedEvents = getQueuedGitXWebhookEvents();
      log.info(String.format("Events fetched in the queue state %d", queuedEvents.size()));
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
    try (GitXWebhookEventLogContext context = new GitXWebhookEventLogContext(gitXWebhookEvent)) {
      log.info(String.format("Picked the event %s from the queue.", gitXWebhookEvent.getEventIdentifier()));
      GitXWebhook gitXWebhook = getGitXWebhook(gitXWebhookEvent);
      if (gitXWebhook == null) {
        log.info(String.format(
            "The webhook event %s will be SKIPPED as there is no webhook configured for webhookIdentifier %s.",
            gitXWebhookEvent.getEventIdentifier(), gitXWebhookEvent.getWebhookIdentifier()));
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
        log.info(String.format(
            "The webhook event %s will be SKIPPED as the webhook is disabled or the folder paths don't match.",
            gitXWebhookEvent.getEventIdentifier()));
        gitXWebhookEventService.updateEvent(gitXWebhookEvent.getAccountIdentifier(),
            gitXWebhookEvent.getEventIdentifier(),
            GitXEventUpdateRequestDTO.builder().gitXWebhookEventStatus(GitXWebhookEventStatus.SKIPPED).build());
      } else {
        log.info(String.format(
            "Submitting the task for PROCESSING the webhook event %s as the webhook is enabled and the folder paths match.",
            gitXWebhookEvent.getEventIdentifier()));
        gitXWebhookCacheUpdateHelper.submitTask(gitXWebhookEvent.getEventIdentifier(),
            buildGitXWebhookRunnableRequest(gitXWebhook, gitXWebhookEvent, modifiedFilePaths, scmConnector));
        gitXWebhookEventService.updateEvent(gitXWebhookEvent.getAccountIdentifier(),
            gitXWebhookEvent.getEventIdentifier(),
            GitXEventUpdateRequestDTO.builder()
                .gitXWebhookEventStatus(GitXWebhookEventStatus.PROCESSING)
                .processedFilePaths(processingFilePaths)
                .build());
      }
    }
  }

  private GitXCacheUpdateHelperRequestDTO buildGitXWebhookRunnableRequest(GitXWebhook gitXWebhook,
      GitXWebhookEvent gitXWebhookEvent, List<String> modifiedFilePaths, ScmConnector scmConnector) {
    return GitXCacheUpdateHelperRequestDTO.builder()
        .accountIdentifier(gitXWebhook.getAccountIdentifier())
        .repoName(gitXWebhook.getRepoName())
        .branch(gitXWebhookEvent.getBranch())
        .connectorRef(gitXWebhook.getConnectorRef())
        .eventIdentifier(gitXWebhookEvent.getEventIdentifier())
        .modifiedFilePaths(modifiedFilePaths)
        .scmConnector(scmConnector)
        .build();
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
