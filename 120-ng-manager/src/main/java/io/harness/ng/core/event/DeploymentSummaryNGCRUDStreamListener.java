/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.deploymentsummary.DeploymentSummaryRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
@Singleton
public class DeploymentSummaryNGCRUDStreamListener implements MessageListener {
  private final DeploymentSummaryRepository deploymentSummaryRepository;
  @Inject
  public DeploymentSummaryNGCRUDStreamListener(DeploymentSummaryRepository deploymentSummaryRepository) {
    this.deploymentSummaryRepository = deploymentSummaryRepository;
  }

  @Override
  public boolean handleMessage(Message message) {
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
        && PROJECT_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
      return processProjectEntityChangeEvent(message);
    }
    return true;
  }

  private boolean processProjectEntityChangeEvent(Message message) {
    if (!(message.getMessage().getMetadataMap().containsKey(ACTION)
            && DELETE_ACTION.equals(message.getMessage().getMetadataMap().get(ACTION)))) {
      return true;
    }
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    return processProjectDeleteEvent(projectEntityChangeDTO);
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return deploymentSummaryRepository.delete(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }
}
