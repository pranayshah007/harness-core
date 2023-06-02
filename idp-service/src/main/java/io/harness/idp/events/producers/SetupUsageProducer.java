/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;

import static software.wings.utils.Utils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class SetupUsageProducer {
  private static final String ACCOUNT_ID = "accountId";
  private final Producer eventProducer;

  @Inject
  public SetupUsageProducer(@Named(SETUP_USAGE) Producer eventProducer) {
    this.eventProducer = eventProducer;
  }

  public void publishEnvVariableSetupUsage(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    envVariables.forEach(envVariable -> {
      if (envVariable.getType() == BackstageEnvVariable.TypeEnum.CONFIG) {
        log.info("Not publishing entity usage for config type");
        return;
      }
      BackstageEnvSecretVariable envSecretVariable = (BackstageEnvSecretVariable) envVariable;
      IdentifierRefProtoDTO secretReference = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
          accountIdentifier, null, null, envSecretVariable.getHarnessSecretIdentifier());
      IdentifierRefProtoDTO envVariableReference = IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
          accountIdentifier, null, null, envSecretVariable.getEnvName());
      EntityDetailProtoDTO secretDetails = EntityDetailProtoDTO.newBuilder()
                                               .setIdentifierRef(secretReference)
                                               .setType(EntityTypeProtoEnum.SECRETS)
                                               .setName(emptyIfNull(envSecretVariable.getHarnessSecretIdentifier()))
                                               .build();
      EntityDetailProtoDTO envVariableDetails = EntityDetailProtoDTO.newBuilder()
                                                    .setIdentifierRef(envVariableReference)
                                                    .setType(EntityTypeProtoEnum.BACKSTAGE_ENVIRONMENT_VARIABLE)
                                                    .setName(emptyIfNull(envSecretVariable.getEnvName()))
                                                    .build();
      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountIdentifier)
                                                           .setReferredByEntity(envVariableDetails)
                                                           .addReferredEntities(secretDetails)
                                                           .setDeleteOldReferredByRecords(false)
                                                           .build();
      try {
        String messageId = eventProducer.send(
            Message.newBuilder()
                .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier,
                    EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                    EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                .setData(entityReferenceDTO.toByteString())
                .build());
        log.info("Emitted environment secret event with id {} for entityreference {} and accountId {}", messageId,
            entityReferenceDTO, accountIdentifier);
      } catch (EventsFrameworkDownException e) {
        log.error("Failed to send event to events framework for env secret: {}, accountId: {} ",
            envVariable.getEnvName(), accountIdentifier, e);
      }
    });
  }

  public void deleteEnvVariableSetupUsage(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    envVariables.forEach(envVariable -> {
      if (envVariable.getType() == BackstageEnvVariable.TypeEnum.CONFIG) {
        log.info("Not publishing entity usage for config type");
        return;
      }
      EntityDetailProtoDTO entityDetail = EntityDetailProtoDTO.newBuilder()
                                              .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                                                  accountIdentifier, null, null, envVariable.getEnvName()))
                                              .setType(EntityTypeProtoEnum.BACKSTAGE_ENVIRONMENT_VARIABLE)
                                              .build();

      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountIdentifier)
                                                           .setReferredByEntity(entityDetail)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      try {
        String messageId = eventProducer.send(
            Message.newBuilder()
                .putAllMetadata(ImmutableMap.of(ACCOUNT_ID, accountIdentifier, EventsFrameworkMetadataConstants.ACTION,
                    EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                .setData(entityReferenceDTO.toByteString())
                .build());
        log.info("Emitted delete environment secret event with id {} for entityreference {} and accountId {}",
            messageId, entityReferenceDTO, accountIdentifier);
      } catch (EventsFrameworkDownException e) {
        log.error("Failed to send event to events framework for env secret: {}, accountId: {} ",
            envVariable.getEnvName(), accountIdentifier, e);
      }
    });
  }
}
