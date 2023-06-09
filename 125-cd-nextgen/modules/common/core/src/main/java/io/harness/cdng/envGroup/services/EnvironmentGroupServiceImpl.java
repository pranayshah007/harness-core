/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.ENVIRONMENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.EntityType;
import io.harness.NGResourceFilterConstants;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.cdng.events.EnvironmentGroupDeleteEvent;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorMessageConstants;
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
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.envGroup.EnvironmentGroupRepository;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;
// TODO: Add transaction for outbox event and setup usages
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class EnvironmentGroupServiceImpl implements EnvironmentGroupService {
  private final EnvironmentGroupRepository environmentRepository;
  private final Producer setupUsagesEventProducer;
  private final EntitySetupUsageService entitySetupUsageService;
  private final EnvironmentGroupServiceHelper environmentGroupServiceHelper;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final OutboxService outboxService;
  private final AccountClient accountClient;
  private final NGSettingsClient settingsClient;

  @Inject
  public EnvironmentGroupServiceImpl(EnvironmentGroupRepository environmentRepository,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer setupUsagesEventProducer,
      EntitySetupUsageService entitySetupUsageService, EnvironmentGroupServiceHelper environmentGroupServiceHelper,
      TransactionTemplate transactionTemplate, OutboxService outboxService, AccountClient accountClient,
      NGSettingsClient settingsClient) {
    this.environmentRepository = environmentRepository;
    this.setupUsagesEventProducer = setupUsagesEventProducer;
    this.entitySetupUsageService = entitySetupUsageService;
    this.environmentGroupServiceHelper = environmentGroupServiceHelper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.accountClient = accountClient;
    this.settingsClient = settingsClient;
  }

  @Override
  public Optional<EnvironmentGroupEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupRef, boolean deleted) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");

    return getEnvironmentGroupByRef(accountId, orgIdentifier, projectIdentifier, envGroupRef, deleted);
  }

  private Optional<EnvironmentGroupEntity> getEnvironmentGroupByRef(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupRef, boolean deleted) {
    String[] envGroupRefSplit = StringUtils.split(envGroupRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envGroupRefSplit == null || envGroupRefSplit.length == 1) {
      return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          accountId, orgIdentifier, projectIdentifier, envGroupRef, !deleted);
    } else {
      IdentifierRef envGroupIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envGroupRef, accountId, orgIdentifier, projectIdentifier);
      return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          envGroupIdentifierRef.getAccountIdentifier(), envGroupIdentifierRef.getOrgIdentifier(),
          envGroupIdentifierRef.getProjectIdentifier(), envGroupIdentifierRef.getIdentifier(), !deleted);
    }
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
  public EnvironmentGroupEntity delete(String accountId, String orgIdentifier, String projectIdentifier,
      String envGroupId, Long version, boolean forceDelete) {
    if (forceDelete && !isForceDeleteEnabled(accountId)) {
      throw new InvalidRequestException(ErrorMessageConstants.FORCE_DELETE_SETTING_NOT_ENABLED, USER);
    }
    Optional<EnvironmentGroupEntity> envGroupEntity =
        get(accountId, orgIdentifier, projectIdentifier, envGroupId, false);
    if (envGroupEntity.isEmpty()) {
      throw new InvalidRequestException(
          format("Environment Group [%s] under Project[%s], Organization [%s] doesn't exist.", envGroupId,
              projectIdentifier, orgIdentifier));
    }
    EnvironmentGroupEntity existingEntity = envGroupEntity.get();

    if (!forceDelete) {
      // Check the usages of environment group
      checkThatEnvironmentGroupIsNotReferredByOthers(existingEntity);
    }

    if (version != null && !version.equals(existingEntity.getVersion())) {
      throw new InvalidRequestException(
          format("Environment Group [%s] under Project[%s], Organization [%s] is not on the correct version.",
              envGroupId, projectIdentifier, orgIdentifier));
    }
    EnvironmentGroupEntity entityWithDelete = existingEntity.withDeleted(true);
    try {
      boolean deleted = environmentRepository.deleteEnvGroup(entityWithDelete, forceDelete);
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
    if (optionalEnvGroupEntity.isEmpty()) {
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
    // originalEntity will have identifier, not ref
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
  public boolean deleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");

    return deleteInternal(accountId, orgIdentifier, projectIdentifier);
  }

  @Override
  public boolean deleteAllInOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgIdentifier must be present");

    return deleteInternal(accountId, orgIdentifier, null);
  }

  private boolean deleteInternal(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    Pageable pageRequest = PageRequest.of(
        0, 1000, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.lastModifiedAt));
    Page<EnvironmentGroupEntity> environmentGroupEntities =
        list(criteria, pageRequest, projectIdentifier, orgIdentifier, accountId);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = environmentRepository.delete(criteria);

      if (deleteResult.wasAcknowledged()) {
        for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
          setupUsagesForEnvironmentList(environmentGroupEntity);
          outboxService.save(EnvironmentGroupDeleteEvent.builder()
                                 .accountIdentifier(environmentGroupEntity.getAccountIdentifier())
                                 .orgIdentifier(environmentGroupEntity.getOrgIdentifier())
                                 .projectIdentifier(environmentGroupEntity.getProjectIdentifier())
                                 .environmentGroupEntity(environmentGroupEntity)
                                 .build());
        }
      } else {
        log.error(getScopedErrorForCascadeDeletion(orgIdentifier, projectIdentifier));
      }

      return deleteResult.wasAcknowledged();
    }));
  }

  private String getScopedErrorForCascadeDeletion(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return String.format("Environment Groups under Project[%s], Organization [%s] couldn't be deleted.",
          projectIdentifier, orgIdentifier);
    }
    return String.format("Environment Groups under Organization: [%s] couldn't be deleted.", orgIdentifier);
  }

  @Override
  public Criteria formCriteria(String accountId, String orgIdentifier, String projectIdentifier, boolean deleted,
      String searchTerm, String filterIdentifier, EnvironmentGroupFilterPropertiesDTO filterProperties,
      boolean includeAllEnvGroupsAccessibleAtScope) {
    Criteria criteria = new Criteria();
    List<Criteria> andCriteriaList = new ArrayList<>();
    if (isNotEmpty(accountId)) {
      criteria.and(EnvironmentGroupKeys.accountId).is(accountId);
      Criteria includeAllEnvGroupsCriteria = null;
      if (includeAllEnvGroupsAccessibleAtScope) {
        includeAllEnvGroupsCriteria = getCriteriaToReturnAllAccessibleEnvGroups(orgIdentifier, projectIdentifier);
      } else {
        criteria.and(EnvironmentGroupKeys.orgIdentifier).is(orgIdentifier);
        criteria.and(EnvironmentGroupKeys.projectIdentifier).is(projectIdentifier);
      }

      if (includeAllEnvGroupsCriteria != null) {
        andCriteriaList.add(includeAllEnvGroupsCriteria);
      }
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
    andCriteriaList.add(filterCriteria);
    andCriteriaList.add(searchCriteria);
    criteria.andOperator(andCriteriaList.toArray(new Criteria[0]));
    return criteria;
  }

  private Criteria getCriteriaToReturnAllAccessibleEnvGroups(String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    Criteria accountCriteria = Criteria.where(EnvironmentGroupKeys.orgIdentifier)
                                   .is(null)
                                   .and(EnvironmentGroupKeys.projectIdentifier)
                                   .is(null);
    Criteria orgCriteria = Criteria.where(EnvironmentGroupKeys.orgIdentifier)
                               .is(orgIdentifier)
                               .and(EnvironmentGroupKeys.projectIdentifier)
                               .is(null);
    Criteria projectCriteria = Criteria.where(EnvironmentGroupKeys.orgIdentifier)
                                   .is(orgIdentifier)
                                   .and(EnvironmentGroupKeys.projectIdentifier)
                                   .is(projectIdentifier);

    if (isNotBlank(projectIdentifier)) {
      return criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      return criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      return criteria.orOperator(accountCriteria);
    }
  }

  public void setupUsagesForEnvironmentList(EnvironmentGroupEntity envGroupEntity) {
    EntityDetailProtoDTO envGroupDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(envGroupEntity.getAccountId(),
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
                EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, ENVIRONMENT.name(),
                EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
            .setData(entityReferenceDTO.build().toByteString())
            .build());
  }

  public List<EntityDetailProtoDTO> getEnvReferredEntities(EnvironmentGroupEntity entity) {
    List<String> envIdentifiers = entity.getEnvIdentifiers();
    return envIdentifiers.stream()
        .map(env
            -> buildEntityDetailProtoDtoForEnvGroup(
                entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), env))
        .collect(Collectors.toList());
  }

  private EntityDetailProtoDTO buildEntityDetailProtoDtoForEnvGroup(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    IdentifierRefProtoDTO.Builder identifierRefProtoDTO =
        IdentifierRefProtoDTO.newBuilder().setAccountIdentifier(StringValue.of(accountId));
    if (isNotEmpty(envIdentifier)) {
      identifierRefProtoDTO.setIdentifier(StringValue.of(envIdentifier));
    }
    if (isNotEmpty(orgIdentifier)) {
      identifierRefProtoDTO.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotEmpty(projectIdentifier)) {
      identifierRefProtoDTO.setProjectIdentifier(StringValue.of(projectIdentifier));
    }

    return EntityDetailProtoDTO.newBuilder()
        .setType(ENVIRONMENT)
        .setIdentifierRef(identifierRefProtoDTO.build())
        .build();
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
      throw new ReferencedEntityException(String.format(
          "Could not delete the Environment Group %s as it is referenced by other entities - " + referredByEntities,
          envGroupEntity.getIdentifier()));
    }
  }
  private boolean isForceDeleteEnabled(String accountIdentifier) {
    return isForceDeleteFFEnabledViaSettings(accountIdentifier);
  }

  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
}
