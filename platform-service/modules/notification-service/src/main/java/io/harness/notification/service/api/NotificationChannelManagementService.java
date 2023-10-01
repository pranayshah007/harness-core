package io.harness.notification.service.api;

import io.harness.notification.entities.NotificationChannel;

import javax.validation.Valid;
import java.util.List;

public interface NotificationChannelManagementService {
    NotificationChannel create(@Valid NotificationChannel notificationChannel);

    NotificationChannel update(@Valid NotificationChannel notificationChannel);

    NotificationChannel getNotificationChannel(
            String accountIdentifier, String orgIdentifier, String projectIdentifier, String notificationRuleNameIdentifier);

    List<NotificationChannel> getNotificationChannelList(
            String accountIdentifier, String orgIdentifier, String projectIdentifier);

    boolean delete(@Valid NotificationChannel notificationChannel);
}
