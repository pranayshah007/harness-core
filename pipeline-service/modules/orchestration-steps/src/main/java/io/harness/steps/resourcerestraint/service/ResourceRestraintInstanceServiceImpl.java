/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.stream.Collectors.toList;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.RunnableConsumers;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.ResourceRestraintInstanceRepository;
import io.harness.springdata.SpringDataMongoUtils;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Slf4j
public class ResourceRestraintInstanceServiceImpl implements ResourceRestraintInstanceService {
  @Inject private ResourceRestraintInstanceRepository restraintInstanceRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private ResourceRestraintRegistry resourceRestraintRegistry;
  @Inject private ResourceRestraintService resourceRestraintService;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public ResourceRestraintInstance save(ResourceRestraintInstance resourceRestraintInstance) {
    return HPersistence.retry(() -> restraintInstanceRepository.save(resourceRestraintInstance));
  }

  @Override
  public void deleteInstancesForGivenReleaseType(Set<String> releaseEntityIds, HoldingScope holdingScope) {
    if (EmptyPredicate.isEmpty(releaseEntityIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - releaseEntityType_releaseEntityId_idx
      Query query = query(where(ResourceRestraintInstanceKeys.releaseEntityType).is(holdingScope.name()))
                        .addCriteria(where(ResourceRestraintInstanceKeys.releaseEntityId).in(releaseEntityIds));
      mongoTemplate.remove(query, ResourceRestraintInstance.class);
      return true;
    });
  }

  @Override
  public ResourceRestraintInstance activateBlockedInstance(String uuid, String resourceUnit) {
    ResourceRestraintInstance instance =
        restraintInstanceRepository
            .findByUuidAndResourceUnitAndStateIn(uuid, resourceUnit, Collections.singletonList(BLOCKED))
            .orElseThrow(
                () -> new InvalidRequestException("Cannot find ResourceRestraintInstance with id [" + uuid + "]."));

    instance.setState(ACTIVE);
    instance.setAcquireAt(System.currentTimeMillis());

    return save(instance);
  }

  @Override
  public ResourceRestraintInstance finishInstance(String uuid, String resourceUnit) {
    Query query = query(where(ResourceRestraintInstanceKeys.uuid).is(uuid))
                      .addCriteria(where(ResourceRestraintInstanceKeys.state).in(EnumSet.of(ACTIVE, BLOCKED)));
    Update update = new Update().set(ResourceRestraintInstanceKeys.state, FINISHED);
    ResourceRestraintInstance modified = mongoTemplate.findAndModify(
        query, update, SpringDataMongoUtils.returnNewOptions, ResourceRestraintInstance.class);

    if (modified == null || modified.getState() != FINISHED) {
      log.error("Cannot unblock constraint" + uuid);
      return null;
    }
    return modified;
  }

  @Override
  public boolean updateActiveConstraintsForInstance(ResourceRestraintInstance instance) {
    boolean finished;
    String releaseEntityId = instance.getReleaseEntityId();
    try {
      HoldingScope scope = HoldingScope.valueOf(instance.getReleaseEntityType());
      switch (scope) {
        case PLAN:
        case PIPELINE:
          Status status;
          try {
            status = planExecutionService.getStatus(releaseEntityId);
            finished = StatusUtils.finalStatuses().contains(status);
          } catch (Exception e) {
            log.warn("Plan Execution doesn't for the releaseEntityId - " + releaseEntityId);
            finished = false;
          }
          break;
        case STAGE:
          NodeExecution nodeExecution =
              nodeExecutionService.getWithFieldsIncluded(releaseEntityId, ImmutableSet.of(NodeExecutionKeys.status));
          finished = nodeExecution != null
              && (StatusUtils.finalStatuses().contains(nodeExecution.getStatus())
                  || DISCONTINUING == nodeExecution.getStatus());
          break;
        default:
          throw new IllegalStateException(String.format("Scope [%s] not supported", scope));
      }
    } catch (Exception ex) {
      log.error("Error Updating active constraints", ex);
      return false;
    }

    if (!finished) {
      return false;
    }

    return resourceRestraintRegistry.consumerFinished(new ConstraintId(instance.getResourceRestraintId()),
        new ConstraintUnit(instance.getResourceUnit()), new ConsumerId(instance.getUuid()), ImmutableMap.of());
  }

