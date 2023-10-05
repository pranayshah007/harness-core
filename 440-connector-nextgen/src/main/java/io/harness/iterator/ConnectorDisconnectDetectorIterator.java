package io.harness.iterator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.notification.NotificationEntity;
import io.harness.notification.NotificationEvent;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.notificationserviceclient.intfc.NotificationServiceClient;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ConnectorDisconnectDetectorIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<Connector> {
  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Connector> persistenceProvider;

  @Inject private NotificationServiceClient notificationServiceClient;

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ConnectorDisconnectDetector";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<Connector, MorphiaFilterExpander<Connector>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, Connector.class,
                MongoPersistenceIterator.<Connector, MorphiaFilterExpander<Connector>>builder()
                    .clazz(Connector.class)
                    .fieldName(ConnectorKeys.connectorDisconnectDetectorNextIteration)
                    .filterExpander(q
                        -> q.field(ConnectorKeys.timeWhenConnectorIsLastUpdated)
                               .lessThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(60)))
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
        (MongoPersistenceIterator<Connector, MorphiaFilterExpander<Connector>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions, Connector.class,
                MongoPersistenceIterator.<Connector, MorphiaFilterExpander<Connector>>builder()
                    .clazz(Connector.class)
                    .fieldName(ConnectorKeys.connectorDisconnectDetectorNextIteration)
                    .filterExpander(q
                        -> q.field(ConnectorKeys.timeWhenConnectorIsLastUpdated)
                               .lessThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(60)))
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(Duration.ofMinutes(2))
                    .handler(this)
                    .persistenceProvider(persistenceProvider));
  }

  @Override
  public void handle(Connector connector) {
    String notificationTriggerRequestId = generateUuid();
    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "connector_down");
    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(connector.getAccountIdentifier())
            .setOrgId(connector.getOrgIdentifier())
            .setProjectId(connector.getProjectIdentifier())
            .setEventEntity(NotificationEntity.CONNECTOR.name())
            .setEvent(NotificationEvent.CONNECTOR_DOWN.name())
            .putAllTemplateData(templateData);
    log.info("Sending connector disconnect notification for {}", connector.getIdentifier());
    notificationServiceClient.sendNotification(notificationTriggerRequestBuilder.build());
  }
}
