/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MILOS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.SlackMessage;
import software.wings.beans.SlackMessageJSON;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.SlackNotificationService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 12/16/16.
 */

@OwnedBy(HarnessTeam.CDC)
public class SlackNotificationServiceTest extends WingsBaseTest {
  @Inject private NotificationSetupService notificationSetupService;

  @Inject private AppService appService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private SlackMessageSender slackMessageSender;
  @Mock private AccountService accountService;
  @Inject @InjectMocks private SlackNotificationService slackNotificationService;

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void shouldSendMessageFromDelegate() {
    when(accountService.isCertValidationRequired(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).send(any(SlackMessage.class), anyBoolean(), anyBoolean());

    slackNotificationService.sendMessage(
        new SlackNotificationSetting("name", "url"), "abc", "sender", "message", "accountId");
    verify(delegateProxyFactory, times(1)).getV2(any(), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldSendJSONMessageFromDelegate() {
    when(accountService.isCertValidationRequired(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(featureFlagService.isGlobalEnabled(any())).thenReturn(true);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).sendJSON(any(SlackMessageJSON.class));

    List<String> slackWebhookUrls = ImmutableList.of("http://webhook.com");
    slackNotificationService.sendJSONMessage("message", slackWebhookUrls, "account-id");
    verify(delegateProxyFactory, times(1)).getV2(any(), any());
    verify(slackMessageSender, times(1)).sendJSON(any(SlackMessageJSON.class));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldSendJSONMessageFromManager() {
    when(accountService.isCertValidationRequired(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(featureFlagService.isGlobalEnabled(any())).thenReturn(false);
    doNothing().when(slackMessageSender).sendJSON(any(SlackMessageJSON.class));

    List<String> slackWebhookUrls = ImmutableList.of("http://webhook.com");
    slackNotificationService.sendJSONMessage("message", slackWebhookUrls, "account-id");
    verify(delegateProxyFactory, times(0)).getV2(any(), any());
    verify(slackMessageSender, times(1)).sendJSON(any(SlackMessageJSON.class));
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void shouldSendMessageFromManager() {
    when(accountService.isCertValidationRequired(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).send(any(SlackMessage.class), anyBoolean(), anyBoolean());

    slackNotificationService.sendMessage(
        new SlackNotificationSetting("name", "url"), "abc", "sender", "message", "accountId");
    verify(slackMessageSender, times(1)).send(any(SlackMessage.class), anyBoolean(), anyBoolean());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void shouldNotSendMessageIfWebhookUrlIsEmpty() {
    when(accountService.isCertValidationRequired(any())).thenReturn(false);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).send(any(SlackMessage.class), anyBoolean(), anyBoolean());

    slackNotificationService.sendMessage(
        new SlackNotificationSetting("name", ""), "abc", "sender", "message", "accountId");
    verify(slackMessageSender, times(0)).send(any(SlackMessage.class), anyBoolean(), anyBoolean());
    verify(slackMessageSender, times(0)).send(any(SlackMessage.class), anyBoolean(), anyBoolean());
    verify(delegateProxyFactory, times(0)).getV2(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldSendMessageFromDelegateWithCertValidation() {
    when(accountService.isCertValidationRequired(any())).thenReturn(true);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).send(any(SlackMessage.class), anyBoolean(), anyBoolean());

    slackNotificationService.sendMessage(
        new SlackNotificationSetting("name", "url"), "abc", "sender", "message", "accountId");
    verify(delegateProxyFactory, times(1)).getV2(any(), any());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldSendMessageFromManagerWithCertValidation() {
    when(accountService.isCertValidationRequired(any())).thenReturn(true);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(delegateProxyFactory.getV2(any(Class.class), any(SyncTaskContext.class))).thenReturn(slackMessageSender);
    doNothing().when(slackMessageSender).send(any(SlackMessage.class), anyBoolean(), anyBoolean());

    slackNotificationService.sendMessage(
        new SlackNotificationSetting("name", "url"), "abc", "sender", "message", "accountId");
    verify(slackMessageSender, times(1)).send(any(SlackMessage.class), anyBoolean(), anyBoolean());
  }
}
