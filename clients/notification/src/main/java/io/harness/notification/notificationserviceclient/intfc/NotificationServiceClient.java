package io.harness.notification.notificationserviceclient.intfc;

import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.notificationclient.NotificationResult;

import java.util.List;

public interface NotificationServiceClient {
  NotificationResult sendNotification(NotificationTriggerRequest notificationTriggerRequest);
  List<NotificationResult> sendBulkNotification(List<NotificationTriggerRequest> notificationTriggerRequest);
}
