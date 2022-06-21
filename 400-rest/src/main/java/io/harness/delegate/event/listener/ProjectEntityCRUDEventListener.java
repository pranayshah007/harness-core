/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.event.listener;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DEL)
@Slf4j
@Singleton
public class ProjectEntityCRUDEventListener implements MessageListener {
  private final DelegateNgTokenService delegateNgTokenService;

  @Inject
  public ProjectEntityCRUDEventListener(final DelegateNgTokenService delegateNgTokenService) {
    this.delegateNgTokenService = delegateNgTokenService;
  }

  @Override
  public boolean handleMessage(@NonNull final Message message) {
    if (message.hasMessage()) {
      final Map<String, String> metadata = message.getMessage().getMetadataMap();
      final String entityType = metadata.get(ENTITY_TYPE);
      if (Objects.equals(entityType, PROJECT_ENTITY)) {
        try {
          final ProjectEntityChangeDTO projectEntityChangeDTO =
              ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
          final String action = metadata.get(ACTION);
          if (action != null) {
            return processEntityChangeEvent(projectEntityChangeDTO, action);
          }
        } catch (final InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
      }
    }
    // Not great, but since filtering happens on the listener level, we need to acknowledge if the message is not for us
    return true;
  }

  private boolean processEntityChangeEvent(final ProjectEntityChangeDTO projectEntityChangeDTO, final String action) {
    if (CREATE_ACTION.equals(action)) {
      return handleCreateEvent(projectEntityChangeDTO);
    } else if (DELETE_ACTION.equals(action)) {
      return handleDeleteEvent(projectEntityChangeDTO);
    } else if (RESTORE_ACTION.equals(action)) {
      return handleRestoreEvent(projectEntityChangeDTO);
    }
    return true;
  }

  private boolean handleCreateEvent(final ProjectEntityChangeDTO projectEntityChangeDTO) {
    try {
      final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(
          projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
      delegateNgTokenService.upsertDefaultToken(projectEntityChangeDTO.getAccountIdentifier(), owner, false);
      log.info("Default Delegate Token created for project {}/{}.", projectEntityChangeDTO.getAccountIdentifier(),
          owner.getIdentifier());
      return true;
    } catch (final Exception e) {
      log.error("Failed to create default Delegate Token for project {}/{}/{}, caused by: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getOrgIdentifier(),
          projectEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }

  private boolean handleDeleteEvent(final ProjectEntityChangeDTO projectEntityChangeDTO) {
    try {
      final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(
          projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
      delegateNgTokenService.deleteAllTokensOwnedByOrgAndProject(projectEntityChangeDTO.getAccountIdentifier(), owner);
      log.info("Project {}/{} deleted and all Delegate Tokens owned by project has been deleted.",
          projectEntityChangeDTO.getAccountIdentifier(), owner.getIdentifier());
      return true;
    } catch (final Exception e) {
      log.error("Failed to delete Delegate Tokens for project {}/{}/{}, caused by: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getOrgIdentifier(),
          projectEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }

  private boolean handleRestoreEvent(final ProjectEntityChangeDTO projectEntityChangeDTO) {
    try {
      delegateNgTokenService.upsertDefaultToken(projectEntityChangeDTO.getAccountIdentifier(),
          DelegateEntityOwnerHelper.buildOwner(
              projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier()),
          false);
      log.info("Project {}/{} restored and new default Delegate Token generated.",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier());
      return true;
    } catch (final Exception e) {
      log.error("Failed to restore default Delegate Token for project {}/{}/{}, caused by: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getOrgIdentifier(),
          projectEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }
}
