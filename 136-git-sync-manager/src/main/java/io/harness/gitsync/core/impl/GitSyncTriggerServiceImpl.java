/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.YamlProcessingLogContext;
import io.harness.gitsync.common.beans.YamlChangeSet;
import io.harness.gitsync.common.beans.YamlChangeSetStatus;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.GitWebhookRequestAttributes;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.core.service.GitSyncTriggerService;
import io.harness.gitsync.core.service.YamlChangeSetService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.JsonUtils;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.trigger.WebhookSource;
import software.wings.service.impl.trigger.WebhookEventUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class GitSyncTriggerServiceImpl implements GitSyncTriggerService {
  public static final String WEBHOOK_SUCCESS_MSG = "Successfully accepted webhook request for processing";
  private final String GIT_YAML_LOG_PREFIX = "GIT_YAML_LOG_PREFIX";
  public static final String RESPONSE_FOR_PING_EVENT = "Found ping event. Only push events are supported";

  private WaitNotifyEngine waitNotifyEngine;
  private SecretManagerClientService ngSecretService;
  private WebhookEventUtils webhookEventUtils;
  private YamlChangeSetService yamlChangeSetService;
  private GitCommitService gitCommitService;
  private YamlGitConfigService yamlGitConfigService;

  @VisibleForTesting
  List<ConnectorInfoDTO> getGitConnectors(String accountId) {
    // TODO(abhinav): Refactor after ConnectorDisconnectHandler impl
    return null;
  }

  @VisibleForTesting
  String getGitConnectorIdByWebhookToken(List<ConnectorInfoDTO> connectors, String webhookToken) {
    String gitConnectorId = null;
    for (ConnectorInfoDTO connector : connectors) {
      final ConnectorType type = connector.getConnectorType();
      // TODO(abhinav): Change name to webhook token
      if (type.equals(ConnectorType.GIT) && webhookToken.equals(connector.getName())) {
        return connector.getIdentifier();
      }
    }
    return gitConnectorId;
  }

  @Override
  public String validateAndQueueWebhookRequest(String accountId, String orgIdentifier, String projectIdentifier,
      String webhookToken, String yamlWebHookPayload, HttpHeaders headers) {
    final Stopwatch startedStopWatch = Stopwatch.createStarted();

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         YamlProcessingLogContext ignore2 =
             YamlProcessingLogContext.builder().webhookToken(webhookToken).build(OVERRIDE_ERROR)) {
      log.info(GIT_YAML_LOG_PREFIX + "Started processing webhook request");

      // todo(abhinav): refactor when webhook is finalised
      List<ConnectorInfoDTO> connectors = getGitConnectors(accountId);

      if (isEmpty(connectors)) {
        log.info(GIT_YAML_LOG_PREFIX + "Git ConnectorDisconnectHandler not found for account");
        throw new InvalidRequestException("Git ConnectorDisconnectHandler not found with webhook token " + webhookToken, USER);
      }

      String gitConnectorId = getGitConnectorIdByWebhookToken(connectors, webhookToken);

      if (isEmpty(gitConnectorId)) {
        throw new InvalidRequestException("Git ConnectorDisconnectHandler not found with webhook token " + webhookToken, USER);
      }

      boolean gitPingEvent = webhookEventUtils.isGitPingEvent(headers);
      if (gitPingEvent) {
        log.info(GIT_YAML_LOG_PREFIX + "Ping event found. Skip processing");
        return RESPONSE_FOR_PING_EVENT;
      }

      final Optional<String> repoName = obtainRepoFromPayload(yamlWebHookPayload, headers);

      if (!repoName.isPresent()) {
        log.info(GIT_YAML_LOG_PREFIX + "Repo not found. webhookToken: {}, yamlWebHookPayload: {}, headers: {}",
            webhookToken, yamlWebHookPayload, headers);
        throw new InvalidRequestException("Repo not found from webhook payload", USER);
      }

      final String branchName = obtainBranchFromPayload(yamlWebHookPayload, headers);

      if (isEmpty(branchName)) {
        log.info(GIT_YAML_LOG_PREFIX + "Branch not found. webhookToken: {}, yamlWebHookPayload: {}, headers: {}",
            webhookToken, yamlWebHookPayload, headers);
        throw new InvalidRequestException("Branch not found from webhook payload", USER);
      }

      List<YamlGitConfigDTO> yamlGitConfigs =
          yamlGitConfigService.getByConnectorRepoAndBranch(gitConnectorId, repoName.get(), branchName, accountId);

      if (isEmpty(yamlGitConfigs)) {
        log.info(
            GIT_YAML_LOG_PREFIX + "No git sync configured for repo = [{}], branch =[{}]", repoName.get(), branchName);
        throw new InvalidRequestException("No git sync configured for the repo and branch.", USER);
      }

      String headCommitId = obtainCommitIdFromPayload(yamlWebHookPayload, headers);

      if (isNotEmpty(headCommitId)
          && gitCommitService.isCommitAlreadyProcessed(accountId, headCommitId, repoName.get(), branchName)) {
        log.info(GIT_YAML_LOG_PREFIX + "CommitId: [{}] already processed.", headCommitId);
        return "Commit already processed";
      }

      log.info(GIT_YAML_LOG_PREFIX + " Found branch name =[{}], headCommitId=[{}]", branchName, headCommitId);

      YamlChangeSet yamlChangeSet = buildYamlChangeSetForGitToHarness(
          accountId, yamlWebHookPayload, headers, gitConnectorId, repoName.get(), branchName, headCommitId);

      // TODO: add changeset save logic
      //      final YamlChangeSetDTO savedYamlChangeSet = yamlChangeSetService.save(yamlChangeSet);

      //      try (ProcessTimeLogContext ignore3 =
      //               new ProcessTimeLogContext(startedStopWatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
      //        log.info(GIT_YAML_LOG_PREFIX + "Successfully accepted webhook request for processing as
      //        yamlChangeSetId=[{}]",
      //            savedYamlChangeSet.getUuid());
      //      }

      return WEBHOOK_SUCCESS_MSG;
    }
  }

  private String obtainBranchFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return null;
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    webhookEventUtils.validatePushEvent(webhookSource, headers);

    Map<String, Object> payLoadMap;
    try {
      payLoadMap = JsonUtils.asObject(yamlWebHookPayload, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      log.info("Webhook payload: " + yamlWebHookPayload, ex);
      throw new InvalidRequestException(
          "Failed to parse the webhook payload. Error " + ExceptionUtils.getMessage(ex), USER);
    }

    return webhookEventUtils.obtainBranchName(webhookSource, headers, payLoadMap);
  }

  private String obtainCommitIdFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return null;
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    webhookEventUtils.validatePushEvent(webhookSource, headers);

    Map<String, Object> payLoadMap =
        JsonUtils.asObject(yamlWebHookPayload, new TypeReference<Map<String, Object>>() {});

    return webhookEventUtils.obtainCommitId(webhookSource, headers, payLoadMap);
  }

  private Optional<String> obtainRepoFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return Optional.empty();
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    webhookEventUtils.validatePushEvent(webhookSource, headers);

    Map<String, Object> payLoadMap;
    try {
      payLoadMap = JsonUtils.asObject(yamlWebHookPayload, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      log.info("Webhook payload: " + yamlWebHookPayload, ex);
      throw new InvalidRequestException(
          "Failed to parse the webhook payload. Error " + ExceptionUtils.getMessage(ex), USER);
    }

    return webhookEventUtils.obtainRepositoryName(webhookSource, headers, payLoadMap);
  }

  private YamlChangeSet buildYamlChangeSetForGitToHarness(String accountId, String yamlWebHookPayload,
      HttpHeaders headers, String gitConnectorId, String repoName, String branchName, String headCommitId) {
    return YamlChangeSet.builder()
        .accountId(accountId)
        .status(YamlChangeSetStatus.QUEUED.name())
        .gitWebhookRequestAttributes(GitWebhookRequestAttributes.builder()
                                         .webhookBody(yamlWebHookPayload)
                                         .gitConnectorId(gitConnectorId)
                                         .webhookHeaders(convertHeadersToJsonString(headers))
                                         .repo(repoName)
                                         .branchName(branchName)
                                         .headCommitId(headCommitId)
                                         .build())
        .retryCount(0)
        .build();
  }

  private String convertHeadersToJsonString(HttpHeaders headers) {
    try {
      return JsonUtils.asJson(headers.getRequestHeaders());
    } catch (Exception ex) {
      log.warn("Failed to convert request headers in json string", ex);
      return null;
    }
  }
}
