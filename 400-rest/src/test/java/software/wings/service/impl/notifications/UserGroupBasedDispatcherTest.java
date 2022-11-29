/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.notifications;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Notification;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.UserGroup;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserGroupBasedDispatcherTest {
  public static final String EMPTY = "";

  @InjectMocks private UserGroupBasedDispatcher dispatcher;
  @Mock private SlackMessageDispatcher slackMessageDispatcher;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldDispatchSlackNotification() {
    final SlackNotificationSetting slackConfig = new SlackNotificationSetting("slackName", "slackUrl");
    final NotificationSettings notificationSettings =
        new NotificationSettings(false, false, Collections.emptyList(), slackConfig, EMPTY, EMPTY);
    UserGroup userGroup = UserGroup.builder().notificationSettings(notificationSettings).build();
    List<Notification> notifications = Collections.emptyList();

    dispatcher.dispatchSlack(notifications, userGroup);

    verify(slackMessageDispatcher).dispatch(notifications, slackConfig);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotDispatchSlackNotificationWhenUrlEmpty() {
    final SlackNotificationSetting slackConfig = new SlackNotificationSetting("", "");
    final NotificationSettings notificationSettings =
        new NotificationSettings(false, false, Collections.emptyList(), slackConfig, EMPTY, EMPTY);
    UserGroup userGroup = UserGroup.builder().notificationSettings(notificationSettings).build();
    List<Notification> notifications = Collections.emptyList();

    dispatcher.dispatchSlack(notifications, userGroup);

    verify(slackMessageDispatcher, never()).dispatch(notifications, slackConfig);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotDispatchSlackNotificationWhenUrlNull() {
    final SlackNotificationSetting slackConfig = new SlackNotificationSetting("", null);
    final NotificationSettings notificationSettings =
        new NotificationSettings(false, false, Collections.emptyList(), slackConfig, EMPTY, EMPTY);
    UserGroup userGroup = UserGroup.builder().notificationSettings(notificationSettings).build();
    List<Notification> notifications = Collections.emptyList();

    dispatcher.dispatchSlack(notifications, userGroup);

    verify(slackMessageDispatcher, never()).dispatch(notifications, slackConfig);
  }
}