  @Override
  public void updateBlockedConstraints(Set<String> constraints) {
    List<? extends ResourceRestraint> restraintList = resourceRestraintService.getConstraintsIn(constraints);
    for (ResourceRestraint restraint : restraintList) {
      final Constraint constraint = createAbstraction(restraint);
      final List<ConstraintUnit> units = units(restraint);
      if (isEmpty(units)) {
        continue;
      }

      log.info("Resource constraint {} has running units {}", restraint.getUuid(), Joiner.on(", ").join(units));

      units.forEach(unit -> {
        final RunnableConsumers runnableConsumers =
            constraint.runnableConsumers(unit, resourceRestraintRegistry, false);
        for (ConsumerId consumerId : runnableConsumers.getConsumerIds()) {
          if (!constraint.consumerUnblocked(unit, consumerId, null, resourceRestraintRegistry)) {
            break;
          }
        }
      });
    }
  }

  @Override
  public List<ResourceRestraintInstance> getAllByRestraintIdAndResourceUnitAndStates(
      String resourceRestraintId, String resourceUnit, List<Consumer.State> states) {
    Query query = query(
        new Criteria().andOperator(where(ResourceRestraintInstanceKeys.resourceRestraintId).is(resourceRestraintId),
            where(ResourceRestraintInstanceKeys.resourceUnit).is(resourceUnit),
            where(ResourceRestraintInstanceKeys.state).in(BLOCKED, ACTIVE)));

    return mongoTemplate.find(query, ResourceRestraintInstance.class);
  }

  @Override
  public Constraint createAbstraction(ResourceRestraint resourceRestraint) {
    return Constraint.builder()
        .id(new ConstraintId(resourceRestraint.getUuid()))
        .spec(Constraint.Spec.builder()
                  .limits(resourceRestraint.getCapacity())
                  .strategy(resourceRestraint.getStrategy())
                  .build())
        .build();
  }

  @Override
  public int getMaxOrder(String resourceRestraintId) {
    Optional<ResourceRestraintInstance> instance =
        restraintInstanceRepository.findFirstByResourceRestraintIdOrderByOrderDesc(resourceRestraintId);

    return instance.map(ResourceRestraintInstance::getOrder).orElse(0);
  }

  @Override
  public void processRestraint(ResourceRestraintInstance instance) {
    String constraintId = instance.getResourceRestraintId();
    boolean toUnblock = false;
    try (AutoLogContext ignore = instance.autoLogContext()) {
      if (BLOCKED == instance.getState()) {
        // If the restraint is blocked then we try to unblock
        toUnblock = true;
      } else if (ACTIVE == instance.getState()) {
        // If the restraint is active then we try to move this to finished
        if (updateActiveConstraintsForInstance(instance)) {
          // If this is finished successfully then we try to unblock the next restraint based in constraint id
          log.info("The following resource constraint needs to be unblocked: {}", constraintId);
          toUnblock = true;
        }
      }
      if (toUnblock) {
        // unblock the constraints
        updateBlockedConstraints(ImmutableSet.of(constraintId));
      }
    }
  }

  @Override
  public List<ResourceRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId) {
    return restraintInstanceRepository.findAllByReleaseEntityIdAndStateIn(releaseEntityId, EnumSet.of(ACTIVE, BLOCKED));
  }

  @Override
  public int getAllCurrentlyAcquiredPermits(HoldingScope scope, String releaseEntityId, String resourceUnit) {
    int currentPermits = 0;

    // THE PERMITS SHOULD BE CONSIDERED FOR THE SAME releaseEntityType, releaseEntityId AND resourceUnit ONLY FOR THE
    // ACTIVE STATE. WHEN STATE IS BLOCKED IT DOES NOT HOLD A PERMITS YET.
    List<ResourceRestraintInstance> instances =
        restraintInstanceRepository.findByReleaseEntityTypeAndReleaseEntityIdAndResourceUnitAndState(
            scope.name(), releaseEntityId, resourceUnit, ACTIVE);
    if (isNotEmpty(instances)) {
      for (ResourceRestraintInstance instance : instances) {
        currentPermits += instance.getPermits();
      }
    }

    return currentPermits;
  }

  private List<ConstraintUnit> units(ResourceRestraint constraint) {
    Set<String> units = new HashSet<>();

    Query query = query(
        new Criteria().andOperator(where(ResourceRestraintInstanceKeys.resourceRestraintId).is(constraint.getUuid()),
            where(ResourceRestraintInstanceKeys.state).in(BLOCKED, ACTIVE)));

    List<ResourceRestraintInstance> instances = mongoTemplate.find(query, ResourceRestraintInstance.class);
    for (ResourceRestraintInstance instance : instances) {
      units.add(instance.getResourceUnit());
    }

    return units.stream().map(ConstraintUnit::new).collect(toList());
  }
}
