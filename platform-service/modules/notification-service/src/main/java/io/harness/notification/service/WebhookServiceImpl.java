/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.exception.WingsException.USER;
import static io.harness.notification.NotificationRequest.Webhook;
import static io.harness.notification.NotificationServiceConstants.TEST_WEBHOOK_TEMPLATE;

import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.delegate.beans.WebhookTaskParams;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.WebhookSettingDTO;
import io.harness.notification.senders.WebhookSenderImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.utils.NotificationSettingsHelper;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import org.apache.commons.text.StrSubstitutor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class WebhookServiceImpl implements ChannelService {
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final WebhookSenderImpl webhookSender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final NotificationSettingsHelper notificationSettingsHelper;

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasWebhook()
        || Objects.isNull(notificationRequest.getAccountId())) {
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    String notificationId = notificationRequest.getId();
    Webhook webhookDetails = notificationRequest.getWebhook();
    String templateId = webhookDetails.getTemplateId();
    Map<String, String> templateData = webhookDetails.getTemplateDataMap();

    if (Objects.isNull(trimToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    List<String> webhookUrls = getRecipients(notificationRequest);
    if (isEmpty(webhookUrls)) {
      log.info("No webhookUrls found in notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    int expressionFunctorToken = Math.toIntExact(webhookDetails.getExpressionFunctorToken());

    Map<String, String> abstractionMap = notificationSettingsService.buildTaskAbstractions(
        notificationRequest.getAccountId(), webhookDetails.getOrgIdentifier(), webhookDetails.getProjectIdentifier());

    return send(webhookUrls, templateId, templateData, notificationRequest.getId(), notificationRequest.getTeam(),
        notificationRequest.getAccountId(), expressionFunctorToken, abstractionMap,
        new HashMap<>(webhookDetails.getHeadersMap()));
  }

  @Override
  public NotificationTaskResponse sendSync(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasWebhook()) {
      throw new NotificationException("Invalid webhook notification request", DEFAULT_ERROR_CODE, USER);
    }

    if (isEmpty(notificationRequest.getAccountId())) {
      throw new NotificationException(
          String.format("No account id encountered for %s.", notificationRequest.getId()), DEFAULT_ERROR_CODE, USER);
    }

    String notificationId = notificationRequest.getId();
    Webhook webhookDetails = notificationRequest.getWebhook();
    String templateId = webhookDetails.getTemplateId();
    Map<String, String> templateData = webhookDetails.getTemplateDataMap();

    List<String> webhookUrls = getRecipients(notificationRequest);
    if (isEmpty(webhookUrls)) {
      log.info("No webhookUrls found in notification request {}", notificationId);
      throw new NotificationException(
          String.format("No webhookUrls found in notification request %s.", notificationRequest.getId()),
          DEFAULT_ERROR_CODE, USER);
    }

    int expressionFunctorToken = Math.toIntExact(webhookDetails.getExpressionFunctorToken());

    Map<String, String> abstractionMap = notificationSettingsService.buildTaskAbstractions(
        notificationRequest.getAccountId(), webhookDetails.getOrgIdentifier(), webhookDetails.getProjectIdentifier());

    NotificationTaskResponse response = sendInSync(webhookUrls, templateId, templateData, notificationRequest.getId(),
        notificationRequest.getTeam(), notificationRequest.getAccountId(), expressionFunctorToken, abstractionMap,
        new HashMap<>(webhookDetails.getHeadersMap()), webhookDetails.getMessage());

    if (response.getProcessingResponse() == null || response.getProcessingResponse().getResult().isEmpty()
        || NotificationProcessingResponse.isNotificationRequestFailed(response.getProcessingResponse())) {
      throw new NotificationException(
          String.format(
              "Failed to send webhook notification. Check webhook configuration. %s", response.getErrorMessage()),
          DEFAULT_ERROR_CODE, USER);
    }

    return response;
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    WebhookSettingDTO webhookSettingDTO = (WebhookSettingDTO) notificationSettingDTO;
    String webhookUrl = webhookSettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(webhookUrl)) || Objects.isNull(stripToNull(webhookSettingDTO.getAccountId()))) {
      throw new NotificationException(
          "Malformed webhook Url encountered while processing Test Connection request " + webhookUrl,
          DEFAULT_ERROR_CODE, USER);
    }
    notificationSettingsHelper.validateRecipient(
        webhookUrl, notificationSettingDTO.getAccountId(), SettingIdentifiers.WEBHOOK_NOTIFICATION_ENDPOINTS_ALLOWLIST);
    NotificationProcessingResponse processingResponse = send(Collections.singletonList(webhookUrl),
        TEST_WEBHOOK_TEMPLATE, Collections.emptyMap(), webhookSettingDTO.getNotificationId(), null,
        notificationSettingDTO.getAccountId(), 0, Collections.emptyMap(), Collections.emptyMap());
    if (NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)) {
      throw new NotificationException(
          "Invalid webhook Url encountered while processing Test Connection request " + webhookUrl, DEFAULT_ERROR_CODE,
          USER);
    }
    return true;
  }

  private NotificationProcessingResponse send(List<String> webhookUrls, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId, int expressionFunctorToken,
      Map<String, String> abstractionMap, Map<String, String> headers) {
    Optional<String> templateOpt = notificationTemplateService.getTemplateAsString(templateId, team);
    if (!templateOpt.isPresent()) {
      log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }
    String template = templateOpt.get();
    StrSubstitutor strSubstitutor = new StrSubstitutor(templateData);
    String message = strSubstitutor.replace(template);
    List<String> webhookDomainAllowlist = notificationSettingsHelper.getTargetAllowlistFromSettings(
        SettingIdentifiers.WEBHOOK_NOTIFICATION_ENDPOINTS_ALLOWLIST, accountId);
    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.checkIfWebhookIsSecret(webhookUrls)
        || notificationSettingsService.checkIfHeadersHasAnySecretValue(headers)) {
      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(accountId)
                                                    .taskType("NOTIFY_WEBHOOK")
                                                    .taskParameters(WebhookTaskParams.builder()
                                                                        .notificationId(notificationId)
                                                                        .message(message)
                                                                        .webhookUrls(webhookUrls)
                                                                        .headers(headers)
                                                                        .webhookDomainAllowlist(webhookDomainAllowlist)
                                                                        .build())
                                                    .taskSetupAbstractions(abstractionMap)
                                                    .expressionFunctorToken(expressionFunctorToken)
                                                    .executionTimeout(Duration.ofMinutes(1L))
                                                    .build();
      String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
      log.info("Async delegate task created with taskID {}", taskId);
      processingResponse = NotificationProcessingResponse.allSent(webhookUrls.size());
    } else {
      processingResponse = webhookSender.send(webhookUrls, message, notificationId, headers, webhookDomainAllowlist);
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return processingResponse;
  }

  private NotificationTaskResponse sendInSync(List<String> webhookUrls, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId, int expressionFunctorToken,
      Map<String, String> abstractionMap, Map<String, String> headers, String message) {
    NotificationTaskResponse notificationTaskResponse;
    if (isNotEmpty(templateId)) {
      Optional<String> templateOpt = notificationTemplateService.getTemplateAsString(templateId, team);
      if (!templateOpt.isPresent()) {
        log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
        return NotificationTaskResponse.builder()
            .processingResponse(NotificationProcessingResponse.trivialResponseWithNoRetries)
            .build();
      }
      String template = templateOpt.get();
      StrSubstitutor strSubstitutor = new StrSubstitutor(templateData);
      message = strSubstitutor.replace(template);
    }

    List<String> webhookDomainAllowlist = notificationSettingsHelper.getTargetAllowlistFromSettings(
        SettingIdentifiers.WEBHOOK_NOTIFICATION_ENDPOINTS_ALLOWLIST, accountId);
    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.checkIfWebhookIsSecret(webhookUrls)
        || notificationSettingsService.checkIfHeadersHasAnySecretValue(headers)) {
      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(accountId)
                                                    .taskType("NOTIFY_WEBHOOK")
                                                    .taskParameters(WebhookTaskParams.builder()
                                                                        .notificationId(notificationId)
                                                                        .message(message)
                                                                        .webhookUrls(webhookUrls)
                                                                        .headers(headers)
                                                                        .webhookDomainAllowlist(webhookDomainAllowlist)
                                                                        .build())
                                                    .taskSetupAbstractions(abstractionMap)
                                                    .expressionFunctorToken(expressionFunctorToken)
                                                    .executionTimeout(Duration.ofMinutes(1L))
                                                    .build();
      DelegateResponseData responseData = null;
      try {
        responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      } catch (DelegateServiceDriverException exception) {
        throw new NotificationException(
            String.format("Failed to send notification %s, %s", notificationId, exception.getMessage()), exception,
            DEFAULT_ERROR_CODE, USER);
      }
      if (responseData instanceof ErrorNotifyResponseData) {
        throw new NotificationException(String.format("Failed to send notification %s ", notificationId),
            ((ErrorNotifyResponseData) responseData).getException(), DEFAULT_ERROR_CODE, USER);
      } else {
        notificationTaskResponse = (NotificationTaskResponse) responseData;
      }
    } else {
      processingResponse = webhookSender.send(webhookUrls, message, notificationId, headers, webhookDomainAllowlist);
      notificationTaskResponse = NotificationTaskResponse.builder().processingResponse(processingResponse).build();
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return notificationTaskResponse;
  }

  private List<String> getRecipients(NotificationRequest notificationRequest) {
    Webhook webhookChannelDetails = notificationRequest.getWebhook();
    List<String> recipients = new ArrayList<>(webhookChannelDetails.getUrlsList());
    if (isNotEmpty(webhookChannelDetails.getUserGroupList())) {
      List<String> resolvedRecipients = notificationSettingsService.getNotificationRequestForUserGroups(
          webhookChannelDetails.getUserGroupList(), NotificationChannelType.WEBHOOK, notificationRequest.getAccountId(),
          notificationRequest.getWebhook().getExpressionFunctorToken());
      recipients.addAll(resolvedRecipients);
    }
    return recipients.stream().distinct().filter(str -> !str.isEmpty()).collect(Collectors.toList());
  }
}
