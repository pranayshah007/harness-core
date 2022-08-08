/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.deployment.checks;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.beans.alert.DeploymentFreezeEventAlert.EventType.ACTIVATION;
import static software.wings.beans.alert.DeploymentFreezeEventAlert.EventType.DEACTIVATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.FREEZE_ACTIVATION_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.FREEZE_DEACTIVATION_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.PIPELINE_FREEZE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.TRIGGER_EXECUTION_REJECTED_NOTIFICATION;

import static java.lang.String.format;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.governance.GovernanceFreezeConfig;

import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.NotificationRule;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.DeploymentFreezeEventAlert;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.helpers.ext.url.SubdomainUrlHelper;
import software.wings.service.impl.compliance.GovernanceUserGroupHelper;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
public class DeploymentFreezeUtils {
  public static final int ALERT_VALID_DAYS = 14;
  public static final String BLACKOUT_WINDOW_NAME = "BLACKOUT_WINDOW_NAME";
  public static final String START_TIME = "START_TIME";
  public static final String END_TIME = "END_TIME";
  public static final int MAXIMUM_ITERATOR_DELAY = 5 * 60 * 1000;
  private static final String BLACKOUT_WINDOW_URL = "BLACKOUT_WINDOW_URL";
  @Inject NotificationService notificationService;
  @Inject GovernanceConfigService governanceConfigService;
  @Inject AlertService alertService;
  @Inject WorkflowNotificationHelper workflowNotificationHelper;
  @Inject private SubdomainUrlHelper subdomainUrlHelper;
  @Inject private GovernanceUserGroupHelper governanceUserGroupHelper;

  public void sendPipelineRejectionNotification(
      String accountId, String appId, List<String> deploymentFreezeIds, Map<String, String> placeholderValues) {
    sendNotificationOfType(PIPELINE_FREEZE_NOTIFICATION, appId, deploymentFreezeIds, placeholderValues, accountId);
  }

  public void sendTriggerRejectedNotification(
      String accountId, String appId, List<String> deploymentFreezeIds, Map<String, String> placeholderValues) {
    sendNotificationOfType(
        TRIGGER_EXECUTION_REJECTED_NOTIFICATION, appId, deploymentFreezeIds, placeholderValues, accountId);
  }

  // Send notification of a particular type for the user groups in freeze windowIds provided
  private void sendNotificationOfType(NotificationMessageType notificationMessageType, String appId,
      List<String> deploymentFreezeIds, Map<String, String> placeholderValues, String accountId) {
    List<GovernanceFreezeConfig> governanceFreezeConfigs =
        governanceConfigService.getGovernanceFreezeConfigs(accountId, deploymentFreezeIds);
    governanceFreezeConfigs.forEach(freezeWindow -> {
      Map<String, String> clonedPlaceHolderMap = new HashMap<>(placeholderValues);
      clonedPlaceHolderMap.put(END_TIME, workflowNotificationHelper.getFormattedTime(freezeWindow.fetchEndTime()));
      List<String> userGroupForNotification = freezeWindow.getUserGroups();
      if (freezeWindow.getUserGroupSelection() != null) {
        userGroupForNotification =
            governanceUserGroupHelper.getUserGroups(freezeWindow.getUserGroupSelection(), accountId);
      }
      sendNotificationToUserGroups(accountId, clonedPlaceHolderMap, userGroupForNotification, appId,
          notificationMessageType, freezeWindow.getName());
    });
  }

  public void handleActivationEvent(GovernanceFreezeConfig governanceFreezeConfig, String accountId) {
    AlertData freezeAlert = createAlert(governanceFreezeConfig, ACTIVATION);
    alertService.closeExistingAlertsAndOpenNew(
        accountId, GLOBAL_APP_ID, AlertType.DEPLOYMENT_FREEZE_EVENT, freezeAlert, null);

    log.info("Deployment freeze activation alert issued for {}", governanceFreezeConfig.getUuid());

    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put(START_TIME, workflowNotificationHelper.getFormattedTime(System.currentTimeMillis()));
    placeholderValues.put(END_TIME, workflowNotificationHelper.getFormattedTime(governanceFreezeConfig.fetchEndTime()));
    List<String> userGroupsForNotification = governanceFreezeConfig.getUserGroups();
    if (governanceFreezeConfig.getUserGroupSelection() != null) {
      userGroupsForNotification =
          governanceUserGroupHelper.getUserGroups(governanceFreezeConfig.getUserGroupSelection(), accountId);
    }
    sendNotificationToUserGroups(accountId, placeholderValues, userGroupsForNotification, GLOBAL_APP_ID,
        FREEZE_ACTIVATION_NOTIFICATION, governanceFreezeConfig.getName());
  }

  private void sendNotificationToUserGroups(String accountId, Map<String, String> placeholderValues,
      List<String> userGroups, String appId, NotificationMessageType freezeNotification, String windowName) {
    placeholderValues.put(BLACKOUT_WINDOW_NAME, windowName);
    placeholderValues.put(BLACKOUT_WINDOW_URL, getGovernanceUrl(accountId));
    NotificationRule notificationRule = aNotificationRule().withUserGroupIds(userGroups).build();
    Notification notification = InformationNotification.builder()
                                    .appId(appId)
                                    .accountId(accountId)
                                    .notificationTemplateId(freezeNotification.name())
                                    .notificationTemplateVariables(placeholderValues)
                                    .build();
    notificationService.sendNotificationAsync(notification, Collections.singletonList(notificationRule));
    log.info("Notification sent of type {} successfully for user groups in freeze window {}", freezeNotification,
        windowName);
  }

  public void handleDeActivationEvent(GovernanceFreezeConfig governanceFreezeConfig, String accountId) {
    AlertData freezeAlert = createAlert(governanceFreezeConfig, DEACTIVATION);
    Date closingDate = Date.from(Instant.now().plus(ALERT_VALID_DAYS, ChronoUnit.DAYS));
    alertService.closeExistingAlertsAndOpenNew(
        accountId, GLOBAL_APP_ID, AlertType.DEPLOYMENT_FREEZE_EVENT, freezeAlert, closingDate);

    log.info("Deployment freeze activation alert closed and de-activation alert issued for {}",
        governanceFreezeConfig.getUuid());

    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put(END_TIME, workflowNotificationHelper.getFormattedTime(System.currentTimeMillis()));
    List<String> userGroupsForNotification = governanceFreezeConfig.getUserGroups();
    if (governanceFreezeConfig.getUserGroupSelection() != null) {
      userGroupsForNotification =
          governanceUserGroupHelper.getUserGroups(governanceFreezeConfig.getUserGroupSelection(), accountId);
    }
    sendNotificationToUserGroups(accountId, placeholderValues, userGroupsForNotification, GLOBAL_APP_ID,
        FREEZE_DEACTIVATION_NOTIFICATION, governanceFreezeConfig.getName());
  }

  private AlertData createAlert(
      GovernanceFreezeConfig governanceFreezeConfig, DeploymentFreezeEventAlert.EventType eventType) {
    return DeploymentFreezeEventAlert.builder()
        .deploymentFreezeId(governanceFreezeConfig.getUuid())
        .deploymentFreezeName(governanceFreezeConfig.getName())
        .freezeEventType(eventType)
        .build();
  }

  public String getGovernanceUrl(String accountId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    return NotificationMessageResolver.buildAbsoluteUrl(format("/account/%s/governance", accountId), baseUrl);
  }
}
