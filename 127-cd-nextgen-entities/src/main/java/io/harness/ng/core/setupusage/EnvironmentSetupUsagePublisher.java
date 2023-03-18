/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ng.core.setupusage;

import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class EnvironmentSetupUsagePublisher {
  @Inject @Named(SETUP_USAGE) private Producer producer;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  public void publishEnvironmentEntitySetupUsage(EntityDetailProtoDTO referredByEntityDetail,
      Set<EntityDetailProtoDTO> referredEntities, String accountId, Set<String> entityTypeProtoEnums) {
    Map<String, List<EntityDetailProtoDTO>> referredEntityTypeToReferredEntities =
        referredEntities.stream().collect(Collectors.groupingBy(proto -> proto.getType().name()));

    for (Map.Entry<String, List<EntityDetailProtoDTO>> entry : referredEntityTypeToReferredEntities.entrySet()) {
      if (entityTypeProtoEnums != null && entityTypeProtoEnums.contains(entry.getKey())) {
        entityTypeProtoEnums.remove(entry.getKey());
      }
      List<EntityDetailProtoDTO> entityDetailProtoDTOs = entry.getValue();
      EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                           .setAccountIdentifier(accountId)
                                                           .setReferredByEntity(referredByEntityDetail)
                                                           .addAllReferredEntities(entityDetailProtoDTOs)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      String messageId = producer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId,
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
      log.info("Emitted event with referred by entity with message id {} for entityreference {} and accountId {}",
          messageId, entityReferenceDTO, accountId);
    }
    if (entityTypeProtoEnums != null) {
      for (String key : entityTypeProtoEnums) {
        EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                             .setAccountIdentifier(accountId)
                                                             .setReferredByEntity(referredByEntityDetail)
                                                             .setDeleteOldReferredByRecords(true)
                                                             .build();
        String messageId = producer.send(
            Message.newBuilder()
                .putAllMetadata(
                    ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, key,
                        EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                .setData(entityReferenceDTO.toByteString())
                .build());
        log.info("Emitted  event with id {} for entityreference {} and accountId {}", messageId, entityReferenceDTO,
            accountId);
      }
    }
  }
  public void deleteEnvironmentSetupUsages(EntityDetailProtoDTO entityDetail, String accountId) {
    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(accountId)
                                                         .setReferredByEntity(entityDetail)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    // Send Events for all referredEntitiesType to delete them
    String messageId = producer.send(
        Message.newBuilder()
            .putAllMetadata(ImmutableMap.of("accountId", accountId, EventsFrameworkMetadataConstants.ACTION,
                EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
            .setData(entityReferenceDTO.toByteString())
            .build());
    log.info("Emitted delete event with id {} for entityreference {} and accountId {}", messageId, entityReferenceDTO,
        accountId);
  }
}
