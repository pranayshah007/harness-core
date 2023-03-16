/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.entity.FreezeConfigEntity.FreezeConfigEntityKeys;

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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
  public boolean delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy("[Retrying]: Failed deleting Freeze Config; attempt: {}",
        "[Failed]: Failed deleting Freeze Config; attempt: {}");
    DeleteResult deleteResult =
        Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, FreezeConfigEntity.class));
    return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
  }

  @Override
  public Optional<FreezeConfigEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String freezeId) {
    final Criteria criteria =
        getCriteria(accountId, orgIdentifier, projectIdentifier).and(FreezeConfigEntityKeys.identifier).is(freezeId);
    FreezeConfigEntity eg = mongoTemplate.findOne(new Query(criteria), FreezeConfigEntity.class);
    return Optional.ofNullable(eg);
  }

  @Override
  public List<FreezeConfigEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> freezeIdList) {
    final Criteria criteria = getCriteria(accountId, orgIdentifier, projectIdentifier)
                                  .and(FreezeConfigEntityKeys.identifier)
                                  .in(freezeIdList);
    return mongoTemplate.find(new Query(criteria), FreezeConfigEntity.class);
  }

  @Override
  public Optional<FreezeConfigEntity> findGlobalByAccountIdAndOrgIdentifierAndProjectIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, FreezeStatus freezeStatus) {
    final Criteria criteria =
        getCriteria(accountId, orgIdentifier, projectIdentifier).and(FreezeConfigEntityKeys.type).is(FreezeType.GLOBAL);
    if (freezeStatus != null) {
      criteria.and(FreezeConfigEntityKeys.status).is(freezeStatus);
    }
    FreezeConfigEntity eg = mongoTemplate.findOne(new Query(criteria), FreezeConfigEntity.class);
    return Optional.ofNullable(eg);
  }

  private Criteria getCriteria(String accountId, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(FreezeConfigEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(FreezeConfigEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(FreezeConfigEntityKeys.accountId)
        .is(accountId);
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
