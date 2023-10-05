package io.harness.iterator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.notification.NotificationEntity;
import io.harness.notification.NotificationEvent;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.notificationserviceclient.intfc.NotificationServiceClient;

import software.wings.service.impl.DelegateDao;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateExpiryAlertIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<Delegate> {
  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Delegate> persistenceProvider;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateService delegateService;
  @Inject private DelegateDao delegateDao;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private NotificationServiceClient notificationServiceClient;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Delegate, MorphiaFilterExpander<Delegate>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, Delegate.class,
                       MongoPersistenceIterator.<Delegate, MorphiaFilterExpander<Delegate>>builder()
                           .clazz(Delegate.class)
                           .fieldName(DelegateKeys.delegateExpiryAlertNextIteration)
                           .filterExpander(q
                               -> q.field(DelegateKeys.expireOn)
                                      .lessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45)))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(Duration.ofMinutes(2))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<Delegate, MorphiaFilterExpander<Delegate>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions, Delegate.class,
                MongoPersistenceIterator.<Delegate, MorphiaFilterExpander<Delegate>>builder()
                    .clazz(Delegate.class)
                    .fieldName(DelegateKeys.delegateExpiryAlertNextIteration)
                    .filterExpander(q
                        -> q.field(DelegateKeys.expireOn)
                               .lessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45)))
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(Duration.ofMinutes(2))
                    .handler(this)
                    .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateDisconnectDetector";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Delegate delegate) {
    String notificationTriggerRequestId = generateUuid();
    String orgId = delegate.getOwner() != null
        ? DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegate.getOwner().getIdentifier())
        : "";
    String projectId = delegate.getOwner() != null
        ? DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegate.getOwner().getIdentifier())
        : "";

    Map<String, String> templateData = new HashMap<>();
    templateData.put("DELEGATE_HOST", delegate.getDelegateGroupName());
    templateData.put("DELEGATE_NAME", delegate.getHostName());
    templateData.put("TEMPLATE_IDENTIFIER", "delegate_expired");
    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(delegate.getAccountId())
            .setOrgId(orgId)
            .setProjectId(projectId)
            .setEventEntity(NotificationEntity.DELEGATE.name())
            .setEvent(NotificationEvent.DELEGATE_DOWN.name())
            .putAllTemplateData(templateData);
    log.info("Sending delegate expiry notifictaion for {}", delegate.getUuid());
    notificationServiceClient.sendNotification(notificationTriggerRequestBuilder.build());
  }
}
