/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.services.impl.monitoredService.MonitoredServiceServiceImpl.buildMonitoredServiceParams;
import static io.harness.cvng.core.utils.FeatureFlagNames.SRM_CODE_ERROR_NOTIFICATIONS;
import static io.harness.cvng.notification.beans.NotificationRuleConditionType.CODE_ERRORS;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_URL;
import static io.harness.cvng.notification.utils.ErrorTrackingNotificationRuleUtils.buildMonitoredServiceConfigurationTabUrl;
import static io.harness.cvng.notification.utils.ErrorTrackingNotificationRuleUtils.getCodeErrorTemplateData;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_IDENTIFIER;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.client.ErrorTrackingService;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
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
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.notification.services.api.NotificationRuleTemplateDataGenerator;
import io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

  AtomicInteger count = new AtomicInteger(100000);

  @Override
  public void handleNotification(MonitoredService monitoredService) {
    int uniqueHandle = count.incrementAndGet();
    log.info("UniqueHandle: " + uniqueHandle + " - START Handling monitored service notifications for " + monitoredService.getServiceIdentifier());
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(monitoredService.getAccountId())
                                      .orgIdentifier(monitoredService.getOrgIdentifier())
                                      .projectIdentifier(monitoredService.getProjectIdentifier())
                                      .build();

    List<String> notificationRuleRefs = monitoredService.getNotificationRuleRefs()
                                            .stream()
                                            .filter(NotificationRuleRef::isEnabled)
                                            .map(NotificationRuleRef::getNotificationRuleRef)
                                            .collect(Collectors.toList());

    List<NotificationRule> notificationRules = notificationRuleService.getEntities(projectParams, notificationRuleRefs);
    final NotificationRuleTemplateDataGenerator notificationRuleTemplateDataGenerator =
            notificationRuleConditionTypeTemplateDataGeneratorMap.get(CODE_ERRORS);

    Set<String> notificationRuleRefsWithChange = new HashSet<>();

    for (NotificationRule notificationRule : notificationRules) {
      List<MonitoredServiceNotificationRuleCondition> conditions =
          ((MonitoredServiceNotificationRule) notificationRule).getConditions();
      for (MonitoredServiceNotificationRuleCondition condition : conditions) {
        NotificationRuleTemplateDataGenerator.NotificationData notificationData;
        if (CODE_ERRORS == condition.getType()) {
          log.info("   UniqueHandle: " + uniqueHandle + " - START Handling code error notification " + notificationRule.getName() );
          notificationData = getCodeErrorsNotificationData(monitoredService,
              (MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition) condition, notificationRule);
          if (notificationData.shouldSendNotification()) {
            Map<String, Object> entityDetails = Map.of(ENTITY_NAME, monitoredService.getName(), ENTITY_IDENTIFIER,
                monitoredService.getIdentifier(), SERVICE_IDENTIFIER, monitoredService.getServiceIdentifier());
            NotificationRule.CVNGNotificationChannel notificationChannel = notificationRule.getNotificationMethod();

            Map<String, String> templateData = notificationRuleTemplateDataGenerator.getTemplateData(
                projectParams, entityDetails, condition, notificationData.getTemplateDataMap());
            String templateId = notificationRuleTemplateDataGenerator.getTemplateId(
                notificationRule.getType(), notificationChannel.getType());
            try {
              NotificationResult notificationResult =
                  notificationClient.sendNotificationAsync(notificationChannel.toNotificationChannel(
                      monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
                      monitoredService.getProjectIdentifier(), templateId, templateData));
              log.info(
                  "Notification with Notification ID {}, Notification Rule {}, Condition {} for Monitored Service {} sent",
                  notificationResult.getNotificationId(), notificationRule.getName(),
                  condition.getType().getDisplayName(), monitoredService.getName());
            } catch (Exception ex) {
              log.error("Unable to send notification because of following exception", ex);
            }
            notificationRuleRefsWithChange.add(notificationRule.getIdentifier());
          }
          log.info("   UniqueHandle: " + uniqueHandle + " - END   Handling code error notification " + notificationRule.getName() );
        }
      }
      updateNotificationRuleRefInMonitoredService(
          projectParams, monitoredService, new ArrayList<>(notificationRuleRefsWithChange));
    }
    log.info("UniqueHandle: " + uniqueHandle + " - END   Handling monitored service notifications for " + monitoredService.getServiceIdentifier());
  }

  private NotificationRuleTemplateDataGenerator.NotificationData getCodeErrorsNotificationData(
      MonitoredService monitoredService,
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition,
      NotificationRule notificationRule) {
    MonitoredServiceParams monitoredServiceParams = buildMonitoredServiceParams(monitoredService);
    Map<String, String> templateDataMap = new HashMap<>();
    boolean featureFlagEnabled =
        featureFlagService.isFeatureFlagEnabled(monitoredService.getAccountId(), SRM_CODE_ERROR_NOTIFICATIONS);
    final List<String> environmentIdentifierList = monitoredService.getEnvironmentIdentifierList();
    boolean oneEnvironmentId = environmentIdentifierList != null && environmentIdentifierList.size() == 1;

    if (featureFlagEnabled && oneEnvironmentId) {
      String environmentId = environmentIdentifierList.get(0);
      ErrorTrackingNotificationData notificationData = null;
      try {
        notificationData = errorTrackingService.getNotificationData(monitoredService.getOrgIdentifier(),
            monitoredService.getAccountId(), monitoredService.getProjectIdentifier(),
            monitoredService.getServiceIdentifier(), environmentId, codeErrorCondition.getErrorTrackingEventStatus(),
            codeErrorCondition.getErrorTrackingEventTypes(), notificationRule.getUuid());
      } catch (Exception e) {
        log.error("Error connecting to the ErrorTracking Event Summary API.", e);
      }
      if (notificationData != null && !notificationData.getScorecards().isEmpty()) {
        final String baseLinkUrl =
            ((ErrorTrackingTemplateDataGenerator) notificationRuleConditionTypeTemplateDataGeneratorMap.get(
                 CODE_ERRORS))
                .getBaseLinkUrl(monitoredService.getAccountId());
        templateDataMap.putAll(
            getCodeErrorTemplateData(codeErrorCondition.getErrorTrackingEventStatus(), notificationData, baseLinkUrl));
        templateDataMap.put(
            NOTIFICATION_URL, buildMonitoredServiceConfigurationTabUrl(baseLinkUrl, monitoredServiceParams));
        templateDataMap.put(NOTIFICATION_NAME, notificationRule.getName());
        templateDataMap.put(ENVIRONMENT_NAME, environmentId);
        return NotificationRuleTemplateDataGenerator.NotificationData.builder()
            .shouldSendNotification(true)
            .templateDataMap(templateDataMap)
            .build();
      }
    }
    return NotificationRuleTemplateDataGenerator.NotificationData.builder().shouldSendNotification(false).build();
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
