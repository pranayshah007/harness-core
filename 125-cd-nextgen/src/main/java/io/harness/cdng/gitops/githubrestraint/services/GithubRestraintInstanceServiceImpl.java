/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

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
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceBuilder;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;
import io.harness.gitopsprovider.entity.GithubRestraintInstanceResponseData;
import io.harness.persistence.HPersistence;
import io.harness.repositories.GithubRestraintInstanceRepository;
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
public class GithubRestraintInstanceServiceImpl implements GithubRestraintInstanceService {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GithubRestraintInstanceRepository githubRestraintInstanceRepository;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public Constraint createAbstraction(String tokenRef) {
    return Constraint.builder()
        .spec(Constraint.Spec.builder().limits(1).strategy(Constraint.Strategy.FIFO).build())
        .id(new ConstraintId(tokenRef))
        .build();
  }

  @Override
  public List<GithubRestraintInstance> getAllActiveAndBlockedByResourceUnit(String resourceUnit) {
    Query query = query(new Criteria().andOperator(where(GithubRestraintInstanceKeys.resourceUnit).is(resourceUnit),
        where(GithubRestraintInstanceKeys.state).in(BLOCKED, ACTIVE)));
    return mongoTemplate.find(query, GithubRestraintInstance.class);
  }

  @Override
  public int getMaxOrder(String resourceUnit) {
    Optional<GithubRestraintInstance> instance =
        githubRestraintInstanceRepository.findFirstByResourceUnitOrderByOrderDesc(resourceUnit);

    return instance.map(GithubRestraintInstance::getOrder).orElse(0);
  }

  @Override
  public List<GithubRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId) {
    return githubRestraintInstanceRepository.findAllByReleaseEntityIdAndStateIn(
        releaseEntityId, EnumSet.of(ACTIVE, BLOCKED));
  }

  @Override
  public GithubRestraintInstance finishInstance(String uuid) {
    Query query = query(where(GithubRestraintInstanceKeys.uuid).is(uuid))
                      .addCriteria(where(GithubRestraintInstanceKeys.state).in(EnumSet.of(ACTIVE, BLOCKED)));
    Update update = new Update().set(GithubRestraintInstanceKeys.state, FINISHED);
    GithubRestraintInstance modified = mongoTemplate.findAndModify(
        query, update, SpringDataMongoUtils.returnNewOptions, GithubRestraintInstance.class);
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
  public GithubRestraintInstance save(GithubRestraintInstance githubRestraintInstance) {
    return HPersistence.retry(() -> githubRestraintInstanceRepository.save(githubRestraintInstance));
  }

  @Override
  public GithubRestraintInstance activateBlockedInstance(String uuid, String resourceUnit) {
    GithubRestraintInstance instance =
        githubRestraintInstanceRepository
            .findByUuidAndResourceUnitAndStateIn(uuid, resourceUnit, Collections.singletonList(BLOCKED))
            .orElseThrow(
                () -> new InvalidRequestException("Cannot find GithubRestraintInstance with id [" + uuid + "]."));

    instance.setState(ACTIVE);
    instance.setAcquireAt(System.currentTimeMillis());

    return save(instance);
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

    List<GithubRestraintInstance> instances = getAllActiveAndBlockedByResourceUnit(unit.getValue());

    instances.forEach(instance
        -> consumers.add(
            Consumer.builder()
                .id(new ConsumerId(instance.getUuid()))
                .state(instance.getState())
                .permits(instance.getPermits())
                .context(ImmutableMap.of(GithubRestraintInstanceKeys.releaseEntityId, instance.getReleaseEntityId()))
                .build()));
    return consumers;
  }

  @Override
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning) {
    final GithubRestraintInstanceBuilder builder =
        GithubRestraintInstance.builder()
            .uuid(consumer.getId().getValue())
            .resourceUnit(unit.getValue())
            .releaseEntityId((String) consumer.getContext().get(GithubRestraintInstanceKeys.releaseEntityId))
            .permits(consumer.getPermits())
            .state(consumer.getState())
            .order((int) consumer.getContext().get(GithubRestraintInstanceKeys.order));

    if (ACTIVE == consumer.getState()) {
      builder.acquireAt(System.currentTimeMillis());
    }

    try {
      save(builder.build());
    } catch (DuplicateKeyException e) {
      log.error("Failed to add GithubRestraintInstance", e);
      return false;
    }

    return true;
  }

  @Override
  public boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context) {
    final int order = getMaxOrder(id.getValue()) + 1;
    if (order == (int) context.get(GithubRestraintInstanceKeys.order)) {
      return false;
    }
    context.put(GithubRestraintInstanceKeys.order, order);
    return true;
  }

  @Override
  public boolean consumerUnblocked(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    activateBlockedInstance(consumerId.getValue(), unit.getValue());
    ResponseData responseData = GithubRestraintInstanceResponseData.builder().resourceUnit(unit.getValue()).build();
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
