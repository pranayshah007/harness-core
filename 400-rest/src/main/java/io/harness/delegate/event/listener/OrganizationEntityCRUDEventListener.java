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
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(DEL)
@Slf4j
@Singleton
public class OrganizationEntityCRUDEventListener implements MessageListener {
  private final DelegateNgTokenService delegateNgTokenService;

  @Inject
  public OrganizationEntityCRUDEventListener(final DelegateNgTokenService delegateNgTokenService) {
    this.delegateNgTokenService = delegateNgTokenService;
  }

  @Override
  public boolean handleMessage(@NonNull final Message message) {
    if (message.hasMessage()) {
      final Map<String, String> metadata = message.getMessage().getMetadataMap();
      final String entityType = metadata.get(ENTITY_TYPE);
      if (Objects.equals(entityType, ORGANIZATION_ENTITY)) {
        try {
          final OrganizationEntityChangeDTO organizationEntityChangeDTO =
              OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
          final String action = metadata.get(ACTION);
          if (action != null) {
            return processEntityChangeEvent(organizationEntityChangeDTO, action);
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

  private boolean processEntityChangeEvent(
      final OrganizationEntityChangeDTO organizationEntityChangeDTO, final String action) {
    if (CREATE_ACTION.equals(action)) {
      return handleCreateEvent(organizationEntityChangeDTO);
    } else if (DELETE_ACTION.equals(action)) {
      return handleDeleteEvent(organizationEntityChangeDTO);
    } else if (RESTORE_ACTION.equals(action)) {
      return handleRestoreEvent(organizationEntityChangeDTO);
    }
    return true;
  }

  private boolean handleCreateEvent(final OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    try {
      final DelegateEntityOwner owner =
          DelegateEntityOwnerHelper.buildOwner(organizationEntityChangeDTO.getIdentifier(), null);
      delegateNgTokenService.upsertDefaultToken(organizationEntityChangeDTO.getAccountIdentifier(), owner, false);
      log.info("Default Delegate Token created for organization {}/{}.",
          organizationEntityChangeDTO.getAccountIdentifier(),
          owner != null ? owner.getIdentifier() : StringUtils.EMPTY);
      return true;
    } catch (final Exception e) {
      log.error("Failed to create default Delegate Token for organization {}/{}, caused by: {}",
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }

  private boolean handleDeleteEvent(final OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    try {
      final DelegateEntityOwner owner =
          DelegateEntityOwnerHelper.buildOwner(organizationEntityChangeDTO.getIdentifier(), null);
      delegateNgTokenService.deleteAllTokensOwnedByOrgAndProject(
          organizationEntityChangeDTO.getAccountIdentifier(), owner);
      log.info("Organization {}/{} deleted and all Delegate Token owned by organization has been deleted.",
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier());
      return true;
    } catch (final Exception e) {
      log.error("Failed to delete Delegate Tokens for organization {}/{}, caused by: {}",
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }

  private boolean handleRestoreEvent(final OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    try {
      delegateNgTokenService.upsertDefaultToken(organizationEntityChangeDTO.getAccountIdentifier(),
          DelegateEntityOwnerHelper.buildOwner(organizationEntityChangeDTO.getIdentifier(), null), false);
      log.info("Organization {}/{} restored and new default Delegate Token generated.",
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier());
      return true;
    } catch (final Exception e) {
      log.error("Failed to create default Delegate Token for organization {}/{}, caused by: {}",
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), e);
      return false;
    }
  }
}
