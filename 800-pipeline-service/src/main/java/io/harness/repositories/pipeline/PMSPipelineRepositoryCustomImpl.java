/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.common.helper.EntityDistinctElementHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.mappers.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.springdata.TransactionHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@GitSyncableHarnessRepo
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineRepositoryCustomImpl implements PMSPipelineRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final GitAwarePersistence gitAwarePersistence;
  private final TransactionHelper transactionHelper;
  private final PipelineMetadataService pipelineMetadataService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final OutboxService outboxService;
  private final GitSyncSdkService gitSyncSdkService;

  @Override
  public Page<PipelineEntity> findAll(Criteria criteria, Pageable pageable, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, boolean getDistinctFromBranches) {
    if (getDistinctFromBranches) {
      return EntityDistinctElementHelper.getDistinctElementPage(mongoTemplate, criteria, pageable, PipelineEntity.class,
          PipelineEntityKeys.accountId, PipelineEntityKeys.orgIdentifier, PipelineEntityKeys.projectIdentifier,
          PipelineEntityKeys.identifier);
    }
    List<PipelineEntity> pipelineEntities = gitAwarePersistence.find(
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, PipelineEntity.class);
    return PageableExecutionUtils.getPage(pipelineEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, PipelineEntity.class));
  }

  @Override
  public Long countAllPipelines(Criteria criteria) {
    Query query = new Query().addCriteria(criteria);
    return mongoTemplate.count(query, PipelineEntity.class);
  }

  @Override
  public PipelineEntity saveForOldGitSync(PipelineEntity pipelineToSave) {
    String accountIdentifier = pipelineToSave.getAccountIdentifier();
    String orgIdentifier = pipelineToSave.getOrgIdentifier();
    String projectIdentifier = pipelineToSave.getProjectIdentifier();

    return transactionHelper.performTransaction(() -> {
      PipelineEntity savedEntity = gitAwarePersistence.save(
          pipelineToSave, pipelineToSave.getYaml(), ChangeType.ADD, PipelineEntity.class, null);
      outboxService.save(
          new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipelineToSave, true));
      checkForMetadataAndSaveIfAbsent(savedEntity);
      return savedEntity;
    });
  }

  @Override
  public PipelineEntity save(PipelineEntity pipelineToSave) {
    String accountIdentifier = pipelineToSave.getAccountIdentifier();
    String orgIdentifier = pipelineToSave.getOrgIdentifier();
    String projectIdentifier = pipelineToSave.getProjectIdentifier();
    Supplier<OutboxEvent> supplier = ()
        -> outboxService.save(
            new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, pipelineToSave));
    return transactionHelper.performTransaction(() -> savePipelineOperations(pipelineToSave, supplier));
  }

  @VisibleForTesting
  PipelineEntity savePipelineOperations(PipelineEntity pipelineToSave, Supplier<OutboxEvent> supplier) {
    PipelineEntity savedEntity = savePipelineEntity(pipelineToSave, supplier);
    checkForMetadataAndSaveIfAbsent(savedEntity);
    return savedEntity;
  }

  @VisibleForTesting
  PipelineEntity savePipelineEntity(PipelineEntity pipelineToSave, Supplier<OutboxEvent> supplier) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo == null || gitEntityInfo.getStoreType().equals(StoreType.INLINE)) {
      pipelineToSave.setStoreType(StoreType.INLINE);
      PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
      if (supplier != null) {
        supplier.get();
      }
      return savedPipelineEntity;
    }
    if (gitSyncSdkService.isGitSimplificationEnabled(pipelineToSave.getAccountIdentifier(),
            pipelineToSave.getOrgIdentifier(), pipelineToSave.getProjectIdentifier())) {
      Scope scope = Scope.builder()
                        .accountIdentifier(pipelineToSave.getAccountIdentifier())
                        .orgIdentifier(pipelineToSave.getOrgIdentifier())
                        .projectIdentifier(pipelineToSave.getProjectIdentifier())
                        .build();
      String yamlToPush = pipelineToSave.getYaml();
      pipelineToSave.setYaml("");
      pipelineToSave.setStoreType(StoreType.REMOTE);
      pipelineToSave.setConnectorRef(gitEntityInfo.getConnectorRef());
      pipelineToSave.setRepo(gitEntityInfo.getRepoName());
      pipelineToSave.setFilePath(gitEntityInfo.getFilePath());

      gitAwareEntityHelper.createEntityOnGit(pipelineToSave, yamlToPush, scope);
    } else {
      log.info(String.format(
          "Marking storeType as INLINE for Pipeline with ID [%s] because Git simplification was not enabled for Project [%s] in Account [%s]",
          pipelineToSave.getIdentifier(), pipelineToSave.getProjectIdentifier(),
          pipelineToSave.getAccountIdentifier()));
      pipelineToSave.setStoreType(StoreType.INLINE);
    }
    supplier.get();
    return mongoTemplate.save(pipelineToSave);
  }

  void checkForMetadataAndSaveIfAbsent(PipelineEntity savedEntity) {
    // checking if PipelineMetadata exists or not, if exists don't re-save the entity, as only one entry across git
    // repos should be there.
    Optional<PipelineMetadataV2> metadataOptional =
        pipelineMetadataService.getMetadata(savedEntity.getAccountIdentifier(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity.getIdentifier());
    if (!metadataOptional.isPresent()) {
      PipelineMetadataV2 metadata = PipelineMetadataV2.builder()
                                        .accountIdentifier(savedEntity.getAccountIdentifier())
                                        .orgIdentifier(savedEntity.getOrgIdentifier())
                                        .projectIdentifier(savedEntity.getProjectIdentifier())
                                        .runSequence(0)
                                        .identifier(savedEntity.getIdentifier())
                                        .build();
      pipelineMetadataService.save(metadata);
    }
  }

  @Override
  public Optional<PipelineEntity> findForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                            .is(!notDeleted)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.accountId)
                            .is(accountId);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
  }

  @Override
  public Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                            .is(!notDeleted)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.accountId)
                            .is(accountId);
    Query query = new Query(criteria);
    PipelineEntity savedEntity = mongoTemplate.findOne(query, PipelineEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      // fetch yaml from git
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      savedEntity = (PipelineEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
          Scope.builder()
              .accountIdentifier(accountId)
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .build(),
          GitContextRequestParams.builder()
              .branchName(gitEntityInfo.getBranch())
              .connectorRef(savedEntity.getConnectorRef())
              .filePath(savedEntity.getFilePath())
              .repoName(savedEntity.getRepo())
              .build(),
          Collections.emptyMap());
    }

    return Optional.of(savedEntity);
  }

  @Override
  public PipelineEntity updatePipelineYamlForOldGitSync(
      PipelineEntity pipelineToUpdate, PipelineEntity oldPipelineEntity, ChangeType changeType) {
    String accountIdentifier = pipelineToUpdate.getAccountIdentifier();
    String orgIdentifier = pipelineToUpdate.getOrgIdentifier();
    String projectIdentifier = pipelineToUpdate.getProjectIdentifier();
    PipelineEntity updatedEntity =
        gitAwarePersistence.save(pipelineToUpdate, pipelineToUpdate.getYaml(), changeType, PipelineEntity.class, null);
    if (updatedEntity != null) {
      outboxService.save(new PipelineUpdateEvent(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineToUpdate, oldPipelineEntity, true));
    }
    return updatedEntity;
  }

  @Override
  public PipelineEntity updatePipelineYaml(PipelineEntity pipelineToUpdate) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                            .is(false)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineToUpdate.getIdentifier())
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(pipelineToUpdate.getProjectIdentifier())
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(pipelineToUpdate.getOrgIdentifier())
                            .and(PipelineEntityKeys.accountId)
                            .is(pipelineToUpdate.getAccountId());
    Query query = new Query(criteria);
    long timeOfUpdate = System.currentTimeMillis();
    Update updateOperations = PMSPipelineFilterHelper.getUpdateOperations(pipelineToUpdate, timeOfUpdate);
    PipelineEntity oldEntityFromDB = mongoTemplate.findAndModify(
        query, updateOperations, new FindAndModifyOptions().returnNew(false), PipelineEntity.class);
    if (oldEntityFromDB == null) {
      return null;
    }
    PipelineEntity updatedPipelineEntity =
        PMSPipelineFilterHelper.updateFieldsInDBEntry(oldEntityFromDB, pipelineToUpdate, timeOfUpdate);
    if (updatedPipelineEntity.getStoreType() == null) {
      // onboarding old entities as INLINE
      Update updateOperationsForOnboardingToInline = PMSPipelineFilterHelper.getUpdateOperationsForOnboardingToInline();
      updatedPipelineEntity = mongoTemplate.findAndModify(query, updateOperationsForOnboardingToInline,
          new FindAndModifyOptions().returnNew(true), PipelineEntity.class);
    }
    if (updatedPipelineEntity == null) {
      return null;
    }
    if (updatedPipelineEntity.getStoreType() == StoreType.INLINE) {
      outboxService.save(
          new PipelineUpdateEvent(pipelineToUpdate.getAccountIdentifier(), pipelineToUpdate.getOrgIdentifier(),
              pipelineToUpdate.getProjectIdentifier(), updatedPipelineEntity, oldEntityFromDB));
      return updatedPipelineEntity;
    }
    if (gitSyncSdkService.isGitSimplificationEnabled(pipelineToUpdate.getAccountIdentifier(),
            pipelineToUpdate.getOrgIdentifier(), pipelineToUpdate.getProjectIdentifier())) {
      Scope scope = Scope.builder()
                        .accountIdentifier(updatedPipelineEntity.getAccountIdentifier())
                        .orgIdentifier(updatedPipelineEntity.getOrgIdentifier())
                        .projectIdentifier(updatedPipelineEntity.getProjectIdentifier())
                        .build();
      gitAwareEntityHelper.updateEntityOnGit(updatedPipelineEntity, pipelineToUpdate.getYaml(), scope);
    }

    outboxService.save(
        new PipelineUpdateEvent(pipelineToUpdate.getAccountIdentifier(), pipelineToUpdate.getOrgIdentifier(),
            pipelineToUpdate.getProjectIdentifier(), updatedPipelineEntity, oldEntityFromDB, true));
    return updatedPipelineEntity;
  }

  @Override
  public PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    if (gitSyncSdkService.isGitSyncEnabled(accountId, orgIdentifier, projectIdentifier)) {
      criteria = gitAwarePersistence.makeCriteriaGitAware(
          accountId, orgIdentifier, projectIdentifier, PipelineEntity.class, criteria);
    }
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicyForPipelineUpdate();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PipelineEntity.class));
  }

  @Override
  public PipelineEntity deleteForOldGitSync(PipelineEntity pipelineToUpdate) {
    String accountId = pipelineToUpdate.getAccountId();
    String orgIdentifier = pipelineToUpdate.getOrgIdentifier();
    String projectIdentifier = pipelineToUpdate.getProjectIdentifier();
    Optional<PipelineEntity> pipelineEntityOptional =
        findForOldGitSync(accountId, orgIdentifier, projectIdentifier, pipelineToUpdate.getIdentifier(), true);
    if (pipelineEntityOptional.isPresent()) {
      PipelineEntity pipelineEntity = gitAwarePersistence.save(
          pipelineToUpdate, pipelineToUpdate.getYaml(), ChangeType.DELETE, PipelineEntity.class, null);
      if (pipelineEntity.getDeleted()) {
        outboxService.save(
            new PipelineDeleteEvent(accountId, orgIdentifier, projectIdentifier, pipelineToUpdate, true));
      }
      return pipelineEntity;
    }
    throw new InvalidRequestException("No such pipeline exists");
  }

  @Override
  public PipelineEntity delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.deleted)
                            .is(false)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.accountId)
                            .is(accountId);
    Query query = new Query(criteria);
    Update updateOperationsForDelete = PMSPipelineFilterHelper.getUpdateOperationsForDelete();
    PipelineEntity deletedPipelineEntity = mongoTemplate.findAndModify(
        query, updateOperationsForDelete, new FindAndModifyOptions().returnNew(true), PipelineEntity.class);
    outboxService.save(new PipelineDeleteEvent(accountId, orgIdentifier, projectIdentifier, deletedPipelineEntity));
    return deletedPipelineEntity;
  }

  private RetryPolicy<Object> getRetryPolicyForPipelineUpdate() {
    int MAX_ATTEMPTS = 3;
    Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .handle(DuplicateKeyException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.info(
                "[Retrying]: Failed updating Pipeline; attempt: {}", event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event
            -> log.error(
                "[Failed]: Failed updating Pipeline; attempt: {}", event.getAttemptCount(), event.getFailure()));
  }

  @Override
  public String importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }
}
