/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.beans.IdentifierRef;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ConnectorChangeEventMessageProcessor implements ConsumerMessageProcessor {
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Override
  public void processMessage(Message message) {
    Preconditions.checkState(validateMessage(message), "Invalid message received by Connector Change Event Processor");

    EntityChangeDTO connectorEntityChangeDTO;
    try {
      connectorEntityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }

    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap.containsKey(EventsFrameworkMetadataConstants.ACTION)) {
      switch (metadataMap.get(EventsFrameworkMetadataConstants.ACTION)) {
        case EventsFrameworkMetadataConstants.UPDATE_ACTION:
          processUpdateAction(connectorEntityChangeDTO);
          return;
        default:
      }
    }
  }
  @VisibleForTesting
  void processUpdateAction(EntityChangeDTO connectorEntityChangeDTO) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        connectorEntityChangeDTO.getIdentifier().getValue(), connectorEntityChangeDTO.getAccountIdentifier().getValue(),
        connectorEntityChangeDTO.getOrgIdentifier().getValue(),
        connectorEntityChangeDTO.getProjectIdentifier().getValue());

    log.info("IdentifierRef {}", identifierRef.getScope().getYamlRepresentation() + "," + identifierRef);

    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
        monitoringSourcePerpetualTaskService.listByConnectorIdentifier(
            connectorEntityChangeDTO.getAccountIdentifier().getValue(),
            connectorEntityChangeDTO.getOrgIdentifier().getValue(),
            connectorEntityChangeDTO.getProjectIdentifier().getValue(),
            connectorEntityChangeDTO.getIdentifier().getValue(), identifierRef.getScope());
    monitoringSourcePerpetualTasks.forEach(monitoringSourcePerpetualTask
        -> monitoringSourcePerpetualTaskService.resetLiveMonitoringPerpetualTask(monitoringSourcePerpetualTask));
  }

  private boolean validateMessage(Message message) {
    return message != null && message.hasMessage() && message.getMessage().getMetadataMap() != null
        && message.getMessage().getMetadataMap().containsKey(EventsFrameworkMetadataConstants.ENTITY_TYPE)
        && EventsFrameworkMetadataConstants.CONNECTOR_ENTITY.equals(
            message.getMessage().getMetadataMap().get(EventsFrameworkMetadataConstants.ENTITY_TYPE));
  }
}
