/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.EntityType.ORCHESTRATED_DEPLOYMENT;
import static software.wings.common.Constants.HARNESS_NAME;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_NEEDED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.ENTITY_CREATE_NOTIFICATION;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.common.NotificationMessageResolver;
import software.wings.service.intfc.SlackNotificationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SlackMessageDispatcherTest extends WingsBaseTest {
  @Inject @InjectMocks private SlackMessageDispatcher slackMessageDispatcher;
  @Mock private SlackNotificationService slackNotificationService;
  @Mock private NotificationMessageResolver notificationMessageResolver;
  @Mock private FeatureFlagService featureFlagService;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldSendSlackMessage() {
    when(notificationMessageResolver.getSlackTemplate(ENTITY_CREATE_NOTIFICATION.name())).thenReturn("some template");

    InformationNotification notification = InformationNotification.builder()
                                               .accountId(ACCOUNT_ID)
                                               .appId(APP_ID)
                                               .entityId(WORKFLOW_EXECUTION_ID)
                                               .entityType(ORCHESTRATED_DEPLOYMENT)
                                               .notificationTemplateId(ENTITY_CREATE_NOTIFICATION.name())
                                               .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME",
                                                   WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE", "DATE"))
                                               .build();

    List<Notification> notifications = Collections.singletonList(notification);

    SlackNotificationSetting setting = SlackNotificationSetting.emptyConfig();

    slackMessageDispatcher.dispatch(notifications, setting);
    Mockito.verify(slackNotificationService)
        .sendMessage(Mockito.eq(setting), Mockito.anyString(), Mockito.eq(HARNESS_NAME), Mockito.anyString(),
            Mockito.anyString());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendApprovalRequiredMessageWithIconOverride() {
    when(featureFlagService.isEnabled(FeatureName.SLACK_APPROVALS, ACCOUNT_ID)).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, ACCOUNT_ID)).thenReturn(false);
    InformationNotification notification =
        InformationNotification.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .entityId(WORKFLOW_EXECUTION_ID)
            .entityType(ORCHESTRATED_DEPLOYMENT)
            .notificationTemplateId(APPROVAL_NEEDED_NOTIFICATION.name())
            .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME", WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE",
                "DATE", SlackApprovalMessageKeys.MESSAGE_IDENTIFIER, "suppressTraditionalNotificationOnSlack"))
            .build();

    List<Notification> notifications = Collections.singletonList(notification);

    SlackNotificationSetting setting = SlackNotificationSetting.emptyConfig();

    slackMessageDispatcher.dispatch(notifications, setting);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(slackNotificationService).sendJSONMessage(stringArgumentCaptor.capture(), anyList(), eq(ACCOUNT_ID));
    assertThat(stringArgumentCaptor.getValue())
        .isNotEmpty()
        .contains("\"username\" : \"Harness\"")
        .contains("\"icon_url\" : \"https://s3.amazonaws.com/wings-assets/slackicons/logo-slack.png\"");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSendSlackApprovalMessageWithIconOverride() {
    when(featureFlagService.isEnabled(FeatureName.SLACK_APPROVALS, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, ACCOUNT_ID)).thenReturn(false);
    InformationNotification notification =
        InformationNotification.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .entityId(WORKFLOW_EXECUTION_ID)
            .entityType(ORCHESTRATED_DEPLOYMENT)
            .notificationTemplateId(APPROVAL_NEEDED_NOTIFICATION.name())
            .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME", WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE",
                "DATE", SlackApprovalMessageKeys.MESSAGE_IDENTIFIER, "suppressTraditionalNotificationOnSlack"))
            .build();

    List<Notification> notifications = Collections.singletonList(notification);

    SlackNotificationSetting setting = SlackNotificationSetting.emptyConfig();

    slackMessageDispatcher.dispatch(notifications, setting);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(slackNotificationService).sendJSONMessage(stringArgumentCaptor.capture(), anyList(), eq(ACCOUNT_ID));
    assertThat(stringArgumentCaptor.getValue())
        .isNotEmpty()
        .contains("\"username\" : \"Harness\"")
        .contains("\"icon_url\" : \"https://s3.amazonaws.com/wings-assets/slackicons/logo-slack.png\"");
  }
}
