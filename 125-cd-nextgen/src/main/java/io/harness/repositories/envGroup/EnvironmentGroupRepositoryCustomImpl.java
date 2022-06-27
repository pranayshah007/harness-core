/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.envGroup;

import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.events.EnvironmentGroupCreateEvent;
import io.harness.cdng.events.EnvironmentGroupDeleteEvent;
import io.harness.cdng.events.EnvironmentGroupUpdateEvent;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.outbox.api.OutboxService;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

/*
   TODO: need to do changes for environment group feature based on if this is available to or will be behind enforcement
   plan
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentGroupRepositoryCustomImpl implements EnvironmentGroupRepositoryCustom {
  private final OutboxService outboxService;
  private final EnvironmentService environmentService;
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<EnvironmentGroupEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupId, boolean notDeleted) {
    final Criteria criteria = Criteria.where(EnvironmentGroupEntity.EnvironmentGroupKeys.deleted)
                                  .is(!notDeleted)
                                  .and(EnvironmentGroupEntity.EnvironmentGroupKeys.projectIdentifier)
                                  .is(projectIdentifier)
                                  .and(EnvironmentGroupEntity.EnvironmentGroupKeys.orgIdentifier)
                                  .is(orgIdentifier)
                                  .and(EnvironmentGroupEntity.EnvironmentGroupKeys.accountId)
                                  .is(accountId)
                                  .and(EnvironmentGroupEntity.EnvironmentGroupKeys.identifier)
                                  .is(envGroupId);
    EnvironmentGroupEntity eg = mongoTemplate.findOne(new Query(criteria), EnvironmentGroupEntity.class);
    return Optional.ofNullable(eg);
  }

  @Override
  public EnvironmentGroupEntity create(@Valid EnvironmentGroupEntity environmentGroupEntity) {
    // validate EnvIdentifiers list
    if (!environmentGroupEntity.getEnvIdentifiers().isEmpty()) {
      validateNotExistentEnvIdentifiers(environmentGroupEntity);
    }
    try {
      final EnvironmentGroupEntity savedEntity = mongoTemplate.insert(environmentGroupEntity);
      outboxService.save(EnvironmentGroupCreateEvent.builder()
                             .accountIdentifier(environmentGroupEntity.getAccountIdentifier())
                             .orgIdentifier(environmentGroupEntity.getOrgIdentifier())
                             .projectIdentifier(environmentGroupEntity.getProjectIdentifier())
                             .environmentGroupEntity(environmentGroupEntity)
                             .build());
      return savedEntity;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format("Identifier %s already exists in organization id %s and project id %s",
              environmentGroupEntity.getIdentifier(), environmentGroupEntity.getProjectIdentifier(),
              environmentGroupEntity.getOrgIdentifier()),
          USER_SRE, ex);
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Error occurred while saving environment group - %s", e.getMessage()));
    }
  }

  @Override
  public Page<EnvironmentGroupEntity> list(
      Criteria criteria, Pageable pageRequest, String projectIdentifier, String orgIdentifier, String accountId) {
    final Query query = new Query(criteria).with(pageRequest);
    final List<EnvironmentGroupEntity> entities = mongoTemplate.find(query, EnvironmentGroupEntity.class);
    return PageableExecutionUtils.getPage(entities, pageRequest,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), EnvironmentGroupEntity.class));
  }

  @Override
  public boolean deleteEnvGroup(EnvironmentGroupEntity entityToDelete) {
    final DeleteResult remove = mongoTemplate.remove(entityToDelete);
    final boolean deleteSuccess = remove.wasAcknowledged() && remove.getDeletedCount() == 1;
    if (deleteSuccess) {
      outboxService.save(EnvironmentGroupDeleteEvent.builder()
                             .accountIdentifier(entityToDelete.getAccountIdentifier())
                             .orgIdentifier(entityToDelete.getOrgIdentifier())
                             .projectIdentifier(entityToDelete.getProjectIdentifier())
                             .environmentGroupEntity(entityToDelete)
                             .build());
    }
    return deleteSuccess;
  }

  @Override
  public EnvironmentGroupEntity update(
      EnvironmentGroupEntity updatedEntity, EnvironmentGroupEntity originalEntity, Criteria criteria) {
    // validate EnvIdentifiers list
    if (!updatedEntity.getEnvIdentifiers().isEmpty()) {
      validateNotExistentEnvIdentifiers(updatedEntity);
    }

    try {
      final EnvironmentGroupEntity updatedEntityFromDB =
          mongoTemplate.findAndReplace(new Query(criteria), updatedEntity, FindAndReplaceOptions.options().returnNew());
      if (updatedEntityFromDB == null) {
        throw new RuntimeException("requested environment group does not exist, failed to update");
      }
      outboxService.save(EnvironmentGroupUpdateEvent.builder()
                             .accountIdentifier(updatedEntityFromDB.getAccountIdentifier())
                             .orgIdentifier(updatedEntityFromDB.getOrgIdentifier())
                             .projectIdentifier(updatedEntityFromDB.getProjectIdentifier())
                             .newEnvironmentGroupEntity(updatedEntityFromDB)
                             .oldEnvironmentGroupEntity(originalEntity)
                             .build());
      return updatedEntityFromDB;
    } catch (Exception e) {
      throw new InvalidRequestException(String.format(
          "Error occurred while updating Environment Group %s - %s", updatedEntity.getIdentifier(), e.getMessage()));
    }
  }

  private void validateNotExistentEnvIdentifiers(EnvironmentGroupEntity environmentGroupEntity) {
    List<String> exitsEnvIdentifiers = environmentService.fetchesNonDeletedEnvIdentifiersFromList(
        environmentGroupEntity.getAccountId(), environmentGroupEntity.getOrgIdentifier(),
        environmentGroupEntity.getProjectIdentifier(), environmentGroupEntity.getEnvIdentifiers());
    List<String> nonExistentEnvIds = environmentGroupEntity.getEnvIdentifiers()
                                         .stream()
                                         .filter(id -> !exitsEnvIdentifiers.contains(id))
                                         .collect(Collectors.toList());
    if (!nonExistentEnvIds.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "These environment identifiers are either deleted or not present for this project %s", nonExistentEnvIds));
    }
  }
}
