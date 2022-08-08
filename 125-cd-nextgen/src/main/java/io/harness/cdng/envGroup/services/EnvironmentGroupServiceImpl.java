/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.EntityType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.envGroup.EnvironmentGroupRepository;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

// TODO: Add transaction for outbox event and setup usages
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class EnvironmentGroupServiceImpl implements EnvironmentGroupService {
  private final EnvironmentGroupRepository environmentRepository;
  private final Producer setupUsagesEventProducer;
  private final IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  private final EntitySetupUsageService entitySetupUsageService;
  private final EnvironmentGroupServiceHelper environmentGroupServiceHelper;

  @Inject
  public EnvironmentGroupServiceImpl(EnvironmentGroupRepository environmentRepository,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer setupUsagesEventProducer,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper, EntitySetupUsageService entitySetupUsageService,
      EnvironmentGroupServiceHelper environmentGroupServiceHelper) {
    this.environmentRepository = environmentRepository;
    this.setupUsagesEventProducer = setupUsagesEventProducer;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
    this.entitySetupUsageService = entitySetupUsageService;
    this.environmentGroupServiceHelper = environmentGroupServiceHelper;
  }

  @Override
  public Optional<EnvironmentGroupEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupId, boolean deleted) {
    return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, envGroupId, !deleted);
  }

  @Override
  public EnvironmentGroupEntity create(EnvironmentGroupEntity entity) {
    EnvironmentGroupEntity savedEntity = environmentRepository.create(entity);
    setupUsagesForEnvironmentList(entity);
    return savedEntity;
  }

  @Override
  public Page<EnvironmentGroupEntity> list(
      Criteria criteria, Pageable pageRequest, String projectIdentifier, String orgIdentifier, String accountId) {
    return environmentRepository.list(criteria, pageRequest, projectIdentifier, orgIdentifier, accountId);
  }

  @Override
  public EnvironmentGroupEntity delete(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupId, Long version) {
    Optional<EnvironmentGroupEntity> envGroupEntity =
        get(accountId, orgIdentifier, projectIdentifier, envGroupId, false);
    if (!envGroupEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Environment Group [%s] under Project[%s], Organization [%s] doesn't exist.", envGroupId,
              projectIdentifier, orgIdentifier));
    }
    EnvironmentGroupEntity existingEntity = envGroupEntity.get();

    // Check the usages of environment group
    checkThatEnvironmentGroupIsNotReferredByOthers(existingEntity);

    if (version != null && !version.equals(existingEntity.getVersion())) {
      throw new InvalidRequestException(
          format("Environment Group [%s] under Project[%s], Organization [%s] is not on the correct version.",
              envGroupId, projectIdentifier, orgIdentifier));
    }
    EnvironmentGroupEntity entityWithDelete = existingEntity.withDeleted(true);
    try {
      boolean deleted = environmentRepository.deleteEnvGroup(entityWithDelete);
      if (deleted) {
        setupUsagesForEnvironmentList(entityWithDelete);
        return entityWithDelete;
      } else {
        throw new InvalidRequestException(
            format("Environment Group Set [%s] under Project[%s], Organization [%s] couldn't be deleted.", envGroupId,
                projectIdentifier, orgIdentifier));
      }
    } catch (Exception e) {
      log.error(String.format("Error while deleting Environment Group [%s]", envGroupId), e);
      throw new InvalidRequestException(
          String.format("Error while deleting Environment Group [%s]: %s", envGroupId, e.getMessage()));
    }
  }

  @Override
  public EnvironmentGroupEntity update(EnvironmentGroupEntity requestedEntity) {
    String accountId = requestedEntity.getAccountId();
    String orgId = requestedEntity.getOrgIdentifier();
    String projectId = requestedEntity.getProjectIdentifier();
    String envGroupId = requestedEntity.getIdentifier();

    Optional<EnvironmentGroupEntity> optionalEnvGroupEntity = get(accountId, orgId, projectId, envGroupId, false);
    if (!optionalEnvGroupEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Environment Group %s in project %s in organization %s is either deleted or was not created",
              envGroupId, projectId, orgId));
    }

    EnvironmentGroupEntity originalEntity = optionalEnvGroupEntity.get();
    if (originalEntity.getVersion() != null && !originalEntity.getVersion().equals(originalEntity.getVersion())) {
      throw new InvalidRequestException(format(
          "Environment Group [%s] under Project[%s], Organization [%s] is not on the correct version.",
          originalEntity.getIdentifier(), originalEntity.getProjectIdentifier(), originalEntity.getOrgIdentifier()));
    }

    EnvironmentGroupEntity updatedEntity = originalEntity.withName(requestedEntity.getName())
                                               .withDescription(requestedEntity.getDescription())
                                               .withLastModifiedAt(System.currentTimeMillis())
                                               .withColor(requestedEntity.getColor())
                                               .withEnvIdentifiers(requestedEntity.getEnvIdentifiers())
                                               .withTags(requestedEntity.getTags())
                                               .withYaml(requestedEntity.getYaml());
    final Criteria criteria = where(EnvironmentGroupKeys.accountId)
                                  .is(originalEntity.getAccountId())
                                  .and(EnvironmentGroupKeys.orgIdentifier)
                                  .is(originalEntity.getOrgIdentifier())
                                  .and(EnvironmentGroupKeys.projectIdentifier)
                                  .is(originalEntity.getProjectIdentifier())
                                  .and(EnvironmentGroupKeys.identifier)
                                  .is(originalEntity.getIdentifier())
                                  .and(EnvironmentGroupKeys.deleted)
                                  .is(false);
    EnvironmentGroupEntity savedEntity = environmentRepository.update(updatedEntity, originalEntity, criteria);
    setupUsagesForEnvironmentList(savedEntity);
    return savedEntity;
  }

  @Override
  public void deleteAllEnvGroupInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    Pageable pageRequest = PageRequest.of(
        0, 1000, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.lastModifiedAt));
    Page<EnvironmentGroupEntity> envGroupListPage =
        list(criteria, pageRequest, projectIdentifier, orgIdentifier, accountId);

    for (EnvironmentGroupEntity entity : envGroupListPage) {
      boolean deleted = environmentRepository.deleteEnvGroup(entity.withDeleted(true));
      if (deleted) {
        setupUsagesForEnvironmentList(entity);
      }
    }
  }

  @Override
  public Criteria formCriteria(String accountId, String orgIdentifier, String projectIdentifier, boolean deleted,
      String searchTerm, String filterIdentifier, EnvironmentGroupFilterPropertiesDTO filterProperties) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(EnvironmentGroupKeys.accountId).is(accountId);
    }
    if (isNotEmpty(orgIdentifier)) {
      criteria.and(EnvironmentGroupKeys.orgIdentifier).is(orgIdentifier);
    }
    if (isNotEmpty(projectIdentifier)) {
      criteria.and(EnvironmentGroupKeys.projectIdentifier).is(projectIdentifier);
    }

    criteria.and(EnvironmentGroupKeys.deleted).is(deleted);

    Criteria filterCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      environmentGroupServiceHelper.populateEnvGroupFilterUsingIdentifier(
          filterCriteria, accountId, orgIdentifier, projectIdentifier, filterIdentifier);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      environmentGroupServiceHelper.populateEnvGroupFilter(filterCriteria, filterProperties);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      try {
        searchCriteria.orOperator(where(EnvironmentGroupKeys.identifier)
                                      .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(EnvironmentGroupKeys.name)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(EnvironmentGroupKeys.tags + "." + NGTagKeys.key)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
            where(EnvironmentGroupKeys.tags + "." + NGTagKeys.value)
                .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      } catch (PatternSyntaxException pex) {
        throw new InvalidRequestException(pex.getMessage() + " Use \\\\ for special character", pex);
      }
    }

    criteria.andOperator(filterCriteria, searchCriteria);
    return criteria;
  }

  public void setupUsagesForEnvironmentList(EnvironmentGroupEntity envGroupEntity) {
    EntityDetailProtoDTO envGroupDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(envGroupEntity.getAccountId(),
                envGroupEntity.getOrgIdentifier(), envGroupEntity.getProjectIdentifier(),
                envGroupEntity.getIdentifier()))
            .setType(EntityTypeProtoEnum.ENVIRONMENT_GROUP)
            .setName(envGroupEntity.getName())
            .build();

    EntitySetupUsageCreateV2DTO.Builder entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                                 .setAccountIdentifier(envGroupEntity.getAccountId())
                                                                 .setReferredByEntity(envGroupDetails)
                                                                 .setDeleteOldReferredByRecords(true);

    // if envGroup is non  deleted, then add all the referred environment entities in env group
    if (!envGroupEntity.getDeleted()) {
      List<EntityDetailProtoDTO> referredEntities = getEnvReferredEntities(envGroupEntity);
      entityReferenceDTO.addAllReferredEntities(referredEntities);
    }

    setupUsagesEventProducer.send(
        Message.newBuilder()
            .putAllMetadata(ImmutableMap.of("accountId", envGroupEntity.getAccountId(),
                EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.ENVIRONMENT.name(),
                EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
            .setData(entityReferenceDTO.build().toByteString())
            .build());
  }

  public List<EntityDetailProtoDTO> getEnvReferredEntities(EnvironmentGroupEntity entity) {
    List<String> envIdentifiers = entity.getEnvIdentifiers();
    return envIdentifiers.stream()
        .map(env
            -> EntityDetailProtoDTO.newBuilder()
                   .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                         .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                         .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                         .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                         .setIdentifier(StringValue.of(env))
                                         .build())
                   .setType(EntityTypeProtoEnum.ENVIRONMENT)
                   .build())
        .collect(Collectors.toList());
  }

  public void checkThatEnvironmentGroupIsNotReferredByOthers(EnvironmentGroupEntity envGroupEntity) {
    List<EntityDetail> referredByEntities;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(envGroupEntity.getAccountId())
                                      .orgIdentifier(envGroupEntity.getOrgIdentifier())
                                      .projectIdentifier(envGroupEntity.getProjectIdentifier())
                                      .identifier(envGroupEntity.getIdentifier())
                                      .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(0, 10,
          envGroupEntity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT_GROUP, "");
      referredByEntities = entitySetupUsageDTOS.stream()
                               .map(EntitySetupUsageDTO::getReferredByEntity)
                               .collect(Collectors.toCollection(LinkedList::new));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          envGroupEntity.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the Environment Group as was not able to check entity reference records.");
    }
    if (EmptyPredicate.isNotEmpty(referredByEntities)) {
      throw new InvalidRequestException(String.format(
          "Could not delete the Environment Group %s as it is referenced by other entities - " + referredByEntities,
          envGroupEntity.getIdentifier()));
    }
  }
}
