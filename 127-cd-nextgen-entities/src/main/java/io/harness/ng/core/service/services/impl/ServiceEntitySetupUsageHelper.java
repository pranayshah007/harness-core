package io.harness.ng.core.service.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.ng.core.setupusage.SetupUsageOwnerEntity;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ServiceEntitySetupUsageHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject private SetupUsageHelper setupUsageHelper;

  /**
   * Update setup usages for the current service entity
   */
  protected void updateSetupUsages(ServiceEntity entity) {
    final String ROOT_LEVEL_NAME = "service";
    final Set<EntityDetailProtoDTO> referredEntities = getAllReferredEntities(ROOT_LEVEL_NAME, entity);
    final SetupUsageOwnerEntity ownerEntity = getOwnerEntity(entity);
    if (isEmpty(referredEntities)) {
      setupUsageHelper.deleteSetupUsages(ownerEntity);
    } else {
      setupUsageHelper.publishEntitySetupUsage(ownerEntity, referredEntities);
    }
  }

  /**
   * Delete all setup usages where the 'referred by' is the current service entity
   */
  protected void deleteSetupUsages(ServiceEntity entity) {
    setupUsageHelper.deleteSetupUsages(getOwnerEntity(entity));
  }

  private Set<EntityDetailProtoDTO> getAllReferredEntities(String rootName, ServiceEntity entity) {
    List<String> qualifiedNameList = List.of(rootName);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), qualifiedNameList);
    NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity);
    visitor.walkElementTree(ngServiceConfig.getNgServiceV2InfoConfig());
    return visitor.getEntityReferenceSet();
  }

  private SetupUsageOwnerEntity getOwnerEntity(ServiceEntity entity) {
    return SetupUsageOwnerEntity.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .identifier(entity.getIdentifier())
        .name(entity.getName())
        .type(EntityTypeProtoEnum.SERVICE)
        .build();
  }
}
