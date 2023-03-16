/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.NGConstants.CONNECTOR_TYPE_NAME;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;

import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.services.ConnectorActivityService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entityactivity.EntityActivityCreateDTO;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ConnectorActivityServiceImpl implements ConnectorActivityService {
  public static final String CREATION_DESCRIPTION = "Connector Created";
  public static final String UPDATE_DESCRIPTION = "Connector Updated";
  private Producer eventProducer;
  private NGActivityService ngActivityService;
  private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Inject
  public ConnectorActivityServiceImpl(@Named(EventsFrameworkConstants.ENTITY_ACTIVITY) Producer eventProducer,
      NGActivityService ngActivityService, IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper) {
    this.eventProducer = eventProducer;
    this.ngActivityService = ngActivityService;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
  }

  @Override
  public void create(String accountIdentifier, ConnectorInfoDTO connector, NGActivityType ngActivityType) {
    if (ngActivityType == NGActivityType.ENTITY_CREATION) {
      createConnectorCreationActivity(accountIdentifier, connector);
    } else if (ngActivityType == NGActivityType.ENTITY_UPDATE) {
      createConnectorUpdateActivity(accountIdentifier, connector);
    }
  }

  private EntityDetail getConnectorEntityDetail(String accountIdentifier, ConnectorInfoDTO connector) {
    IdentifierRef entityRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        connector.getIdentifier(), accountIdentifier, connector.getOrgIdentifier(), connector.getProjectIdentifier());
    return EntityDetail.builder().type(EntityType.CONNECTORS).name(connector.getName()).entityRef(entityRef).build();
  }

  private EntityActivityCreateDTO createNGActivityObject(
      String accountIdentifier, ConnectorInfoDTO connector, String activityDescription, NGActivityType type) {
    IdentifierRefProtoDTO identifierRefProtoDTO = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, connector.getOrgIdentifier(), connector.getProjectIdentifier(), connector.getIdentifier(),
        connector.getConnectorType() != null
            ? Collections.singletonMap(CONNECTOR_TYPE_NAME, connector.getConnectorType().name())
            : null);
    EntityDetailProtoDTO referredEntity = EntityDetailProtoDTO.newBuilder()
                                              .setType(CONNECTORS)
                                              .setIdentifierRef(identifierRefProtoDTO)
                                              .setName(connector.getName() == null ? "" : connector.getName())
                                              .build();
    return EntityActivityCreateDTO.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .setStatus(NGActivityStatus.SUCCESS.toString())
        .setType(type.toString())
        .setDescription(activityDescription)
        .setReferredEntity(referredEntity)
        .setActivityTime(System.currentTimeMillis())
        .build();
  }

  private void createConnectorCreationActivity(String accountIdentifier, ConnectorInfoDTO connector) {
    EntityActivityCreateDTO creationActivity =
        createNGActivityObject(accountIdentifier, connector, CREATION_DESCRIPTION, NGActivityType.ENTITY_CREATION);
    publishEntityActivityEvent(
        creationActivity, connector.getIdentifier(), connector.getOrgIdentifier(), connector.getProjectIdentifier());
  }

  private void createConnectorUpdateActivity(String accountIdentifier, ConnectorInfoDTO connector) {
    EntityActivityCreateDTO updateActivity =
        createNGActivityObject(accountIdentifier, connector, UPDATE_DESCRIPTION, NGActivityType.ENTITY_UPDATE);
    publishEntityActivityEvent(
        updateActivity, connector.getIdentifier(), connector.getOrgIdentifier(), connector.getProjectIdentifier());
  }

  private void publishEntityActivityEvent(
      EntityActivityCreateDTO creationActivity, String identifier, String orgIdentifier, String projectIdentifier) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", creationActivity.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.CREATE_ACTION))
              .setData(creationActivity.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("{} Exception while pushing the heartbeat result {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
          String.format(
              CONNECTOR_STRING, identifier, creationActivity.getAccountIdentifier(), orgIdentifier, projectIdentifier));
    }
  }

  @Override
  public void deleteAllActivities(String accountIdentifier, String connectorFQN) {
    ngActivityService.deleteAllActivitiesOfAnEntity(accountIdentifier, connectorFQN, EntityType.CONNECTORS);
  }
}
