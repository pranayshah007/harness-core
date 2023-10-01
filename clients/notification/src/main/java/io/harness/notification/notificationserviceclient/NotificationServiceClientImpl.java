package io.harness.notification.notificationserviceclient;

import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.messageclient.MessageClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notification.notificationclient.NotificationResultWithoutStatus;
import io.harness.notification.notificationserviceclient.intfc.NotificationServiceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationServiceClientImpl implements NotificationServiceClient {
  private MessageClient messageClient;
  @Override
  public NotificationResult sendNotification(NotificationTriggerRequest notificationTriggerRequest) {
    messageClient.send(notificationTriggerRequest, notificationTriggerRequest.getAccountId());
    return NotificationResultWithoutStatus.builder().notificationId(notificationTriggerRequest.getId()).build();
  }

  @Override
  public List<NotificationResult> sendBulkNotification(List<NotificationTriggerRequest> notificationTriggerRequest) {
    return notificationTriggerRequest.stream().map(this::sendNotification).collect(Collectors.toList());
  }
}
