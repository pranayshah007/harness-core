/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.service.custom;

import static io.harness.springdata.PersistenceUtils.getRetryPolicy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceGitXMetadataDTO;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.springdata.TransactionHelper;

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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ServiceRepositoryCustomImpl implements ServiceRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final TransactionHelper transactionHelper;
  private final GitSyncSdkService gitSyncSdkService;
  private final GitAwareEntityHelper gitAwareEntityHelper;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
  private final int MAX_ATTEMPTS = 3;

  @Override
  public Page<ServiceEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
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
  public ServiceEntity upsert(
      Criteria criteria, ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO) {
    Query query = new Query(criteria);
    Update update = ServiceFilterHelper.getUpdateOperations(serviceEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed upserting Service; attempt: {}", "[Failed]: Failed upserting Service; attempt: {}");
    ServiceEntity serviceEntityUpserted =
        Failsafe.with(retryPolicy)
            .get(()
                     -> mongoTemplate.findAndModify(
                         query, update, new FindAndModifyOptions().returnNew(true).upsert(true), ServiceEntity.class));
    if (serviceEntityUpserted == null) {
      log.warn("Error occurred upserting the serviceEntity, returning without upserting entity to git");
      return null;
    }

    if (serviceGitXMetadataDTO != null) {
      Scope scope = buildScope(serviceEntityUpserted);
      gitAwareEntityHelper.updateServiceEntityOnGit(
          serviceEntityUpserted, serviceEntity.getYaml(), scope, serviceGitXMetadataDTO);
      // Todo: handle git operation fail and delete db entity in that case
    }
    return serviceEntityUpserted;
  }

  @Override
  public ServiceEntity update(
      Criteria criteria, ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO) {
    Query query = new Query(criteria);
    Update update = ServiceFilterHelper.getUpdateOperations(serviceEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Service; attempt: {}", "[Failed]: Failed updating Service; attempt: {}");

    ServiceEntity serviceEntityUpdated =
        Failsafe.with(retryPolicy)
            .get(()
                     -> mongoTemplate.findAndModify(
                         query, update, new FindAndModifyOptions().returnNew(true), ServiceEntity.class));

    if (serviceEntityUpdated == null) {
      log.warn("Error occurred updating the serviceEntity, returning without updating entity to git");
      return null;
    }

    if (serviceGitXMetadataDTO != null) {
      Scope scope = buildScope(serviceEntityUpdated);
      gitAwareEntityHelper.updateServiceEntityOnGit(
          serviceEntityUpdated, serviceEntity.getYaml(), scope, serviceGitXMetadataDTO);
      // Todo: handle git operation fail and delete db entity in that case
    }
    return serviceEntityUpdated;
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
  public Optional<ServiceEntity> find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean deleted, String branch, boolean getMetadataOnly) {
    Criteria baseCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                .is(accountIdentifier)
                                .and(ServiceEntityKeys.orgIdentifier)
                                .is(orgIdentifier)
                                .and(ServiceEntityKeys.projectIdentifier)
                                .is(projectIdentifier);

    Criteria filterDeleted = Criteria.where(ServiceEntityKeys.deleted).is(deleted);
    Query query = new Query(baseCriteria.andOperator(filterDeleted));
    ServiceEntity savedEntity = mongoTemplate.findById(query, ServiceEntity.class);
    if (savedEntity == null) {
      return Optional.empty();
    }
    if (getMetadataOnly) {
      return Optional.of(savedEntity);
    }

    if (savedEntity.getStoreType() == StoreType.REMOTE) {
      // fetch yaml from git
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      savedEntity = (ServiceEntity) gitAwareEntityHelper.fetchEntityFromRemote(savedEntity,
          Scope.of(accountIdentifier, orgIdentifier, projectIdentifier),
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

  /**
   *
   * @param serviceEntity
   * @param serviceGitXMetadataDTO
   * @return serviceEntity
   */
  @Override
  public ServiceEntity save(ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO) {
    return transactionHelper.performTransaction(() -> saveServiceInternal(serviceEntity, serviceGitXMetadataDTO));
  }

  private ServiceEntity saveServiceInternal(
      ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO) {
    if (serviceGitXMetadataDTO == null) {
      serviceEntity.setStoreType(StoreType.INLINE);
    } else if (gitSyncSdkService.isGitSimplificationEnabled(serviceEntity.getAccountId(),
                   serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier())) {
      Scope scope = buildScope(serviceEntity);
      String yamlToPush = serviceEntity.getYaml();
      serviceEntity.setStoreType(StoreType.REMOTE);
      gitAwareEntityHelper.createServiceEntityOnGit(serviceEntity, serviceGitXMetadataDTO, yamlToPush, scope);
    } else {
      log.info(String.format(
          "Marking storeType as INLINE for Service with ID [%s] because Git simplification was not enabled for Project [%s] in Account [%s]",
          serviceEntity.getIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getAccountId()));
      serviceEntity.setStoreType(StoreType.INLINE);
    }
    return mongoTemplate.save(serviceEntity);
  }

  Scope buildScope(ServiceEntity serviceEntity) {
    return Scope.of(
        serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier());
  }
}
