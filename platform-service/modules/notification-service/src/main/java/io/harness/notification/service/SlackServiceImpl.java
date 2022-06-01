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
import static io.harness.notification.NotificationRequest.Slack;
import static io.harness.notification.NotificationServiceConstants.TEST_SLACK_TEMPLATE;

import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.delegate.beans.SlackTaskParams;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.SlackSettingDTO;
import io.harness.notification.senders.SlackSenderImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
public class SlackServiceImpl implements ChannelService {
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final SlackSenderImpl slackSender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasSlack()
        || Objects.isNull(notificationRequest.getAccountId())) {
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    String notificationId = notificationRequest.getId();
    NotificationRequest.Slack slackDetails = notificationRequest.getSlack();
    String templateId = slackDetails.getTemplateId();
    Map<String, String> templateData = slackDetails.getTemplateDataMap();

    if (Objects.isNull(trimToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    List<String> slackWebhookUrls = getRecipients(notificationRequest);
    if (isEmpty(slackWebhookUrls)) {
      log.info("No slackWebhookUrls found in notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    return send(slackWebhookUrls, templateId, templateData, notificationRequest.getId(), notificationRequest.getTeam(),
        notificationRequest.getAccountId());
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    SlackSettingDTO slackSettingDTO = (SlackSettingDTO) notificationSettingDTO;
    String webhookUrl = slackSettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(webhookUrl)) || Objects.isNull(stripToNull(slackSettingDTO.getAccountId()))) {
      throw new NotificationException(
          "Malformed webhook Url encountered while processing Test Connection request " + webhookUrl,
          DEFAULT_ERROR_CODE, USER);
    }
    NotificationProcessingResponse processingResponse = send(Collections.singletonList(webhookUrl), TEST_SLACK_TEMPLATE,
        Collections.emptyMap(), slackSettingDTO.getNotificationId(), null, notificationSettingDTO.getAccountId());
    if (NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)) {
      throw new NotificationException(
          "Invalid webhook Url encountered while processing Test Connection request " + webhookUrl, DEFAULT_ERROR_CODE,
          USER);
    }
    return true;
  }

  private NotificationProcessingResponse send(List<String> slackWebhookUrls, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId) {
    Optional<String> templateOpt = notificationTemplateService.getTemplateAsString(templateId, team);
    if (!templateOpt.isPresent()) {
      log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }
    String template = templateOpt.get();
    StrSubstitutor strSubstitutor = new StrSubstitutor(templateData);
    String message = strSubstitutor.replace(template);
    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.getSendNotificationViaDelegate(accountId)) {
      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(accountId)
                                                    .taskType("NOTIFY_SLACK")
                                                    .taskParameters(SlackTaskParams.builder()
                                                                        .notificationId(notificationId)
                                                                        .message(message)
                                                                        .slackWebhookUrls(slackWebhookUrls)
                                                                        .build())
                                                    .executionTimeout(Duration.ofMinutes(1L))
                                                    .build();
      NotificationTaskResponse notificationTaskResponse =
          (NotificationTaskResponse) delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
      processingResponse = notificationTaskResponse.getProcessingResponse();
    } else {
      processingResponse = slackSender.send(slackWebhookUrls, message, notificationId);
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return processingResponse;
  }

  private List<String> getRecipients(NotificationRequest notificationRequest) {
    Slack slackChannelDetails = notificationRequest.getSlack();
    List<String> recipients = new ArrayList<>(slackChannelDetails.getSlackWebHookUrlsList());
    if (isNotEmpty(slackChannelDetails.getUserGroupList())) {
      List<String> resolvedRecipients = notificationSettingsService.getNotificationRequestForUserGroups(
          slackChannelDetails.getUserGroupList(), NotificationChannelType.SLACK, notificationRequest.getAccountId());
      recipients.addAll(resolvedRecipients);
    }
    return recipients.stream().distinct().collect(Collectors.toList());
  }
}
