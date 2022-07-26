/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.preflight;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.entity.PreFlightEntity.PreFlightEntityKeys;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PreFlightRepositoryCustomImpl implements PreFlightRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PreFlightEntity update(Criteria criteria, PreFlightEntity entity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(PreFlightEntityKeys.connectorCheckResponse, entity.getConnectorCheckResponse());
    update.set(PreFlightEntityKeys.pipelineInputResponse, entity.getPipelineInputResponse());
    RetryPolicy<Object> retryPolicy = getRetryPolicy();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PreFlightEntity.class));
  }

  @Override
  public PreFlightEntity update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy();
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), PreFlightEntity.class));
  }

  private RetryPolicy<Object> getRetryPolicy() {
    Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);
    int MAX_ATTEMPTS = 3;
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .handle(DuplicateKeyException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.info(
                "[Retrying]: Failed updating Service; attempt: {}", event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event
            -> log.error(
                "[Failed]: Failed updating Service; attempt: {}", event.getAttemptCount(), event.getFailure()));
  }
}
