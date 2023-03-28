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
import java.util.List;
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
  public void update(String planExecutionId, Update update) {
    String lockName = String.format(PLAN_EXPANSION_LOCK_PREFIX, planExecutionId);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.error("Unable to acquire lock while adding json");
      }
      Criteria criteria = Criteria.where("planExecutionId").is(planExecutionId);
      Query query = new Query(criteria);
      mongoTemplate.findAndModify(query, update, PlanExecutionExpansion.class);
    }
  }

  @Override
  public PlanExecutionExpansion find(Query query) {
    return mongoTemplate.findOne(query, PlanExecutionExpansion.class);
  }

  @Override
  public List<PlanExecutionExpansion> findAll(Query query) {
    return mongoTemplate.find(query, PlanExecutionExpansion.class);
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
}
