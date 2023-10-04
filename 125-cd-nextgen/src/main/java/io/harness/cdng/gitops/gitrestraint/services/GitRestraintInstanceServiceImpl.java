/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.gitrestraint.services;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.RunnableConsumers;
import io.harness.distribution.constraint.UnableToLoadConstraintException;
import io.harness.distribution.constraint.UnableToSaveConstraintException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitopsprovider.entity.GitRestraintInstance;
import io.harness.gitopsprovider.entity.GitRestraintInstance.GitRestraintInstanceBuilder;
import io.harness.gitopsprovider.entity.GitRestraintInstance.GitRestraintInstanceKeys;
import io.harness.gitopsprovider.entity.GitRestraintInstanceResponseData;
import io.harness.persistence.HPersistence;
import io.harness.repositories.GitRestraintInstanceRepository;
import io.harness.springdata.SpringDataMongoUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class GitRestraintInstanceServiceImpl implements GitRestraintInstanceService {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GitRestraintInstanceRepository gitRestraintInstanceRepository;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public Constraint createAbstraction(String tokenRef) {
    return Constraint.builder()
        .spec(Constraint.Spec.builder().limits(1).strategy(Constraint.Strategy.FIFO).build())
        .id(new ConstraintId(tokenRef))
        .build();
  }

  @Override
  public List<GitRestraintInstance> getAllActiveAndBlockedByResourceUnit(String resourceUnit) {
    Query query = query(new Criteria().andOperator(where(GitRestraintInstanceKeys.resourceUnit).is(resourceUnit),
        where(GitRestraintInstanceKeys.state).in(BLOCKED, ACTIVE)));
    return mongoTemplate.find(query, GitRestraintInstance.class);
  }

  @Override
  public int getMaxOrder(String resourceUnit) {
    Optional<GitRestraintInstance> instance =
        gitRestraintInstanceRepository.findFirstByResourceUnitOrderByOrderDesc(resourceUnit);

    return instance.map(GitRestraintInstance::getOrder).orElse(0);
  }

  @Override
  public List<GitRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId) {
    return gitRestraintInstanceRepository.findAllByReleaseEntityIdAndStateIn(
        releaseEntityId, EnumSet.of(ACTIVE, BLOCKED));
  }

  @Override
  public GitRestraintInstance finishInstance(String uuid) {
    Query query = query(where(GitRestraintInstanceKeys.uuid).is(uuid))
                      .addCriteria(where(GitRestraintInstanceKeys.state).in(EnumSet.of(ACTIVE, BLOCKED)));
    Update update = new Update().set(GitRestraintInstanceKeys.state, FINISHED);
    GitRestraintInstance modified =
        mongoTemplate.findAndModify(query, update, SpringDataMongoUtils.returnNewOptions, GitRestraintInstance.class);
    if (modified == null || modified.getState() != FINISHED) {
      log.error("Cannot unblock constraint" + uuid);
      return null;
    }
    return modified;
  }

  @Override
  public void updateBlockedConstraints(String constraintUnit) {
    final Constraint constraint = createAbstraction(constraintUnit);
    final RunnableConsumers runnableConsumers =
        constraint.runnableConsumers(new ConstraintUnit(constraintUnit), getRegistry(), false);
    for (ConsumerId consumerId : runnableConsumers.getConsumerIds()) {
      if (!constraint.consumerUnblocked(new ConstraintUnit(constraintUnit), consumerId, null, getRegistry())) {
        break;
      }
    }
  }

  public ConstraintRegistry getRegistry() {
    return this;
  }

  @Override
  public GitRestraintInstance save(GitRestraintInstance gitRestraintInstance) {
    return HPersistence.retry(() -> gitRestraintInstanceRepository.save(gitRestraintInstance));
  }

  @Override
  public void activateBlockedInstance(String uuid, String resourceUnit) {
    GitRestraintInstance instance =
        gitRestraintInstanceRepository
            .findByUuidAndResourceUnitAndStateIn(uuid, resourceUnit, Collections.singletonList(BLOCKED))
            .orElseThrow(() -> new InvalidRequestException("Cannot find GitRestraintInstance with id [" + uuid + "]."));

    instance.setState(ACTIVE);
    instance.setAcquireAt(System.currentTimeMillis());

    save(instance);
  }

  @Override
  public void save(ConstraintId id, Constraint.Spec spec) throws UnableToSaveConstraintException {}

  @Override
  public Constraint load(ConstraintId id) throws UnableToLoadConstraintException {
    return null;
  }

  @Override
  public List<Consumer> loadConsumers(ConstraintId id, ConstraintUnit unit, boolean hitSecondaryNode) {
    List<Consumer> consumers = new ArrayList<>();

    List<GitRestraintInstance> instances = getAllActiveAndBlockedByResourceUnit(unit.getValue());

    instances.forEach(instance
        -> consumers.add(
            Consumer.builder()
                .id(new ConsumerId(instance.getUuid()))
                .state(instance.getState())
                .permits(instance.getPermits())
                .context(ImmutableMap.of(GitRestraintInstanceKeys.releaseEntityId, instance.getReleaseEntityId()))
                .build()));
    return consumers;
  }

  @Override
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning) {
    final GitRestraintInstanceBuilder builder =
        GitRestraintInstance.builder()
            .uuid(consumer.getId().getValue())
            .resourceUnit(unit.getValue())
            .releaseEntityId((String) consumer.getContext().get(GitRestraintInstanceKeys.releaseEntityId))
            .permits(consumer.getPermits())
            .state(consumer.getState())
            .order((int) consumer.getContext().get(GitRestraintInstanceKeys.order));

    if (ACTIVE == consumer.getState()) {
      builder.acquireAt(System.currentTimeMillis());
    }

    try {
      save(builder.build());
    } catch (DuplicateKeyException e) {
      log.error("Failed to add GitRestraintInstance", e);
      return false;
    }

    return true;
  }

  @Override
  public boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context) {
    final int order = getMaxOrder(id.getValue()) + 1;
    if (order == (int) context.get(GitRestraintInstanceKeys.order)) {
      return false;
    }
    context.put(GitRestraintInstanceKeys.order, order);
    return true;
  }

  @Override
  public boolean consumerUnblocked(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    activateBlockedInstance(consumerId.getValue(), unit.getValue());
    ResponseData responseData = GitRestraintInstanceResponseData.builder().resourceUnit(unit.getValue()).build();
    waitNotifyEngine.doneWith(consumerId.getValue(), responseData);
    return true;
  }

  @Override
  public boolean consumerFinished(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    return false;
  }

  @Override
  public boolean overlappingScope(Consumer consumer, Consumer blockedConsumer) {
    return false;
  }
}
