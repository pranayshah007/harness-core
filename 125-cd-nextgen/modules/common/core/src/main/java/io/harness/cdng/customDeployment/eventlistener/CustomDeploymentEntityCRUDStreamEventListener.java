/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.eventlistener;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.TEMPLATE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static software.wings.beans.AccountType.log;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.core.template.TemplateEntityType;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
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
      if (metadataMap.get(ENTITY_TYPE) != null) {
        return processChangeEvent(message);
      }
    }
    return true;
  }

  private boolean processChangeEvent(Message message) {
    if (message != null && message.hasMessage() && message.getMessage().getMetadataMap().containsKey(ENTITY_TYPE)) {
      final Map<String, String> metadata = message.getMessage().getMetadataMap();
      final String entityType = metadata.get(ENTITY_TYPE);
      if (Objects.equals(entityType, TEMPLATE_ENTITY.toUpperCase())) {
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
            case DELETE_ACTION:
              return true;
            case UPDATE_ACTION:
              return processUpdateEvent(entityChangeDTO);
            default:
          }
        }
      }
    }
    return true;
  }

  private boolean processUpdateEvent(EntityChangeDTO entityChangeDTO) {
    try {
      if (Objects.equals(entityChangeDTO.getMetadataMap().get("templateType"),
              TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.toString())) {
        String orgId = isEmpty(entityChangeDTO.getOrgIdentifier().getValue())
            ? null
            : entityChangeDTO.getOrgIdentifier().getValue();
        String projectId = isEmpty(entityChangeDTO.getProjectIdentifier().getValue())
            ? null
            : entityChangeDTO.getProjectIdentifier().getValue();
        if (entityChangeDTO.getMetadataMap().get("isStable") != null) {
          deploymentTemplateEntityCRUDEventHandler.updateInfraAsObsolete(
              entityChangeDTO.getAccountIdentifier().getValue(), orgId, projectId,
              entityChangeDTO.getIdentifier().getValue(), null);
        }
        return deploymentTemplateEntityCRUDEventHandler.updateInfraAsObsolete(
            entityChangeDTO.getAccountIdentifier().getValue(), orgId, projectId,
            entityChangeDTO.getIdentifier().getValue(), entityChangeDTO.getMetadataMap().get("versionLabel"));
      }
    } catch (Exception e) {
      log.error("Could not Update the infra for deployment template change for account identifier :{}",
          entityChangeDTO.getAccountIdentifier());
    }
    return true;
  }
}
