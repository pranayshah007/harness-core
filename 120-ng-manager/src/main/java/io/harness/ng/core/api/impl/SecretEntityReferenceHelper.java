/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.entitysetupusageclient.EntitySetupUsageHelper;
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
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class SecretEntityReferenceHelper {
  EntitySetupUsageHelper entityReferenceHelper;
  EntitySetupUsageService entitySetupUsageService;
  Producer eventProducer;
  IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  @Inject
  public SecretEntityReferenceHelper(EntitySetupUsageHelper entityReferenceHelper,
      EntitySetupUsageService entitySetupUsageService,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer eventProducer,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper) {
    this.entityReferenceHelper = entityReferenceHelper;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
  }

  public void createSetupUsageForSecretManager(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String secretIdentifier, String secretName, String secretManagerIdentifier) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    IdentifierRefProtoDTO secretReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);

    IdentifierRefProtoDTO secretManagerReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);

    EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                             .setIdentifierRef(secretReference)
                                             .setType(EntityTypeProtoEnum.SECRETS)
                                             .setName(emptyIfNull(secretName))
                                             .build();

    EntityDetailProtoDTO secretManagerDetails = EntityDetailProtoDTO.newBuilder()
                                                    .setIdentifierRef(secretManagerReference)
                                                    .setType(EntityTypeProtoEnum.CONNECTORS)
                                                    .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountIdentifier)
                                                         .setReferredByEntity(secretDetails)
                                                         .addReferredEntities(secretManagerDetails)
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
              + "The entity reference was not created when the secret [{}] was created from the secret manager [{}]",
          secretFQN, secretMangerFQN);
    }
  }

  public void deleteSecretEntityReferenceWhenSecretGetsDeleted(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String secretIdentifier, String secretManagerIdentifier) {
    String secretMangerFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier);
    String secretFQN = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    try {
      DeleteSetupUsageDTO deleteSetupUsageDTO = DeleteSetupUsageDTO.newBuilder()
                                                    .setAccountIdentifier(accountIdentifier)
                                                    .setReferredByEntityFQN(secretFQN)
                                                    .setReferredByEntityType(EntityTypeProtoEnum.SECRETS)
                                                    .setReferredEntityFQN(secretMangerFQN)
                                                    .setReferredEntityType(EntityTypeProtoEnum.CONNECTORS)
                                                    .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.DELETE_ACTION))
              .setData(deleteSetupUsageDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not deleted when the secret [{}] was deleted from the secret manager [{}] with the exception [{}]",
          secretFQN, secretMangerFQN, ex.getMessage());
    }
  }

  public void validateSecretIsNotUsedByOthers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifier) {
    boolean isEntityReferenced;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(secretIdentifier)
                                      .build();
    String referredEntityFQN = identifierRef.getFullyQualifiedName();
    try {
      isEntityReferenced =
          entitySetupUsageService.isEntityReferenced(accountIdentifier, referredEntityFQN, EntityType.SECRETS);
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          secretIdentifier, ex);
      throw new UnexpectedException("Error while deleting the secret");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(
          String.format("Could not delete the secret %s as it is referenced by other entities", secretIdentifier));
    }
  }
}
