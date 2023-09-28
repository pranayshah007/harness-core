package clients.notificationservice.src.java.io.harness.notificationservice.client;

import clients.notificationservice.src.java.io.harness.notificationservice.model.NotificationTriggerRequest;
import io.harness.notification.notificationclient.NotificationResult;

public interface NotificationServiceClient {

    NotificationResult sendNotificationAsync(NotificationTriggerRequest notificationTriggerRequest);
}
