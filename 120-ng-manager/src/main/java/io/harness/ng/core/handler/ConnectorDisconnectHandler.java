package io.harness.ng.core.handler;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class ConnectorDisconnectHandler implements MongoPersistenceIterator.Handler<Connector> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NotificationClient notificationClient;

  MongoPersistenceIterator<Connector, SpringFilterExpander> iterator;
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2;
  private static final int REDIS_LOCK_TIMEOUT_SECONDS = 5;

  public void registerIterators(int threadPoolSize) {
    int redisBatchSize = BATCH_SIZE_MULTIPLY_FACTOR * threadPoolSize;

    PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions =
        PersistenceIteratorFactory.RedisBatchExecutorOptions.builder()
            .name("ConnectorDisconnect")
            .poolSize(threadPoolSize)
            .batchSize(redisBatchSize)
            .lockTimeout(REDIS_LOCK_TIMEOUT_SECONDS)
            .interval(ofSeconds(45))
            .build();

    iterator = (MongoPersistenceIterator<Connector, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       ConnectorDisconnectHandler.class,
                       MongoPersistenceIterator.<Connector, SpringFilterExpander>builder()
                           .mode(PersistenceIterator.ProcessMode.REDIS_BATCH)
                           .clazz(Connector.class)
                           .fieldName(ConnectorKeys.connectorDisconnectIteration)
                           .acceptableNoAlertDelay(ofSeconds(60))
                           .targetInterval(ofDays(1))
                           .semaphore(new Semaphore(10))
                           .handler(this)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .filterExpander(q
                               -> q.addCriteria(where(ConnectorKeys.connectionLastConnectedAt)
                                                    .lt(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(60))))
                           .schedulingType(IRREGULAR_SKIP_MISSED));
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(Connector connector) {
    if (isConnectorDisconnectAlertSend(connector)) {
      return;
    }
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
    NotificationResult notificationResult =
        notificationClient.sendNotificationTrigger(notificationTriggerRequestBuilder.build());
    if (isNotEmpty(notificationResult.getNotificationId())) {
      //update
    }
  }

  private boolean isConnectorDisconnectAlertSend(Connector connector) {
    return connector.getLastAlertSent() != null
        && connector.getLastAlertSent() == connector.getConnectivityDetails().getLastConnectedAt();
  }


}
