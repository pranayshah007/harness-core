/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructure.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.mappers.InfrastructureFilterHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.OptimisticLockingFailureException;
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
public class InfrastructureRepositoryCustomImpl implements InfrastructureRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
  private final int MAX_ATTEMPTS = 3;

  @Override
  public Page<InfrastructureEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<InfrastructureEntity> infrastructures = mongoTemplate.find(query, InfrastructureEntity.class);
    return PageableExecutionUtils.getPage(infrastructures, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), InfrastructureEntity.class));
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
    Query query = new Query(criteria);
    Update update = InfrastructureFilterHelper.getUpdateOperations(infraEntity);

    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed updating Infrastructure; attempt: {}",
        "[Failed]: Failed updating Infrastructure; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), InfrastructureEntity.class));
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

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
