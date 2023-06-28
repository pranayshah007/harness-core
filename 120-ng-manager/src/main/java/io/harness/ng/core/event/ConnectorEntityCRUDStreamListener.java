/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;

import static java.lang.Boolean.parseBoolean;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.eventHandlers.ConnectorEntityCRUDEventHandler;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class ConnectorEntityCRUDStreamListener implements MessageListener {
  private final HarnessSMManager harnessSMManager;
  private final ConnectorEntityCRUDEventHandler connectorEntityCRUDEventHandler;
  private final NGSettingsClient settingsClient;
  private final NGFeatureFlagHelperService featureFlagHelperService;

  @Inject
  public ConnectorEntityCRUDStreamListener(HarnessSMManager harnessSMManager,
      ConnectorEntityCRUDEventHandler connectorEntityCRUDEventHandler, NGSettingsClient settingsClient,
      NGFeatureFlagHelperService featureFlagHelperService) {
    this.harnessSMManager = harnessSMManager;
    this.connectorEntityCRUDEventHandler = connectorEntityCRUDEventHandler;
    this.settingsClient = settingsClient;
    this.featureFlagHelperService = featureFlagHelperService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        switch (entityType) {
          case ORGANIZATION_ENTITY:
            return processOrganizationChangeEvent(message);
          case PROJECT_ENTITY:
            return processProjectChangeEvent(message);
          case ACCOUNT_ENTITY:
            return processAccountChangeEvent(message);
          default:
        }
      }
    }
    return true;
  }

  private boolean processAccountChangeEvent(Message message) {
    if (!(message.getMessage().getMetadataMap().containsKey(ACTION)
            && DELETE_ACTION.equals(message.getMessage().getMetadataMap().get(ACTION)))) {
      return true;
    }
    AccountEntityChangeDTO accountEntityChangeDTO;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    return processAccountDeleteEvent(accountEntityChangeDTO);
  }

  private boolean processAccountDeleteEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    return connectorEntityCRUDEventHandler.deleteAssociatedConnectors(
        accountEntityChangeDTO.getAccountId(), null, null);
  }

  private boolean processOrganizationChangeEvent(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processOrganizationCreateEvent(organizationEntityChangeDTO);
        case DELETE_ACTION:
          return processOrganizationDeleteEvent(organizationEntityChangeDTO);
        case RESTORE_ACTION:
          return processOrganizationRestoreEvent(organizationEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processOrganizationCreateEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();

    Boolean isBuiltInSMDisabled =
        parseBoolean(NGRestUtils
                         .getResponse(settingsClient.getSetting(
                             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
                         .getValue());

    if (!isBuiltInSMDisabled) {
      harnessSMManager.createHarnessSecretManager(
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
    }
    return true;
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return connectorEntityCRUDEventHandler.deleteAssociatedConnectors(
        organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
  }

  private boolean processOrganizationRestoreEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return true;
  }

  private boolean processProjectChangeEvent(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processProjectCreateEvent(projectEntityChangeDTO);
        case DELETE_ACTION:
          return processProjectDeleteEvent(projectEntityChangeDTO);
        case RESTORE_ACTION:
          return processProjectRestoreEvent(projectEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processProjectCreateEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();
    Boolean isBuiltInSMDisabled =
        parseBoolean(NGRestUtils
                         .getResponse(settingsClient.getSetting(
                             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
                         .getValue());

    if (!isBuiltInSMDisabled) {
      harnessSMManager.createHarnessSecretManager(projectEntityChangeDTO.getAccountIdentifier(),
          projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
    }
    return true;
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return connectorEntityCRUDEventHandler.deleteAssociatedConnectors(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }

  private boolean processProjectRestoreEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return true;
  }
}
