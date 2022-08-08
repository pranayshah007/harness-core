/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.common.Constants.HARNESS_NAME;
import static software.wings.common.NotificationMessageResolver.getDecoratedNotificationMessage;

import static org.apache.commons.lang3.StringUtils.stripToEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Notification;
import software.wings.beans.SettingAttribute;
import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.common.NotificationMessageResolver;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.SlackNotificationService;
import software.wings.settings.SettingVariableTypes;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StrSubstitutor;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class SlackMessageDispatcher {
  @Inject private SettingsService settingsService;
  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private SlackNotificationService slackNotificationService;
  @Inject private FeatureFlagService featureFlagService;

  /**
   * This method is a bit deceiving. It just picks first slack config from DB and notifies that.
   * Also, Slack does NOT let sender specify which channel to notify. That is tied on the webHook URL, and defined when
   * that URL is created. So, {@code channels} param is a bit redundant.
   *
   * The method has been kept for backward compatibility reasons. Prefer to use {@link #dispatch(List,
   * SlackNotificationConfiguration)}.
   */
  @Deprecated
  public void dispatch(List<Notification> notifications, List<String> channels) {
    if (isEmpty(channels)) {
      return;
    }

    String accountId = notifications.get(0).getAccountId();
    List<SettingAttribute> settingAttributes =
        settingsService.getGlobalSettingAttributesByType(accountId, SettingVariableTypes.SLACK.name());
    if (isEmpty(settingAttributes)) {
      log.warn("No slack configuration found ");
      return;
    }
    SettingAttribute settingAttribute = settingAttributes.iterator().next();
    SlackNotificationConfiguration slackConfig = (SlackNotificationConfiguration) settingAttribute.getValue();

    List<String> messages = new ArrayList<>();

    notifications.forEach(notification -> {
      String slackTemplate = notificationMessageResolver.getSlackTemplate(notification.getNotificationTemplateId());
      if (slackTemplate == null) {
        log.error("No slack template found for templateId {}", notification.getNotificationTemplateId());
        return;
      }
      messages.add(getDecoratedNotificationMessage(slackTemplate, notification.getNotificationTemplateVariables()));
    });

    messages.forEach(message
        -> channels.forEach(
            channel -> slackNotificationService.sendMessage(slackConfig, channel, HARNESS_NAME, message, accountId)));
  }

  public void dispatch(List<Notification> notifications, SlackNotificationConfiguration slackConfig) {
    if (isEmpty(notifications)) {
      return;
    }

    String accountId = notifications.get(0).getAccountId();

    List<String> messages = new ArrayList<>();

    for (Notification notification : notifications) {
      if (notification.getNotificationTemplateVariables().containsKey(SlackApprovalMessageKeys.MESSAGE_IDENTIFIER)) {
        boolean isChannelNameEmpty = true;
        // Fetch Channel and add to Notification Template Variables
        String slackChannel = stripToEmpty(slackConfig.getName());
        if (isNotEmpty(slackChannel)) {
          isChannelNameEmpty = false;
          if (slackChannel.charAt(0) != '#') {
            slackChannel = "#" + slackChannel;
          }
          notification.getNotificationTemplateVariables().put("channelName", slackChannel);
        }
        // Enable this feature flag to allow user to approve via slack
        boolean slackApprovalsFeatureFlag = featureFlagService.isEnabled(FeatureName.SLACK_APPROVALS, accountId);

        URL url;
        if (slackApprovalsFeatureFlag) {
          if (isChannelNameEmpty) {
            url = this.getClass().getResource(
                SlackApprovalMessageKeys.APPROVAL_MESSAGE_WITHOUT_CHANNEL_NAME_PAYLOAD_TEMPLATE);
          } else {
            url = this.getClass().getResource(SlackApprovalMessageKeys.APPROVAL_MESSAGE_PAYLOAD_TEMPLATE);
          }
        } else {
          if (isChannelNameEmpty) {
            url = this.getClass().getResource(
                SlackApprovalMessageKeys.APPROVAL_MESSAGE_WITHOUT_BUTTONS_WITHOUT_CHANNEL_NAME_PAYLOAD_TEMPLATE);
          } else {
            url =
                this.getClass().getResource(SlackApprovalMessageKeys.APPROVAL_MESSAGE_WITHOUT_BUTTONS_PAYLOAD_TEMPLATE);
          }
        }

        String approvalTemplate;
        StrSubstitutor sub = new StrSubstitutor(notification.getNotificationTemplateVariables());
        try {
          approvalTemplate = Resources.toString(url, Charsets.UTF_8);
        } catch (IOException e) {
          return;
        }
        String resolvedString = sub.replace(approvalTemplate);
        slackNotificationService.sendJSONMessage(
            resolvedString, Collections.singletonList(slackConfig.getOutgoingWebhookUrl()), accountId);
        continue;
      }

      String slackTemplate = notificationMessageResolver.getSlackTemplate(notification.getNotificationTemplateId());
      if (slackTemplate == null) {
        log.error("No slack template found for templateId {}", notification.getNotificationTemplateId());
        continue;
      }
      messages.add(getDecoratedNotificationMessage(slackTemplate, notification.getNotificationTemplateVariables()));
    }

    for (String message : messages) {
      slackNotificationService.sendMessage(
          slackConfig, stripToEmpty(slackConfig.getName()), HARNESS_NAME, message, accountId);
    }
  }
}
