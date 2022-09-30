/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.SlackMessage;
import software.wings.beans.SlackMessageJSON;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.notification.SlackNotificationConfiguration;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.SlackNotificationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by anubhaw on 12/14/16.
 */

@OwnedBy(CDC)
@Slf4j
@Singleton
public class SlackNotificationServiceImpl implements SlackNotificationService {
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SlackMessageSender slackMessageSender;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AccountService accountService;

  public static final String SLACK_WEBHOOK_URL_PREFIX = "https://hooks.slack.com/services/";
  public static final MediaType APPLICATION_JSON = MediaType.parse("application/json; charset=utf-8");

  @Override
  public void sendMessage(SlackNotificationConfiguration slackConfig, String slackChannel, String senderName,
      String message, String accountId) {
    if (Objects.requireNonNull(slackConfig, "Slack Config can't be null")
            .equals(SlackNotificationSetting.emptyConfig())) {
      return;
    }

    String webhookUrl = slackConfig.getOutgoingWebhookUrl();
    if (StringUtils.isEmpty(webhookUrl)) {
      log.error("Webhook URL is empty. No message will be sent. Config: {}, Message: {}", slackConfig, message);
      return;
    }

    boolean isCertValidationRequired = accountService.isCertValidationRequired(accountId);

    if (featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, accountId)) {
      try {
        log.info("Sending message via delegate");
        SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                              .accountId(accountId)
                                              .appId(GLOBAL_APP_ID)
                                              .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build();
        log.info("Sending message for account {} via delegate", accountId);
        delegateProxyFactory.get(SlackMessageSender.class, syncTaskContext)
            .send(new SlackMessage(slackConfig.getOutgoingWebhookUrl(), slackChannel, senderName, message), true,
                isCertValidationRequired);
      } catch (Exception ex) {
        log.error("Failed to send slack message", ex);
      }
    } else {
      log.info("Sending message for account {} via manager", accountId);
      slackMessageSender.send(new SlackMessage(slackConfig.getOutgoingWebhookUrl(), slackChannel, senderName, message),
          false, isCertValidationRequired);
    }
  }

  @Override
  public void sendJSONMessage(String message, List<String> slackWebhooks, String accountId) {
    for (String slackWebHook : slackWebhooks) {
      // need to add feature flag so it is backwards compatible
      if (featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, accountId)) {
        log.info("Sending message via delegate");
        SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                              .accountId(accountId)
                                              .appId(GLOBAL_APP_ID)
                                              .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build();
        log.info("Sending message for account {} via delegate", accountId);

        delegateProxyFactory.get(SlackMessageSender.class, syncTaskContext)
            .sendJSON(new SlackMessageJSON(slackWebHook, message));
      } else {
        log.info("Sending message for account {} via manager", accountId);
        slackMessageSender.sendJSON(new SlackMessageJSON(slackWebHook, message));
      }
    }
  }
}
