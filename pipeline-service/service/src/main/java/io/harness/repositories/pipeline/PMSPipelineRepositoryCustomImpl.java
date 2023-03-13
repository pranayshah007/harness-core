/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
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
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.filters.PMSPipelineFilterHelper;
import io.harness.pms.pipeline.service.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.service.PipelineEntityReadHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PipelineExceptionsHelper;
import io.harness.utils.PipelineGitXHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
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
  private final PipelineEntityReadHelper pipelineEntityReadHelper;

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
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public Long countAllPipelinesInAccount(String accountId) {
    Criteria criteria =
        Criteria.where(PipelineEntityKeys.accountId).is(accountId).and(PipelineEntityKeys.deleted).is(false);
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public PipelineEntity saveForOldGitSync(PipelineEntity pipelineToSave) {
    return transactionHelper.performTransaction(() -> {
      PipelineEntity savedEntity = gitAwarePersistence.save(
          pipelineToSave, pipelineToSave.getYaml(), ChangeType.ADD, PipelineEntity.class, null);
      PipelineCreateEvent pipelineCreateEvent = getPipelineSaveEvent(savedEntity, true);
      outboxService.save(pipelineCreateEvent);
      checkForMetadataAndSaveIfAbsent(savedEntity);
      return savedEntity;
    });
  }

  @Override
  public PipelineEntity save(PipelineEntity pipelineToSave) {
    return transactionHelper.performTransaction(() -> savePipelineOperations(pipelineToSave));
  }

  @VisibleForTesting
  PipelineEntity savePipelineOperations(PipelineEntity pipelineToSave) {
    PipelineEntity savedEntity = savePipelineEntity(pipelineToSave);
    checkForMetadataAndSaveIfAbsent(savedEntity);
    return savedEntity;
  }

  @VisibleForTesting
  PipelineEntity savePipelineEntity(PipelineEntity pipelineToSave) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo == null || gitEntityInfo.getStoreType() == null
        || gitEntityInfo.getStoreType().equals(StoreType.INLINE)) {
      pipelineToSave.setStoreType(StoreType.INLINE);
      PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
      outboxService.save(getPipelineSaveEvent(savedPipelineEntity, false));
      return savedPipelineEntity;
    }
    if (gitSyncSdkService.isGitSimplificationEnabled(pipelineToSave.getAccountIdentifier(),
            pipelineToSave.getOrgIdentifier(), pipelineToSave.getProjectIdentifier())) {
      createRemoteEntity(pipelineToSave);
    } else {
      log.info(String.format(
          "Marking storeType as INLINE for Pipeline with ID [%s] because Git simplification was not enabled for Project [%s] in Account [%s]",
          pipelineToSave.getIdentifier(), pipelineToSave.getProjectIdentifier(),
          pipelineToSave.getAccountIdentifier()));
      pipelineToSave.setStoreType(StoreType.INLINE);
    }
    PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
    outboxService.save(getPipelineSaveEvent(savedPipelineEntity, false));
    return savedPipelineEntity;
  }

  PipelineCreateEvent getPipelineSaveEvent(PipelineEntity savedPipelineEntity, boolean isOldGitSync) {
    return PipelineCreateEvent.builder()
        .accountIdentifier(savedPipelineEntity.getAccountId())
        .orgIdentifier(savedPipelineEntity.getOrgIdentifier())
        .projectIdentifier(savedPipelineEntity.getProjectIdentifier())
        .pipeline(savedPipelineEntity)
        .isForOldGitSync(isOldGitSync)
        .build();
  }

  void checkForMetadataAndSaveIfAbsent(PipelineEntity savedEntity) {
    // checking if PipelineMetadata exists or not, if exists don't re-save the entity, as only one entry across git
    // repos should be there.
    Optional<PipelineMetadataV2> metadataOptional =
        pipelineMetadataService.getMetadata(savedEntity.getAccountIdentifier(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity.getIdentifier());
    if (metadataOptional.isEmpty()) {
      PipelineMetadataV2 metadata =
          PipelineMetadataV2.builder()
              .accountIdentifier(savedEntity.getAccountIdentifier())
              .orgIdentifier(savedEntity.getOrgIdentifier())
              .projectIdentifier(savedEntity.getProjectIdentifier())
              .runSequence(0)
              .identifier(savedEntity.getIdentifier())
              .entityGitDetails(EntityGitDetails.builder().branch(GitContextHelper.getBranch()).build())
              .build();
      pipelineMetadataService.save(metadata);
    }
  }

  @Override
  public Optional<PipelineEntity> findForOldGitSync(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, notDeleted);
    return gitAwarePersistence.findOne(criteria, projectIdentifier, orgIdentifier, accountId, PipelineEntity.class);
  }

  @Override
  public Optional<PipelineEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean getMetadataOnly, boolean loadFromFallbackBranch,
      boolean loadFromCache) {
    PipelineEntity savedEntity =
        getPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, notDeleted, getMetadataOnly);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepoURL());

      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(
            accountId, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      } else {
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      }
    }
    return Optional.of(savedEntity);
  }

  private PipelineEntity getPipelineEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean notDeleted, boolean metadataOnly) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, notDeleted);
    Query query = new Query(criteria);
    if (metadataOnly) {
      for (String nonMetadataField : PMSPipelineFilterHelper.getPipelineNonMetadataFields()) {
        query.fields().exclude(nonMetadataField);
      }
    }
    return mongoTemplate.findOne(query, PipelineEntity.class);
  }

  PipelineEntity fetchRemoteEntityWithFallBackBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, PipelineEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity =
          fetchRemoteEntity(accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      String fallBackBranch = getFallBackBranch(savedEntity);
      if (PipelineGitXHelper.shouldRetryWithFallBackBranch(
              PipelineExceptionsHelper.getScmException(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving pipeline [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        GitAwareContextHelper.updateGitEntityContextWithBranch(fallBackBranch);
        savedEntity = fetchRemoteEntity(
            accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  private String getFallBackBranch(PipelineEntity savedEntity) {
    Optional<PipelineMetadataV2> metadataOptional =
        pipelineMetadataService.getMetadata(savedEntity.getAccountIdentifier(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity.getIdentifier());
    if (metadataOptional.isPresent() && metadataOptional.get().getEntityGitDetails() != null) {
      return metadataOptional.get().getEntityGitDetails().getBranch();
    }
    return null;
  }

  @VisibleForTesting
  PipelineEntity fetchRemoteEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineEntity savedEntity, String branch, boolean loadFromCache) {
    return (PipelineEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branch)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .loadFromCache(loadFromCache)
            .entityType(EntityType.PIPELINES)
            .getOnlyFileContent(PipelineGitXHelper.isExecutionFlow())
            .build(),
        Collections.emptyMap());
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
    Criteria criteria =
        PMSPipelineFilterHelper.getCriteriaForFind(pipelineToUpdate.getAccountId(), pipelineToUpdate.getOrgIdentifier(),
            pipelineToUpdate.getProjectIdentifier(), pipelineToUpdate.getIdentifier(), true);
    Query query = new Query(criteria);
    long timeOfUpdate = System.currentTimeMillis();
    Update updateOperations = PMSPipelineFilterHelper.getUpdateOperations(pipelineToUpdate, timeOfUpdate);

    PipelineEntity updatedPipelineEntity = transactionHelper.performTransaction(
        () -> updatePipelineEntityInDB(query, updateOperations, pipelineToUpdate, timeOfUpdate));

    if (updatedPipelineEntity == null) {
      return null;
    }

    updatedPipelineEntity = onboardToInlineIfNullStoreType(updatedPipelineEntity, query);
    if (updatedPipelineEntity == null) {
      return null;
    }

    if (updatedPipelineEntity.getStoreType() == StoreType.REMOTE
        && gitSyncSdkService.isGitSimplificationEnabled(pipelineToUpdate.getAccountIdentifier(),
            pipelineToUpdate.getOrgIdentifier(), pipelineToUpdate.getProjectIdentifier())) {
      Scope scope = buildScope(updatedPipelineEntity);
      gitAwareEntityHelper.updateEntityOnGit(updatedPipelineEntity, pipelineToUpdate.getYaml(), scope);
    }
    return updatedPipelineEntity;
  }

  PipelineEntity updatePipelineEntityInDB(
      Query query, Update updateOperations, PipelineEntity pipelineToUpdate, long timeOfUpdate) {
    PipelineEntity oldEntityFromDB = mongoTemplate.findAndModify(
        query, updateOperations, new FindAndModifyOptions().returnNew(false), PipelineEntity.class);
    if (oldEntityFromDB == null) {
      return null;
    }
    PipelineEntity pipelineEntityAfterUpdate =
        PMSPipelineFilterHelper.updateFieldsInDBEntry(oldEntityFromDB, pipelineToUpdate, timeOfUpdate);
    outboxService.save(
        new PipelineUpdateEvent(pipelineToUpdate.getAccountIdentifier(), pipelineToUpdate.getOrgIdentifier(),
            pipelineToUpdate.getProjectIdentifier(), pipelineEntityAfterUpdate, oldEntityFromDB));
    return pipelineEntityAfterUpdate;
  }

  PipelineEntity onboardToInlineIfNullStoreType(PipelineEntity updatedPipelineEntity, Query query) {
    if (updatedPipelineEntity.getStoreType() == null) {
      // onboarding old entities as INLINE
      Update updateOperationsForOnboardingToInline = PMSPipelineFilterHelper.getUpdateOperationsForOnboardingToInline();
      updatedPipelineEntity = mongoTemplate.findAndModify(query, updateOperationsForOnboardingToInline,
          new FindAndModifyOptions().returnNew(true), PipelineEntity.class);
    }
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
  public void deleteForOldGitSync(PipelineEntity pipelineToDelete) {
    String accountId = pipelineToDelete.getAccountId();
    String orgIdentifier = pipelineToDelete.getOrgIdentifier();
    String projectIdentifier = pipelineToDelete.getProjectIdentifier();
    gitAwarePersistence.delete(pipelineToDelete, ChangeType.DELETE, PipelineEntity.class);
    outboxService.save(new PipelineDeleteEvent(accountId, orgIdentifier, projectIdentifier, pipelineToDelete, true));
  }

  @Override
  public void delete(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, true);
    Query query = new Query(criteria);
    PipelineEntity deletedPipelineEntity;
    deletedPipelineEntity = mongoTemplate.findAndRemove(query, PipelineEntity.class);
    outboxService.save(new PipelineDeleteEvent(accountId, orgIdentifier, projectIdentifier, deletedPipelineEntity));
  }

  @Override
  public boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForAllPipelinesInProject(accountId, orgId, projectId);
    Query query = new Query(criteria);
    try {
      List<PipelineEntity> entities = mongoTemplate.findAllAndRemove(query, PipelineEntity.class);
      entities.stream().forEach(deletedPipelineEntity -> {
        outboxService.save(new PipelineDeleteEvent(accountId, orgId, projectId, deletedPipelineEntity));
      });
      return true;
    } catch (Exception e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForPipelinesNotDeleted(
          accountId, orgId, projectId, ExceptionUtils.getMessage(e));
      log.error(errorMessage, e);
      return false;
    }
  }

  private RetryPolicy<Object> getRetryPolicyForPipelineUpdate() {
    return PersistenceUtils.getRetryPolicy(
        "[Retrying]: Failed updating Pipeline; attempt: {}", "[Failed]: Failed updating Pipeline; attempt: {}");
  }

  @Override
  public PipelineEntity savePipelineEntityForImportedYAML(PipelineEntity pipelineToSave) {
    String accountIdentifier = pipelineToSave.getAccountIdentifier();
    String orgIdentifier = pipelineToSave.getOrgIdentifier();
    String projectIdentifier = pipelineToSave.getProjectIdentifier();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    addGitParamsToPipelineEntity(pipelineToSave, gitEntityInfo);
    return transactionHelper.performTransaction(() -> {
      PipelineEntity savedPipelineEntity = mongoTemplate.save(pipelineToSave);
      checkForMetadataAndSaveIfAbsent(savedPipelineEntity);
      outboxService.save(
          new PipelineCreateEvent(accountIdentifier, orgIdentifier, projectIdentifier, savedPipelineEntity));
      return savedPipelineEntity;
    });
  }

  Scope buildScope(PipelineEntity pipelineEntity) {
    return Scope.of(pipelineEntity.getAccountIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getProjectIdentifier());
  }

  void addGitParamsToPipelineEntity(PipelineEntity pipelineToSave, GitEntityInfo gitEntityInfo) {
    pipelineToSave.setStoreType(StoreType.REMOTE);
    if (EmptyPredicate.isEmpty(pipelineToSave.getRepoURL())) {
      pipelineToSave.setRepoURL(gitAwareEntityHelper.getRepoUrl(
          pipelineToSave.getAccountId(), pipelineToSave.getOrgIdentifier(), pipelineToSave.getProjectIdentifier()));
    }
    pipelineToSave.setConnectorRef(gitEntityInfo.getConnectorRef());
    pipelineToSave.setRepo(gitEntityInfo.getRepoName());
    pipelineToSave.setFilePath(gitEntityInfo.getFilePath());
  }

  @Override
  public Long countFileInstances(String accountId, String repoURL, String filePath) {
    Criteria criteria = PMSPipelineFilterHelper.getCriteriaForFileUniquenessCheck(accountId, repoURL, filePath);
    Query query = new Query(criteria);
    return pipelineEntityReadHelper.findCount(query);
  }

  @Override
  public List<String> findAllUniqueRepos(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, PipelineEntityKeys.repo, PipelineEntity.class, String.class);
  }

  @Override
  public PipelineEntity updatePipelineEntity(PipelineEntity pipelineToSave, Update pipelineUpdate,
      Criteria pipelineCriteria, Update metadataUpdate, Criteria metadataCriteria,
      MoveConfigOperationType moveConfigOperationType) {
    return transactionHelper.performTransaction(
        ()
            -> moveConfigOperations(pipelineToSave, pipelineUpdate, pipelineCriteria, metadataUpdate, metadataCriteria,
                moveConfigOperationType));
  }

  @VisibleForTesting
  PipelineEntity moveConfigOperations(PipelineEntity pipelineToMove, Update pipelineUpdate, Criteria pipelineCriteria,
      Update metadataUpdate, Criteria metadataCriteria, MoveConfigOperationType moveConfigOperationType) {
    //   create file if inline to remote
    if (INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      createRemoteEntity(pipelineToMove);
    }
    //    update the mongo db
    PipelineEntity movedPipelineEntity = updatePipelineMetadata(pipelineToMove.getAccountId(),
        pipelineToMove.getOrgIdentifier(), pipelineToMove.getProjectIdentifier(), pipelineCriteria, pipelineUpdate);
    //    update the metadataV2 db
    updatePipelineMetadataV2(metadataUpdate, metadataCriteria);
    return movedPipelineEntity;
  }

  private PipelineMetadataV2 updatePipelineMetadataV2(Update update, Criteria criteria) {
    return pipelineMetadataService.update(criteria, update);
  }

  private ScmCreateFileGitResponse createRemoteEntity(PipelineEntity pipelineEntity) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    Scope scope = buildScope(pipelineEntity);
    String yamlToPush = pipelineEntity.getYaml();
    addGitParamsToPipelineEntity(pipelineEntity, gitEntityInfo);

    return gitAwareEntityHelper.createEntityOnGit(pipelineEntity, yamlToPush, scope);
  }
}
