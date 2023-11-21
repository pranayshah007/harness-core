/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.core.services.CVNextGenConstants.ACCOUNT_IDENTIFIER_PREFIX;
import static io.harness.cvng.core.services.CVNextGenConstants.ORG_IDENTIFIER_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.MONITORED_SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.changeSourceSpec.ChangeSourceWithConnectorSpec;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.SetupUsageEventService;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
public class SetupUsageEventServiceImpl implements SetupUsageEventService {
  @Inject @Named(SETUP_USAGE) private Producer eventProducer;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  public static final String STABLE_VERSION = "__STABLE__";

  @Override
  public void sendCreateEventsForMonitoredService(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    IdentifierRefProtoDTO referredByIdentifier = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(),
        monitoredServiceDTO.getIdentifier());
    EntityDetailProtoDTO referredByEntity = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(referredByIdentifier)
                                                .setName(monitoredServiceDTO.getName())
                                                .setType(MONITORED_SERVICE)
                                                .build();

    List<EntityDetailProtoDTO> referredConnectorEntities =
        getReferredConnectorEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredConnectorEntities, CONNECTORS);

    List<EntityDetailProtoDTO> referredServiceEntities = getReferredServiceEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredServiceEntities, SERVICE);

    List<EntityDetailProtoDTO> referredEnvEntities = getReferredEnvironmentEntities(projectParams, monitoredServiceDTO);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, referredEnvEntities, ENVIRONMENT);

    if (!Objects.isNull(monitoredServiceDTO.getTemplate())
        && monitoredServiceDTO.getTemplate().isTemplateByReference()) {
      sendEvents(projectParams.getAccountIdentifier(), referredByEntity,
          Collections.singletonList(getTemplateReferenceProtoDTOFromDTO(projectParams, monitoredServiceDTO)), TEMPLATE);
    }
  }

  @Override
  public void sendDeleteEventsForMonitoredService(ProjectParams projectParams, MonitoredService monitoredService) {
    IdentifierRefProtoDTO referredByIdentifier =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(projectParams.getAccountIdentifier(),
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), monitoredService.getIdentifier());
    EntityDetailProtoDTO referredByEntity = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(referredByIdentifier)
                                                .setName(monitoredService.getName())
                                                .setType(MONITORED_SERVICE)
                                                .build();

    // Send empty dependency events
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), CONNECTORS);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), SERVICE);
    sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), ENVIRONMENT);
    if (monitoredService.isTemplateByReference()) {
      sendEvents(projectParams.getAccountIdentifier(), referredByEntity, new ArrayList<>(), TEMPLATE);
    }
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

  private EntityDetailProtoDTO getTemplateReferenceProtoDTOFromDTO(
      ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO) {
    try {
      String templateRef = monitoredServiceDTO.getTemplate().getTemplateRef();
      String versionLabel = monitoredServiceDTO.getTemplate().getVersionLabel();
      if (isEmpty(versionLabel)) {
        versionLabel = STABLE_VERSION;
      }
      TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO =
          TemplateReferenceProtoDTO.newBuilder().setAccountIdentifier(
              StringValue.of(projectParams.getAccountIdentifier()));
      if (templateRef.contains(ACCOUNT_IDENTIFIER_PREFIX)) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
            .setIdentifier(StringValue.of(templateRef.replace(ACCOUNT_IDENTIFIER_PREFIX, "")));
      } else if (templateRef.contains(ORG_IDENTIFIER_PREFIX)) {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.ORG)
            .setOrgIdentifier(StringValue.of(projectParams.getOrgIdentifier()))
            .setIdentifier(StringValue.of(templateRef.replace(ORG_IDENTIFIER_PREFIX, "")));
      } else {
        templateReferenceProtoDTO.setScope(ScopeProtoEnum.PROJECT)
            .setOrgIdentifier(StringValue.of(projectParams.getOrgIdentifier()))
            .setProjectIdentifier(StringValue.of(projectParams.getProjectIdentifier()))
            .setIdentifier(StringValue.of(templateRef));
      }
      templateReferenceProtoDTO.setVersionLabel(StringValue.of(versionLabel));
      return EntityDetailProtoDTO.newBuilder()
          .setType(TEMPLATE)
          .setTemplateRef(templateReferenceProtoDTO.build())
          .build();
    } catch (Exception e) {
      log.error(
          "Could not add the reference in entity setup usage for acc: {}, project: {}, monitoredServiceRef: {}: {}",
          projectParams.getAccountIdentifier(), projectParams.getProjectIdentifier(),
          monitoredServiceDTO.getIdentifier(), e.getMessage());
      throw new InvalidRequestException(
          format("Could not add the reference in entity setup usage for monitoredServiceRef: [%s] and [%s]",
              monitoredServiceDTO.getIdentifier(), e.getMessage()));
    }
  }
}
