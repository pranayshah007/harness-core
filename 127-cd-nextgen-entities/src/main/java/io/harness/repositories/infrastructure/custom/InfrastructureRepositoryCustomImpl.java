/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructure.custom;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SCM_BAD_REQUEST;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.service.beans.ServiceDefinitionType;
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
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;
import io.harness.ng.core.utils.CDGitXService;
import io.harness.ng.core.utils.GitXUtils;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class InfrastructureRepositoryCustomImpl implements InfrastructureRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
  private final int MAX_ATTEMPTS = 3;
  private final CDGitXService cdGitXService;
  private final GitAwareEntityHelper gitAwareEntityHelper;

  @Override
  public Page<InfrastructureEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<InfrastructureEntity> infrastructures = mongoTemplate.find(query, InfrastructureEntity.class);
    return PageableExecutionUtils.getPage(infrastructures, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), InfrastructureEntity.class));
  }

  @Override
  public InfrastructureEntity saveGitAware(InfrastructureEntity infrastructureToSave) {
    GitAwareContextHelper.initDefaultScmGitMetaData();
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

    // inline entity
    if (gitEntityInfo == null || StoreType.INLINE.equals(gitEntityInfo.getStoreType())
        || gitEntityInfo.getStoreType() == null) {
      infrastructureToSave.setStoreType(StoreType.INLINE);
      return mongoTemplate.save(infrastructureToSave);
    }

    if (!cdGitXService.isNewGitXEnabled(infrastructureToSave.getAccountId(), infrastructureToSave.getOrgIdentifier(),
            infrastructureToSave.getProjectIdentifier())) {
      throw new InvalidRequestException(GitXUtils.getErrorMessageForGitSimplificationNotEnabled(
          infrastructureToSave.getOrgIdentifier(), infrastructureToSave.getProjectIdentifier()));
    }
    addGitParamsToInfrastructureEntity(infrastructureToSave, gitEntityInfo);
    Scope scope = Scope.of(infrastructureToSave.getAccountId(), infrastructureToSave.getOrgIdentifier(),
        infrastructureToSave.getProjectIdentifier());
    String yamlToPush = infrastructureToSave.getYaml();

    gitAwareEntityHelper.createEntityOnGit(infrastructureToSave, yamlToPush, scope);
    return mongoTemplate.save(infrastructureToSave);
  }
  @Override
  public InfrastructureEntity upsert(Criteria criteria, InfrastructureEntity infraEntity) {
    Query query = new Query(criteria);
    Update update = InfrastructureFilterHelper.getUpdateOperations(infraEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed upserting Infrastructure; attempt: {}",
        "[Failed]: Failed upserting Infrastructure; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true).upsert(true),
                     InfrastructureEntity.class));
  }

  @Override
  public InfrastructureEntity update(Criteria criteria, InfrastructureEntity infraEntity) {
    try {
      Query query = new Query(criteria);
      Update update = InfrastructureFilterHelper.getUpdateOperations(infraEntity);
      RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed updating Infrastructure; attempt: {}",
          "[Failed]: Failed updating Infrastructure; attempt: {}");

      GitAwareContextHelper.initDefaultScmGitMetaData();
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();

      // inline entity
      if (!GitAwareContextHelper.isRemoteEntity(gitEntityInfo)) {
        return updateInfrastructureEntityInMongo(query, update, retryPolicy);
      }

      // check whether GitX enabled for project
      if (!cdGitXService.isNewGitXEnabled(
              infraEntity.getAccountId(), infraEntity.getOrgIdentifier(), infraEntity.getProjectIdentifier())) {
        throw new InvalidRequestException(GitXUtils.getErrorMessageForGitSimplificationNotEnabled(
            infraEntity.getOrgIdentifier(), infraEntity.getProjectIdentifier()));
      }

      Scope scope = Scope.of(
          infraEntity.getAccountIdentifier(), infraEntity.getOrgIdentifier(), infraEntity.getProjectIdentifier());
      gitAwareEntityHelper.updateEntityOnGit(infraEntity, infraEntity.getYaml(), scope);

      return updateInfrastructureEntityInMongo(query, update, retryPolicy);
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(String.format("Error while updating Infrastructure [%s]", infraEntity.getIdentifier()), e);
      throw e;
    } catch (Exception e) {
      log.error(String.format("Error while updating Infrastructure [%s]", infraEntity.getIdentifier()), e);
      throw new InternalServerErrorException(
          String.format("Error while updating Infrastructure [%s]: [%s]", infraEntity.getIdentifier(), e.getMessage()),
          e);
    }
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Infrastructure; attempt: {}",
        "[Failed]: Failed deleting Infrastructure; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, InfrastructureEntity.class));
  }

  @Override
  public InfrastructureEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String infraIdentifier) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier)
                                .and(InfrastructureEntityKeys.envIdentifier)
                                .is(envIdentifier)
                                .and(InfrastructureEntityKeys.identifier)
                                .is(infraIdentifier);

    Query query = new Query(baseCriteria);
    return mongoTemplate.findById(query, InfrastructureEntity.class);
  }

  @Override
  public List<InfrastructureEntity> findAllFromInfraIdentifierList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifierList) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier)
                                .and(InfrastructureEntityKeys.envIdentifier)
                                .is(envIdentifier)
                                .and(InfrastructureEntityKeys.identifier)
                                .in(infraIdentifierList);

    Query query = new Query(baseCriteria);
    return mongoTemplate.find(query, InfrastructureEntity.class);
  }

  @Override
  public List<InfrastructureEntity> findAllFromEnvIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier)
                                .and(InfrastructureEntityKeys.envIdentifier)
                                .is(envIdentifier);

    Query query = new Query(baseCriteria);
    return mongoTemplate.find(query, InfrastructureEntity.class);
  }

  @Override
  public List<InfrastructureEntity> findAllFromEnvIdentifierAndDeploymentType(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, ServiceDefinitionType deploymentType) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier)
                                .and(InfrastructureEntityKeys.envIdentifier)
                                .is(envIdentifier)
                                .and(InfrastructureEntityKeys.deploymentType)
                                .is(deploymentType);

    Query query = new Query(baseCriteria).with(Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    return mongoTemplate.find(query, InfrastructureEntity.class);
  }

  @Override
  public List<InfrastructureEntity> findAllFromProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier);

    Query query = new Query(baseCriteria);
    return mongoTemplate.find(query, InfrastructureEntity.class);
  }

  @Override
  public Optional<InfrastructureEntity>
  findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String environmentIdentifier, String infraIdentifier,
      boolean loadFromCache, boolean loadFromFallbackBranch) {
    if (EmptyPredicate.isEmpty(infraIdentifier)) {
      return Optional.empty();
    }
    Query query = new Query(buildCriteriaForInfrastructureIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, infraIdentifier));
    InfrastructureEntity savedEntity = mongoTemplate.findOne(query, InfrastructureEntity.class);

    if (savedEntity == null) {
      return Optional.empty();
    }

    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      // fetch yaml from git
      String branchName = gitAwareEntityHelper.getWorkingBranch(savedEntity.getRepo());
      if (loadFromFallbackBranch) {
        savedEntity = fetchRemoteEntityWithFallBackBranch(
            accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      } else {
        savedEntity = fetchRemoteEntity(
            accountIdentifier, orgIdentifier, projectIdentifier, savedEntity, branchName, loadFromCache);
      }
    }

    return Optional.of(savedEntity);
  }

  private InfrastructureEntity updateInfrastructureEntityInMongo(
      Query query, Update update, RetryPolicy<Object> retryPolicy) {
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InfrastructureEntity.class));
  }

  private InfrastructureEntity fetchRemoteEntity(String accountId, String orgIdentifier, String projectIdentifier,
      InfrastructureEntity savedEntity, String branchName, boolean loadFromCache) {
    return (InfrastructureEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
        Scope.of(accountId, orgIdentifier, projectIdentifier),
        GitContextRequestParams.builder()
            .branchName(branchName)
            .connectorRef(savedEntity.getConnectorRef())
            .filePath(savedEntity.getFilePath())
            .repoName(savedEntity.getRepo())
            .entityType(EntityType.INFRASTRUCTURE)
            .loadFromCache(loadFromCache)
            .build(),
        Collections.emptyMap());
  }

  private InfrastructureEntity fetchRemoteEntityWithFallBackBranch(String accountId, String orgIdentifier,
      String projectIdentifier, InfrastructureEntity savedEntity, String branch, boolean loadFromCache) {
    try {
      savedEntity = fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, branch, loadFromCache);
    } catch (WingsException ex) {
      String fallBackBranch = savedEntity.getFallBackBranch();
      GitAwareContextHelper.setIsDefaultBranchInGitEntityInfoWithParameter(savedEntity.getFallBackBranch());
      if (shouldRetryWithFallBackBranch(GitXUtils.getScmExceptionIfExists(ex), branch, fallBackBranch)) {
        log.info(String.format(
            "Retrieving Infrastructure [%s] from fall back branch [%s] ", savedEntity.getIdentifier(), fallBackBranch));
        savedEntity =
            fetchRemoteEntity(accountId, orgIdentifier, projectIdentifier, savedEntity, fallBackBranch, loadFromCache);
      } else {
        throw ex;
      }
    }
    return savedEntity;
  }

  boolean shouldRetryWithFallBackBranch(ScmException scmException, String branchTried, String fallbackBranch) {
    return scmException != null && SCM_BAD_REQUEST.equals(scmException.getCode())
        && (isNotEmpty(fallbackBranch) && !branchTried.equals(fallbackBranch));
  }

  @Override
  public UpdateResult batchUpdateInfrastructure(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifierList, Update update) {
    Criteria baseCriteria = Criteria.where(InfrastructureEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(InfrastructureEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(InfrastructureEntityKeys.projectIdentifier)
                                .is(projectIdentifier)
                                .and(InfrastructureEntityKeys.envIdentifier)
                                .is(envIdentifier)
                                .and(InfrastructureEntityKeys.identifier)
                                .in(infraIdentifierList);
    Query query = new Query(baseCriteria);
    return mongoTemplate.updateMulti(query, update, InfrastructureEntity.class);
  }
  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }

  private void addGitParamsToInfrastructureEntity(InfrastructureEntity infrastructure, GitEntityInfo gitEntityInfo) {
    infrastructure.setStoreType(StoreType.REMOTE);
    if (EmptyPredicate.isEmpty(infrastructure.getRepoURL())) {
      infrastructure.setRepoURL(gitAwareEntityHelper.getRepoUrl(
          infrastructure.getAccountId(), infrastructure.getOrgIdentifier(), infrastructure.getProjectIdentifier()));
    }
    infrastructure.setConnectorRef(gitEntityInfo.getConnectorRef());
    infrastructure.setRepo(gitEntityInfo.getRepoName());
    infrastructure.setFilePath(gitEntityInfo.getFilePath());
    infrastructure.setFallBackBranch(gitEntityInfo.getBranch());
  }

  private Criteria buildCriteriaForInfrastructureIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String environmentIdentifier, String infraIdentifier) {
    return Criteria.where(InfrastructureEntityKeys.accountId)
        .is(accountIdentifier)
        .and(InfrastructureEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(InfrastructureEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(InfrastructureEntityKeys.envIdentifier)
        .is(environmentIdentifier)
        .and(InfrastructureEntityKeys.identifier)
        .is(infraIdentifier);
  }
}
