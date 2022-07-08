/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CV)
public class ProjectChangeEventMessageProcessor extends EntityChangeEventMessageProcessor {
  @Inject private Injector injector;
  @Inject private MetricPackService metricPackService;

  @Override
  public void processMessage(Message message) {
    Preconditions.checkState(validateMessage(message), "Invalid message received by Project Change Event Processor");

    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking ProjectEntityChangeDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }

    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap.containsKey(EventsFrameworkMetadataConstants.ACTION)) {
      switch (metadataMap.get(EventsFrameworkMetadataConstants.ACTION)) {
        case EventsFrameworkMetadataConstants.CREATE_ACTION:
          processCreateAction(projectEntityChangeDTO);
          processUpdateAction(projectEntityChangeDTO);
          return;
        case EventsFrameworkMetadataConstants.UPDATE_ACTION:
          processUpdateAction(projectEntityChangeDTO);
          return;
        case EventsFrameworkMetadataConstants.DELETE_ACTION:
          processDeleteAction(projectEntityChangeDTO);
          return;
        default:
      }
    }
  }

  @VisibleForTesting
  void processCreateAction(ProjectEntityChangeDTO projectEntityChangeDTO) {
    metricPackService.createDefaultMetricPackAndThresholds(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }

  @VisibleForTesting
  void processUpdateAction(ProjectEntityChangeDTO projectEntityChangeDTO) {
    // add code on project update.
  }

  @VisibleForTesting
  void processDeleteAction(ProjectEntityChangeDTO projectEntityChangeDTO) {
    ENTITIES_MAP.forEach((entity, handler)
                             -> injector.getInstance(handler).deleteByProjectIdentifier(entity,
                                 projectEntityChangeDTO.getAccountIdentifier(),
                                 projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier()));
  }

  private boolean validateMessage(Message message) {
    return message != null && message.hasMessage() && message.getMessage().getMetadataMap() != null
        && message.getMessage().getMetadataMap().containsKey(EventsFrameworkMetadataConstants.ENTITY_TYPE)
        && EventsFrameworkMetadataConstants.PROJECT_ENTITY.equals(
            message.getMessage().getMetadataMap().get(EventsFrameworkMetadataConstants.ENTITY_TYPE));
  }
}
