/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.expressions.functors.ExecutionSweepingOutputFunctor;
import io.harness.engine.expressions.functors.ExpandedJsonFunctor;
import io.harness.engine.expressions.functors.ExpandedJsonFunctorUtils;
import io.harness.engine.expressions.functors.NodeExecutionAncestorFunctor;
import io.harness.engine.expressions.functors.NodeExecutionChildFunctor;
import io.harness.engine.expressions.functors.NodeExecutionEntityType;
import io.harness.engine.expressions.functors.NodeExecutionQualifiedFunctor;
import io.harness.engine.expressions.functors.OutcomeFunctor;
import io.harness.engine.expressions.functors.SecretFunctor;
import io.harness.engine.expressions.functors.SecretFunctorWithRbac;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.secrets.ExpressionsObserverFactory;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.JsonFunctor;
import io.harness.expression.RegexFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.XmlFunctor;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.NGJsonFunctor;
import io.harness.expression.functors.NGShellScriptFunctor;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionEvaluatorResolver;
import io.harness.pms.expression.ProcessorResult;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterDocumentField;
import io.harness.pms.yaml.ParameterDocumentFieldMapper;
import io.harness.pms.yaml.ParameterDocumentFieldProcessor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.shell.ScriptType;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
@Slf4j
public class AmbianceExpressionEvaluator extends EngineExpressionEvaluator {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanService planService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;

  @Inject private PlanExpansionService planExpansionService;

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Inject private ExpressionsObserverFactory expressionsObserverFactory;

  protected final Ambiance ambiance;
  private final Set<NodeExecutionEntityType> entityTypes;
  private final boolean refObjectSpecific;
  private final Map<String, String> groupAliases;
  protected NodeExecutionsCache nodeExecutionsCache;
  private final String SECRETS = "secrets";

  private boolean contextMapProvided = false;

  @Builder
  public AmbianceExpressionEvaluator(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, String> contextMap) {
    super(variableResolverTracker);
    this.ambiance = ambiance;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    this.refObjectSpecific = refObjectSpecific;
    this.groupAliases = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(contextMap)) {
      // TODO(REMOVE): ENABLED_FEATURE_FLAGS_KEY is not a real contextMap entry. This we added to pass the FF to
      // engineExpressionEvaluator.
      if (contextMap.size() > 1 || !contextMap.containsKey(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY)) {
        contextMapProvided = true;
      }
      contextMap.forEach(this::addToContext);
    }
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
      addToContext("shell", new NGShellScriptFunctor(ScriptType.BASH));
      // Todo(Archit): revisit NGJsonFunctor(PIE-9772)
      if (contextMapProvided) {
        addToContext("json", new JsonFunctor(getContextMap()));
      } else {
        addToContext("json", new NGJsonFunctor());
      }
      addToContext("xml", new XmlFunctor());
      if (pmsFeatureFlagService.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.PIE_USE_SECRET_FUNCTOR_WITH_RBAC)) {
        addToContext(SECRETS,
            new SecretFunctorWithRbac(ambiance, pipelineRbacHelper,
                expressionsObserverFactory.getSubjectForSecretsRuntimeUsages(ExpressionsObserverFactory.SECRET)));
      } else {
        addToContext(SECRETS,
            new SecretFunctor(ambiance,
                expressionsObserverFactory.getSubjectForSecretsRuntimeUsages(ExpressionsObserverFactory.SECRET)));
      }
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
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .engine(getEngine())
            .build());
    // Access StepParameters and Outcomes of ancestors.
    addToContext("ancestor",
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .groupAliases(groupAliases)
            .engine(getEngine())
            .build());
    // Access StepParameters and Outcomes using fully qualified names.
    addToContext("qualified",
        NodeExecutionQualifiedFunctor.builder()
            .nodeExecutionsCache(nodeExecutionsCache)
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .nodeExecutionInfoService(nodeExecutionInfoService)
            .ambiance(ambiance)
            .entityTypes(entityTypes)
            .engine(getEngine())
            .build());
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
    return listBuilder.add("child").add("ancestor").add("qualified").addAll(super.fetchPrefixes()).build();
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
    private final ParameterDocumentFieldProcessor parameterDocumentFieldProcessor;

    public AmbianceResolveFunctorImpl(AmbianceExpressionEvaluator expressionEvaluator,
        InputSetValidatorFactory inputSetValidatorFactory, ExpressionMode expressionMode) {
      super(expressionEvaluator, expressionMode);
      this.parameterDocumentFieldProcessor = new ParameterDocumentFieldProcessor(
          new EngineExpressionEvaluatorResolver(getExpressionEvaluator()), inputSetValidatorFactory, expressionMode);
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
      ProcessorResult processorResult = parameterDocumentFieldProcessor.process(documentField);
      if (processorResult.isError()) {
        throw new EngineExpressionEvaluationException(processorResult.getMessage(), processorResult.getExpression());
      }
    }
  }

  @Override
  protected Object evaluatePrefixCombinations(
      String expressionBlock, EngineJexlContext ctx, int depth, ExpressionMode expressionMode) {
    try {
      // Currently we use RefObjectSpecific only when the call is from PmsOutcomeServiceImpl or
      // PmsSweepingOutputServiceImpl. We will use new functor if RefObjectSpecific is used because we need recast
      // additions in our map.
      if (!refObjectSpecific && AmbianceUtils.shouldUseExpressionEngineV2(ambiance)) {
        String normalizedExpression = applyStaticAliases(expressionBlock);
        // Apply all the prefixes and return first one that evaluates successfully.
        List<String> finalExpressions = fetchExpressionsV2(normalizedExpression);
        Object obj = ExpandedJsonFunctor.builder()
                         .planExpansionService(planExpansionService)
                         .nodeExecutionInfoService(nodeExecutionInfoService)
                         .ambiance(ambiance)
                         .groupAliases(groupAliases)
                         .build()
                         .asJson(finalExpressions);
        if (obj != null) {
          ctx.addToContext(Map.of("expandedJson", obj));
        }
        Object object = evaluateCombinations(normalizedExpression, finalExpressions, ctx, depth, expressionMode);

        if (object != null) {
          return object;
        }
        log.warn(String.format("Could not resolve via V2 expression engine: %s. Falling back to V1", expressionBlock));
      }
    } catch (Exception ex) {
      log.error(
          String.format("Could not resolve via V2 expression engine: %s. Falling back to V1", expressionBlock), ex);
    }
    return super.evaluatePrefixCombinations(expressionBlock, ctx, depth, expressionMode);
  }

  private List<String> fetchExpressionsV2(String normalizedExpression) {
    if (hasExpressions(normalizedExpression)) {
      return Collections.singletonList(normalizedExpression);
    }
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    if (entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      listBuilder.add(String.format("outcome.%s", normalizedExpression));
    }
    if (entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      listBuilder.add(String.format("output.%s", normalizedExpression));
    }
    listBuilder.addAll(ExpandedJsonFunctorUtils.getExpressions(ambiance, groupAliases, normalizedExpression));
    return listBuilder.build();
  }
}
