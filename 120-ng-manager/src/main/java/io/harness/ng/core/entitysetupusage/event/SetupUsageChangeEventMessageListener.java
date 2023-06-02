/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.event;

import static io.harness.NGConstants.BRANCH;
import static io.harness.NGConstants.REPO;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT_GROUP;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.FILES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.INFRASTRUCTURE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.PIPELINES;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SECRETS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.SERVICE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TRIGGERS;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entitysetupusage.DeleteSetupUsageDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.logging.AutoLogContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.mapper.EntitySetupUsageEventDTOMapper;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.event.EventProtoToEntityHelper;
import io.harness.ng.core.event.MessageListener;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DX)
@Slf4j
@Singleton
public class SetupUsageChangeEventMessageListener implements MessageListener {
  EntitySetupUsageService entitySetupUsageService;
  EntitySetupUsageEventDTOMapper entitySetupUsageEventDTOToRestDTOMapper;
  final Set<EntityTypeProtoEnum> entityTypesSupportedByNGCore = Sets.newHashSet(SECRETS, CONNECTORS, SERVICE,
      ENVIRONMENT, ENVIRONMENT_GROUP, TEMPLATE, FILES, PIPELINES, INFRASTRUCTURE, TRIGGERS);

  @Inject
  public SetupUsageChangeEventMessageListener(EntitySetupUsageService entitySetupUsageService,
      EntitySetupUsageEventDTOMapper entitySetupUsageEventDTOToRestDTOMapper) {
    this.entitySetupUsageService = entitySetupUsageService;
    this.entitySetupUsageEventDTOToRestDTOMapper = entitySetupUsageEventDTOToRestDTOMapper;
  }

  @Override
  public boolean handleMessage(Message message) {
    final String messageId = message.getId();
    log.info("Processing the setup usage crud event with the id {}", messageId);
    try (AutoLogContext ignore1 = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap.containsKey(REFERRED_ENTITY_TYPE) && !handledByNgCore(metadataMap.get(REFERRED_ENTITY_TYPE))) {
        log.info("Skipping processing the message {}", message);
        return true;
      }
      final GitEntityInfo newBranch = getGitEntityInfoFromMessage(message);
      try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
        GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
        EntityType entityTypeFromProto = null;
        if (metadataMap.containsKey(REFERRED_ENTITY_TYPE)) {
          entityTypeFromProto = EventProtoToEntityHelper.getEntityTypeFromProto(
              EntityTypeProtoEnum.valueOf(metadataMap.get(REFERRED_ENTITY_TYPE)));
          log.info("Event received for entityType: [{}]", entityTypeFromProto.getYamlName());
        }
        if (metadataMap.containsKey(EventsFrameworkMetadataConstants.ACTION)) {
          switch (metadataMap.get(EventsFrameworkMetadataConstants.ACTION)) {
            case EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION:
              log.info("Event received for action: FLUSH_CREATE_ACTION");
              final EntitySetupUsageCreateV2DTO setupUsageCreateDTO = getEntitySetupUsageCreateDTO(message);
              return processCreateAction(setupUsageCreateDTO, entityTypeFromProto);
            case EventsFrameworkMetadataConstants.DELETE_ACTION:
              log.info("Event received for action: DELETE_ACTION");
              final DeleteSetupUsageDTO deleteRequestDTO = getEntitySetupUsageDeleteDTO(message);
              return processDeleteAction(deleteRequestDTO, entityTypeFromProto);
            default:
              log.info("Invalid action type: {}", metadataMap.get(EventsFrameworkMetadataConstants.ACTION));
          }
        }
      }
      log.info("Cannot process the setup usage event with id {}", messageId);
      return false;
    }
  }

  private boolean handledByNgCore(String entityTypeProtoEnum) {
    return EntityTypeProtoEnum.getDescriptor().findValueByName(entityTypeProtoEnum) != null
        && entityTypesSupportedByNGCore.contains(EntityTypeProtoEnum.valueOf(entityTypeProtoEnum));
  }

  private Boolean processDeleteAction(DeleteSetupUsageDTO deleteRequestDTO, EntityType referredEntityTypeFromChannel) {
    if (deleteRequestDTO == null) {
      return false;
    }
    if (deleteRequestDTO.getReferredEntityType() == EntityTypeProtoEnum.valueOf(referredEntityTypeFromChannel.name())) {
      throw new InvalidRequestException(
          String.format("Delete action for wrong entity: [%s] type published with wrong meta data map: [%s]",
              deleteRequestDTO.getReferredEntityType(), referredEntityTypeFromChannel));
    }
    return entitySetupUsageService.delete(deleteRequestDTO.getAccountIdentifier(),
        deleteRequestDTO.getReferredEntityFQN(), EntityType.valueOf(deleteRequestDTO.getReferredEntityType().name()),
        deleteRequestDTO.getReferredByEntityFQN(), referredEntityTypeFromChannel);
  }

  private Boolean processCreateAction(
      EntitySetupUsageCreateV2DTO setupUsageCreateDTO, @Nullable EntityType entityType) {
    if (setupUsageCreateDTO == null) {
      return false;
    }
    final List<EntitySetupUsage> entitySetupUsages =
        entitySetupUsageEventDTOToRestDTOMapper.toEntityDTO(setupUsageCreateDTO);
    return entitySetupUsageService.flushSave(entitySetupUsages, entityType,
        setupUsageCreateDTO.getDeleteOldReferredByRecords(), setupUsageCreateDTO.getAccountIdentifier());
  }

  private EntitySetupUsageCreateV2DTO getEntitySetupUsageCreateDTO(Message entitySetupUsageMessage) {
    EntitySetupUsageCreateV2DTO entitySetupUsageCreateDTO = null;
    try {
      entitySetupUsageCreateDTO = EntitySetupUsageCreateV2DTO.parseFrom(entitySetupUsageMessage.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntitySetupUsageCreateDTO   for key {}", entitySetupUsageMessage.getId(), e);
    }
    return entitySetupUsageCreateDTO;
  }

  private DeleteSetupUsageDTO getEntitySetupUsageDeleteDTO(Message entityDeleteMessage) {
    DeleteSetupUsageDTO deleteRequestDTO = null;
    try {
      deleteRequestDTO = DeleteSetupUsageDTO.parseFrom(entityDeleteMessage.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking DeleteSetupUsageDTO for key {}", entityDeleteMessage.getId(), e);
    }
    return deleteRequestDTO;
  }

  private GitEntityInfo getGitEntityInfoFromMessage(Message message) {
    Map<String, String> metaDataMap = message.getMessage() != null ? message.getMessage().getMetadataMap() : null;
    if (metaDataMap != null) {
      String repo = null, branch = null;
      if (metaDataMap.containsKey(REPO)) {
        repo = metaDataMap.get(REPO);
      }
      if (metaDataMap.containsKey(BRANCH)) {
        branch = metaDataMap.get(BRANCH);
      }
      if (isNotBlank(repo) && isNotBlank(branch)) {
        return GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).findDefaultFromOtherRepos(true).build();
      }
    }
    return null;
  }
}
