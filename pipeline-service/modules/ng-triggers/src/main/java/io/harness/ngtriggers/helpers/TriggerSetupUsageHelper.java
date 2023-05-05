/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.TriggerReferenceProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TriggerSetupUsageHelper {
  @Inject @Named(EventsFrameworkConstants.SETUP_USAGE) private Producer eventProducer;
  @Inject EntitySetupUsageClient entitySetupUsageClient;

  public void publishSetupUsageEvent(NGTriggerEntity ngTriggerEntity, List<EntityDetailProtoDTO> referredEntities) {
    // Deleting all references so that any deleted entity is not still referred.
    deleteExistingSetupUsages(ngTriggerEntity);
    if (EmptyPredicate.isEmpty(referredEntities)) {
      return;
    }

    String accountId = ngTriggerEntity.getAccountId();
    EntityDetailProtoDTO referredEntity =
        EntityDetailProtoDTO.newBuilder()
            .setTriggerRef(TriggerReferenceProtoDTO.newBuilder()
                               .setPipelineIdentifier(StringValue.of(ngTriggerEntity.getTargetIdentifier()))
                               .setProjectIdentifier(StringValue.of(ngTriggerEntity.getProjectIdentifier()))
                               .setIdentifier(StringValue.of(ngTriggerEntity.getIdentifier()))
                               .setOrgIdentifier(StringValue.of(ngTriggerEntity.getOrgIdentifier()))
                               .setAccountIdentifier(StringValue.of(ngTriggerEntity.getAccountId()))
                               .build())
            .setType(EntityTypeProtoEnum.TRIGGERS)
            .setName(ngTriggerEntity.getName())
            .build();

    Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities = new HashMap<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : referredEntities) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOS =
          referredEntityTypeToReferredEntities.getOrDefault(entityDetailProtoDTO.getType().name(), new ArrayList<>());
      entityDetailProtoDTOS.add(entityDetailProtoDTO);
      referredEntityTypeToReferredEntities.put(entityDetailProtoDTO.getType().name(), entityDetailProtoDTOS);
    }

    for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
      List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountId)
                                                           .setReferredByEntity(referredEntity)
                                                           .addAllReferredEntities(entityDetailProtoDTOs)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  public void deleteExistingSetupUsages(NGTriggerEntity ngTriggerEntity) {
    String accountId = ngTriggerEntity.getAccountId();
    EntityDetailProtoDTO triggerDetails =
        EntityDetailProtoDTO.newBuilder()
            .setTriggerRef(TriggerReferenceProtoDTO.newBuilder()
                               .setPipelineIdentifier(StringValue.of(ngTriggerEntity.getTargetIdentifier()))
                               .setProjectIdentifier(StringValue.of(ngTriggerEntity.getProjectIdentifier()))
                               .setIdentifier(StringValue.of(ngTriggerEntity.getIdentifier()))
                               .setOrgIdentifier(StringValue.of(ngTriggerEntity.getOrgIdentifier()))
                               .setAccountIdentifier(StringValue.of(ngTriggerEntity.getAccountId()))
                               .build())
            .setType(EntityTypeProtoEnum.TRIGGERS)
            .build();
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountId)
                                                         .setReferredByEntity(triggerDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ACTION,
                  EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception ex) {
      log.error("Error deleting the setup usages for the trigger with the identifier {}, in project {} in org {}",
          ngTriggerEntity.getIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier());
    }
  }
}
