package io.harness.releaseradar.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.releaseradar.beans.EventFilter;
import io.harness.releaseradar.beans.EventNotifyData;
import io.harness.releaseradar.entities.EventEntity;
import io.harness.releaseradar.entities.UserSubscription;
import io.harness.releaseradar.util.SlackWebhookEncryptionUtil;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class EventProcessor {
  @Inject private UserSubscriptionService userSubscriptionService;
  @Inject private SlackNotifier notifier;

  public void process(EventEntity deployEvent) {
    List<UserSubscription> allSubscriptions =
        userSubscriptionService.getAllSubscriptions(EventFilter.builder()
                                                        .eventType(deployEvent.getEventType())
                                                        .serviceName(deployEvent.getServiceName())
                                                        .buildVersion(deployEvent.getBuildVersion())
                                                        .release(deployEvent.getRelease())
                                                        .environment(deployEvent.getEnvironment())
                                                        .build());
    if (isNotEmpty(allSubscriptions)) {
      EventNotifyData notifyData = EventNotifyData.builder()
                                       .eventType(deployEvent.getEventType())
                                       .serviceName(deployEvent.getServiceName())
                                       .buildVersion(deployEvent.getBuildVersion())
                                       .release(deployEvent.getRelease())
                                       .environment(deployEvent.getEnvironment())
                                       .build();
      allSubscriptions.stream()
          .filter(userSubscription -> isNotEmpty(userSubscription.getSlackWebhookUrlEncrypted()))
          .forEach(userSubscription -> {
            try {
              notifier.notify(
                  SlackWebhookEncryptionUtil.decrypt(userSubscription.getSlackWebhookUrlEncrypted()), notifyData);
            } catch (Exception e) {
              e.printStackTrace();
            }
          });
    }
  }
}
