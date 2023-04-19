/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.EntityType.ORCHESTRATED_DEPLOYMENT;
import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.ENTITY_CREATE_NOTIFICATION;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_GROUP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.NotificationChannelType;
import software.wings.beans.NotificationGroup;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.UserGroup;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.ChannelTemplate.EmailTemplate;
import software.wings.processingcontrollers.NotificationProcessingController;
import software.wings.service.intfc.NotificationSetupService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class NotificationDispatcherTest extends WingsBaseTest {
  @Inject
  @InjectMocks
  @UseNotificationGroup
  private NotificationDispatcher<NotificationGroup> notificationGroupDispatcher;

  @Inject @InjectMocks @UseUserGroup private NotificationDispatcher<UserGroup> userGroupNotificationDispatcher;

  @Mock private NotificationSetupService notificationSetupService;
  @Mock private NotificationMessageResolver notificationMessageResolver;
  @Mock private EmailDispatcher emailDispatcher;
  @Mock private SlackMessageDispatcher slackDispatcher;

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void testNotificationGroupBasedDispatcher() throws IllegalAccessException {
    List<String> toAddresses = Lists.newArrayList("a@b.com, c@d.com");
    List<String> slackChannels = Lists.newArrayList("#some-channel");

    NotificationGroup notificationGroup = aNotificationGroup()
                                              .withUuid(NOTIFICATION_GROUP_ID)
                                              .withAppId(APP_ID)
                                              .addAddressesByChannelType(NotificationChannelType.EMAIL, toAddresses)
                                              .addAddressesByChannelType(NotificationChannelType.SLACK, slackChannels)
                                              .build();

    when(notificationSetupService.readNotificationGroup(APP_ID, NOTIFICATION_GROUP_ID)).thenReturn(notificationGroup);

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setBody(ENTITY_CREATE_NOTIFICATION.name());
    emailTemplate.setSubject(ENTITY_CREATE_NOTIFICATION.name());
    when(notificationMessageResolver.getEmailTemplate(ENTITY_CREATE_NOTIFICATION.name())).thenReturn(emailTemplate);

    InformationNotification notification = InformationNotification.builder()
                                               .accountId(ACCOUNT_ID)
                                               .appId(APP_ID)
                                               .entityId(WORKFLOW_EXECUTION_ID)
                                               .entityType(ORCHESTRATED_DEPLOYMENT)
                                               .notificationTemplateId(ENTITY_CREATE_NOTIFICATION.name())
                                               .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME",
                                                   WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE", "DATE"))
                                               .build();

    NotificationProcessingController notificationProcessingController = mock(NotificationProcessingController.class);
    when(notificationProcessingController.canProcessAccount(any())).thenReturn(true);
    FieldUtils.writeField(
        notificationGroupDispatcher, "notificationProcessingController", notificationProcessingController, true);

    List<Notification> notifications = Collections.singletonList(notification);
    notificationGroupDispatcher.dispatch(notifications, notificationGroup);
    verify(emailDispatcher).dispatch(notifications, toAddresses);
    verify(slackDispatcher).dispatch(notifications, slackChannels);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserGroupBasedDispatcher() throws IllegalAccessException {
    List<String> toAddresses = Lists.newArrayList("a@b.com, c@d.com");

    SlackNotificationSetting slackConfig = new SlackNotificationSetting("", "http://");
    NotificationSettings settings = new NotificationSettings(true, true, toAddresses, slackConfig, "", "");
    UserGroup userGroup = UserGroup.builder()
                              .uuid(NOTIFICATION_GROUP_ID)
                              .appId(APP_ID)
                              .accountId("some-account-id")
                              .notificationSettings(settings)
                              .build();

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setBody(ENTITY_CREATE_NOTIFICATION.name());
    emailTemplate.setSubject(ENTITY_CREATE_NOTIFICATION.name());
    when(notificationMessageResolver.getEmailTemplate(ENTITY_CREATE_NOTIFICATION.name())).thenReturn(emailTemplate);

    InformationNotification notification = InformationNotification.builder()
                                               .accountId(ACCOUNT_ID)
                                               .appId(APP_ID)
                                               .entityId(WORKFLOW_EXECUTION_ID)
                                               .entityType(ORCHESTRATED_DEPLOYMENT)
                                               .notificationTemplateId(ENTITY_CREATE_NOTIFICATION.name())
                                               .notificationTemplateVariables(ImmutableMap.of("WORKFLOW_NAME",
                                                   WORKFLOW_NAME, "ENV_NAME", ENV_NAME, "DATE", "DATE"))
                                               .build();

    NotificationProcessingController notificationProcessingController = mock(NotificationProcessingController.class);
    when(notificationProcessingController.canProcessAccount(anyString())).thenReturn(true);
    FieldUtils.writeField(
        userGroupNotificationDispatcher, "notificationProcessingController", notificationProcessingController, true);
    List<Notification> notifications = Collections.singletonList(notification);
    userGroupNotificationDispatcher.dispatch(notifications, userGroup);
    verify(emailDispatcher).dispatch(notifications, toAddresses);
    verify(slackDispatcher).dispatch(notifications, slackConfig);
  }
}
