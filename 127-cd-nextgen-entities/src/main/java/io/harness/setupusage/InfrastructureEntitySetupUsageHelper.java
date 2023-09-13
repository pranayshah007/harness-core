/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.setupusage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.setupusage.InfraDefinitionRefProtoDTOHelper.createInfraDefinitionReferenceProtoDTO;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.InfraDefReference;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Singleton
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class InfrastructureEntitySetupUsageHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject private SetupUsageHelper setupUsageHelper;
  @Inject private EnvironmentService environmentService;

  @Inject private EntitySetupUsageService entitySetupUsageService;

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

  /**
   * Update setup usages for the current infrastructure entity with referred entities already provided
   */
  public void updateSetupUsages(@NonNull InfrastructureEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
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

  /**
   * Create setup usages for the current infrastructure entity if referred entities are present
   */
  public void createSetupUsages(@NonNull InfrastructureEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    if (isNotEmpty(referredEntities)) {
      publishEntitySetupUsage(entity, referredEntities);
    }
  }

  public void checkThatInfraIsNotReferredByOthers(InfrastructureEntity infrastructure) {
    List<EntityDetail> referredByEntities;
    InfraDefReference identifierRef = InfraDefReference.builder()
                                          .accountIdentifier(infrastructure.getAccountId())
                                          .orgIdentifier(infrastructure.getOrgIdentifier())
                                          .projectIdentifier(infrastructure.getProjectIdentifier())
                                          .envIdentifier(infrastructure.getEnvIdentifier())
                                          .identifier(infrastructure.getIdentifier())
                                          .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          0, 10, infrastructure.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.INFRASTRUCTURE, "");
      referredByEntities = entitySetupUsageDTOS != null ? entitySetupUsageDTOS.stream()
                                                              .map(EntitySetupUsageDTO::getReferredByEntity)
                                                              .collect(Collectors.toCollection(LinkedList::new))
                                                        : List.of();
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          infrastructure.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the infrastructure as was not able to check entity reference records.");
    }
    if (isNotEmpty(referredByEntities)) {
      throw new ReferencedEntityException(String.format(
          "The infrastructure %s cannot be deleted because it is being referenced in %d %s. To delete your infrastructure, please remove the reference infrastructure from these entities.",
          infrastructure.getIdentifier(), referredByEntities.size(),
          referredByEntities.size() > 1 ? "entities" : "entity"));
    }
  }

  private void publishEntitySetupUsage(InfrastructureEntity entity, Set<EntityDetailProtoDTO> referredEntities) {
    EntityDetailProtoDTO entityDetail = buildInfraDefRefBasedEntityDetailProtoDTO(entity);
    setupUsageHelper.publishInfraEntitySetupUsage(entityDetail, referredEntities, entity.getAccountId());
  }

  private EntityDetailProtoDTO buildInfraDefRefBasedEntityDetailProtoDTO(@NonNull InfrastructureEntity entity) {
    Optional<Environment> environment = environmentService.get(entity.getAccountId(), entity.getOrgIdentifier(),
        entity.getProjectIdentifier(), entity.getEnvIdentifier(), false);
    return EntityDetailProtoDTO.newBuilder()
        .setInfraDefRef(createInfraDefinitionReferenceProtoDTO(entity.getAccountId(), entity.getOrgIdentifier(),
            entity.getProjectIdentifier(), entity.getEnvIdentifier(), entity.getIdentifier(),
            environment.isPresent() ? environment.get().getName() : StringUtils.EMPTY))
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

  public Set<EntityDetailProtoDTO> getAllReferredEntities(InfrastructureEntity entity) {
    final String ROOT_LEVEL_NAME = "infrastructureDefinition";
    return getAllReferredEntities(ROOT_LEVEL_NAME, entity);
  }
}
