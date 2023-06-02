/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.pipeline.MoveConfigOperationType.INLINE_TO_REMOTE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.events.InputSetCreateEvent;
import io.harness.pms.events.InputSetDeleteEvent;
import io.harness.pms.events.InputSetUpdateEvent;
import io.harness.pms.inputset.gitsync.InputSetYamlDTO;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.pipeline.MoveConfigOperationType;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PipelineExceptionsHelper;
import io.harness.utils.PipelineGitXHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputSetRepositoryCustomImpl implements PMSInputSetRepositoryCustom {
  private final GitAwarePersistence gitAwarePersistence;
  private final MongoTemplate mongoTemplate;
  private final OutboxService outboxService;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final TransactionHelper transactionHelper;

  @Override
  public List<InputSetEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, InputSetEntity.class);
  }

  @Override
  public Page<InputSetEntity> findAll(
      Criteria criteria, Pageable pageable, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<InputSetEntity> inputSetEntities = gitAwarePersistence.find(
        criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier, InputSetEntity.class);

    return PageableExecutionUtils.getPage(inputSetEntities, pageable,
        ()
            -> gitAwarePersistence.count(
                criteria, projectIdentifier, orgIdentifier, accountIdentifier, InputSetEntity.class));
  }

  @Override
  public InputSetEntity saveForOldGitSync(InputSetEntity entityToSave, InputSetYamlDTO yamlDTO) {
    InputSetEntity savedInputSetEntity =
        gitAwarePersistence.save(entityToSave, entityToSave.getYaml(), ChangeType.ADD, InputSetEntity.class, null);
    outboxService.save(InputSetCreateEvent.builder()
                           .accountIdentifier(entityToSave.getAccountIdentifier())
                           .orgIdentifier(entityToSave.getOrgIdentifier())
                           .projectIdentifier(entityToSave.getProjectIdentifier())
                           .pipelineIdentifier(entityToSave.getPipelineIdentifier())
                           .inputSet(savedInputSetEntity)
                           .isForOldGitSync(true)
                           .build());
    return savedInputSetEntity;
  }

  @Override
  public InputSetEntity save(InputSetEntity entityToSave) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    boolean isRemoteFlow = gitEntityInfo != null && gitEntityInfo.getStoreType() == StoreType.REMOTE;
    if (gitSyncSdkService.isGitSimplificationEnabled(
            entityToSave.getAccountIdentifier(), entityToSave.getOrgIdentifier(), entityToSave.getProjectIdentifier())
        && isRemoteFlow) {
      createRemoteEntity(entityToSave);
    } else {
      entityToSave.setStoreType(StoreType.INLINE);
    }
    return transactionHelper.performTransaction(() -> {
      InputSetEntity savedInputSetEntity = mongoTemplate.save(entityToSave);
      outboxService.save(InputSetCreateEvent.builder()
                             .accountIdentifier(entityToSave.getAccountIdentifier())
                             .orgIdentifier(entityToSave.getOrgIdentifier())
                             .projectIdentifier(entityToSave.getProjectIdentifier())
                             .pipelineIdentifier(entityToSave.getPipelineIdentifier())
                             .inputSet(savedInputSetEntity)
                             .isForOldGitSync(false)
                             .build());
      return savedInputSetEntity;
    });
  }

  @Override
  public InputSetEntity saveForImportedYAML(InputSetEntity entityToSave) {
    String accountIdentifier = entityToSave.getAccountIdentifier();
    String orgIdentifier = entityToSave.getOrgIdentifier();
    String projectIdentifier = entityToSave.getProjectIdentifier();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String yamlToPush = entityToSave.getYaml();
    entityToSave.setStoreType(StoreType.REMOTE);
    setRepoUrlForSave(entityToSave);
    entityToSave.setConnectorRef(gitEntityInfo.getConnectorRef());
    entityToSave.setRepo(gitEntityInfo.getRepoName());
    entityToSave.setFilePath(gitEntityInfo.getFilePath());
    return transactionHelper.performTransaction(() -> {
      InputSetEntity savedInputSetEntity = mongoTemplate.save(entityToSave);
      outboxService.save(InputSetCreateEvent.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .pipelineIdentifier(savedInputSetEntity.getPipelineIdentifier())
                             .inputSet(savedInputSetEntity)
                             .isForOldGitSync(false)
                             .build());
      return savedInputSetEntity;
    });
  }

  @Override
  public Optional<InputSetEntity> findForOldGitSync(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean notDeleted) {
    Criteria criteriaForFind = PMSInputSetFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, notDeleted);
    return gitAwarePersistence.findOne(
        criteriaForFind, projectIdentifier, orgIdentifier, accountId, InputSetEntity.class);
  }

  @Override
  public Optional<InputSetEntity> find(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String identifier, boolean notDeleted, boolean getMetadataOnly,
      boolean loadFromFallbackBranch, boolean loadFromCache) {
    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, notDeleted);
    Query query = new Query(criteria);
    InputSetEntity savedEntity = mongoTemplate.findOne(query, InputSetEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }
    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      String branch = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(
            accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
      } else {
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
      }
    }

    return Optional.of(savedEntity);
  }

  private InputSetEntity fetchRemoteEntityWithFallBackBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, InputSetEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity =
          fetchRemoteEntity(accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      log.info(String.format("Failed to fetch input-set from default branch [%s]", branch));
      String fallBackBranch = savedEntity.getFallBackBranch();
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

  InputSetEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      InputSetEntity savedEntity, String branch, boolean loadFromCache) {
    // fetch yaml from git
    return (InputSetEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branch)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .getOnlyFileContent(PipelineGitXHelper.isExecutionFlow())
            .entityType(EntityType.INPUT_SETS)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }

  @Override
  public InputSetEntity updateForOldGitSync(
      InputSetEntity entityToUpdate, InputSetYamlDTO yamlDTO, ChangeType changeType) {
    Supplier<OutboxEvent> functor = null;
    if (!gitSyncSdkService.isGitSyncEnabled(entityToUpdate.getAccountIdentifier(), entityToUpdate.getOrgIdentifier(),
            entityToUpdate.getProjectIdentifier())) {
      Optional<InputSetEntity> inputSetEntityOptional = findForOldGitSync(entityToUpdate.getAccountIdentifier(),
          entityToUpdate.getOrgIdentifier(), entityToUpdate.getProjectIdentifier(),
          entityToUpdate.getPipelineIdentifier(), entityToUpdate.getIdentifier(), true);
      if (inputSetEntityOptional.isPresent()) {
        InputSetEntity oldInputSet = inputSetEntityOptional.get();
        functor = ()
            -> outboxService.save(InputSetUpdateEvent.builder()
                                      .accountIdentifier(entityToUpdate.getAccountIdentifier())
                                      .orgIdentifier(entityToUpdate.getOrgIdentifier())
                                      .projectIdentifier(entityToUpdate.getProjectIdentifier())
                                      .pipelineIdentifier(entityToUpdate.getPipelineIdentifier())
                                      .newInputSet(entityToUpdate)
                                      .oldInputSet(oldInputSet)
                                      .isForOldGitSync(true)
                                      .build());
      } else {
        throw new InvalidRequestException("No such input set exist");
      }
    }

    return gitAwarePersistence.save(
        entityToUpdate, entityToUpdate.getYaml(), changeType, InputSetEntity.class, functor);
  }

  @Override
  public InputSetEntity update(InputSetEntity entityToUpdate) {
    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForFind(entityToUpdate.getAccountId(),
        entityToUpdate.getOrgIdentifier(), entityToUpdate.getProjectIdentifier(),
        entityToUpdate.getPipelineIdentifier(), entityToUpdate.getIdentifier(), true);
    Query query = new Query(criteria);
    long timeOfUpdate = System.currentTimeMillis();
    Update updateOperations = PMSInputSetFilterHelper.getUpdateOperations(entityToUpdate, timeOfUpdate);
    InputSetEntity updatedEntity = transactionHelper.performTransaction(
        () -> updateInputSetInDB(query, updateOperations, entityToUpdate, timeOfUpdate));

    updatedEntity = onboardToInlineIfNullStoreType(updatedEntity, query);
    if (updatedEntity == null) {
      return null;
    }

    if (updatedEntity.getStoreType() == StoreType.REMOTE
        && gitSyncSdkService.isGitSimplificationEnabled(entityToUpdate.getAccountIdentifier(),
            entityToUpdate.getOrgIdentifier(), entityToUpdate.getProjectIdentifier())) {
      Scope scope = Scope.builder()
                        .accountIdentifier(updatedEntity.getAccountIdentifier())
                        .orgIdentifier(updatedEntity.getOrgIdentifier())
                        .projectIdentifier(updatedEntity.getProjectIdentifier())
                        .build();
      gitAwareEntityHelper.updateEntityOnGit(updatedEntity, entityToUpdate.getYaml(), scope);
    }
    return updatedEntity;
  }

  InputSetEntity updateInputSetInDB(
      Query query, Update updateOperations, InputSetEntity entityToUpdate, long timeOfUpdate) {
    InputSetEntity oldEntityFromDB = mongoTemplate.findAndModify(
        query, updateOperations, new FindAndModifyOptions().returnNew(false), InputSetEntity.class);
    if (oldEntityFromDB == null) {
      return null;
    }
    InputSetEntity updatedEntity =
        PMSInputSetFilterHelper.updateFieldsInDBEntry(oldEntityFromDB, entityToUpdate, timeOfUpdate);
    outboxService.save(InputSetUpdateEvent.builder()
                           .accountIdentifier(entityToUpdate.getAccountIdentifier())
                           .orgIdentifier(entityToUpdate.getOrgIdentifier())
                           .projectIdentifier(entityToUpdate.getProjectIdentifier())
                           .pipelineIdentifier(entityToUpdate.getPipelineIdentifier())
                           .newInputSet(updatedEntity)
                           .oldInputSet(oldEntityFromDB)
                           .isForOldGitSync(false)
                           .build());
    return updatedEntity;
  }

  InputSetEntity onboardToInlineIfNullStoreType(InputSetEntity updatedEntity, Query query) {
    if (updatedEntity.getStoreType() == null) {
      Update updateOperationsForOnboardingToInline = PMSInputSetFilterHelper.getUpdateOperationsForOnboardingToInline();
      updatedEntity = mongoTemplate.findAndModify(query, updateOperationsForOnboardingToInline,
          new FindAndModifyOptions().returnNew(true), InputSetEntity.class);
    }
    return updatedEntity;
  }

  @Override
  public InputSetEntity update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Input Set; attempt: {}", "[Failed]: Failed updating Input Set; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }

  @Override
  public InputSetEntity update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Criteria criteria, Update update) {
    criteria = gitAwarePersistence.makeCriteriaGitAware(
        accountIdentifier, orgIdentifier, projectIdentifier, InputSetEntity.class, criteria);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Input Set; attempt: {}", "[Failed]: Failed updating Input Set; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }

  @Override
  public void deleteForOldGitSync(InputSetEntity entityToDelete, InputSetYamlDTO yamlDTO) {
    gitAwarePersistence.delete(entityToDelete, ChangeType.DELETE, InputSetEntity.class);
    outboxService.save(InputSetDeleteEvent.builder()
                           .accountIdentifier(entityToDelete.getAccountIdentifier())
                           .orgIdentifier(entityToDelete.getOrgIdentifier())
                           .projectIdentifier(entityToDelete.getProjectIdentifier())
                           .pipelineIdentifier(entityToDelete.getPipelineIdentifier())
                           .inputSet(entityToDelete)
                           .isForOldGitSync(true)
                           .build());
  }

  @Override
  public void delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String identifier) {
    Criteria criteria = PMSInputSetFilterHelper.getCriteriaForFind(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, true);
    Query query = new Query(criteria);
    InputSetEntity removedEntity = mongoTemplate.findAndRemove(query, InputSetEntity.class);
    outboxService.save(InputSetDeleteEvent.builder()
                           .accountIdentifier(accountId)
                           .orgIdentifier(orgIdentifier)
                           .projectIdentifier(projectIdentifier)
                           .pipelineIdentifier(pipelineIdentifier)
                           .inputSet(removedEntity)
                           .isForOldGitSync(false)
                           .build());
  }

  @Override
  public void deleteAllInputSetsWhenPipelineDeleted(Query query) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Input Set; attempt: {}", "[Failed]: Failed deleting Input Set; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, InputSetEntity.class));
  }

  @Override
  public boolean existsByAccountIdAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean notDeleted) {
    return gitAwarePersistence.exists(Criteria.where(InputSetEntityKeys.deleted)
                                          .is(!notDeleted)
                                          .and(InputSetEntityKeys.accountId)
                                          .is(accountId)
                                          .and(InputSetEntityKeys.orgIdentifier)
                                          .is(orgIdentifier)
                                          .and(InputSetEntityKeys.projectIdentifier)
                                          .is(projectIdentifier)
                                          .and(InputSetEntityKeys.pipelineIdentifier)
                                          .is(pipelineIdentifier),
        projectIdentifier, orgIdentifier, accountId, InputSetEntity.class);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  @Override
  public boolean checkIfInputSetWithGivenFilePathExists(String accountIdentifier, String repoURL, String filePath) {
    Criteria criteria = Criteria.where(InputSetEntityKeys.accountId)
                            .is(accountIdentifier)
                            .and(InputSetEntityKeys.repoURL)
                            .is(repoURL)
                            .and(InputSetEntityKeys.filePath)
                            .is(filePath);
    List<InputSetEntity> listOfInputSetEntities = findAll(criteria);
    return !listOfInputSetEntities.isEmpty();
  }

  @Override
  public InputSetEntity updateInputSetEntity(InputSetEntity inputSetToMove, Criteria criteria, Update update,
      MoveConfigOperationType moveConfigOperationType) {
    return transactionHelper.performTransaction(
        () -> moveConfigOperations(inputSetToMove, criteria, update, moveConfigOperationType));
  }

  @Override
  public List<String> findAllUniqueInputSetRepos(@NotNull Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, InputSetEntityKeys.repo, InputSetEntity.class, String.class);
  }

  @Override
  public InputSetEntity updateEntity(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return Failsafe.with(DEFAULT_RETRY_POLICY)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InputSetEntity.class));
  }

  @VisibleForTesting
  InputSetEntity moveConfigOperations(InputSetEntity inputSetToMove, Criteria criteria, Update update,
      MoveConfigOperationType moveConfigOperationType) {
    //   create file if inline to remote
    if (INLINE_TO_REMOTE.equals(moveConfigOperationType)) {
      createRemoteEntity(inputSetToMove);
    }
    //    update the mongo db
    return updateInputSetInDB(new Query(criteria), update, inputSetToMove, System.currentTimeMillis());
  }

  void setRepoUrlForSave(InputSetEntity entityToSave) {
    if (EmptyPredicate.isEmpty(entityToSave.getRepoURL())) {
      entityToSave.setRepoURL(gitAwareEntityHelper.getRepoUrl(
          entityToSave.getAccountId(), entityToSave.getOrgIdentifier(), entityToSave.getProjectIdentifier()));
    }
  }

  private ScmCreateFileGitResponse createRemoteEntity(InputSetEntity inputSetEntity) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    Scope scope = Scope.builder()
                      .accountIdentifier(inputSetEntity.getAccountIdentifier())
                      .orgIdentifier(inputSetEntity.getOrgIdentifier())
                      .projectIdentifier(inputSetEntity.getProjectIdentifier())
                      .build();

    String yamlToPush = inputSetEntity.getYaml();
    addGitParamsToInputSetEntity(inputSetEntity, gitEntityInfo);
    return gitAwareEntityHelper.createEntityOnGit(inputSetEntity, yamlToPush, scope);
  }

  private void addGitParamsToInputSetEntity(InputSetEntity inputSetEntity, GitEntityInfo gitEntityInfo) {
    inputSetEntity.setStoreType(StoreType.REMOTE);
    inputSetEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    inputSetEntity.setRepo(gitEntityInfo.getRepoName());
    inputSetEntity.setFilePath(gitEntityInfo.getFilePath());
    inputSetEntity.setFallBackBranch(gitEntityInfo.getBranch());
    setRepoUrlForSave(inputSetEntity);
  }
}
