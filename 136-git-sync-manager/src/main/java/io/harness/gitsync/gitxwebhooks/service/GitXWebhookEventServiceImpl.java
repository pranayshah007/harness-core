/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GitXEventsListResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.Author;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent.GitXWebhookEventKeys;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookLogContext;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookEventServiceImpl implements GitXWebhookEventService {
  @Inject GitXWebhookEventsRepository gitXWebhookEventsRepository;
  @Inject GitXWebhookService gitXWebhookService;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "GitX Webhook event with event identifier [%s] already exists in the account [%s].";

  private static final String WEBHOOK_FAILURE_ERROR_MESSAGE =
      "Unexpected error occurred while [%s] git webhook. Please contact Harness Support.";

  private static final String LISTING_EVENTS = "listing events";

  @Override
  public void processEvent(WebhookDTO webhookDTO) {
    try {
      GitXWebhook gitXWebhook =
          fetchGitXWebhook(webhookDTO.getAccountId(), webhookDTO.getParsedResponse().getPush().getRepo().getName());
      if (gitXWebhook == null) {
        log.info(String.format("Skipping processing of event [%s] as no GitX Webhook found.", webhookDTO.getEventId()));
        return;
      }
      GitXWebhookEvent gitXWebhookEvent = buildGitXWebhookEvent(webhookDTO, gitXWebhook.getIdentifier());
      GitXWebhookEvent createdGitXWebhookEvent = gitXWebhookEventsRepository.create(gitXWebhookEvent);
      log.info(
          String.format("Successfully created the webhook event %s", createdGitXWebhookEvent.getEventIdentifier()));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format(DUP_KEY_EXP_FORMAT_STRING, webhookDTO.getEventId(), webhookDTO.getAccountId()), USER_SRE, ex);
    } catch (Exception exception) {
      throw new InternalServerErrorException(
          String.format("Failed to parse the webhook event [%s].", webhookDTO.getEventId()));
    }
  }

  @Override
  public GitXEventsListResponseDTO listEvents(GitXEventsListRequestDTO gitXEventsListRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(gitXEventsListRequestDTO)) {
      try {
        Criteria criteria = buildEventsListCriteria(gitXEventsListRequestDTO);
        List<GitXWebhookEvent> gitXWebhookEventList = gitXWebhookEventsRepository.list(criteria);
        return GitXEventsListResponseDTO.builder()
            .gitXEventDTOS(prepareGitXWebhookEvents(gitXWebhookEventList))
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
      String accountIdentifier, String eventIdentifier, GitXWebhookEventStatus gitXWebhookEventStatus) {
    Criteria criteria = buildCriteria(accountIdentifier, eventIdentifier);
    Query query = new Query(criteria);
    Update update = buildGitXWebhookEventUpdate(gitXWebhookEventStatus);
    gitXWebhookEventsRepository.update(query, update);
  }

  private Criteria buildCriteria(String accountIdentifier, String eventIdentifier) {
    return Criteria.where(GitXWebhookEventKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(GitXWebhookEventKeys.eventIdentifier)
        .is(eventIdentifier);
  }

  private Update buildGitXWebhookEventUpdate(GitXWebhookEventStatus gitXWebhookEventStatus) {
    Update update = new Update();
    update.set(GitXWebhookEventKeys.eventStatus, gitXWebhookEventStatus.name());
    return update;
  }

  private List<GitXEventDTO> prepareGitXWebhookEvents(List<GitXWebhookEvent> gitXWebhookEventList) {
    return emptyIfNull(gitXWebhookEventList)
        .stream()
        .map(gitXWebhookEvent
            -> GitXEventDTO.builder()
                   .webhookIdentifier(gitXWebhookEvent.getWebhookIdentifier())
                   .authorName(gitXWebhookEvent.getAuthor().getName())
                   .eventTriggerTime(gitXWebhookEvent.getEventTriggeredTime())
                   .payload(gitXWebhookEvent.getPayload())
                   .eventIdentifier(gitXWebhookEvent.getEventIdentifier())
                   .build())
        .collect(Collectors.toList());
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
    return criteria;
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
        .branch(webhookDTO.getParsedResponse().getPush().getRepo().getBranch())
        .build();
  }

  private Author buildAuthor(WebhookDTO webhookDTO) {
    return Author.builder().name(webhookDTO.getParsedResponse().getPush().getCommit().getAuthor().getName()).build();
  }
}
