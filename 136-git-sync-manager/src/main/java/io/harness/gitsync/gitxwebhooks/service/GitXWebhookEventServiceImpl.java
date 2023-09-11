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
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.gitxwebhooks.entity.Author;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent.GitXWebhookEventKeys;
import io.harness.gitsync.gitxwebhooks.runnable.FetchFilesFromGitHelper;
import io.harness.gitsync.gitxwebhooks.utils.GitXWebhookUtils;
import io.harness.repositories.gitxwebhook.GitXWebhookEventsRepository;
import io.harness.repositories.gitxwebhook.GitXWebhookRepository;

import com.google.inject.Inject;
import java.util.List;
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

  @Inject GitXWebhookRepository gitXWebhookRepository;

  @Inject GitXWebhookEventHelper gitXWebhookEventHelper;

  @Inject FetchFilesFromGitHelper fetchFilesFromGitHelper;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "GitX Webhook event with event identifier [%s] already exists in the account [%s].";

  @Override
  public void processEvent(WebhookDTO webhookDTO) {
    try {
      GitXWebhook gitXWebhook =
          fetchGitXWebhook(webhookDTO.getAccountId(), webhookDTO.getParsedResponse().getPush().getRepo().getName());
      GitXWebhookEvent gitXWebhookEvent = buildGitXWebhookEvent(webhookDTO, gitXWebhook.getIdentifier());
      GitXWebhookEvent createdGitXWebhookEvent = gitXWebhookEventsRepository.create(gitXWebhookEvent);
      log.info(
          String.format("Successfully created the webhook event %s", createdGitXWebhookEvent.getEventIdentifier()));

      ScmConnector scmConnector = gitXWebhookEventHelper.getScmConnector(
          gitXWebhook.getAccountIdentifier(), gitXWebhook.getConnectorRef(), gitXWebhook.getRepoName());
      List<String> filesToBeFetched = shouldParsePayload(gitXWebhook, webhookDTO, scmConnector);
      if (filesToBeFetched == null || filesToBeFetched.isEmpty()) {
        log.info("The webhook event will be SKIPPED as the webhook is disabled or the folder paths don't match.");
        updateGitXWebhookEvent(createdGitXWebhookEvent, webhookDTO);
        return;
      }
      fetchFilesFromGitHelper.submitTask(gitXWebhook.getAccountIdentifier(),
          webhookDTO.getParsedResponse().getPush().getRepo().getName(),
          webhookDTO.getParsedResponse().getPush().getRepo().getBranch(), scmConnector, webhookDTO.getEventId(),
          filesToBeFetched);
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
      log.info(String.format("No GitXWebhook found for the given key with accountIdentifier %s and repo %s.",
          accountIdentifier, repoName));
      throw new InternalServerErrorException(
          String.format("No GitXWebhook found for the given key with accountIdentifier %s and repo %s.",
              accountIdentifier, repoName));
    }
    log.info(String.format("Successfully retrieved the gitx webhook [%s] from account %s and repo %s.",
        gitXWebhook.getIdentifier(), gitXWebhook.getAccountIdentifier(), gitXWebhook.getRepoName()));
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

  private List<String> shouldParsePayload(GitXWebhook gitXWebhook, WebhookDTO webhookDTO, ScmConnector scmConnector) {
    if (gitXWebhook.getIsEnabled()) {
      log.info(String.format(
          "The webhook with identifier [%s] is enabled. Checking for the folder paths.", gitXWebhook.getIdentifier()));
      List<String> modifiedFolderPaths = gitXWebhookEventHelper.getDiffFilesUsingSCM(gitXWebhook.getAccountIdentifier(),
          scmConnector, webhookDTO.getParsedResponse().getPush().getBefore(),
          webhookDTO.getParsedResponse().getPush().getAfter());

      log.info(String.format("Successfully fetched %d of modified folder paths", modifiedFolderPaths.size()));
      return GitXWebhookUtils.compareFolderPaths(gitXWebhook.getFolderPaths(), modifiedFolderPaths);
    }
    return null;
  }

  private void updateGitXWebhookEvent(GitXWebhookEvent gitXWebhookEvent, WebhookDTO webhookDTO) {
    Criteria criteria = buildCriteria(gitXWebhookEvent.getAccountIdentifier(), gitXWebhookEvent.getEventIdentifier());
    Query query = new Query(criteria);
    Update update = buildUpdate(webhookDTO);
    gitXWebhookEventsRepository.update(query, update);
  }

  private Criteria buildCriteria(String accountIdentifier, String eventIdentifier) {
    return Criteria.where(GitXWebhookEventKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(GitXWebhookEventKeys.eventIdentifier)
        .is(eventIdentifier);
  }

  private Update buildUpdate(WebhookDTO webhookDTO) {
    Update update = new Update();
    update.set(GitXWebhookEventKeys.eventStatus, GitXWebhookEventStatus.SKIPPED.name());
    update.set(GitXWebhookEventKeys.payload, webhookDTO.getJsonPayload());
    return update;
  }
}
