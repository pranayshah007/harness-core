/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.MONITORED_SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.ChangeSourceWithConnectorSpec;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
public class SetupUsageEventServiceImpl implements SetupUsageEventService {
  @Inject @Named(SETUP_USAGE) private Producer eventProducer;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Override
  public void sendCreateEventsForMonitoredService(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    IdentifierRefProtoDTO referredByIdentifier = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
        monitoredServiceDTO.getIdentifier());
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder().setIdentifierRef(referredByIdentifier).setType(MONITORED_SERVICE).build();

    List<EntityDetailProtoDTO> referredConnectorEntities =
        getReferredConnectorEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredConnectorEntities, CONNECTORS);

    List<EntityDetailProtoDTO> referredServiceEntities = getReferredServiceEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredServiceEntities, SERVICE);

    List<EntityDetailProtoDTO> referredEnvEntities = getReferredEnvironmentEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredEnvEntities, ENVIRONMENT);
  }

  @Override
  public void sendDeleteEventsForMonitoredService(ProjectParams projectParams, String identifier) {
    IdentifierRefProtoDTO referredByIdentifier =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier);
    EntityDetailProtoDTO referredByEntity =
        EntityDetailProtoDTO.newBuilder().setIdentifierRef(referredByIdentifier).setType(MONITORED_SERVICE).build();

    // Send empty dependency events
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), CONNECTORS);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), SERVICE);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), ENVIRONMENT);
  }

  @Override
  public void sendEvents(String accountId, EntityDetailProtoDTO referredByEntity,
      List<EntityDetailProtoDTO> referredEntities, EntityTypeProtoEnum referredEntityType) {
    EntitySetupUsageCreateV2DTO setupUsageCreateV2DTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                            .setAccountIdentifier(accountId)
                                                            .setReferredByEntity(referredByEntity)
                                                            .addAllReferredEntities(referredEntities)
                                                            .setDeleteOldReferredByRecords(true)
                                                            .build();
    Message message =
        Message.newBuilder()
            .putAllMetadata(ImmutableMap.of("accountId", accountId,
                EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, referredEntityType.name(),
                EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
            .setData(setupUsageCreateV2DTO.toByteString())
            .build();
    sendMessage(message, referredByEntity);
  }

  private void sendMessage(Message message, EntityDetailProtoDTO entityToLog) {
    try {
      eventProducer.send(message);
    } catch (Exception e) {
      log.error(ENTITY_REFERENCE_LOG_PREFIX + " Error while sending referenced Object: {}, Message: {}", entityToLog,
          message);
      throw new IllegalStateException(e);
    }
  }

  private List<EntityDetailProtoDTO> getReferredConnectorEntities(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    Set<String> connectorRefs = monitoredServiceDTO.getSources()
                                    .getHealthSources()
                                    .stream()
                                    .map(hs -> hs.getSpec().getConnectorRef())
                                    .collect(Collectors.toSet());

    monitoredServiceDTO.getSources().getChangeSources().forEach(changeSourceDTO -> {
      if (changeSourceDTO.getSpec().connectorPresent()) {
        connectorRefs.add(((ChangeSourceWithConnectorSpec) changeSourceDTO.getSpec()).getConnectorRef());
      }
    });
    return getEntityDetailProtoDTOList(projectParams, connectorRefs, CONNECTORS);
  }

  private List<EntityDetailProtoDTO> getReferredServiceEntities(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    Set<String> serviceRefs = new HashSet<>();
    serviceRefs.add(monitoredServiceDTO.getServiceRef());
    return getEntityDetailProtoDTOList(projectParams, serviceRefs, SERVICE);
  }

  private List<EntityDetailProtoDTO> getReferredEnvironmentEntities(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    Set<String> environmentRefs = Sets.newHashSet(monitoredServiceDTO.getEnvironmentRef());
    return getEntityDetailProtoDTOList(projectParams, environmentRefs, ENVIRONMENT);
  }

  private List<EntityDetailProtoDTO> getEntityDetailProtoDTOList(
      ProjectParams projectParams, Set<String> refs, EntityTypeProtoEnum entityType) {
    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    refs.forEach(ref -> referredEntities.add(getEntityDetailProtoDTOFromRef(projectParams, ref, entityType)));
    return referredEntities;
  }

  private EntityDetailProtoDTO getEntityDetailProtoDTOFromRef(
      ProjectParams projectParams, String ref, EntityTypeProtoEnum entityType) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(ref, projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    IdentifierRefProtoDTO referredDTO =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    return EntityDetailProtoDTO.newBuilder().setIdentifierRef(referredDTO).setType(entityType).build();
  }
}
