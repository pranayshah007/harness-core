/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.FeatureFlagNames.CET_SAVED_SEARCH_NOTIFICATION;
import static io.harness.cvng.core.utils.FeatureFlagNames.CET_SINGLE_NOTIFICATION;
import static io.harness.cvng.core.utils.FeatureFlagNames.SRM_CODE_ERROR_NOTIFICATIONS;
import static io.harness.cvng.notification.beans.NotificationRuleConditionType.CODE_ERRORS;
import static io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator.NotificationData;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_IDENTIFIER;

import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.client.ErrorTrackingService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.monitoredService.ErrorTrackingNotificationService;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator;
import io.harness.cvng.notification.utils.errortracking.interfaces.AggregatedNotification;
import io.harness.cvng.notification.utils.errortracking.interfaces.ImmediateNotification;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorTrackingNotificationServiceImpl implements ErrorTrackingNotificationService {
  @Inject private Clock clock;
  @Inject private NotificationRuleService notificationRuleService;
  @Inject
  private Map<NotificationRuleConditionType, NotificationRuleTemplateDataGenerator>
      notificationRuleConditionTypeTemplateDataGeneratorMap;
  @Inject private NotificationClient notificationClient;
  @Inject private HPersistence hPersistence;
  @Inject private ErrorTrackingService errorTrackingService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void handleNotification(MonitoredService monitoredService) {
    boolean errorTrackingNotificationsEnabled =
        featureFlagService.isFeatureFlagEnabled(monitoredService.getAccountId(), SRM_CODE_ERROR_NOTIFICATIONS);
    boolean errorTrackingSavedSearchNotificationsEnabled =
        featureFlagService.isFeatureFlagEnabled(monitoredService.getAccountId(), CET_SAVED_SEARCH_NOTIFICATION);
    boolean errorTrackingSingleNotificationsEnabled =
        featureFlagService.isFeatureFlagEnabled(monitoredService.getAccountId(), CET_SINGLE_NOTIFICATION);

    if (errorTrackingNotificationsEnabled) {
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(monitoredService.getAccountId())
                                        .orgIdentifier(monitoredService.getOrgIdentifier())
                                        .projectIdentifier(monitoredService.getProjectIdentifier())
                                        .build();

      final NotificationRuleTemplateDataGenerator notificationRuleTemplateDataGenerator =
          notificationRuleConditionTypeTemplateDataGeneratorMap.get(CODE_ERRORS);

      Set<String> notificationRuleRefsWithChange = new HashSet<>();
      final Optional<String> environmentId = getSingleEnvironmentId(monitoredService);
      if (environmentId.isPresent()) {
        for (NotificationRuleRef notificationRuleRef : monitoredService.getNotificationRuleRefs()) {
          if (notificationRuleRef.isEnabled()) {
            MonitoredServiceNotificationRule notificationRule =
                (MonitoredServiceNotificationRule) notificationRuleService.getEntity(
                    projectParams, notificationRuleRef.getNotificationRuleRef());
            for (MonitoredServiceNotificationRuleCondition condition : notificationRule.getConditions()) {
              if (CODE_ERRORS == condition.getType()) {
                MonitoredServiceCodeErrorCondition codeErrorCondition = (MonitoredServiceCodeErrorCondition) condition;
                // if aggregated is null is for backwards compatibility when aggregated wasn't set
                if (codeErrorCondition.getAggregated() == null || codeErrorCondition.getAggregated()) {
                  final Integer thresholdMinutes = codeErrorCondition.getVolumeThresholdMinutes();
                  // check and send the message if there are no threshold minutes
                  // OR check the eligibility against a defined threshold minutes
                  if (thresholdMinutes == null
                      || notificationRuleRef.isEligible(clock.instant(), Duration.ofMinutes(thresholdMinutes))) {
                    NotificationData notification = getAggregatedNotificationData(monitoredService, environmentId.get(),
                        codeErrorCondition, notificationRule, errorTrackingSavedSearchNotificationsEnabled);
                    sendNotification(notification, monitoredService, notificationRule,
                        notificationRuleTemplateDataGenerator, projectParams, codeErrorCondition,
                        notificationRuleRefsWithChange);

                    // when we don't send notification, and we had an eligible threshold minutes
                    if (!notification.shouldSendNotification() && thresholdMinutes != null) {
                      // add it to the refs with change list which will update the lastSuccessfullNotificationTime, even
                      // though we don't send the notification, we want to update this time to delay the next check to
                      // not check again until the threshold duration has been met against the last time we checked.
                      notificationRuleRefsWithChange.add(notificationRule.getIdentifier());
                    }
                  }
                } else if (errorTrackingSingleNotificationsEnabled) {
                  List<NotificationData> notifications;
                  notifications =
                      getStackTraceNotificationsData(monitoredService, environmentId.get(), notificationRule);
                  for (NotificationData notification : notifications) {
                    sendNotification(notification, monitoredService, notificationRule,
                        notificationRuleTemplateDataGenerator, projectParams, codeErrorCondition,
                        notificationRuleRefsWithChange);
                  }
                }
              }
            }
            updateNotificationRuleRefInMonitoredService(
                projectParams, monitoredService, new ArrayList<>(notificationRuleRefsWithChange));
          }
        }
      }
    }
  }

  private void sendNotification(NotificationData notification, MonitoredService monitoredService,
      NotificationRule notificationRule, NotificationRuleTemplateDataGenerator notificationRuleTemplateDataGenerator,
      ProjectParams projectParams, MonitoredServiceCodeErrorCondition codeErrorCondition,
      Set<String> notificationRuleRefsWithChange) {
    if (notification.shouldSendNotification()) {
      Map<String, Object> entityDetails = Map.of(ENTITY_NAME, monitoredService.getName(), ENTITY_IDENTIFIER,
          monitoredService.getIdentifier(), SERVICE_IDENTIFIER, monitoredService.getServiceIdentifier());

      NotificationRule.CVNGNotificationChannel notificationChannel = notificationRule.getNotificationMethod();

      Map<String, String> templateData = notificationRuleTemplateDataGenerator.getTemplateData(
          projectParams, entityDetails, codeErrorCondition, notification.getTemplateDataMap());

      String templateId = notificationRuleTemplateDataGenerator.getTemplateId(
          notificationRule.getType(), notificationChannel.getType());

      try {
        NotificationResult notificationResult =
            notificationClient.sendNotificationAsync(notificationChannel.toNotificationChannel(
                monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
                monitoredService.getProjectIdentifier(), templateId, templateData));
        log.info(
            "Notification with Notification ID {}, Notification Rule {}, Condition {} for Monitored Service {} sent",
            notificationResult.getNotificationId(), notificationRule.getName(), CODE_ERRORS.getDisplayName(),
            monitoredService.getName());
      } catch (Exception ex) {
        log.error("Unable to send notification because of following exception", ex);
      }
      notificationRuleRefsWithChange.add(notificationRule.getIdentifier());
    }
  }

  private NotificationData getAggregatedNotificationData(MonitoredService monitoredService, String environmentId,
      MonitoredServiceCodeErrorCondition codeErrorCondition, NotificationRule notificationRule,
      boolean errorTrackingSavedSearchNotificationsEnabled) {
    ErrorTrackingNotificationData notificationData = null;
    try {
      if (codeErrorCondition.getSavedFilterId() == null) {
        notificationData = errorTrackingService.getNotificationData(monitoredService.getOrgIdentifier(),
            monitoredService.getAccountId(), monitoredService.getProjectIdentifier(),
            monitoredService.getServiceIdentifier(), environmentId, codeErrorCondition.getErrorTrackingEventStatus(),
            codeErrorCondition.getErrorTrackingEventTypes(), notificationRule.getUuid(),
            codeErrorCondition.getVolumeThresholdCount());
      } else if (errorTrackingSavedSearchNotificationsEnabled) {
        notificationData = errorTrackingService.getNotificationSavedFilterData(monitoredService.getOrgIdentifier(),
            monitoredService.getAccountId(), monitoredService.getProjectIdentifier(),
            monitoredService.getServiceIdentifier(), environmentId, codeErrorCondition.getSavedFilterId(),
            codeErrorCondition.getVolumeThresholdCount(), notificationRule.getUuid());
      }
    } catch (Exception e) {
      log.error("Error connecting to the ErrorTracking Event Summary API.", e);
    }
    try {
      if (notificationData != null && !notificationData.getScorecards().isEmpty()) {
        final String baseLinkUrl = getBaseLinkUrl(monitoredService);
        return NotificationData.builder()
            .shouldSendNotification(true)
            .templateDataMap(AggregatedNotification.getNotificationDataMap(
                notificationData, codeErrorCondition, baseLinkUrl, monitoredService, notificationRule, environmentId))
            .build();
      }
    } catch (Exception e) {
      log.error("Error building the template data map", e);
    }
    return NotificationData.builder().shouldSendNotification(false).build();
  }

  private List<NotificationData> getStackTraceNotificationsData(
      MonitoredService monitoredService, String environmentId, NotificationRule notificationRule) {
    List<NotificationData> notifications = new ArrayList<>();
    List<ErrorTrackingHitSummary> hitSummaries = null;
    try {
      hitSummaries = errorTrackingService.getNotificationNewData(monitoredService.getOrgIdentifier(),
          monitoredService.getAccountId(), monitoredService.getProjectIdentifier(),
          monitoredService.getServiceIdentifier(), environmentId);

    } catch (Exception e) {
      log.error("Error connecting to the ErrorTracking Event Summary API.", e);
    }
    if (hitSummaries != null) {
      final String baseLinkUrl = getBaseLinkUrl(monitoredService);

      for (ErrorTrackingHitSummary hitSummary : hitSummaries) {
        notifications.add(NotificationData.builder()
                              .shouldSendNotification(true)
                              .templateDataMap(ImmediateNotification.getNotificationDataMap(
                                  hitSummary, baseLinkUrl, monitoredService, notificationRule, environmentId))
                              .build());
      }
    }
    return notifications;
  }

  private Optional<String> getSingleEnvironmentId(MonitoredService monitoredService) {
    Optional<String> environmentId = Optional.empty();
    final List<String> environmentIdentifierList = monitoredService.getEnvironmentIdentifierList();
    boolean oneEnvironmentId = environmentIdentifierList != null && environmentIdentifierList.size() == 1;

    if (oneEnvironmentId) {
      environmentId = Optional.ofNullable(environmentIdentifierList.get(0));
    }
    return environmentId;
  }

  private String getBaseLinkUrl(MonitoredService monitoredService) {
    return ((ErrorTrackingTemplateDataGenerator) notificationRuleConditionTypeTemplateDataGeneratorMap.get(CODE_ERRORS))
        .getBaseLinkUrl(monitoredService.getAccountId());
  }

  private void updateNotificationRuleRefInMonitoredService(
      ProjectParams projectParams, MonitoredService monitoredService, List<String> notificationRuleRefs) {
    List<NotificationRuleRef> allNotificationRuleRefs = new ArrayList<>();
    List<NotificationRuleRef> notificationRuleRefsWithoutChange =
        monitoredService.getNotificationRuleRefs()
            .stream()
            .filter(notificationRuleRef -> !notificationRuleRefs.contains(notificationRuleRef.getNotificationRuleRef()))
            .collect(Collectors.toList());
    List<NotificationRuleRefDTO> notificationRuleRefDTOs =
        notificationRuleRefs.stream()
            .map(notificationRuleRef
                -> NotificationRuleRefDTO.builder().notificationRuleRef(notificationRuleRef).enabled(true).build())
            .collect(Collectors.toList());
    List<NotificationRuleRef> notificationRuleRefsWithChange = notificationRuleService.getNotificationRuleRefs(
        projectParams, notificationRuleRefDTOs, NotificationRuleType.MONITORED_SERVICE, clock.instant());
    allNotificationRuleRefs.addAll(notificationRuleRefsWithChange);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithoutChange);
    UpdateOperations<MonitoredService> updateOperations = hPersistence.createUpdateOperations(MonitoredService.class);
    updateOperations.set(MonitoredServiceKeys.notificationRuleRefs, allNotificationRuleRefs);

    hPersistence.update(monitoredService, updateOperations);
  }
}
