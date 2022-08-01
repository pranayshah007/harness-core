/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngtriggers.service.NGTriggerService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PipelineEntityCRUDStreamListener implements MessageListener {
  private final NGTriggerService ngTriggerService;

  @Inject
  public PipelineEntityCRUDStreamListener(NGTriggerService ngTriggerService) {
    this.ngTriggerService = ngTriggerService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && PIPELINE_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        EntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processAccountEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processAccountEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        return processDeleteEvent(entityChangeDTO);
      default:
    }
    return true;
  }

  private boolean processDeleteEvent(EntityChangeDTO entityChangeDTO) {
    // Delete all triggers
    return ngTriggerService.deleteAllForPipeline(entityChangeDTO.getAccountIdentifier().getValue(),
        entityChangeDTO.getOrgIdentifier().getValue(), entityChangeDTO.getProjectIdentifier().getValue(),
        entityChangeDTO.getIdentifier().getValue());
    // delete all input sets
  }
}
