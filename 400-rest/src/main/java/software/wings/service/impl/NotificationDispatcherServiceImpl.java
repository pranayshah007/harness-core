/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.beans.EmbeddedUser;
import io.harness.exception.ExceptionUtils;

import software.wings.beans.Notification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationReceiverInfo;
import software.wings.beans.NotificationRule;
import software.wings.beans.alert.AlertNotificationRule;
import software.wings.beans.security.UserGroup;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.ChannelTemplate.EmailTemplate;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.service.impl.notifications.EmailDispatcher;
import software.wings.service.impl.notifications.NotificationDispatcher;
import software.wings.service.impl.notifications.UseNotificationGroup;
import software.wings.service.impl.notifications.UseUserGroup;
import software.wings.service.intfc.NotificationDispatcherService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by rishi on 10/30/16.
 */
@Singleton
@Slf4j
public class NotificationDispatcherServiceImpl implements NotificationDispatcherService {
  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private UserGroupService userGroupService;
  @Inject @UseNotificationGroup private NotificationDispatcher<NotificationGroup> notificationGroupDispatcher;
  @Inject @UseUserGroup private NotificationDispatcher<UserGroup> userGroupDispatcher;

  @Inject private EmailDispatcher emailDispatcher;

  @Override
  public void dispatchNotification(Notification notification, List<NotificationRule> notificationRules) {
    if (notificationRules == null) {
      return;
    }

    String accountId = notification.getAccountId();
    if (StringUtils.isEmpty(accountId)) {
      throw new IllegalStateException("No AccountId present in notification. Notification: " + notification);
    }

    List<UserGroup> userGroups = getDistinctUserGroups(notificationRules, accountId);
    List<NotificationGroup> notificationGroups = getDistinctNotificationGroups(notificationRules);

    dispatch(singletonList(notification), userGroups);
    dispatch(singletonList(notification), notificationGroups);
  }

  protected List<NotificationGroup> getDistinctNotificationGroups(List<NotificationRule> notificationRules) {
    return notificationRules.stream()
        .flatMap(notificationRule -> notificationRule.getNotificationGroups().stream())
        .distinct()
        .collect(toList());
  }

  protected List<UserGroup> getDistinctUserGroups(List<NotificationRule> notificationRules, String accountId) {
    return notificationRules.stream()
        .flatMap(notificationRule -> notificationRule.getUserGroupIds().stream())
        .distinct()
        .map(id -> userGroupService.get(accountId, id))
        .filter(Objects::nonNull)
        .collect(toList());
  }

  @Override
  public void dispatch(Notification notification, List<AlertNotificationRule> rules) {
    String accountId = notification.getAccountId();
    if (StringUtils.isEmpty(accountId)) {
      throw new IllegalStateException(
          "[dispatch-alertNotificationRule] No AccountId present in notification. Notification: " + notification);
    }

    for (AlertNotificationRule rule : rules) {
      List<UserGroup> userGroups = rule.getUserGroupsToNotify()
                                       .stream()
                                       .distinct()
                                       .map(id -> userGroupService.get(accountId, id))
                                       .filter(Objects::nonNull)
                                       .collect(toList());

      dispatch(singletonList(notification), userGroups);
    }
  }

  private <T extends NotificationReceiverInfo> void dispatch(
      List<Notification> notifications, final List<T> notificationReceivers) {
    List<T> receivers = notificationReceivers.stream().filter(Objects::nonNull).collect(toList());

    if (isEmpty(receivers) || isEmpty(notifications)) {
      return;
    }

    for (NotificationReceiverInfo notificationReceiver : receivers) {
      if (notificationReceiver instanceof NotificationGroup) {
        log.info("notification group dispatch");
        notificationGroupDispatcher.dispatch(notifications, (NotificationGroup) notificationReceiver);
      } else if (notificationReceiver instanceof UserGroup) {
        log.info("user group dispatch");
        userGroupDispatcher.dispatch(notifications, (UserGroup) notificationReceiver);
      } else {
        log.error("Unhandled implementation of NotificationReceiverInfo. Class: {}",
            notificationReceiver.getClass().getSimpleName());
      }
    }
  }

  @Override
  public EmailData obtainEmailData(String notificationTemplateId, Map<String, String> placeholderValues) {
    EmailTemplate emailTemplate = notificationMessageResolver.getEmailTemplate(notificationTemplateId);
    String body =
        notificationMessageResolver.getDecoratedNotificationMessage(emailTemplate.getBody(), placeholderValues);
    String subject =
        notificationMessageResolver.getDecoratedNotificationMessage(emailTemplate.getSubject(), placeholderValues);

    String emailBody = EmailDispatcher.processEmailHtml(body);
    String emailSubject = EmailDispatcher.processEmailHtml(subject);

    return EmailData.builder().subject(emailSubject).body(emailBody).build();
  }

  @Override
  public void dispatchNotificationToTriggeredByUserOnly(List<Notification> notifications, EmbeddedUser user) {
    try {
      emailDispatcher.dispatch(notifications, asList(user.getEmail()));
    } catch (Exception e) {
      log.warn(ExceptionUtils.getMessage(e));
    }
  }
}
