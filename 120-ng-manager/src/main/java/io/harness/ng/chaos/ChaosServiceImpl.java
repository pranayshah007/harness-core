/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.cdng.chaos.ChaosStepNotifyData;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ChaosServiceImpl implements ChaosService {
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Override
  public void notifyStep(String notifyId, ChaosStepNotifyData data) {
    waitNotifyEngine.doneWith(notifyId, data);
  }

  @Override
  public boolean registerChaosInfrastructure(ChaosInfrastructureRequest chaosInfrastructureRequest) {
    String accountIdentifier = chaosInfrastructureRequest.getAccountId();
    String orgIdentifier = chaosInfrastructureRequest.getOrgId();
    String projectIdentifier = chaosInfrastructureRequest.getProjectId();
    String environmentIdentifier = chaosInfrastructureRequest.getEnvironmentId();
    String chaosInfrastructureId = chaosInfrastructureRequest.getChaosInfrastructureId();
    String chaosInfrastructureName = chaosInfrastructureRequest.getChaosInfrastructureName();

    String environmentFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);

    String chaosInfrastructureFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosInfrastructureId);

    IdentifierRefProtoDTO chaosInfrastructureReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosInfrastructureId);

    IdentifierRefProtoDTO environmentReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);

    EntityDetailProtoDTO chaosInfrastructureDetails = EntityDetailProtoDTO.newBuilder()
                                                          .setIdentifierRef(chaosInfrastructureReference)
                                                          .setType(EntityTypeProtoEnum.CHAOS_INFRASTRUCTURE)
                                                          .setName(emptyIfNull(chaosInfrastructureName))
                                                          .build();

    EntityDetailProtoDTO environmentDetails = EntityDetailProtoDTO.newBuilder()
                                                  .setIdentifierRef(environmentReference)
                                                  .setType(EntityTypeProtoEnum.ENVIRONMENT)
                                                  .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(chaosInfrastructureDetails)
                                                         .addReferredEntities(environmentDetails)
                                                         .setDeleteOldReferredByRecords(false)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.ENVIRONMENT.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not created when the chaosInfrastructure [{}] was created in the environment [{}] with the exception [{}]",
          chaosInfrastructureFQN, environmentFQN, ex.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public boolean deleteChaosInfrastructure(ChaosInfrastructureRequest chaosInfrastructureRequest) {
    String accountIdentifier = chaosInfrastructureRequest.getAccountId();
    String orgIdentifier = chaosInfrastructureRequest.getOrgId();
    String projectIdentifier = chaosInfrastructureRequest.getProjectId();
    String environmentIdentifier = chaosInfrastructureRequest.getEnvironmentId();
    String chaosInfrastructureId = chaosInfrastructureRequest.getChaosInfrastructureId();

    String environmentFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier);

    String chaosInfrastructureFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosInfrastructureId);

    DeleteSetupUsageDTO deleteSetupUsageDTO = DeleteSetupUsageDTO.newBuilder()
                                                  .setAccountIdentifier(accountIdentifier)
                                                  .setReferredByEntityFQN(chaosInfrastructureFQN)
                                                  .setReferredByEntityType(EntityTypeProtoEnum.CHAOS_INFRASTRUCTURE)
                                                  .setReferredEntityFQN(environmentFQN)
                                                  .setReferredEntityType(EntityTypeProtoEnum.ENVIRONMENT)
                                                  .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE,
                      EntityTypeProtoEnum.CHAOS_INFRASTRUCTURE.name(), EventsFrameworkMetadataConstants.ACTION,
                      EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(deleteSetupUsageDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the infrastructure [{}] was deleted from the environment [{}] with the exception [{}]",
          chaosInfrastructureFQN, environmentFQN, ex.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public boolean registerChaosHub(ChaosHubRequest chaosHubRequest) {
    String accountIdentifier = chaosHubRequest.getAccountId();
    String orgIdentifier = chaosHubRequest.getOrgId();
    String projectIdentifier = chaosHubRequest.getProjectId();
    String connectorIdentifier = chaosHubRequest.getConnectorId();
    String chaosHubId = chaosHubRequest.getChaosHubId();
    String chaosHubName = chaosHubRequest.getChaosHubName();

    String connectorFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);

    String chaosHubFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosHubId);

    IdentifierRefProtoDTO chaosHubReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosHubId);

    IdentifierRefProtoDTO connectorReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);

    EntityDetailProtoDTO chaosHubDetails = EntityDetailProtoDTO.newBuilder()
                                               .setIdentifierRef(chaosHubReference)
                                               .setType(EntityTypeProtoEnum.CHAOS_HUB)
                                               .setName(emptyIfNull(chaosHubName))
                                               .build();

    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(connectorReference)
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(chaosHubDetails)
                                                         .addReferredEntities(connectorDetails)
                                                         .setDeleteOldReferredByRecords(false)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not created when the chaosHub [{}] was created for the connector [{}] with the exception [{}]",
          chaosHubFQN, connectorFQN, ex.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public boolean deleteChaosHub(ChaosHubRequest chaosHubRequest) {
    String accountIdentifier = chaosHubRequest.getAccountId();
    String orgIdentifier = chaosHubRequest.getOrgId();
    String projectIdentifier = chaosHubRequest.getProjectId();
    String connectorIdentifier = chaosHubRequest.getConnectorId();
    String chaosHubId = chaosHubRequest.getChaosHubId();

    String connectorFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);

    String chaosHubFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, chaosHubId);

    DeleteSetupUsageDTO deleteSetupUsageDTO = DeleteSetupUsageDTO.newBuilder()
                                                  .setAccountIdentifier(accountIdentifier)
                                                  .setReferredByEntityFQN(chaosHubFQN)
                                                  .setReferredByEntityType(EntityTypeProtoEnum.CHAOS_HUB)
                                                  .setReferredEntityFQN(connectorFQN)
                                                  .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
                                                  .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CHAOS_HUB.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(deleteSetupUsageDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the chaos hub [{}] was deleted from the connector [{}] with the exception [{}]",
          chaosHubFQN, connectorFQN, ex.getMessage());
      return false;
    }
    return true;
  }
}
