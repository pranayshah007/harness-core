/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.orchestrationEventLog;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationEventLog;
import io.harness.beans.OrchestrationEventLog.OrchestrationEventLogKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class OrchestrationEventLogRepositoryCustomImpl implements OrchestrationEventLogRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public List<OrchestrationEventLog> findUnprocessedEvents(
      String planExecutionId, long lastUpdatedAt, int thresholdLog) {
    Criteria criteria = Criteria.where(OrchestrationEventLogKeys.planExecutionId).is(planExecutionId);
    criteria.andOperator(Criteria.where(OrchestrationEventLogKeys.createdAt).gt(lastUpdatedAt));
    Query query = new Query(criteria).with(Sort.by(Sort.Order.asc("createdAt"))).limit(thresholdLog);
    return mongoTemplate.find(query, OrchestrationEventLog.class);
  }

  @Override
  public boolean checkIfAnyUnprocessedEvents(String planExecutionId, long lastUpdatedAt) {
    Criteria criteria = Criteria.where(OrchestrationEventLogKeys.planExecutionId).is(planExecutionId);
    criteria.andOperator(Criteria.where(OrchestrationEventLogKeys.createdAt).gt(lastUpdatedAt));
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, OrchestrationEventLog.class);
  }

  @Override
  public void deleteAllOrchestrationLogEvents(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    // Uses - planExecutionId_createdAt idx
    Criteria criteria = where(OrchestrationEventLogKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting OrchestrationEventLog; attempt: {}",
            "[Failed]: Failed deleting OrchestrationEventLog; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, OrchestrationEventLog.class));
  }
}
