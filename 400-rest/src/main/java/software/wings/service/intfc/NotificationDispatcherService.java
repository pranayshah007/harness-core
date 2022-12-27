/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.EmbeddedUser;

import software.wings.beans.Notification;
import software.wings.beans.NotificationRule;
import software.wings.beans.alert.AlertNotificationRule;
import software.wings.persistence.mail.EmailData;

import java.util.List;
import java.util.Map;

/**
 * Created by rishi on 10/30/16.
 */
public interface NotificationDispatcherService {
  void dispatchNotification(Notification notification, List<NotificationRule> notificationRuleList);

  void dispatch(Notification notification, List<AlertNotificationRule> alertNotificationRules);

  EmailData obtainEmailData(String notificationTemplateId, Map<String, String> placeholderValues);

  void dispatchNotificationToTriggeredByUserOnly(List<Notification> notifications, EmbeddedUser user);
}
