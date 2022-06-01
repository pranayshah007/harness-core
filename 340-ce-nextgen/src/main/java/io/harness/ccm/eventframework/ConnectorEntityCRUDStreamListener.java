/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.service.intf.AwsEntityChangeEventService;
import io.harness.ccm.service.intf.GCPEntityChangeEventService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
@Singleton
public class ConnectorEntityCRUDStreamListener implements MessageListener {
  @Inject AwsEntityChangeEventService awsEntityChangeEventService;
  @Inject EntityChangeHandler entityChangeHandler;
  @Inject CEMetadataRecordDao ceMetadataRecordDao;
  @Inject GCPEntityChangeEventService gcpEntityChangeEventService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();

      if (isUpdateCEMetadataRecordRequired(metadataMap)) {
        updateCEMetadataRecord(metadataMap, getEntityChangeDTO(message));
      }

      if (hasRequiredMetadata(metadataMap)) {
        EntityChangeDTO entityChangeDTO = getEntityChangeDTO(message);
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processK8sEntityChangeEvent(entityChangeDTO, action, metadataMap.get(CONNECTOR_ENTITY_TYPE));
        }
      }

      if (isCEAWSEvent(metadataMap)) {
        EntityChangeDTO entityChangeDTO = getEntityChangeDTO(message);
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return awsEntityChangeEventService.processAWSEntityChangeEvent(entityChangeDTO, action);
        }
      }

      if (isCEGCPEvent(metadataMap)) {
        EntityChangeDTO entityChangeDTO = getEntityChangeDTO(message);
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processGCPEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private void updateCEMetadataRecord(Map<String, String> metadataMap, EntityChangeDTO entityChangeDTO) {
    String action = metadataMap.get(ACTION);
    log.info("In updateCEMetadataRecord with action: {}", action);
    if (CREATE_ACTION.equals(action)) {
      if (isCEAWSEvent(metadataMap)) {
        log.info("CE AWS Create Event");
        ceMetadataRecordDao.upsert(CEMetadataRecord.builder()
                                       .accountId(entityChangeDTO.getAccountIdentifier().getValue())
                                       .awsConnectorConfigured(true)
                                       .build());
      }
      if (isCEAzureEvent(metadataMap)) {
        log.info("CE Azure Create Event");
        ceMetadataRecordDao.upsert(CEMetadataRecord.builder()
                                       .accountId(entityChangeDTO.getAccountIdentifier().getValue())
                                       .azureConnectorConfigured(true)
                                       .build());
      }
      if (isCEGCPEvent(metadataMap)) {
        log.info("CE GCP Create Event");
        ceMetadataRecordDao.upsert(CEMetadataRecord.builder()
                                       .accountId(entityChangeDTO.getAccountIdentifier().getValue())
                                       .gcpConnectorConfigured(true)
                                       .build());
      }
      if (isCEK8sEvent(metadataMap)) {
        log.info("CE K8s Create Event");
        ceMetadataRecordDao.upsert(CEMetadataRecord.builder()
                                       .accountId(entityChangeDTO.getAccountIdentifier().getValue())
                                       .clusterConnectorConfigured(true)
                                       .build());
      }
    }
  }

  private EntityChangeDTO getEntityChangeDTO(Message message) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    return entityChangeDTO;
  }

  private static boolean hasRequiredMetadata(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && (ConnectorType.KUBERNETES_CLUSTER.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE))
            || ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE)));
  }

  private static boolean isUpdateCEMetadataRecordRequired(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && (ConnectorType.CE_AWS.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE))
            || ConnectorType.CE_AZURE.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE))
            || ConnectorType.GCP_CLOUD_COST.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE))
            || ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE)));
  }

  private static boolean isCEAWSEvent(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && ConnectorType.CE_AWS.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE));
  }

  private static boolean isCEAzureEvent(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && ConnectorType.CE_AZURE.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE));
  }

  private static boolean isCEGCPEvent(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && ConnectorType.GCP_CLOUD_COST.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE));
  }

  private static boolean isCEK8sEvent(Map<String, String> metadataMap) {
    return metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))
        && ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(metadataMap.get(CONNECTOR_ENTITY_TYPE));
  }

  private boolean processK8sEntityChangeEvent(
      EntityChangeDTO entityChangeDTO, String action, String connectorEntityType) {
    log.info("In processEntityChangeEvent {}, {}, {}", entityChangeDTO, action, connectorEntityType);
    switch (action) {
      case CREATE_ACTION:
        entityChangeHandler.handleCreateEvent(entityChangeDTO, connectorEntityType);
        break;
      case UPDATE_ACTION:
        entityChangeHandler.handleUpdateEvent(entityChangeDTO, connectorEntityType);
        break;
      case DELETE_ACTION:
        entityChangeHandler.handleDeleteEvent(entityChangeDTO, connectorEntityType);
        break;
      default:
        log.error("Change Event of type %s, not handled", action);
    }
    return true;
  }

  private boolean processGCPEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    log.info("In processEntityChangeEvent {}, {} ", entityChangeDTO, action);
    switch (action) {
      case CREATE_ACTION:
        gcpEntityChangeEventService.processGCPEntityCreateEvent(entityChangeDTO);
        break;
      case UPDATE_ACTION:
        gcpEntityChangeEventService.processGCPEntityUpdateEvent(entityChangeDTO);
        break;
      case DELETE_ACTION:
        gcpEntityChangeEventService.processGCPEntityDeleteEvent(entityChangeDTO);
        break;
      default:
        log.error("GCP Entity Change Event of type {}, not handled", action);
    }
    return true;
  }
}
