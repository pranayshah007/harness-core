/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;
import io.harness.freeze.helpers.FreezeFilterHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class FreezeRepositoryCustomImpl implements FreezeRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
  private final int MAX_ATTEMPTS = 3;

  @Override
  public Page<FreezeConfigEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<FreezeConfigEntity> freezeConfigEntities = mongoTemplate.find(query, FreezeConfigEntity.class);
    return PageableExecutionUtils.getPage(freezeConfigEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), FreezeConfigEntity.class));
  }

  @Override
  public FreezeConfigEntity upsert(Criteria criteria, FreezeConfigEntity freezeConfigEntity) {
    Query query = new Query(criteria);
    Update update = FreezeFilterHelper.getUpdateOperations(freezeConfigEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed upserting Freeze Config; attempt: {}",
        "[Failed]: Failed upserting Freeze Config; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true).upsert(true), FreezeConfigEntity.class));
  }

  @Override
  public FreezeConfigEntity update(Criteria criteria, FreezeConfigEntity freezeConfigEntity) {
    Query query = new Query(criteria);
    Update update = FreezeFilterHelper.getUpdateOperations(freezeConfigEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed updating Freeze Config; attempt: {}",
        "[Failed]: Failed updating Freeze Config; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), FreezeConfigEntity.class));
  }

  @Override
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Freeze Config; attempt: {}",
        "[Failed]: Failed deleting Freeze Config; attempt: {}");
    DeleteResult deleteResult =
        Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, FreezeConfigEntity.class));
    return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
  }

  @Override
  public DeleteResult deleteMany(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Freeze Configs; attempt: {}",
        "[Failed]: Failed deleting Freeze Configs; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, FreezeConfigEntity.class));
  }

  @Override
  public Optional<FreezeConfigEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String freezeId) {
    final Criteria criteria = Criteria.where(FreezeConfigEntityKeys.projectIdentifier)
                                  .is(projectIdentifier)
                                  .and(FreezeConfigEntityKeys.orgIdentifier)
                                  .is(orgIdentifier)
                                  .and(FreezeConfigEntityKeys.accountId)
                                  .is(accountId)
                                  .and(FreezeConfigEntityKeys.identifier)
                                  .is(freezeId);
    FreezeConfigEntity eg = mongoTemplate.findOne(new Query(criteria), FreezeConfigEntity.class);
    return Optional.ofNullable(eg);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .handle(DuplicateKeyException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
