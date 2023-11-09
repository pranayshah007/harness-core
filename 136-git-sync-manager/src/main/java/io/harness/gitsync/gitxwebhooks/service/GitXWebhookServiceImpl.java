/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HookEventType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.ScmException;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.service.GitFileCacheService;
import io.harness.gitsync.common.helper.GitRepoHelper;
import io.harness.gitsync.common.service.GitSyncConnectorService;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook.GitXWebhookKeys;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookLogContext;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.repositories.gitxwebhook.GitXWebhookRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookServiceImpl implements GitXWebhookService {
  @Inject GitXWebhookRepository gitXWebhookRepository;
  @Inject GitRepoHelper gitRepoHelper;
  @Inject GitSyncConnectorService gitSyncConnectorService;
  @Inject WebhookEventService webhookEventService;
  @Inject GitFileCacheService gitFileCacheService;

  private static final String DUP_KEY_EXP_FORMAT_STRING =
      "GitX Webhook with identifier [%s] or repo [%s] already exists in the account [%s].";

  private static final String WEBHOOK_FAILURE_ERROR_MESSAGE =
      "Unexpected error occurred while [%s] git webhook. Please contact Harness Support.";

  private static final String CREATING = "creating";
  private static final String FETCHING = "fetching";
  private static final String UPDATING = "updating";
  private static final String DELETING = "deleting";
  private static final String LISTING_WEBHOOKS = "listing webhooks";

  @Override
  public CreateGitXWebhookResponseDTO createGitXWebhook(CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(createGitXWebhookRequestDTO)) {
      try {
        clearCache(createGitXWebhookRequestDTO.getAccountIdentifier(), createGitXWebhookRequestDTO.getRepoName());
        GitXWebhook gitXWebhook = buildGitXWebhooks(createGitXWebhookRequestDTO);
        log.info(String.format("Creating Webhook with identifier %s in account %s",
            createGitXWebhookRequestDTO.getWebhookIdentifier(), createGitXWebhookRequestDTO.getAccountIdentifier()));
        registerWebhookOnGit(gitXWebhook.getAccountIdentifier(), gitXWebhook.getRepoName(),
            gitXWebhook.getConnectorRef(), gitXWebhook.getIdentifier());
        GitXWebhook createdGitXWebhook = gitXWebhookRepository.create(gitXWebhook);
        if (createdGitXWebhook == null) {
          log.error(String.format("Error while saving webhook [%s] in DB", gitXWebhook.getIdentifier()));
          throw new InternalServerErrorException(
              String.format("Error while saving webhook [%s] in DB", gitXWebhook.getIdentifier()));
        }
        return CreateGitXWebhookResponseDTO.builder().webhookIdentifier(createdGitXWebhook.getIdentifier()).build();
      } catch (DuplicateKeyException ex) {
        throw new DuplicateFieldException(
            format(DUP_KEY_EXP_FORMAT_STRING, createGitXWebhookRequestDTO.getWebhookIdentifier(),
                createGitXWebhookRequestDTO.getRepoName(), createGitXWebhookRequestDTO.getAccountIdentifier()),
            USER_SRE, ex);
      } catch (InternalServerErrorException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, CREATING));
      }
    }
  }

  @Override
  public Optional<GetGitXWebhookResponseDTO> getGitXWebhook(GetGitXWebhookRequestDTO getGitXWebhookRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(getGitXWebhookRequestDTO)) {
      try {
        log.info(String.format("Retrieving Webhook with identifier %s in account %s.",
            getGitXWebhookRequestDTO.getWebhookIdentifier(), getGitXWebhookRequestDTO.getAccountIdentifier()));
        List<GitXWebhook> gitXWebhookList = gitXWebhookRepository.findByAccountIdentifierAndIdentifier(
            getGitXWebhookRequestDTO.getAccountIdentifier(), getGitXWebhookRequestDTO.getWebhookIdentifier());
        if (isEmpty(gitXWebhookList)) {
          log.info(String.format(
              "For the given key with accountIdentifier %s and gitXWebhookIdentifier %s no webhook found.",
              getGitXWebhookRequestDTO.getAccountIdentifier(), getGitXWebhookRequestDTO.getWebhookIdentifier()));
          return Optional.empty();
        }
        List<GetGitXWebhookResponseDTO> getGitXWebhookResponseList = prepareGitXWebhooks(gitXWebhookList);
        if (getGitXWebhookResponseList.size() > 1) {
          throw new InternalServerErrorException(String.format(
              "For the given key with accountIdentifier %s and gitXWebhookIdentifier %s found more than one unique record.",
              getGitXWebhookRequestDTO.getAccountIdentifier(), getGitXWebhookRequestDTO.getWebhookIdentifier()));
        }
        return Optional.of(getGitXWebhookResponseList.get(0));
      } catch (InternalServerErrorException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, FETCHING));
      }
    }
  }

  @Override
  public Optional<GitXWebhook> getGitXWebhook(String accountIdentifier, String webhookIdentifier, String repoName) {
    List<GitXWebhook> gitXWebhookList;
    if (isNotEmpty(webhookIdentifier)) {
      gitXWebhookList =
          gitXWebhookRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, webhookIdentifier);
    } else {
      gitXWebhookList = gitXWebhookRepository.findByAccountIdentifierAndRepoName(accountIdentifier, repoName);
    }
    if (isEmpty(gitXWebhookList)) {
      log.info(String.format(
          "For the given key with accountIdentifier %s and gitXWebhookIdentifier %s or repoName %s no webhook found.",
          accountIdentifier, webhookIdentifier, repoName));
      return Optional.empty();
    }
    return Optional.of(gitXWebhookList.get(0));
  }

  @Override
  public UpdateGitXWebhookResponseDTO updateGitXWebhook(UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO,
      UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO) {
    try (GitXWebhookLogContext context =
             new GitXWebhookLogContext(updateGitXWebhookCriteriaDTO, updateGitXWebhookRequestDTO)) {
      try {
        log.info(String.format("Updating Webhook with identifier %s in account %s",
            updateGitXWebhookCriteriaDTO.getWebhookIdentifier(), updateGitXWebhookCriteriaDTO.getAccountIdentifier()));
        clearCache(updateGitXWebhookCriteriaDTO.getAccountIdentifier(), updateGitXWebhookRequestDTO.getRepoName());
        Criteria criteria = buildCriteria(
            updateGitXWebhookCriteriaDTO.getAccountIdentifier(), updateGitXWebhookCriteriaDTO.getWebhookIdentifier());
        Query query = new Query(criteria);
        Update update = buildUpdate(updateGitXWebhookRequestDTO);
        if (isNotEmpty(updateGitXWebhookRequestDTO.getRepoName())) {
          String connectorRef = getConnectorRef(updateGitXWebhookCriteriaDTO, updateGitXWebhookRequestDTO);
          registerWebhookOnGit(updateGitXWebhookCriteriaDTO.getAccountIdentifier(),
              updateGitXWebhookRequestDTO.getRepoName(), connectorRef,
              updateGitXWebhookCriteriaDTO.getWebhookIdentifier());
        }
        GitXWebhook updatedGitXWebhook;
        updatedGitXWebhook = gitXWebhookRepository.update(query, update);
        if (updatedGitXWebhook == null) {
          log.error(String.format(
              "Error while updating webhook [%s] in DB", updateGitXWebhookCriteriaDTO.getWebhookIdentifier()));
          throw new InternalServerErrorException(String.format(
              "Error while updating webhook [%s] in DB", updateGitXWebhookCriteriaDTO.getWebhookIdentifier()));
        }
        return UpdateGitXWebhookResponseDTO.builder().webhookIdentifier(updatedGitXWebhook.getIdentifier()).build();
      } catch (InternalServerErrorException exception) {
        throw exception;
      } catch (Exception exception) {
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, UPDATING));
      }
    }
  }

  @Override
  public ListGitXWebhookResponseDTO listGitXWebhooks(ListGitXWebhookRequestDTO listGitXWebhookRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(listGitXWebhookRequestDTO)) {
      try {
        log.info(
            String.format("Get List of GitX Webhooks in account %s", listGitXWebhookRequestDTO.getAccountIdentifier()));
        Criteria criteria = buildListCriteria(listGitXWebhookRequestDTO);
        List<GitXWebhook> gitXWebhookList = gitXWebhookRepository.list(criteria);
        return ListGitXWebhookResponseDTO.builder().gitXWebhooksList(prepareGitXWebhooks(gitXWebhookList)).build();
      } catch (Exception exception) {
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, LISTING_WEBHOOKS));
      }
    }
  }

  @Override
  public DeleteGitXWebhookResponseDTO deleteGitXWebhook(DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO) {
    try (GitXWebhookLogContext context = new GitXWebhookLogContext(deleteGitXWebhookRequestDTO)) {
      try {
        log.info(String.format("Deleting Webhook with identifier %s in account %s",
            deleteGitXWebhookRequestDTO.getWebhookIdentifier(), deleteGitXWebhookRequestDTO.getAccountIdentifier()));
        Criteria criteria = buildCriteria(
            deleteGitXWebhookRequestDTO.getAccountIdentifier(), deleteGitXWebhookRequestDTO.getWebhookIdentifier());
        DeleteResult deleteResult = gitXWebhookRepository.delete(criteria);
        return DeleteGitXWebhookResponseDTO.builder().successfullyDeleted(deleteResult.getDeletedCount() == 1).build();
      } catch (Exception exception) {
        throw new InternalServerErrorException(String.format(WEBHOOK_FAILURE_ERROR_MESSAGE, DELETING));
      }
    }
  }

  private String getConnectorRef(UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO,
      UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO) {
    if (isEmpty(updateGitXWebhookRequestDTO.getConnectorRef())) {
      Optional<GetGitXWebhookResponseDTO> optionalGetGitXWebhookResponse =
          getGitXWebhook(GetGitXWebhookRequestDTO.builder()
                             .accountIdentifier(updateGitXWebhookCriteriaDTO.getAccountIdentifier())
                             .webhookIdentifier(updateGitXWebhookCriteriaDTO.getWebhookIdentifier())
                             .build());
      if (optionalGetGitXWebhookResponse.isEmpty()) {
        throw new InternalServerErrorException(String.format(
            "Failed to fetch the connectorRef for webhook with identifier %s in account %s",
            updateGitXWebhookCriteriaDTO.getWebhookIdentifier(), updateGitXWebhookCriteriaDTO.getAccountIdentifier()));
      }
      return optionalGetGitXWebhookResponse.get().getConnectorRef();
    } else {
      return updateGitXWebhookRequestDTO.getConnectorRef();
    }
  }

  private List<GetGitXWebhookResponseDTO> prepareGitXWebhooks(List<GitXWebhook> gitXWebhookList) {
    return emptyIfNull(gitXWebhookList)
        .stream()
        .map(gitXWebhookResponseDTO
            -> GetGitXWebhookResponseDTO.builder()
                   .accountIdentifier(gitXWebhookResponseDTO.getAccountIdentifier())
                   .webhookIdentifier(gitXWebhookResponseDTO.getIdentifier())
                   .webhookName(gitXWebhookResponseDTO.getName())
                   .connectorRef(gitXWebhookResponseDTO.getConnectorRef())
                   .folderPaths(gitXWebhookResponseDTO.getFolderPaths())
                   .isEnabled(gitXWebhookResponseDTO.getIsEnabled())
                   .repoName(gitXWebhookResponseDTO.getRepoName())
                   .eventTriggerTime(gitXWebhookResponseDTO.getLastEventTriggerTime())
                   .build())
        .collect(Collectors.toList());
  }

  private Criteria buildCriteria(String accountIdentifier, String webhookIdentifier) {
    return Criteria.where(GitXWebhookKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(GitXWebhookKeys.identifier)
        .is(webhookIdentifier);
  }

  private Criteria buildListCriteria(ListGitXWebhookRequestDTO listGitXWebhookRequestDTO) {
    Criteria criteria = new Criteria();
    criteria.and(GitXWebhookKeys.accountIdentifier).is(listGitXWebhookRequestDTO.getAccountIdentifier());
    if (isNotEmpty(listGitXWebhookRequestDTO.getWebhookIdentifier())) {
      criteria.and(GitXWebhookKeys.identifier).is(listGitXWebhookRequestDTO.getWebhookIdentifier());
    }
    return criteria;
  }

  private Update buildUpdate(UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO) {
    long currentTimeInMilliseconds = System.currentTimeMillis();
    Update update = new Update();
    update.set(GitXWebhookKeys.folderPaths, updateGitXWebhookRequestDTO.getFolderPaths());
    if (isNotEmpty(updateGitXWebhookRequestDTO.getRepoName())) {
      update.set(GitXWebhookKeys.repoName, updateGitXWebhookRequestDTO.getRepoName());
    }
    if (isNotEmpty(updateGitXWebhookRequestDTO.getConnectorRef())) {
      update.set(GitXWebhookKeys.connectorRef, updateGitXWebhookRequestDTO.getConnectorRef());
    }
    if (isNotEmpty(updateGitXWebhookRequestDTO.getWebhookName())) {
      update.set(GitXWebhookKeys.name, updateGitXWebhookRequestDTO.getWebhookName());
    }
    if (updateGitXWebhookRequestDTO.getIsEnabled() != null) {
      update.set(GitXWebhookKeys.isEnabled, Boolean.TRUE.equals(updateGitXWebhookRequestDTO.getIsEnabled()));
    }
    if (updateGitXWebhookRequestDTO.getLastEventTriggerTime() != null) {
      update.set(GitXWebhookKeys.lastEventTriggerTime, updateGitXWebhookRequestDTO.getLastEventTriggerTime());
    }
    update.set(GitXWebhookKeys.lastUpdatedAt, currentTimeInMilliseconds);
    return update;
  }

  private GitXWebhook buildGitXWebhooks(CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO) {
    return GitXWebhook.builder()
        .accountIdentifier(createGitXWebhookRequestDTO.getAccountIdentifier())
        .identifier(createGitXWebhookRequestDTO.getWebhookIdentifier())
        .name(createGitXWebhookRequestDTO.getWebhookName())
        .connectorRef(createGitXWebhookRequestDTO.getConnectorRef())
        .folderPaths(createGitXWebhookRequestDTO.getFolderPaths())
        .repoName(createGitXWebhookRequestDTO.getRepoName())
        .isEnabled(true)
        .build();
  }

  private void registerWebhookOnGit(
      String accountIdentifier, String repoName, String connectorRef, String webhookIdentifier) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO =
        buildUpsertWebhookRequestDTO(accountIdentifier, repoName, connectorRef);
    try {
      final RetryPolicy<Object> retryPolicy = getWebhookRegistrationRetryPolicy(
          "[Retrying] attempt: {} for failure case of save webhook call", "Failed to save webhook after {} attempts");
      Failsafe.with(retryPolicy).get(() -> registerWebhook(upsertWebhookRequestDTO));
    } catch (ExplanationException | HintException | ScmException e) {
      log.error("Error while creating webhook on git." + webhookIdentifier, e);
      throw e;
    }
    log.info(String.format("Successfully created the webhook with identifier %s on git", webhookIdentifier));
  }

  private UpsertWebhookResponseDTO registerWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = webhookEventService.upsertWebhook(upsertWebhookRequestDTO);
    log.info(String.format(
        "Successfully registered webhook %s on git.", upsertWebhookResponseDTO.getWebhookResponse().getId()));
    return upsertWebhookResponseDTO;
  }

  private RetryPolicy<Object> getWebhookRegistrationRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withMaxAttempts(2)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private UpsertWebhookRequestDTO buildUpsertWebhookRequestDTO(
      String accountIdentifier, String repoName, String connectorRef) {
    ScmConnector scmConnector = gitSyncConnectorService.getScmConnector(accountIdentifier, "", "", connectorRef);
    String repoUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);
    return UpsertWebhookRequestDTO.builder()
        .accountIdentifier(accountIdentifier)
        .connectorIdentifierRef(connectorRef)
        .hookEventType(HookEventType.TRIGGER_EVENTS)
        .repoURL(repoUrl)
        .build();
  }

  private void clearCache(String accountIdentifier, String repoName) {
    GitFileCacheKey cacheKey =
        GitFileCacheKey.builder().accountIdentifier(accountIdentifier).repoName(repoName).build();
    try {
      gitFileCacheService.invalidateCache(cacheKey);
    } catch (Exception ex) {
      log.error("Exception occurred while clearing the cache for key {}", cacheKey, ex);
      throw new InternalServerErrorException(
          String.format("Error occurred while clearing the cache files in repo %s", repoName), ex);
    }
  }
}
