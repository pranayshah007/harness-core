/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.setupusage.EnvironmentSetupUsagePublisher;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentEntitySetupUsageHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject private EnvironmentSetupUsagePublisher environmentSetupUsagePublisher;
  @Inject private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  private Set<EntityDetailProtoDTO> getAllReferredEntities(String rootName, Environment entity) {
    List<String> qualifiedNameList = Collections.singletonList(rootName);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), qualifiedNameList);

    final NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(entity);
    visitor.walkElementTree(ngEnvironmentConfig.getNgEnvironmentInfoConfig());
    return visitor.getEntityReferenceSet();
  }
  public void createSetupUsages(@NonNull Environment entity, Set<EntityDetailProtoDTO> referredEntities) {
    if (isNotEmpty(referredEntities)) {
      publishEntitySetupUsage(entity, referredEntities, null);
    }
  }
  public void updateSetupUsages(
      @NonNull Environment entity, Set<EntityDetailProtoDTO> referredEntities, Set<String> olderreferredEntitiestypes) {
    if (isEmpty(referredEntities)) {
      deleteSetupUsages(entity);
    } else {
      publishEntitySetupUsage(entity, referredEntities, olderreferredEntitiestypes);
    }
  }
  public void deleteSetupUsages(@NonNull Environment entity) {
    EntityDetailProtoDTO entityDetailProtoDTO = buildEnvironmentRefBasedEntityDetailProtoDTO(entity);
    environmentSetupUsagePublisher.deleteEnvironmentSetupUsages(entityDetailProtoDTO, entity.getAccountId());
  }
  public void deleteSetupUsagesWithOnlyIdentifierInfo(
      String environmentId, String accountId, String orgIdentifier, String projectIdentifier) {
    EntityDetailProtoDTO entityDetailProtoDTO =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
                accountId, orgIdentifier, projectIdentifier, environmentId))
            .setType(EntityTypeProtoEnum.ENVIRONMENT)
            .build();

    environmentSetupUsagePublisher.deleteEnvironmentSetupUsages(entityDetailProtoDTO, accountId);
  }

  private void publishEntitySetupUsage(
      Environment entity, Set<EntityDetailProtoDTO> referredEntities, Set<String> olderreferredEntitiestypes) {
    EntityDetailProtoDTO entityDetail = buildEnvironmentRefBasedEntityDetailProtoDTO(entity);
    environmentSetupUsagePublisher.publishEnvironmentEntitySetupUsage(
        entityDetail, referredEntities, entity.getAccountId(), olderreferredEntitiestypes);
  }
  private EntityDetailProtoDTO buildEnvironmentRefBasedEntityDetailProtoDTO(@NonNull Environment entity) {
    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
            entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier()))
        .setType(EntityTypeProtoEnum.ENVIRONMENT)
        .setName(entity.getName())
        .build();
  }
  public Set<EntityDetailProtoDTO> getAllReferredEntities(Environment entity) {
    final String ROOT_LEVEL_NAME = "environment";
    return getAllReferredEntities(ROOT_LEVEL_NAME, entity);
  }
}
