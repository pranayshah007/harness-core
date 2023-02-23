/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.mapper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetail;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.helper.SetupUsageGitInfoPopulator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(DX)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
public class EntitySetupUsageEventDTOMapper {
  EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  SetupUsageDetailProtoToRestMapper setupUsageDetailProtoToRestMapper;
  SetupUsageGitInfoPopulator setupUsageGitInfoPopulator;

  public EntitySetupUsageDTO toRestDTO(EntitySetupUsageCreateDTO setupUsageEventsDTO) {
    EntityDetail referredEntity =
        entityDetailProtoToRestMapper.createEntityDetailDTO(setupUsageEventsDTO.getReferredEntity());
    EntityDetail referredByEntity =
        entityDetailProtoToRestMapper.createEntityDetailDTO(setupUsageEventsDTO.getReferredByEntity());

    return EntitySetupUsageDTO.builder()
        .accountIdentifier(setupUsageEventsDTO.getAccountIdentifier())
        .createdAt(setupUsageEventsDTO.getCreatedAt())
        .referredByEntity(referredByEntity)
        .referredEntity(referredEntity)
        .build();
  }

  public List<EntitySetupUsage> toEntityDTO(EntitySetupUsageCreateV2DTO setupUsageEventsDTO) {
    final EntityDetail referredByEntity =
        entityDetailProtoToRestMapper.createEntityDetailDTO(setupUsageEventsDTO.getReferredByEntity());
    final List<EntityDetail> referredEntities =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(setupUsageEventsDTO.getReferredEntitiesList());
    List<EntitySetupUsage> setupUsages = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(referredEntities)) {
      referredEntities.forEach(referredEntity
          -> setupUsages.add(EntitySetupUsage.builder()
                                 .accountIdentifier(setupUsageEventsDTO.getAccountIdentifier())
                                 .referredByEntity(referredByEntity)
                                 .referredByEntityFQN(referredByEntity.getEntityRef().getFullyQualifiedName())
                                 .referredByEntityType(referredByEntity.getType().toString())
                                 .referredEntityFQN(referredEntity.getEntityRef().getFullyQualifiedName())
                                 .referredEntityType(referredEntity.getType().toString())
                                 .referredEntity(referredEntity)
                                 .build()));
    }

    List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailWithSetupUsageDetailProtoDTOs =
        setupUsageEventsDTO.getReferredEntityWithSetupUsageDetailList();
    if (EmptyPredicate.isNotEmpty(entityDetailWithSetupUsageDetailProtoDTOs)) {
      entityDetailWithSetupUsageDetailProtoDTOs.forEach(entityDetailWithSetupUsageDetailProtoDTO -> {
        SetupUsageDetail setupUsageDetail =
            setupUsageDetailProtoToRestMapper.toRestDTO(entityDetailWithSetupUsageDetailProtoDTO);
        EntityDetail referredEntity = entityDetailProtoToRestMapper.createEntityDetailDTO(
            entityDetailWithSetupUsageDetailProtoDTO.getReferredEntity());
        setupUsages.add(EntitySetupUsage.builder()
                            .accountIdentifier(setupUsageEventsDTO.getAccountIdentifier())
                            .referredByEntity(referredByEntity)
                            .referredByEntityFQN(referredByEntity.getEntityRef().getFullyQualifiedName())
                            .referredByEntityType(referredByEntity.getType().toString())
                            .referredEntityFQN(referredEntity.getEntityRef().getFullyQualifiedName())
                            .referredEntityType(referredEntity.getType().toString())
                            .referredEntity(referredEntity)
                            .detail(setupUsageDetail)
                            .build());
      });
    }

    if (isEmpty(setupUsages)) {
      setupUsages.add(EntitySetupUsage.builder()
                          .accountIdentifier(setupUsageEventsDTO.getAccountIdentifier())
                          .referredByEntity(referredByEntity)
                          .referredByEntityFQN(referredByEntity.getEntityRef().getFullyQualifiedName())
                          .referredByEntityType(referredByEntity.getType().toString())
                          .build());
    }
    setupUsageGitInfoPopulator.populateRepoAndBranchForSetupUsageEntities(setupUsages);
    populateTheRepoAndBranchAtTopLevel(setupUsages);
    return setupUsages;
  }

  private void populateTheRepoAndBranchAtTopLevel(List<EntitySetupUsage> setupUsages) {
    if (isEmpty(setupUsages)) {
      return;
    }
    for (EntitySetupUsage setupUsage : setupUsages) {
      EntityDetail referredByEntity = setupUsage.getReferredByEntity();
      EntityReference referredByEntityRef = referredByEntity.getEntityRef();
      setupUsage.setReferredByEntityBranch(referredByEntityRef.getBranch());
      setupUsage.setReferredByEntityRepoIdentifier(referredByEntityRef.getRepoIdentifier());
      if (referredByEntityRef.isDefault() == null) {
        setupUsage.setReferredByEntityIsDefault(true);
        referredByEntityRef.setIsDefault(true);
      } else {
        setupUsage.setReferredByEntityIsDefault(referredByEntityRef.isDefault());
      }

      EntityDetail referredEntity = setupUsage.getReferredEntity();
      if (referredEntity != null) {
        EntityReference referredEntityRef = referredEntity.getEntityRef();
        setupUsage.setReferredEntityBranch(referredEntityRef.getBranch());
        setupUsage.setReferredEntityRepoIdentifier(referredEntityRef.getRepoIdentifier());
        if (referredEntityRef.isDefault() == null) {
          setupUsage.setReferredEntityIsDefault(true);
          referredEntityRef.setIsDefault(true);
        } else {
          setupUsage.setReferredEntityIsDefault(referredEntityRef.isDefault());
        }
      }
    }
  }
}
