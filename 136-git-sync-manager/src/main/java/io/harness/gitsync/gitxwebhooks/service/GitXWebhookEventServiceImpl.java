/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

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
import io.harness.gitsync.gitxwebhooks.entity.Author;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;
import io.harness.repositories.gitxwebhook.GitXWebhookRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookEventServiceImpl implements GitXWebhookEventService {
  @Inject GitXWebhookEventsRepository gitXWebhookEventsRepository;
  @Inject GitXWebhookRepository gitXWebhookRepository;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "GitX Webhook event with event identifier [%s] already exists in the account [%s].";

  @Override
  public void processEvent(WebhookDTO webhookDTO) {
    try {
      GitXWebhook gitXWebhook =
          fetchGitXWebhook(webhookDTO.getAccountId(), webhookDTO.getParsedResponse().getPush().getRepo().getName());
      if (!shouldParsePayload(gitXWebhook, webhookDTO)) {
        log.info("The webhook event will not be parsed as the webhook is disabled or the folder paths don't match.");
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

  private GitXWebhook fetchGitXWebhook(String accountIdentifier, String repoName) {
    GitXWebhook gitXWebhook = gitXWebhookRepository.findByAccountIdentifierAndRepoName(accountIdentifier, repoName);
    if (gitXWebhook == null) {
      throw new InternalServerErrorException(
          String.format("No GitXWebhook found for the given key with accountIdentifier %s and repo %s.",
              accountIdentifier, repoName));
    }
    return gitXWebhook;
  }

  private GitXWebhookEvent buildGitXWebhookEvent(WebhookDTO webhookDTO, String webhookIdentifier) {
    return GitXWebhookEvent.builder()
        .accountIdentifier(webhookDTO.getAccountId())
        .eventIdentifier(webhookDTO.getEventId())
        .webhookIdentifier(webhookIdentifier)
        .author(buildAuthor(webhookDTO))
        .eventTriggeredTime(webhookDTO.getTime())
        .eventStatus(GitXWebhookEventStatus.QUEUED.name())
        .build();
  }

  private Author buildAuthor(WebhookDTO webhookDTO) {
    return Author.builder().name(webhookDTO.getParsedResponse().getPush().getCommit().getAuthor().getName()).build();
  }

  private boolean shouldParsePayload(GitXWebhook gitXWebhook, WebhookDTO webhookDTO) {
    return false;
  }
}
