/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.expressions.functors.ExecutionSweepingOutputFunctor;
import io.harness.engine.expressions.functors.ExpandedJsonFunctor;
import io.harness.engine.expressions.functors.NodeExecutionAncestorFunctor;
import io.harness.engine.expressions.functors.NodeExecutionChildFunctor;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.engine.expressions.functors.NodeExecutionQualifiedFunctor;
import io.harness.engine.expressions.functors.OutcomeFunctor;
import io.harness.engine.expressions.functors.SecretFunctor;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.RegexFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.XmlFunctor;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.NGJsonFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.ProcessorResult;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterDocumentField;
import io.harness.pms.yaml.ParameterDocumentFieldMapper;
import io.harness.pms.yaml.ParameterFieldProcessor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * AmbianceExpressionEvaluator is the basic expression evaluator provided by the orchestration engine. It provides
 * support for expressions based on the runtime graph, outcomes and sweeping output. It contains other helpful
 * functors like regex, json and xml. Apart from this, it also supports static and group based aliases. All these
 * concepts are explained in detail here:
 * https://harness.atlassian.net/wiki/spaces/WR/pages/722536048/Expression+Evaluation.
 *
 * In order to add support for custom expressions/functors, users need to extend this class and override 2 methods -
 * {@link #initialize()} and {@link #fetchPrefixes()}. This subclass needs a corresponding {@link
 * ExpressionEvaluatorProvider} to be provided when adding a dependency on {@link io.harness.OrchestrationModule}. For a
 * sample implementation, look at SampleExpressionEvaluator.java and SampleExpressionEvaluatorProvider.java.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
public class AmbianceExpressionEvaluator extends EngineExpressionEvaluator {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanService planService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;

  @Inject private PlanExpansionService planExpansionService;

  protected final Ambiance ambiance;
  private final Set<NodeExecutionEntityType> entityTypes;
  private final boolean refObjectSpecific;
  private final Map<String, String> groupAliases;
  private NodeExecutionsCache nodeExecutionsCache;

  @Builder
  public AmbianceExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific) {
    super(variableResolverTracker);
    this.ambiance = ambiance;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    this.refObjectSpecific = refObjectSpecific;
    this.groupAliases = new HashMap<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialize() {
    super.initialize();
    if (!refObjectSpecific) {
      // Add basic functors.
      addToContext("regex", new RegexFunctor());
      addToContext("json", new NGJsonFunctor());
      addToContext("xml", new XmlFunctor());
      addToContext("secrets", new SecretFunctor(ambiance.getExpressionFunctorToken()));
    }

    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      addToContext("outcome", OutcomeFunctor.builder().ambiance(ambiance).pmsOutcomeService(pmsOutcomeService).build());
    }

    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      addToContext("output",
          ExecutionSweepingOutputFunctor.builder()
              .pmsSweepingOutputService(pmsSweepingOutputService)
              .ambiance(ambiance)
              .build());
    }

    PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(ambiance.getPlanExecutionId());
    if (planExecution == null) {
      return;
    }

    nodeExecutionsCache = new NodeExecutionsCache(nodeExecutionService, planService, ambiance);
    // Access StepParameters and Outcomes of self and children.
    addToContext("child",
        NodeExecutionChildFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .build());
    // Access StepParameters and Outcomes of ancestors.
    addToContext("ancestor",
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .groupAliases(groupAliases)
            .build());
    // Access StepParameters and Outcomes using fully qualified names.
    addToContext("qualified",
        NodeExecutionQualifiedFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .build());

    addToContext("expandedJson",
        ExpandedJsonFunctor.builder().planExpansionService(planExpansionService).ambiance(ambiance).build());
  }

  /**
   * Add a group alias. Any expression that starts with `aliasName` will be replaced by the identifier of the first
   * ancestor node with the given groupName. Should be called within the initialize method only.
   *
   * @param aliasName   the name of the alias
   * @param groupName the name of the group
   */
  protected void addGroupAlias(@NotNull String aliasName, @NotNull String groupName) {
    if (isInitialized()) {
      return;
    }
    if (!validAliasName(aliasName)) {
      throw new InvalidRequestException("Invalid alias: " + aliasName);
    }
    groupAliases.put(aliasName, groupName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotEmpty
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      listBuilder.add("outcome");
    }
    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      listBuilder.add("output");
    }
    return listBuilder.add("expandedJson")
        .add("child")
        .add("ancestor")
        .add("qualified")
        .addAll(super.fetchPrefixes())
        .build();
  }

  @Override
  @Deprecated
  public Object resolve(Object o, boolean skipUnresolvedExpressionsCheck) {
    return resolve(o, calculateExpressionMode(skipUnresolvedExpressionsCheck));
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    return ExpressionEvaluatorUtils.updateExpressions(
        o, new AmbianceResolveFunctorImpl(this, inputSetValidatorFactory, expressionMode));
  }

  public static class AmbianceResolveFunctorImpl extends ResolveFunctorImpl {
    private final ParameterFieldProcessor parameterFieldProcessor;

    public AmbianceResolveFunctorImpl(AmbianceExpressionEvaluator expressionEvaluator,
        InputSetValidatorFactory inputSetValidatorFactory, ExpressionMode expressionMode) {
      super(expressionEvaluator, expressionMode);
      this.parameterFieldProcessor =
          new ParameterFieldProcessor(getExpressionEvaluator(), inputSetValidatorFactory, expressionMode);
    }

    @Override
    public ResolveObjectResponse processObject(Object o) {
      Optional<ParameterDocumentField> docFieldOptional = ParameterDocumentFieldMapper.fromParameterFieldMap(o);
      if (!docFieldOptional.isPresent()) {
        return new ResolveObjectResponse(false, null);
      }

      ParameterDocumentField docField = docFieldOptional.get();
      processObjectInternal(docField);

      Map<String, Object> map = (Map<String, Object>) o;
      RecastOrchestrationUtils.setEncodedValue(map, RecastOrchestrationUtils.toMap(docField));
      return new ResolveObjectResponse(true, map);
    }

    private void processObjectInternal(ParameterDocumentField documentField) {
      ProcessorResult processorResult = parameterFieldProcessor.process(documentField);
      if (processorResult.isError()) {
        throw new EngineExpressionEvaluationException(processorResult.getMessage(), processorResult.getExpression());
      }
    }
  }
}
