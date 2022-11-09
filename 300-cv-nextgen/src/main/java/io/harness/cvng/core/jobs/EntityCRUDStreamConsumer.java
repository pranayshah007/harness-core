/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class EntityCRUDStreamConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  private final Map<String, ConsumerMessageProcessor> processorMap;

  @Inject
  public EntityCRUDStreamConsumer(@Named(EventsFrameworkConstants.ENTITY_CRUD) Consumer abstractConsumer,
      @Named(
          EventsFrameworkMetadataConstants.PROJECT_ENTITY) ConsumerMessageProcessor projectChangeEventMessageProcessor,
      @Named(EventsFrameworkMetadataConstants.CONNECTOR_ENTITY)
      ConsumerMessageProcessor connectorChangeEventMessageProcessor,
      @Named(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY)
      ConsumerMessageProcessor organizationChangeEventMessageProcessor,
      @Named(EventsFrameworkMetadataConstants.ACCOUNT_ENTITY)
      ConsumerMessageProcessor accountChangeEventMessageProcessor, QueueController queueController) {
    super(MAX_WAIT_TIME_SEC, abstractConsumer, queueController);
    processorMap = new HashMap<>();
    processorMap.put(EventsFrameworkMetadataConstants.PROJECT_ENTITY, projectChangeEventMessageProcessor);
    processorMap.put(EventsFrameworkMetadataConstants.CONNECTOR_ENTITY, connectorChangeEventMessageProcessor);
    processorMap.put(EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY, organizationChangeEventMessageProcessor);
    processorMap.put(EventsFrameworkMetadataConstants.ACCOUNT_ENTITY, accountChangeEventMessageProcessor);
  }

  @Override
  protected boolean processMessage(Message message) {
    if (message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.containsKey(EventsFrameworkMetadataConstants.ENTITY_TYPE)) {
        String entityType = metadataMap.get(EventsFrameworkMetadataConstants.ENTITY_TYPE);
        if (processorMap.containsKey(entityType)) {
          try {
            processorMap.get(entityType).processMessage(message);
          } catch (Exception ex) {
            log.error(String.format("Error occurred in processing message of entityType %s and id %s", entityType,
                          message.getId()),
                ex);
          }
        }
      }
    }

    return true;
  }
}
