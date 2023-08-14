/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.OutcomeInstance;
import io.harness.data.OutcomeInstance.OutcomeInstanceKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.springdata.PersistenceUtils;
import io.harness.springdata.TransactionHelper;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.DuplicateKeyException;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.jexl3.JexlException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsOutcomeServiceImpl implements PmsOutcomeService {
  @Inject private ExpressionEvaluatorProvider expressionEvaluatorProvider;
  @Inject private Injector injector;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PlanExpansionService planExpansionService;

  @Inject private TransactionHelper transactionHelper;

  @Override
  public String resolve(Ambiance ambiance, RefObject refObject) {
    if (EmptyPredicate.isNotEmpty(refObject.getProducerId())) {
      return resolveUsingProducerSetupId(ambiance, refObject);
    }
    if (!refObject.getName().contains(".")) {
      // It is not an expression-like ref-object.
      return resolveUsingRuntimeId(ambiance, refObject);
    }

    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());
    String valueUsingFullyQualifiedName = resolveUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
    if (valueUsingFullyQualifiedName == null) {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        log.warn(String.format("Not able to find the outcome using fullyQualifiedName: %s", fullyQualifiedName));
      }
      EngineExpressionEvaluator evaluator =
          expressionEvaluatorProvider.get(null, ambiance, EnumSet.of(NodeExecutionEntityType.OUTCOME), true);
      injector.injectMembers(evaluator);
      Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
      if (value != null) {
        // Add a log line
      }
      return value == null ? null : RecastOrchestrationUtils.toJson(value);
    }
    return valueUsingFullyQualifiedName;
  }

  @Override
  public String consumeInternal(Ambiance ambiance, Level producedBy, String name, String value, String groupName) {
    try {
      return transactionHelper.performTransaction(() -> {
        OutcomeInstance instance = mongoTemplate.insert(
            OutcomeInstance.builder()
                .uuid(generateUuid())
                .planExecutionId(ambiance.getPlanExecutionId())
                .stageExecutionId(ambiance.getStageExecutionId())
                .producedBy(producedBy)
                .name(name)
                .outcomeValue(PmsOutcome.parse(value))
                .groupName(groupName)
                .levelRuntimeIdIdx(ResolverUtils.prepareLevelRuntimeIdIdx(ambiance.getLevelsList()))
                .fullyQualifiedName(ExpandedJsonFunctorUtils.generateFullyQualifiedName(ambiance, name))
                .build());
        planExpansionService.addOutcomes(ambiance, name, instance.getOutcomeValue());
        return instance.getUuid();
      });
    } catch (DuplicateKeyException ex) {
      throw new OutcomeException(format("Outcome with name %s is already saved", name), ex);
    }
  }

  @Override
  public List<String> findAllByRuntimeId(String planExecutionId, String runtimeId) {
    Map<String, String> outcomesMap = findAllOutcomesMapByRuntimeId(planExecutionId, runtimeId);
    if (isEmpty(outcomesMap)) {
      return Collections.emptyList();
    }
    return new ArrayList<>(outcomesMap.values());
  }

  @Override
  public Map<String, String> findAllOutcomesMapByRuntimeId(String planExecutionId, String runtimeId) {
    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(OutcomeInstanceKeys.producedByRuntimeId).is(runtimeId))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt));

    List<OutcomeInstance> outcomeInstances = mongoTemplate.find(query, OutcomeInstance.class);
    if (isEmpty(outcomeInstances)) {
      return Collections.emptyMap();
    }

    Map<String, String> outcomesMap = new LinkedHashMap<>();
    outcomeInstances.forEach(oi -> outcomesMap.put(oi.getName(), oi.getOutcomeJsonValue()));
    return outcomesMap;
  }

  @Override
  public List<String> fetchOutcomes(List<String> outcomeInstanceIds) {
    if (isEmpty(outcomeInstanceIds)) {
      return Collections.emptyList();
    }
    List<String> outcomes = new ArrayList<>();
    Query query = query(where(OutcomeInstanceKeys.uuid).in(outcomeInstanceIds));
    Iterable<OutcomeInstance> outcomesInstances = mongoTemplate.find(query, OutcomeInstance.class);
    for (OutcomeInstance instance : outcomesInstances) {
      outcomes.add(instance.getOutcomeJsonValue());
    }
    return outcomes;
  }

  @Override
  public String fetchOutcome(@NonNull String outcomeInstanceId) {
    Query query = query(where(OutcomeInstanceKeys.uuid).is(outcomeInstanceId));
    Optional<OutcomeInstance> outcomeInstance =
        Optional.ofNullable(mongoTemplate.findOne(query, OutcomeInstance.class));
    return outcomeInstance.map(OutcomeInstance::getOutcomeJsonValue).orElse(null);
  }

  private String resolveUsingRuntimeId(@NotNull Ambiance ambiance, @NotNull RefObject refObject) {
    String name = refObject.getName();
    Query query =
        query(where(OutcomeInstanceKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
            .addCriteria(where(OutcomeInstanceKeys.name).is(name))
            .addCriteria(
                where(OutcomeInstanceKeys.levelRuntimeIdIdx).in(ResolverUtils.prepareLevelRuntimeIdIndices(ambiance)));

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same name was saved at different levels/specificity.
    OutcomeInstance instance = EmptyPredicate.isEmpty(instances)
        ? null
        : instances.stream().max(Comparator.comparing(OutcomeInstance::getLevelRuntimeIdIdx)).orElse(null);
    if (instance == null) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instance.getOutcomeJsonValue();
  }

  private String resolveUsingProducerSetupId(@NotNull Ambiance ambiance, @NotNull RefObject refObject) {
    String name = refObject.getName();

    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(OutcomeInstanceKeys.name).is(name))
                      .addCriteria(where(OutcomeInstanceKeys.producedBySetupId).is(refObject.getProducerId()))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt));

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same plan node executed multiple times.
    if (EmptyPredicate.isEmpty(instances)) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instances.get(0).getOutcomeJsonValue();
  }

  private String resolveUsingFullyQualifiedName(
      @NotNull Ambiance ambiance, @NotNull RefObject refObject, String fullyQualifiedName) {
    String name = refObject.getName();

    Query query = query(where(OutcomeInstanceKeys.planExecutionId).is(ambiance.getPlanExecutionId()))
                      .addCriteria(where(OutcomeInstanceKeys.fullyQualifiedName).is(fullyQualifiedName))
                      .with(Sort.by(Sort.Direction.DESC, OutcomeInstanceKeys.createdAt))
                      .limit(1);

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);

    // Multiple instances might be returned if the same plan node executed multiple times.
    if (EmptyPredicate.isEmpty(instances)) {
      throw new OutcomeException(format("Could not resolve outcome with name '%s'", name));
    }
    return instances.get(0).getOutcomeJsonValue();
  }

  @Override
  public List<StepOutcomeRef> fetchOutcomeRefs(String nodeExecutionId) {
    List<OutcomeInstance> instances = fetchOutcomeInstanceByRuntimeId(nodeExecutionId);
    if (isEmpty(instances)) {
      return new ArrayList<>();
    }
    return instances.stream()
        .map(oi -> StepOutcomeRef.newBuilder().setName(oi.getName()).setInstanceId(oi.getUuid()).build())
        .collect(Collectors.toList());
  }

  @Override
  public OptionalOutcome resolveOptional(Ambiance ambiance, RefObject refObject) {
    if (EmptyPredicate.isNotEmpty(refObject.getProducerId())) {
      return resolveOptionalUsingProducerSetupId(ambiance, refObject);
    }
    if (!refObject.getName().contains(".")) {
      // It is not an expression-like ref-object.
      return resolveOptionalUsingRuntimeId(ambiance, refObject);
    }

    String fullyQualifiedName = ExpandedJsonFunctorUtils.createFullQualifiedName(ambiance, refObject.getName());

    OptionalOutcome optionalOutcome = resolveOptionalUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
    if (optionalOutcome.isFound()) {
      return optionalOutcome;
    }
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      log.warn(String.format("Not able to find the outcome using fullyQualifiedName: %s", fullyQualifiedName));
    }
    EngineExpressionEvaluator evaluator =
        expressionEvaluatorProvider.get(null, ambiance, EnumSet.of(NodeExecutionEntityType.OUTCOME), true);
    injector.injectMembers(evaluator);
    try {
      Object value = evaluator.evaluateExpression(EngineExpressionEvaluator.createExpression(refObject.getName()));
      return OptionalOutcome.builder()
          .found(value != null)
          .outcome(value == null ? null : RecastOrchestrationUtils.toJson(value))
          .build();
    } catch (UnresolvedExpressionsException | JexlException ignore) {
      return OptionalOutcome.builder().found(false).build();
    }
  }

  @Override
  public List<OutcomeInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId) {
    Query query = query(where(OutcomeInstanceKeys.producedByRuntimeId).is(runtimeId));
    return mongoTemplate.find(query, OutcomeInstance.class);
  }

  @Override
  public List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionUuid) {
    List<String> outcomeUuids = new ArrayList<>();
    List<OutcomeInstance> outcomeInstances = fetchOutcomeInstanceByRuntimeId(originalNodeExecutionUuid);
    for (OutcomeInstance outcomeInstance : outcomeInstances) {
      String uuid = consume(ambiance, outcomeInstance.getName(), outcomeInstance.getOutcomeValue().toJson(),
          outcomeInstance.getGroupName());
      outcomeUuids.add(uuid);
    }
    return outcomeUuids;
  }

  @Override
  public Map<String, List<StepOutcomeRef>> fetchOutcomeRefs(List<String> nodeExecutionIds) {
    Map<String, List<StepOutcomeRef>> refMap = new HashMap<>();
    Query query = query(where(OutcomeInstanceKeys.producedByRuntimeId).in(nodeExecutionIds));
    query.fields()
        .include(OutcomeInstanceKeys.uuid)
        .include(OutcomeInstanceKeys.name)
        .include(OutcomeInstanceKeys.producedBy);

    List<OutcomeInstance> instances = mongoTemplate.find(query, OutcomeInstance.class);
    for (OutcomeInstance oi : instances) {
      StepOutcomeRef stepOutcomeRef =
          StepOutcomeRef.newBuilder().setName(oi.getName()).setInstanceId(oi.getUuid()).build();
      refMap.compute(oi.getProducedBy().getRuntimeId(), (k, v) -> {
        if (v == null) {
          return new ArrayList<>(Collections.singletonList(stepOutcomeRef));
        } else {
          v.add(stepOutcomeRef);
          return v;
        }
      });
    }
    return refMap;
  }

  @Override
  public void deleteAllOutcomesInstances(Set<String> planExecutionIds) {
    Criteria criteria = where(OutcomeInstanceKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting OutcomeInstance; attempt: {}",
            "[Failed]: Failed deleting OutcomeInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, OutcomeInstance.class));
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    Criteria criteria = where(OutcomeInstanceKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    Update ops = new Update();
    ops.set(OutcomeInstanceKeys.validUntil, ttlDate);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed updating TTL OutcomeInstance; attempt: {}",
            "[Failed]: Failed updating TTL OutcomeInstance; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> {
      UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, OutcomeInstance.class);
      if (!updateResult.wasAcknowledged()) {
        log.warn("No OutcomeInstance could be marked as updated TTL for given planExecutionIds - " + planExecutionId);
      }
      return true;
    });
  }

  private OptionalOutcome resolveOptionalUsingProducerSetupId(Ambiance ambiance, RefObject refObject) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingProducerSetupId(ambiance, refObject);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }

  private OptionalOutcome resolveOptionalUsingRuntimeId(Ambiance ambiance, RefObject refObject) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingRuntimeId(ambiance, refObject);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }

  private OptionalOutcome resolveOptionalUsingFullyQualifiedName(
      Ambiance ambiance, RefObject refObject, String fullyQualifiedName) {
    String outcome;
    boolean isResolvable;
    try {
      outcome = resolveUsingFullyQualifiedName(ambiance, refObject, fullyQualifiedName);
      isResolvable = true;
    } catch (OutcomeException ignore) {
      outcome = null;
      isResolvable = false;
    }
    return OptionalOutcome.builder().found(isResolvable).outcome(outcome).build();
  }
}
