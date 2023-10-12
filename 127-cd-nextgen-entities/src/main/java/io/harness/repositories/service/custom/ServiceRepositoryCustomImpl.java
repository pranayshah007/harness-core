/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.service.custom;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;
import static io.harness.springdata.PersistenceUtils.getRetryPolicyWithDuplicateKeyException;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.utils.CDGitXService;
import io.harness.ng.core.utils.GitXUtils;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ServiceRepositoryCustomImpl implements ServiceRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
  private final int MAX_ATTEMPTS = 3;
  private final CDGitXService cdGitXService;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final GitAwarePersistence gitAwarePersistence;

  @Override
  public Page<ServiceEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable).collation(Collation.of(Locale.ENGLISH).strength(1));
    List<ServiceEntity> projects = mongoTemplate.find(query, ServiceEntity.class);
    return PageableExecutionUtils.getPage(
        projects, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ServiceEntity.class));
  }

  @Override
  public List<ServiceEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, ServiceEntity.class);
  }

  @Override
  public ServiceEntity upsert(Criteria criteria, ServiceEntity serviceEntity) {
    Query query = new Query(criteria);
    Update update = ServiceFilterHelper.getUpdateOperations(serviceEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicyWithDuplicateKeyException(
        "[Retrying]: Failed upserting Service; attempt: {}", "[Failed]: Failed upserting Service; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true).upsert(true), ServiceEntity.class));
  }

  @Override
  public ServiceEntity update(Criteria criteria, ServiceEntity serviceEntity) {
    try {
      Query query = new Query(criteria);
      Update update = ServiceFilterHelper.getUpdateOperations(serviceEntity);
      RetryPolicy<Object> retryPolicy = getRetryPolicy(
          "[Retrying]: Failed updating Service; attempt: {}", "[Failed]: Failed updating Service; attempt: {}");

      GitAwareContextHelper.initDefaultScmGitMetaData();
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

      // inline entity
      if (!GitAwareContextHelper.isRemoteEntity(gitEntityInfo)) {
        return updateServiceEntityInMongo(query, update, retryPolicy);
      }

      // check whether GitX enabled for project
      if (!cdGitXService.isNewGitXEnabled(
              serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier())) {
        throw new InvalidRequestException(GitXUtils.getErrorMessageForGitSimplificationNotEnabled(
            serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier()));
      }

      Scope scope = Scope.of(
          serviceEntity.getAccountIdentifier(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier());
      gitAwareEntityHelper.updateEntityOnGit(serviceEntity, serviceEntity.getYaml(), scope);

      return updateServiceEntityInMongo(query, update, retryPolicy);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while updating service [%s]", serviceEntity.getIdentifier()), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating service [%s]", serviceEntity.getIdentifier()), e);
      throw new InternalServerErrorException(
          String.format("Error while updating service [%s]: [%s]", serviceEntity.getIdentifier(), e.getMessage()), e);
    }
  }

  private ServiceEntity updateServiceEntityInMongo(Query query, Update update, RetryPolicy<Object> retryPolicy) {
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), ServiceEntity.class));
  }

  @Override
  public boolean softDelete(Criteria criteria) {
    Query query = new Query(criteria);
    Update updateOperationsForDelete = ServiceFilterHelper.getUpdateOperationsForDelete();
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Service; attempt: {}", "[Failed]: Failed deleting Service; attempt: {}");
    UpdateResult updateResult =
        Failsafe.with(retryPolicy)
            .get(() -> mongoTemplate.updateFirst(query, updateOperationsForDelete, ServiceEntity.class));
    return updateResult.wasAcknowledged() && updateResult.getModifiedCount() == 1;
  }

  @Override
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Service; attempt: {}", "[Failed]: Failed deleting Service; attempt: {}");
    DeleteResult deleteResult = Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, ServiceEntity.class));
    return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
  }

  @Override
  public DeleteResult deleteMany(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Services; attempt: {}", "[Failed]: Failed deleting Services; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, ServiceEntity.class));
  }

  @Override
  public Long findActiveServiceCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs) {
    Criteria baseCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(ServiceEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(ServiceEntityKeys.projectIdentifier)
                                .is(projectIdentifier);

    Criteria filterCreatedAt = Criteria.where(ServiceEntityKeys.createdAt).lte(timestampInMs);
    Criteria filterDeletedAt = Criteria.where(ServiceEntityKeys.deletedAt).gte(timestampInMs);
    Criteria filterDeleted = Criteria.where(ServiceEntityKeys.deleted).is(false);

    Query query =
        new Query().addCriteria(baseCriteria.andOperator(filterCreatedAt.orOperator(filterDeleted, filterDeletedAt)));
    return mongoTemplate.count(query, ServiceEntity.class);
  }

  @Override
  public List<ServiceEntity> findAllRunTimePermission(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, ServiceEntity.class);
  }

  @Override
  public ServiceEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean deleted) {
    Criteria baseCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(ServiceEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(ServiceEntityKeys.projectIdentifier)
                                .is(projectIdentifier);

    Criteria filterDeleted = Criteria.where(ServiceEntityKeys.deleted).is(deleted);
    Query query = new Query(baseCriteria.andOperator(filterDeleted));
    return mongoTemplate.findById(query, ServiceEntity.class);
  }

  @Override
  public List<String> getServiceIdentifiers(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria baseCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(ServiceEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(ServiceEntityKeys.projectIdentifier)
                                .is(projectIdentifier);

    Query query = new Query(baseCriteria);

    query.fields().include(ServiceEntityKeys.identifier).exclude(ServiceEntityKeys.id);

    List<ServiceEntity> serviceEntities = mongoTemplate.find(query, ServiceEntity.class);
    return serviceEntities.stream().map(serviceEntity -> serviceEntity.getIdentifier()).collect(Collectors.toList());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  @Override
  public ServiceEntity saveGitAware(ServiceEntity serviceToSave) throws InvalidRequestException, DuplicateKeyException {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    // inline entity
    if (!GitAwareContextHelper.isRemoteEntity(gitEntityInfo)) {
      serviceToSave.setStoreType(StoreType.INLINE);
      return mongoTemplate.save(serviceToSave);
    }

    // trying to create remote entity with git simplification disabled
    if (!cdGitXService.isNewGitXEnabled(
            serviceToSave.getAccountId(), serviceToSave.getOrgIdentifier(), serviceToSave.getProjectIdentifier())) {
      throw new InvalidRequestException(GitXUtils.getErrorMessageForGitSimplificationNotEnabled(
          serviceToSave.getOrgIdentifier(), serviceToSave.getProjectIdentifier()));
    }

    addGitParamsToServiceEntity(serviceToSave, gitEntityInfo);
    Scope scope =
        Scope.of(serviceToSave.getAccountId(), serviceToSave.getOrgIdentifier(), serviceToSave.getProjectIdentifier());
    String yamlToPush = serviceToSave.getYaml();

    gitAwareEntityHelper.createEntityOnGit(serviceToSave, yamlToPush, scope);
    return mongoTemplate.save(serviceToSave);
  }

  private void addGitParamsToServiceEntity(ServiceEntity serviceEntity, GitEntityInfo gitEntityInfo) {
    serviceEntity.setStoreType(StoreType.REMOTE);
    if (EmptyPredicate.isEmpty(serviceEntity.getRepoURL())) {
      serviceEntity.setRepoURL(gitAwareEntityHelper.getRepoUrl(
          serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier()));
    }
    serviceEntity.setConnectorRef(gitEntityInfo.getConnectorRef());
    serviceEntity.setRepo(gitEntityInfo.getRepoName());
    serviceEntity.setFilePath(gitEntityInfo.getFilePath());
    serviceEntity.setFallBackBranch(gitEntityInfo.getBranch());
  }

  @Override
  public Optional<ServiceEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean notDeleted, boolean loadFromCache, boolean loadFromFallbackBranch, boolean getMetadataOnly) {
    Query query = new Query(buildCriteriaForServiceIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, !notDeleted));

    ServiceEntity savedEntity = mongoTemplate.findOne(query, ServiceEntity.class);

    if (savedEntity == null) {
      return Optional.empty();
    }

    if (getMetadataOnly || !StoreType.REMOTE.equals(savedEntity.getStoreType())) {
      return Optional.of(savedEntity);
    }

    return Optional.of(getRemoteServiceWithYaml(savedEntity, loadFromCache, loadFromFallbackBranch));
  }

  /**
   * @param savedEntity remote service for which YAML needs to be fetched
   * @param loadFromCache whether to load the entity from cache
   * @return entity populated with YAML fetched from scm
   */
  @Override
  public ServiceEntity getRemoteServiceWithYaml(
      @NonNull ServiceEntity savedEntity, boolean loadFromCache, boolean loadFromFallbackBranch) {
    try {
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(savedEntity.getAccountId(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity, branchName, loadFromCache);
      } else {
        savedEntity = fetchRemoteEntity(savedEntity.getAccountId(), savedEntity.getOrgIdentifier(),
            savedEntity.getProjectIdentifier(), savedEntity, branchName, loadFromCache);
      }

      return savedEntity;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while retrieving service YAML: [%s]", savedEntity.getIdentifier()), e);
      throw e;
    } catch (Exception e) {
      log.error(
          String.format("Unexpected error occurred while retrieving service YAML: [%s]", savedEntity.getIdentifier()),
          e);
      throw new InternalServerErrorException(
          String.format("Unexpected error occurred while retrieving service YAML: [%s]", savedEntity.getIdentifier()));
    }
  }

  private Criteria buildCriteriaForServiceIdentifier(@NonNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NonNull String serviceIdentifier, boolean deleted) {
    return Criteria.where(ServiceEntityKeys.accountId)
        .is(accountIdentifier)
        .and(ServiceEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(ServiceEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(ServiceEntityKeys.identifier)
        .is(serviceIdentifier)
        .and(ServiceEntityKeys.deleted)
        .is(deleted);
  }

  private ServiceEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      ServiceEntity savedEntity, String branchName, boolean loadFromCache) {
    return (ServiceEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .entityType(EntityType.SERVICE)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }

  private ServiceEntity fetchRemoteEntityWithFallBackBranch(String accountId, String orgIdentifier,
      String projectIdentifier, ServiceEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity = fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      String fallBackBranch = savedEntity.getFallBackBranch();
      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(savedEntity.getFallBackBranch());
      if (shouldRetryWithFallBackBranch(GitXUtils.getScmExceptionIfExists(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving service [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  boolean shouldRetryWithFallBackBranch(ScmException scmException, String branchTried, String serviceFallbackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(serviceFallbackBranch) && !branchTried.equals(serviceFallbackBranch));
  }

  @Override
  public List<String> getListOfDistinctRepos(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(query, ServiceEntityKeys.repo, ServiceEntity.class, String.class);
  }
}
