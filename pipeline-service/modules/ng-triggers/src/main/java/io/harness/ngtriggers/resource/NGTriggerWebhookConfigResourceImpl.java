/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.UNRECOGNIZED_WEBHOOK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGProcessWebhookResponseDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookExecutionDetails;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.ngtriggers.helpers.UrlHelper;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.validations.TriggerWebhookValidator;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.HttpHeaders;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class NGTriggerWebhookConfigResourceImpl implements NGTriggerWebhookConfigResource {
  private final NGTriggerService ngTriggerService;
  private final NGTriggerElementMapper ngTriggerElementMapper;
  private final TriggerWebhookValidator triggerWebhookValidator;
  @Inject private UrlHelper urlHelper;

  public ResponseDTO<Map<WebhookSourceRepo, List<WebhookEvent>>> getSourceRepoToEvent() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getSourceRepoToEvent());
  }

  public ResponseDTO<Map<String, Map<String, List<String>>>> getGitTriggerEventDetails() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitTriggerEventDetails());
  }

  public ResponseDTO<List<WebhookTriggerType>> getWebhookTriggerTypes() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getWebhookTriggerType());
  }

  public ResponseDTO<List<GithubTriggerEvent>> getGithubTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubTriggerEvents());
  }

  public ResponseDTO<List<GithubPRAction>> getGithubPRActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubPRAction());
  }

  public ResponseDTO<List<GithubIssueCommentAction>> getGithubIssueCommentActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGithubIssueCommentAction());
  }

  public ResponseDTO<List<GitlabTriggerEvent>> getGitlabTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitlabTriggerEvents());
  }

  public ResponseDTO<List<GitlabPRAction>> getGitlabTriggerActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getGitlabPRAction());
  }

  public ResponseDTO<List<BitbucketTriggerEvent>> getBitbucketTriggerEvents() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getBitbucketTriggerEvents());
  }

  public ResponseDTO<List<BitbucketPRAction>> getBitbucketPRActions() {
    return ResponseDTO.newResponse(WebhookConfigHelper.getBitbucketPRAction());
  }

  public ResponseDTO<List<WebhookAction>> getActionsList(@NotNull WebhookSourceRepo sourceRepo, @NotNull String event) {
    WebhookEvent webhookEvent;
    try {
      webhookEvent = YamlPipelineUtils.read(event, WebhookEvent.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Event: " + event + " is not valid");
    }
    return ResponseDTO.newResponse(WebhookConfigHelper.getActionsList(sourceRepo, webhookEvent));
  }

  public ResponseDTO<String> processWebhookEvent(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));

    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEvent(accountIdentifier, orgIdentifier, projectIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      return ResponseDTO.newResponse(newEvent.getUuid());
    } else {
      return ResponseDTO.newResponse(UNRECOGNIZED_WEBHOOK);
    }
  }
  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<String> processWebhookEvent(@NotNull String accountIdentifier, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, String pipelineIdentifier, String triggerIdentifier,
      @NotNull String eventPayload, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, null);
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      return ResponseDTO.newResponse(newEvent.getUuid());
    } else {
      return ResponseDTO.newResponse(UNRECOGNIZED_WEBHOOK);
    }
  }
  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<NGProcessWebhookResponseDTO> processWebhookEventV2(@NotNull String accountIdentifier,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, String pipelineIdentifier,
      String triggerIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, null);
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      String uuid = newEvent.getUuid();
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder()
              .eventCorrelationId(uuid)
              .apiUrl(urlHelper.buildApiExecutionUrl(uuid, accountIdentifier))
              .uiUrl(urlHelper.buildUiUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .uiSetupUrl(
                  urlHelper.buildUiSetupUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .build());
    } else {
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder().eventCorrelationId(UNRECOGNIZED_WEBHOOK).build());
    }
  }

  @PipelineServiceAuthIfHasApiKey
  public ResponseDTO<NGProcessWebhookResponseDTO> processWebhookEventV3(@NotNull String webhookToken,
      @NotNull String accountIdentifier, @NotNull String orgIdentifier, @NotNull String projectIdentifier,
      String pipelineIdentifier, String triggerIdentifier, @NotNull String eventPayload, HttpHeaders httpHeaders) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.getRequestHeaders().forEach(
        (k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));
    ngTriggerService.checkAuthorization(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, headerConfigs);
    TriggerWebhookEvent eventEntity =
        ngTriggerElementMapper
            .toNGTriggerWebhookEventForCustom(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier,
                triggerIdentifier, eventPayload, headerConfigs)
            .build();
    if (eventEntity != null) {
      triggerWebhookValidator.applyValidationsForCustomWebhook(eventEntity, webhookToken);
      TriggerWebhookEvent newEvent = ngTriggerService.addEventToQueue(eventEntity);
      String uuid = newEvent.getUuid();
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder()
              .eventCorrelationId(uuid)
              .apiUrl(urlHelper.buildApiExecutionUrl(uuid, accountIdentifier))
              .uiUrl(urlHelper.buildUiUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .uiSetupUrl(
                  urlHelper.buildUiSetupUrl(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier))
              .build());
    } else {
      return ResponseDTO.newResponse(
          NGProcessWebhookResponseDTO.builder().eventCorrelationId(UNRECOGNIZED_WEBHOOK).build());
    }
  }

  public ResponseDTO<WebhookEventProcessingDetails> fetchWebhookDetails(
      @NotNull String accountIdentifier, @NotNull String eventId) {
    return ResponseDTO.newResponse(ngTriggerService.fetchTriggerEventHistory(accountIdentifier, eventId));
  }

  public ResponseDTO<WebhookExecutionDetails> fetchWebhookExecutionDetails(
      @NotNull String eventId, @NotNull String accountIdentifier) {
    WebhookEventProcessingDetails webhookProcessingDetails =
        ngTriggerService.fetchTriggerEventHistory(accountIdentifier, eventId);
    Object executionDetails = null;
    try {
      executionDetails =
          ngTriggerService.fetchExecutionSummaryV2(webhookProcessingDetails.getPipelineExecutionId(), accountIdentifier,
              webhookProcessingDetails.getOrgIdentifier(), webhookProcessingDetails.getProjectIdentifier());
    } catch (Exception e) {
      log.error(String.format("Unable to find execution details for trigger with eventCorrelationId %s", eventId), e);
    }
    return ResponseDTO.newResponse(WebhookExecutionDetails.builder()
                                       .webhookProcessingDetails(webhookProcessingDetails)
                                       .executionDetails(executionDetails)
                                       .build());
  }
}
