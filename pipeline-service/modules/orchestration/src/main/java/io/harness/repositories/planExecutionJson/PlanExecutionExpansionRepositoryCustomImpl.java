/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.repositories.planExecutionJson;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionExpansion;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class PlanExecutionExpansionRepositoryCustomImpl implements PlanExecutionExpansionRepositoryCustom {
  private static final String PLAN_EXPANSION_LOCK_PREFIX = "planExpansion_%s";
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;

  @Inject
  public PlanExecutionExpansionRepositoryCustomImpl(MongoTemplate mongoTemplate, PersistentLocker persistentLocker) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void update(String planExecutionId, Update update, long lockTimeoutInMinutes) {
    try {
      String lockName = String.format(PLAN_EXPANSION_LOCK_PREFIX, planExecutionId);
      Duration lockTimeOut = Duration.ofMinutes(lockTimeoutInMinutes);
      Duration releaseTimeOut = Duration.ofMinutes(lockTimeoutInMinutes + 1);

      try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(lockName, lockTimeOut, releaseTimeOut)) {
        if (lock == null) {
          log.error("Unable to acquire lock while adding json");
        }
        long startTs = System.currentTimeMillis();
        Criteria criteria = Criteria.where("planExecutionId").is(planExecutionId);
        Query query = new Query(criteria);
        mongoTemplate.findAndModify(query, update, PlanExecutionExpansion.class);
        long timeForUpdate = System.currentTimeMillis() - startTs;
        if (timeForUpdate > 500) {
          log.warn("Time taken to update the json is greater than 500ms. Please investigate");
        }
      }
    } catch (Exception ex) {
      log.error("Unable to update expanded json", ex);
    }
  }

  @Override
  public PlanExecutionExpansion find(Query query) {
    return mongoTemplate.findOne(query, PlanExecutionExpansion.class);
  }

  @Override
  public void deleteAllExpansions(Set<String> planExecutionIds) {
    Criteria criteria = where("planExecutionId").in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting PlanExecutionExpansion entity; attempt: {}",
            "[Failed]: Failed deleting PlanExecutionExpansion entity; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, PlanExecutionExpansion.class));
  }

  @Override
  public void multiUpdate(Query query, Update updateOps) {
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed updating PlanExecutionExpansion entity; attempt: {}",
            "[Failed]: Failed updating PlanExecutionExpansion entity; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.updateMulti(query, updateOps, PlanExecutionExpansion.class));
  }
}
