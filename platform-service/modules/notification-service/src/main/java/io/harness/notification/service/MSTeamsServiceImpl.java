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
import static io.harness.notification.NotificationRequest.MSTeam;
import static io.harness.notification.NotificationServiceConstants.TEST_MSTEAMS_TEMPLATE;
import static io.harness.notification.constant.NotificationConstants.ABORTED_COLOR;
import static io.harness.notification.constant.NotificationConstants.BLUE_COLOR;
import static io.harness.notification.constant.NotificationConstants.COMPLETED_COLOR;
import static io.harness.notification.constant.NotificationConstants.FAILED_COLOR;
import static io.harness.notification.constant.NotificationConstants.PAUSED_COLOR;
import static io.harness.notification.constant.NotificationConstants.RESUMED_COLOR;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.MicrosoftTeamsTaskParams;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.MSTeamSettingDTO;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.senders.MSTeamsSenderImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StrSubstitutor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class MSTeamsServiceImpl implements ChannelService {
  private static final String ARTIFACTS = "ARTIFACTS";
  private static final String ASTERISK = "\\*";
  private static final String ASTERISK_REPLACEMENT = "**";
  private static final String COMMA = ",";
  private static final String KEY_VERB = "VERB";
  private static final String NA = "N / A";
  private static final String NAME = "_NAME";
  private static final String NEW_LINE = "\\n";
  private static final String NEW_LINE_REPLACEMENT = "\n\n";
  private static final String PIPELINE = "PIPELINE";
  private static final String[] schemes = {"https", "http", "rtsp", "ftp"};
  private static final List<String> TEMPLATE_KEYS_TO_BE_PROCESSED =
      ImmutableList.of("APPLICATION", ARTIFACTS, "ENVIRONMENT", PIPELINE, "SERVICE", "TRIGGER");
  private static final String THEME_COLOR = "THEME_COLOR";
  private static final String UNDERSCORE_REPLACEMENT = "\\\\\\\\\\_";
  private static final String UNDERSCORE = "_";
  private static final String URL = "_URL";
  private static final Pattern placeHolderPattern = Pattern.compile("\\$\\{.+?}");

  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final MSTeamsSenderImpl microsoftTeamsSender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasMsTeam()) {
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    String notificationId = notificationRequest.getId();
    MSTeam msTeamDetails = notificationRequest.getMsTeam();
    String templateId = msTeamDetails.getTemplateId();
    Map<String, String> templateData = msTeamDetails.getTemplateDataMap();

    if (Objects.isNull(trimToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    List<String> microsoftTeamsWebhookUrls = getRecipients(notificationRequest);
    if (isEmpty(microsoftTeamsWebhookUrls)) {
      log.info("No microsoft teams webhook url found in notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    int expressionFunctorToken = Math.toIntExact(msTeamDetails.getExpressionFunctorToken());

    Map<String, String> abstractionMap = notificationSettingsService.buildTaskAbstractions(
        notificationRequest.getAccountId(), msTeamDetails.getOrgIdentifier(), msTeamDetails.getProjectIdentifier());

    return send(microsoftTeamsWebhookUrls, templateId, templateData, notificationId, notificationRequest.getTeam(),
        notificationRequest.getAccountId(), expressionFunctorToken, abstractionMap);
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    MSTeamSettingDTO msTeamSettingDTO = (MSTeamSettingDTO) notificationSettingDTO;
    String webhookUrl = msTeamSettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(webhookUrl)) || Objects.isNull(stripToNull(msTeamSettingDTO.getAccountId()))) {
      throw new NotificationException("Malformed webhook Url encountered while processing Test Connection request "
              + notificationSettingDTO.getNotificationId(),
          DEFAULT_ERROR_CODE, USER);
    }
    NotificationProcessingResponse response = send(Collections.singletonList(webhookUrl), TEST_MSTEAMS_TEMPLATE,
        Collections.emptyMap(), msTeamSettingDTO.getNotificationId(), null, notificationSettingDTO.getAccountId(), 0,
        Collections.emptyMap());
    if (NotificationProcessingResponse.isNotificationRequestFailed(response)) {
      throw new NotificationException("Invalid webhook Url encountered while processing Test Connection request "
              + notificationSettingDTO.getNotificationId(),
          DEFAULT_ERROR_CODE, USER);
    }
    return true;
  }

  private NotificationProcessingResponse send(List<String> microsoftTeamsWebhookUrls, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId, int expressionFunctorToken,
      Map<String, String> abstractionMap) {
    Optional<String> templateOpt = notificationTemplateService.getTemplateAsString(templateId, team);
    if (!templateOpt.isPresent()) {
      log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }
    String template = templateOpt.get();

    templateData = processTemplateVariables(templateData);
    Optional<String> messageOpt = getDecoratedNotificationMessage(template, templateData);
    if (!messageOpt.isPresent()) {
      log.error(
          "Can't qualify the template {} with given template data for notification request {}. Please check the list of required fields",
          templateId, notificationId);
    }
    String message = messageOpt.get();
    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.checkIfWebhookIsSecret(microsoftTeamsWebhookUrls)) {
      DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountId)
              .taskType("NOTIFY_MICROSOFTTEAMS")
              .taskParameters(MicrosoftTeamsTaskParams.builder()
                                  .notificationId(notificationId)
                                  .message(message)
                                  .microsoftTeamsWebhookUrls(microsoftTeamsWebhookUrls)
                                  .build())
              .taskSetupAbstractions(abstractionMap)
              .expressionFunctorToken(expressionFunctorToken)
              .executionTimeout(Duration.ofMinutes(1L))
              .build();
      String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
      log.info("Async delegate task created with taskID {}", taskId);
      processingResponse = NotificationProcessingResponse.allSent(microsoftTeamsWebhookUrls.size());
    } else {
      processingResponse = microsoftTeamsSender.send(microsoftTeamsWebhookUrls, message, notificationId);
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return processingResponse;
  }

  private Optional<String> getDecoratedNotificationMessage(String templateText, Map<String, String> params) {
    templateText = StrSubstitutor.replace(templateText, params);
    if (placeHolderPattern.matcher(templateText).find()) {
      return Optional.empty();
    }
    return Optional.of(templateText);
  }

  private List<String> getRecipients(NotificationRequest notificationRequest) {
    MSTeam msTeamDetails = notificationRequest.getMsTeam();
    List<String> recipients = new ArrayList<>(msTeamDetails.getMsTeamKeysList());
    if (isNotEmpty(msTeamDetails.getUserGroupList())) {
      List<String> resolvedRecipients = notificationSettingsService.getNotificationRequestForUserGroups(
          msTeamDetails.getUserGroupList(), NotificationChannelType.MSTEAMS, notificationRequest.getAccountId(),
          notificationRequest.getMsTeam().getExpressionFunctorToken());
      recipients.addAll(resolvedRecipients);
    }
    return recipients.stream().distinct().collect(Collectors.toList());
  }

  Map<String, String> processTemplateVariables(Map<String, String> templateVariables) {
    Map<String, String> clonedTemplateVariables = new HashMap<>(templateVariables);
    clonedTemplateVariables.forEach((key, value) -> {
      String newValue = handleSpecialCharacters(key, value);
      if (newValue.isEmpty() && key.endsWith(NAME)) {
        newValue = NA;
      }
      clonedTemplateVariables.put(key, newValue);
    });
    formatTemplateUrlAndName(clonedTemplateVariables);
    String notificationStatus = templateVariables.getOrDefault(KEY_VERB, EMPTY);
    clonedTemplateVariables.put(THEME_COLOR, getThemeColor(notificationStatus, BLUE_COLOR));
    return clonedTemplateVariables;
  }

  private String getThemeColor(String status, String defaultColor) {
    switch (status) {
      case "completed":
        return COMPLETED_COLOR;
      case "expired":
      case "rejected":
      case "failed":
        return FAILED_COLOR;
      case "paused":
        return PAUSED_COLOR;
      case "resumed":
        return RESUMED_COLOR;
      case "aborted":
        return ABORTED_COLOR;
      default:
        return defaultColor;
    }
  }

  String handleSpecialCharacters(String key, String value) {
    if (key.contains(URL)) {
      return value;
    }
    value = value.replaceAll(ASTERISK, ASTERISK_REPLACEMENT).replaceAll(NEW_LINE, NEW_LINE_REPLACEMENT);
    String[] parts = value.split(SPACE);
    for (int index = 0; index < parts.length; index++) {
      String formattedValue = parts[index].replaceAll(UNDERSCORE, UNDERSCORE_REPLACEMENT);
      parts[index] = formattedValue;
    }
    return join(SPACE, parts);
  }

  private void formatTemplateUrlAndName(Map<String, String> templateVariables) {
    for (String key : TEMPLATE_KEYS_TO_BE_PROCESSED) {
      if (templateVariables.containsKey(key + NAME) && templateVariables.containsKey(key + URL)) {
        if (ARTIFACTS.equals(key)) {
          templateVariables.put(key, templateVariables.get(key + NAME));
        } else {
          String[] names = templateVariables.get(key + NAME).split(COMMA);
          String[] urls = templateVariables.get(key + URL).split(COMMA);
          String updatedValue = getUpdatedValue(names, urls);
          if (PIPELINE.equals(key)) {
            updatedValue = (!NA.equals(updatedValue)) ? format("in pipeline %s", updatedValue) : EMPTY;
          }
          templateVariables.put(key, updatedValue);
        }
      }
    }
  }

  String getUpdatedValue(String[] names, String[] urls) {
    List<String> updatedValue = new ArrayList<>();
    if (names.length != urls.length) {
      log.info("Name and URL array has length mismatch. Names={} Urls={}", names, urls);
    } else {
      for (int index = 0; index < names.length; index++) {
        if (StringUtils.isNotEmpty(urls[index])) {
          updatedValue.add(format("[%s](%s)", names[index], urls[index]));
        } else {
          updatedValue.add(names[index]);
        }
      }
    }
    return join(", ", updatedValue);
  }
}
