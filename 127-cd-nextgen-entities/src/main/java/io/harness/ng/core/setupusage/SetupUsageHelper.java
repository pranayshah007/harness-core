package io.harness.ng.core.setupusage;

import static io.harness.eventsframework.EventsFrameworkConstants.SETUP_USAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class SetupUsageHelper {
  @Inject @Named(SETUP_USAGE) private Producer producer;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;

  public void publishEntitySetupUsage(@Valid SetupUsageOwnerEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    EntityDetailProtoDTO entityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(entity.getAccountId(),
                entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier()))
            .setType(entity.getType())
            .setName(entity.getName())
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
                                                           .setAccountIdentifier(entity.getAccountId())
                                                           .setReferredByEntity(entityDetail)
                                                           .addAllReferredEntities(entityDetailProtoDTOs)
                                                           .setDeleteOldReferredByRecords(true)
                                                           .build();
      producer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", entity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, entry.getKey(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }

  public void deleteSetupUsages(@Valid SetupUsageOwnerEntity entity) {
    EntityDetailProtoDTO entityDetail =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(entity.getAccountId(),
                entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier()))
            .setType(entity.getType())
            .setName(entity.getName())
            .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(entity.getAccountId())
                                                         .setReferredByEntity(entityDetail)
                                                         .addAllReferredEntities(new ArrayList<>())
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    // Send Events for all referredEntitiesType to delete them
    for (EntityTypeProtoEnum protoEnum : EntityTypeProtoEnum.values()) {
      producer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", entity.getAccountId(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, protoEnum.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    }
  }
}
