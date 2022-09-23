/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.EventListener;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;

public class CustomDeploymentEntityCRUDStreamEventListener implements MessageListener {
  private final CustomDeploymentEntityCRUDEventHandler deploymentTemplateEntityCRUDEventHandler;
  @Inject
  public CustomDeploymentEntityCRUDStreamEventListener(
      CustomDeploymentEntityCRUDEventHandler customDeploymentEntityCRUDEventHandler) {
    this.deploymentTemplateEntityCRUDEventHandler = customDeploymentEntityCRUDEventHandler;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        processChangeEvent(message);
      }
    }
    return true;
  }

  private boolean processChangeEvent(Message message) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processCreateEvent(entityChangeDTO);
        case DELETE_ACTION:
          return processDeleteEvent(entityChangeDTO);
        case UPDATE_ACTION:
          return processRestoreEvent(entityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processRestoreEvent(EntityChangeDTO entityChangeDTO) {
    if (Objects.equals(entityChangeDTO.getMetadataMap().get("DeploymentTemplate"), "true")) {
      return deploymentTemplateEntityCRUDEventHandler.updateInfraAsObsolete(
          entityChangeDTO.getAccountIdentifier().getValue(), entityChangeDTO.getOrgIdentifier().getValue(),
          entityChangeDTO.getProjectIdentifier().getValue(), entityChangeDTO.getIdentifier().getValue(),
          entityChangeDTO.getMetadataMap().get("versionLabel"));
    }
    return true;
  }

  private boolean processCreateEvent(EntityChangeDTO entityChangeDTO) {
    return true;
  }
  private boolean processDeleteEvent(EntityChangeDTO entityChangeDTO) {
    return true;
  }
}
