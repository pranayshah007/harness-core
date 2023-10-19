/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.GITX_WEBHOOK_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventUpdateRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.entity.Author;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent.GitXWebhookEventKeys;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookEventLogContext;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookLogContext;
import io.harness.gitsync.gitxwebhooks.utils.GitXWebhookUtils;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookEventServiceImpl implements GitXWebhookEventService {
  @Inject GitXWebhookEventsRepository gitXWebhookEventsRepository;
  @Inject GitXWebhookService gitXWebhookService;
  @Inject HsqsClientService hsqsClientService;

  private static final String QUEUE_TOPIC_PREFIX = "ng";
  private static final String WEBHOOK_FAILURE_ERROR_MESSAGE =
      "Unexpected error occurred while [%s] git webhook. Please contact Harness Support.";

  private static final String LISTING_EVENTS = "listing events";

  @Override
  public void processEvent(WebhookDTO webhookDTO) {
    try (GitXWebhookEventLogContext context = new GitXWebhookEventLogContext(webhookDTO)) {
      try {
        GitXWebhook gitXWebhook =
            fetchGitXWebhook(webhookDTO.getAccountId(), webhookDTO.getParsedResponse().getPush().getRepo().getName());
        if (gitXWebhook == null) {
          log.info(
              String.format("Skipping processing of event [%s] as no GitX Webhook found.", webhookDTO.getEventId()));
          return;
        }
        GitXWebhookEvent gitXWebhookEvent = buildGitXWebhookEvent(webhookDTO, gitXWebhook.getIdentifier());
        GitXWebhookEvent createdGitXWebhookEvent = gitXWebhookEventsRepository.create(gitXWebhookEvent);
        updateGitXWebhook(gitXWebhook, webhookDTO.getTime());
        enqueueWebhookEvents(webhookDTO);
        log.info(
            String.format("Successfully created the webhook event %s", createdGitXWebhookEvent.getEventIdentifier()));
      } catch (Exception exception) {
        log.error("Failed to process the webhook event {}", webhookDTO.getEventId(), exception);
        throw new InternalServerErrorException(
            String.format("Failed to process the webhook event [%s].", webhookDTO.getEventId()));
      }
    }
  }

  @Override
  public GitXEventsListResponseDTO listEvents(GitXEventsListRequestDTO gitXEventsListRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(gitXEventsListRequestDTO)) {
      try {
        if (isNotEmpty(gitXEventsListRequestDTO.getRepoName())) {
          GitXWebhook gitXWebhook =
              fetchGitXWebhook(gitXEventsListRequestDTO.getAccountIdentifier(), gitXEventsListRequestDTO.getRepoName());
          if (gitXWebhook != null) {
            gitXEventsListRequestDTO.setWebhookIdentifier(gitXWebhook.getIdentifier());
          }
        }
        Query query = buildEventsListQuery(gitXEventsListRequestDTO);
        List<GitXWebhookEvent> gitXWebhookEventList = gitXWebhookEventsRepository.list(query);
        return GitXEventsListResponseDTO.builder()
            .gitXEventDTOS(prepareGitXWebhookEvents(gitXEventsListRequestDTO.getFilePath(), gitXWebhookEventList))
            .build();
      } catch (Exception exception) {
        log.error(String.format(
            "Error occurred while GitX listing events in account %s", gitXEventsListRequestDTO.getAccountIdentifier()));
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, LISTING_EVENTS));
      }
    }
  }

  @Override
  public void updateEvent(
      String accountIdentifier, String eventIdentifier, GitXEventUpdateRequestDTO gitXEventUpdateRequestDTO) {
    Criteria criteria = buildCriteria(accountIdentifier, eventIdentifier);
    Query query = new Query(criteria);
    Update update = buildGitXWebhookEventUpdate(gitXEventUpdateRequestDTO);
    gitXWebhookEventsRepository.update(query, update);
  }

  private Criteria buildCriteria(String accountIdentifier, String eventIdentifier) {
    return Criteria.where(GitXWebhookEventKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(GitXWebhookEventKeys.eventIdentifier)
        .is(eventIdentifier);
  }

  private Update buildGitXWebhookEventUpdate(GitXEventUpdateRequestDTO gitXEventUpdateRequestDTO) {
    Update update = new Update();
    update.set(GitXWebhookEventKeys.eventStatus, gitXEventUpdateRequestDTO.getGitXWebhookEventStatus().name());
    if (isNotEmpty(gitXEventUpdateRequestDTO.getProcessedFilePaths())) {
      update.set(GitXWebhookEventKeys.processedFilePaths, gitXEventUpdateRequestDTO.getProcessedFilePaths());
    }
    return update;
  }

  private List<GitXEventDTO> prepareGitXWebhookEvents(
      String entityFilePath, List<GitXWebhookEvent> gitXWebhookEventList) {
    List<GitXEventDTO> gitXEventList = new ArrayList<>();
    for (GitXWebhookEvent gitXWebhookEvent : gitXWebhookEventList) {
      if (isEmpty(entityFilePath)) {
        gitXEventList.add(buildGitXEventDTO(gitXWebhookEvent));
      } else if (isNotEmpty(entityFilePath)
          && isFilePathMatching(entityFilePath, gitXWebhookEvent.getProcessedFilePaths())) {
        gitXEventList.add(buildGitXEventDTO(gitXWebhookEvent));
      }
    }
    return gitXEventList;
  }

  private GitXEventDTO buildGitXEventDTO(GitXWebhookEvent gitXWebhookEvent) {
    return GitXEventDTO.builder()
        .webhookIdentifier(gitXWebhookEvent.getWebhookIdentifier())
        .authorName(gitXWebhookEvent.getAuthor().getName())
        .eventTriggerTime(gitXWebhookEvent.getEventTriggeredTime())
        .payload(gitXWebhookEvent.getPayload())
        .eventIdentifier(gitXWebhookEvent.getEventIdentifier())
        .build();
  }

  private boolean isFilePathMatching(String entityFilePath, List<String> modifiedFilePaths) {
    return isNotEmpty(
        GitXWebhookUtils.compareFolderPaths(Collections.singletonList(entityFilePath), modifiedFilePaths));
  }

  private Criteria buildEventsListCriteria(GitXEventsListRequestDTO gitXEventsListRequestDTO) {
    Criteria criteria = new Criteria();
    criteria.and(GitXWebhookEventKeys.accountIdentifier).is(gitXEventsListRequestDTO.getAccountIdentifier());
    if (isNotEmpty(gitXEventsListRequestDTO.getWebhookIdentifier())) {
      criteria.and(GitXWebhookEventKeys.webhookIdentifier).is(gitXEventsListRequestDTO.getWebhookIdentifier());
    }
    if (gitXEventsListRequestDTO.getEventStartTime() != null && gitXEventsListRequestDTO.getEventEndTime() != null) {
      criteria.and(GitXWebhookEventKeys.eventTriggeredTime)
          .gte(gitXEventsListRequestDTO.getEventStartTime())
          .lte(gitXEventsListRequestDTO.getEventEndTime());
    }
    if (isNotEmpty(gitXEventsListRequestDTO.getRepoName())) {
      criteria.and(GitXWebhookEventKeys.repo).is(gitXEventsListRequestDTO.getRepoName());
    }
    return criteria;
  }

  private Query buildEventsListQuery(GitXEventsListRequestDTO gitXEventsListRequestDTO) {
    Criteria criteria = buildEventsListCriteria(gitXEventsListRequestDTO);
    Query query = new Query(criteria);
    query.addCriteria(Criteria.where(GitXWebhookEventKeys.createdAt).exists(true))
        .with(Sort.by(Sort.Direction.DESC, GitXWebhookEventKeys.createdAt));
    return query;
  }

  private GitXWebhook fetchGitXWebhook(String accountIdentifier, String repoName) {
    Optional<GitXWebhook> optionalGitXWebhook = gitXWebhookService.getGitXWebhook(accountIdentifier, null, repoName);
    if (optionalGitXWebhook.isEmpty()) {
      return null;
    }
    return optionalGitXWebhook.get();
  }

  private GitXWebhookEvent buildGitXWebhookEvent(WebhookDTO webhookDTO, String webhookIdentifier) {
    return GitXWebhookEvent.builder()
        .accountIdentifier(webhookDTO.getAccountId())
        .eventIdentifier(webhookDTO.getEventId())
        .webhookIdentifier(webhookIdentifier)
        .author(buildAuthor(webhookDTO))
        .eventTriggeredTime(webhookDTO.getTime())
        .eventStatus(GitXWebhookEventStatus.QUEUED.name())
        .payload(webhookDTO.getJsonPayload())
        .afterCommitId(webhookDTO.getParsedResponse().getPush().getAfter())
        .beforeCommitId(webhookDTO.getParsedResponse().getPush().getBefore())
        .branch(getBranch(webhookDTO))
        .repo(webhookDTO.getParsedResponse().getPush().getRepo().getName())
        .build();
  }

  private String getBranch(WebhookDTO webhookDTO) {
    String ref = webhookDTO.getParsedResponse().getPush().getRef();
    return ref.replaceFirst("^refs/heads/", "");
  }

  private Author buildAuthor(WebhookDTO webhookDTO) {
    return Author.builder().name(webhookDTO.getParsedResponse().getPush().getCommit().getAuthor().getName()).build();
  }

  private void updateGitXWebhook(GitXWebhook gitXWebhook, long triggerEventTime) {
    gitXWebhookService.updateGitXWebhook(UpdateGitXWebhookCriteriaDTO.builder()
                                             .accountIdentifier(gitXWebhook.getAccountIdentifier())
                                             .webhookIdentifier(gitXWebhook.getIdentifier())
                                             .build(),
        UpdateGitXWebhookRequestDTO.builder().lastEventTriggerTime(triggerEventTime).build());
  }

  private void enqueueWebhookEvents(WebhookDTO webhookDTO) {
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic(QUEUE_TOPIC_PREFIX + GITX_WEBHOOK_EVENT)
                                        .subTopic(webhookDTO.getAccountId())
                                        .producerName(QUEUE_TOPIC_PREFIX + GITX_WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
    log.info("GitXWebhook event queued message id: {} for eventIdentifier: {}", execute.getItemId(),
        webhookDTO.getEventId());
  }
}
