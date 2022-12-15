/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.setupusage.InfraDefinitionRefProtoDTOHelper.createInfraDefinitionReferenceProtoDTO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.NonNull;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class InfrastructureEntitySetupUsageHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject private SetupUsageHelper setupUsageHelper;

  /**
   * Update setup usages for the current infrastructure entity
   */
  public void updateSetupUsages(@NonNull InfrastructureEntity entity) {
    final String ROOT_LEVEL_NAME = "infrastructureDefinition";
    final Set<EntityDetailProtoDTO> referredEntities = getAllReferredEntities(ROOT_LEVEL_NAME, entity);
    if (isEmpty(referredEntities)) {
      deleteSetupUsages(entity);
    } else {
      publishEntitySetupUsage(entity, referredEntities);
    }
  }

  public void deleteSetupUsages(@NonNull InfrastructureEntity entity) {
    EntityDetailProtoDTO entityDetailProtoDTO = buildInfraDefRefBasedEntityDetailProtoDTO(entity);
    setupUsageHelper.deleteInfraSetupUsages(entityDetailProtoDTO, entity.getAccountId());
  }

  private void publishEntitySetupUsage(InfrastructureEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    EntityDetailProtoDTO entityDetail = buildInfraDefRefBasedEntityDetailProtoDTO(entity);
    setupUsageHelper.publishInfraEntitySetupUsage(entityDetail, referredEntities, entity.getAccountId());
  }

  private EntityDetailProtoDTO buildInfraDefRefBasedEntityDetailProtoDTO(@NonNull InfrastructureEntity entity) {
    return EntityDetailProtoDTO.newBuilder()
        .setInfraDefRef(createInfraDefinitionReferenceProtoDTO(entity.getAccountId(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier(), entity.getEnvIdentifier(), entity.getIdentifier()))
        .setType(EntityTypeProtoEnum.INFRASTRUCTURE)
        .setName(entity.getName())
        .build();
  }

  private Set<EntityDetailProtoDTO> getAllReferredEntities(String rootName, InfrastructureEntity entity) {
    List<String> qualifiedNameList = Collections.singletonList(rootName);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), qualifiedNameList);
    final InfrastructureConfig ngInfrastructureConfig = InfrastructureEntityConfigMapper.toInfrastructureConfig(entity);
    visitor.walkElementTree(ngInfrastructureConfig.getInfrastructureDefinitionConfig());
    return visitor.getEntityReferenceSet();
  }
}
