/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitops.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.entity.Cluster.ClusterKeys;

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

@OwnedBy(HarnessTeam.GITOPS)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class ClusterRepositoryCustomImpl implements ClusterRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(10);

  @Override
  public Page<Cluster> find(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<Cluster> projects = mongoTemplate.find(query, Cluster.class);
    return PageableExecutionUtils.getPage(
        projects, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Cluster.class));
  }

  public Cluster create(Cluster cluster) {
    // Todo(yogesh): Do we need this ?
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed upserting Cluster; attempt: {}", "[Failed]: Failed upserting Cluster; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.insert(cluster));
  }

  @Override
  public Cluster update(Criteria criteria, Cluster cluster) {
    Query query = new Query(criteria);
    Update update = getUpdateOperations(cluster);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Cluster; attempt: {}", "[Failed]: Failed updating Service; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), Cluster.class));
  }

  @Override
  public DeleteResult delete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed deleting Cluster; attempt: {}", "[Failed]: Failed deleting Service; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, Cluster.class));
  }

  @Override
  public Cluster findOne(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, Cluster.class);
  }

  private Update getUpdateOperations(Cluster cluster) {
    // Todo(yogesh): decide what needs to be updated for gitops
    return new Update().set(ClusterKeys.envRef, cluster.getEnvRef());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    int MAX_ATTEMPTS = 3;
    return new RetryPolicy<>()
        .handle(OptimisticLockingFailureException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
