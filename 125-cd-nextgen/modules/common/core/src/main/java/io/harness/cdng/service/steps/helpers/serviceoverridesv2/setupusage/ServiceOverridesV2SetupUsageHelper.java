/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.setupusage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.ng.core.setupusage.SetupUsageOwnerEntity;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceOverridesV2SetupUsageHelper {
  @Inject SetupUsageHelper setupUsageHelper;
  @Inject SimpleVisitorFactory simpleVisitorFactory;
  private static final String ROOT_LEVEL_NAME = "overrides";

  public void createSetupUsages(@NonNull NGServiceOverridesEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    final SetupUsageOwnerEntity ownerEntity = getOwnerEntity(entity);
    if (isNotEmpty(referredEntities)) {
      setupUsageHelper.publishOverridesEntitySetupUsage(ownerEntity, referredEntities, entity.getType().name());
    }
  }

  /**
   * Update setup usages for the current override entity with referred entities
   */
  public void updateSetupUsages(NGServiceOverridesEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    final SetupUsageOwnerEntity ownerEntity = getOwnerEntity(entity);
    if (isEmpty(referredEntities)) {
      setupUsageHelper.deleteEntitySetupUsages(ownerEntity);
    } else {
      setupUsageHelper.publishOverridesEntitySetupUsage(ownerEntity, referredEntities, entity.getType().name());
    }
  }

  /**
   * Delete all setup usages where the 'referred by' is the current override entity
   */
  public void deleteSetupUsages(NGServiceOverridesEntity entity) {
    setupUsageHelper.deleteEntitySetupUsages(getOwnerEntity(entity));
  }

  public Set<EntityDetailProtoDTO> getAllReferredEntities(NGServiceOverridesEntity entity) {
    return getAllReferredEntities(ROOT_LEVEL_NAME, entity);
  }

  private SetupUsageOwnerEntity getOwnerEntity(NGServiceOverridesEntity entity) {
    return SetupUsageOwnerEntity.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .identifier(entity.getIdentifier())
        .name(entity.getIdentifier())
        .type(EntityTypeProtoEnum.OVERRIDES)
        .build();
  }

  private Set<EntityDetailProtoDTO> getAllReferredEntities(String rootName, NGServiceOverridesEntity entity) {
    List<String> qualifiedNameList = Collections.singletonList(rootName);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), qualifiedNameList);
    final NGServiceOverrideConfigV2 serviceOverridesConfigV2 = NGServiceOverrideConfigV2.builder()
                                                                   .identifier(entity.getIdentifier())
                                                                   .serviceRef(entity.getServiceRef())
                                                                   .type(entity.getType())
                                                                   .spec(entity.getSpec())
                                                                   .environmentRef(entity.getEnvironmentRef())
                                                                   .infraId(entity.getInfraIdentifier())
                                                                   .build();
    visitor.walkElementTree(serviceOverridesConfigV2);
    return visitor.getEntityReferenceSet();
  }
}
