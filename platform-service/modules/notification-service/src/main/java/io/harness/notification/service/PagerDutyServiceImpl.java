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
import static io.harness.notification.NotificationRequest.PagerDuty;
import static io.harness.notification.NotificationServiceConstants.TEST_PD_TEMPLATE;
import static io.harness.notification.constant.NotificationClientConstants.HARNESS_NAME;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.NotificationProcessingResponse;
import io.harness.delegate.beans.NotificationTaskResponse;
import io.harness.delegate.beans.PagerDutyTaskParams;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.remote.dto.NotificationSettingDTO;
import io.harness.notification.remote.dto.PagerDutySettingDTO;
import io.harness.notification.senders.PagerDutySenderImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.utils.NotificationSettingsHelper;
import io.harness.serializer.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.dikhan.pagerduty.client.events.domain.LinkContext;
import com.github.dikhan.pagerduty.client.events.domain.Payload;
import com.github.dikhan.pagerduty.client.events.domain.Severity;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StrSubstitutor;
import org.apache.commons.text.WordUtils;
import org.json.JSONObject;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class PagerDutyServiceImpl implements ChannelService {
  private final NotificationSettingsService notificationSettingsService;
  private final NotificationTemplateService notificationTemplateService;
  private final YamlUtils yamlUtils;
  private final PagerDutySenderImpl pagerDutySender;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final NotificationSettingsHelper notificationSettingsHelper;

  @Override
  public NotificationProcessingResponse send(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasPagerDuty()) {
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    String notificationId = notificationRequest.getId();
    PagerDuty pagerDutyDetails = notificationRequest.getPagerDuty();
    String templateId = pagerDutyDetails.getTemplateId();
    Map<String, String> templateData = pagerDutyDetails.getTemplateDataMap();

    if (Objects.isNull(stripToNull(templateId))) {
      log.info("template Id is null for notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    List<String> pagerDutyKeys = getRecipients(notificationRequest);
    if (isEmpty(pagerDutyKeys)) {
      log.info("No pagerduty integration key found in notification request {}", notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }

    int expressionFunctorToken = Math.toIntExact(pagerDutyDetails.getExpressionFunctorToken());

    Map<String, String> abstractionMap =
        notificationSettingsService.buildTaskAbstractions(notificationRequest.getAccountId(),
            pagerDutyDetails.getOrgIdentifier(), pagerDutyDetails.getProjectIdentifier());

    return send(pagerDutyKeys, templateId, templateData, notificationRequest.getId(), notificationRequest.getTeam(),
        notificationRequest.getAccountId(), expressionFunctorToken, abstractionMap);
  }

  @Override
  public NotificationTaskResponse sendSync(NotificationRequest notificationRequest) {
    if (Objects.isNull(notificationRequest) || !notificationRequest.hasPagerDuty()) {
      throw new NotificationException("Invalid pager duty notification request", DEFAULT_ERROR_CODE, USER);
    }

    if (isEmpty(notificationRequest.getAccountId())) {
      throw new NotificationException(
          String.format("No account id encountered for %s.", notificationRequest.getId()), DEFAULT_ERROR_CODE, USER);
    }

    String notificationId = notificationRequest.getId();
    PagerDuty pagerDutyDetails = notificationRequest.getPagerDuty();
    String templateId = pagerDutyDetails.getTemplateId();
    Map<String, String> templateData = pagerDutyDetails.getTemplateDataMap();

    List<String> pagerDutyKeys = getRecipients(notificationRequest);
    if (isEmpty(pagerDutyKeys)) {
      log.info("No pagerduty integration key found in notification request {}", notificationId);
      throw new NotificationException(
          String.format("No pagerduty integration key found in notification request %s.", notificationRequest.getId()),
          DEFAULT_ERROR_CODE, USER);
    }

    int expressionFunctorToken = Math.toIntExact(pagerDutyDetails.getExpressionFunctorToken());

    Map<String, String> abstractionMap =
        notificationSettingsService.buildTaskAbstractions(notificationRequest.getAccountId(),
            pagerDutyDetails.getOrgIdentifier(), pagerDutyDetails.getProjectIdentifier());

    NotificationTaskResponse response = sendInSync(pagerDutyKeys, templateId, templateData, notificationRequest.getId(),
        notificationRequest.getTeam(), notificationRequest.getAccountId(), expressionFunctorToken, abstractionMap,
        pagerDutyDetails.getSummary(), pagerDutyDetails.getLinksMap());

    if (response.getProcessingResponse() == null || response.getProcessingResponse().getResult().isEmpty()
        || NotificationProcessingResponse.isNotificationRequestFailed(response.getProcessingResponse())) {
      throw new NotificationException(
          String.format("Failed to send pagerduty notification. Check configuration. %s", response.getErrorMessage()),
          DEFAULT_ERROR_CODE, USER);
    }

    return response;
  }

  @Override
  public boolean sendTestNotification(NotificationSettingDTO notificationSettingDTO) {
    PagerDutySettingDTO pagerDutySettingDTO = (PagerDutySettingDTO) notificationSettingDTO;
    String pagerdutyKey = pagerDutySettingDTO.getRecipient();
    if (Objects.isNull(stripToNull(pagerdutyKey)) || Objects.isNull(stripToNull(pagerDutySettingDTO.getAccountId()))) {
      throw new NotificationException("Malformed pagerduty key encountered while processing Test Connection request "
              + notificationSettingDTO.getNotificationId(),
          DEFAULT_ERROR_CODE, USER);
    }
    notificationSettingsHelper.validateRecipient(pagerdutyKey, notificationSettingDTO.getAccountId(),
        SettingIdentifiers.PAGERDUTY_NOTIFICATION_INTEGRATION_KEYS_ALLOWLIST);
    NotificationProcessingResponse processingResponse = send(Collections.singletonList(pagerdutyKey), TEST_PD_TEMPLATE,
        Collections.emptyMap(), pagerDutySettingDTO.getNotificationId(), null, notificationSettingDTO.getAccountId(), 0,
        Collections.emptyMap());
    if (NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)) {
      throw new NotificationException("Invalid pagerduty key encountered while processing Test Connection request "
              + notificationSettingDTO.getNotificationId(),
          DEFAULT_ERROR_CODE, USER);
    }
    return true;
  }

  private NotificationProcessingResponse send(List<String> pagerDutyKeys, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId, int expressionFunctorToken,
      Map<String, String> abstractionMap) {
    Optional<PagerDutyTemplate> templateOpt = getTemplate(templateId, team);
    if (!templateOpt.isPresent()) {
      log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
      return NotificationProcessingResponse.trivialResponseWithNoRetries;
    }
    PagerDutyTemplate template = templateOpt.get();

    StrSubstitutor strSubstitutor = new StrSubstitutor(templateData);
    String summary = template.getSummary();
    summary = strSubstitutor.replace(summary);

    List<LinkContext> links = new ArrayList<>();
    Map<String, String> customDetails =
        templateData.keySet()
            .stream()
            .filter(key -> !key.equalsIgnoreCase("START_TS_SECS") && !key.equalsIgnoreCase("END_TS_SECS"))
            .collect(Collectors.toMap(
                key -> WordUtils.capitalizeFully(key.replace("_", " ")), templateData::get, (a, b) -> b));

    JSONObject jsonObject = new JSONObject(customDetails);
    Payload payload = Payload.Builder.newBuilder()
                          .setTimestamp(OffsetDateTime.now())
                          .setSeverity(Severity.ERROR)
                          .setCustomDetails(jsonObject)
                          .setSummary(summary)
                          .setSource(HARNESS_NAME)
                          .build();

    if (Objects.nonNull(template.getLink())) {
      String linkHref = template.getLink().getHref();
      String linkText = template.getLink().getText();
      LinkContext linkContext = new LinkContext(linkHref, linkText);
      links.add(linkContext);
    }

    List<String> pagerDutyKeysAllowlist = notificationSettingsHelper.getTargetAllowlistFromSettings(
        SettingIdentifiers.PAGERDUTY_NOTIFICATION_INTEGRATION_KEYS_ALLOWLIST, accountId);

    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.checkIfWebhookIsSecret(pagerDutyKeys)) {
      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(accountId)
                                                    .taskType("NOTIFY_PAGERDUTY")
                                                    .taskParameters(PagerDutyTaskParams.builder()
                                                                        .notificationId(notificationId)
                                                                        .pagerDutyKeys(pagerDutyKeys)
                                                                        .payload(payload)
                                                                        .links(links)
                                                                        .pagerDutyKeysAllowlist(pagerDutyKeysAllowlist)
                                                                        .build())
                                                    .taskSetupAbstractions(abstractionMap)
                                                    .expressionFunctorToken(expressionFunctorToken)
                                                    .executionTimeout(Duration.ofMinutes(1L))
                                                    .build();
      String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
      log.info("Async delegate task created with taskID {}", taskId);
      processingResponse = NotificationProcessingResponse.allSent(pagerDutyKeys.size());
    } else {
      processingResponse = pagerDutySender.send(pagerDutyKeys, payload, links, notificationId, pagerDutyKeysAllowlist);
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return processingResponse;
  }

  private NotificationTaskResponse sendInSync(List<String> pagerDutyKeys, String templateId,
      Map<String, String> templateData, String notificationId, Team team, String accountId, int expressionFunctorToken,
      Map<String, String> abstractionMap, String summary, Map<String, String> links) {
    NotificationTaskResponse notificationTaskResponse;
    List<LinkContext> linkContexts = new ArrayList<>();
    if (isNotEmpty(links)) {
      linkContexts = links.entrySet()
                         .stream()
                         .map(entry -> new LinkContext(entry.getKey(), entry.getValue()))
                         .collect(Collectors.toList());
    }

    if (isNotEmpty(templateId)) {
      Optional<PagerDutyTemplate> templateOpt = getTemplate(templateId, team);
      if (!templateOpt.isPresent()) {
        log.info("Can't find template with templateId {} for notification request {}", templateId, notificationId);
        return NotificationTaskResponse.builder()
            .processingResponse(NotificationProcessingResponse.trivialResponseWithNoRetries)
            .build();
      }
      PagerDutyTemplate template = templateOpt.get();

      StrSubstitutor strSubstitutor = new StrSubstitutor(templateData);
      summary = template.getSummary();
      summary = strSubstitutor.replace(summary);

      linkContexts = new ArrayList<>();
      if (Objects.nonNull(template.getLink())) {
        String linkHref = template.getLink().getHref();
        String linkText = template.getLink().getText();
        LinkContext linkContext = new LinkContext(linkHref, linkText);
        linkContexts.add(linkContext);
      }
    }

    Map<String, String> customDetails =
        templateData.keySet()
            .stream()
            .filter(key -> !key.equalsIgnoreCase("START_TS_SECS") && !key.equalsIgnoreCase("END_TS_SECS"))
            .collect(Collectors.toMap(
                key -> WordUtils.capitalizeFully(key.replace("_", " ")), templateData::get, (a, b) -> b));

    JSONObject jsonObject = new JSONObject(customDetails);
    Payload payload = Payload.Builder.newBuilder()
                          .setTimestamp(OffsetDateTime.now())
                          .setSeverity(Severity.ERROR)
                          .setCustomDetails(jsonObject)
                          .setSummary(summary)
                          .setSource(HARNESS_NAME)
                          .build();

    List<String> pagerDutyKeysAllowlist = notificationSettingsHelper.getTargetAllowlistFromSettings(
        SettingIdentifiers.PAGERDUTY_NOTIFICATION_INTEGRATION_KEYS_ALLOWLIST, accountId);

    NotificationProcessingResponse processingResponse = null;
    if (notificationSettingsService.checkIfWebhookIsSecret(pagerDutyKeys)) {
      DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                    .accountId(accountId)
                                                    .taskType("NOTIFY_PAGERDUTY")
                                                    .taskParameters(PagerDutyTaskParams.builder()
                                                                        .notificationId(notificationId)
                                                                        .pagerDutyKeys(pagerDutyKeys)
                                                                        .payload(payload)
                                                                        .links(linkContexts)
                                                                        .pagerDutyKeysAllowlist(pagerDutyKeysAllowlist)
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
      processingResponse =
          pagerDutySender.send(pagerDutyKeys, payload, linkContexts, notificationId, pagerDutyKeysAllowlist);
      notificationTaskResponse = NotificationTaskResponse.builder().processingResponse(processingResponse).build();
    }
    log.info(NotificationProcessingResponse.isNotificationRequestFailed(processingResponse)
            ? "Failed to send notification for request {}"
            : "Notification request {} sent",
        notificationId);
    return notificationTaskResponse;
  }

  private Optional<PagerDutyTemplate> getTemplate(String templateId, Team team) {
    Optional<String> templateStrOptional = notificationTemplateService.getTemplateAsString(templateId, team);
    if (!templateStrOptional.isPresent()) {
      return Optional.empty();
    }
    PagerDutyTemplate pagerDutyTemplate = null;
    try {
      pagerDutyTemplate = yamlUtils.read(templateStrOptional.get(), new TypeReference<PagerDutyTemplate>() {});
    } catch (IOException e) {
      log.error("Failed to parse the saved file into yaml. check format for {}", templateId);
    }
    return Optional.ofNullable(pagerDutyTemplate);
  }

  private List<String> getRecipients(NotificationRequest notificationRequest) {
    PagerDuty pagerDutyDetails = notificationRequest.getPagerDuty();
    List<String> recipients = new ArrayList<>(pagerDutyDetails.getPagerDutyIntegrationKeysList());
    if (isNotEmpty(pagerDutyDetails.getUserGroupList())) {
      List<String> resolvedRecipients = notificationSettingsService.getNotificationRequestForUserGroups(
          pagerDutyDetails.getUserGroupList(), NotificationChannelType.PAGERDUTY, notificationRequest.getAccountId(),
          notificationRequest.getPagerDuty().getExpressionFunctorToken());
      recipients.addAll(resolvedRecipients);
    }
    return recipients.stream().distinct().filter(str -> !str.isEmpty()).collect(Collectors.toList());
  }

  @Getter
  @Setter
  public static class Link {
    private String href;
    private String text;
  }

  @Getter
  @Setter
  public static class PagerDutyTemplate {
    private String summary;
    private Link link;
  }
}
