/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.ProcessorResult;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterDocumentField;
import io.harness.pms.yaml.ParameterDocumentFieldMapper;
import io.harness.pms.yaml.ParameterFieldProcessor;

import java.util.Map;
import java.util.Optional;

public class StrategyExpressionEvaluator extends EngineExpressionEvaluator {
  private Map<String, String> combinations;
  private int currentIteration;
  private int totalIteration;
  private String itemValue;
  public StrategyExpressionEvaluator(
      Map<String, String> combinations, int currentIteration, int totalIteration, String itemValue) {
    super(null);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIteration = totalIteration;
    this.itemValue = itemValue;
  }

  public StrategyExpressionEvaluator(Map<String, String> combinations, int currentIteration, int totalIteration,
      String itemValue, Map<String, String> contextMap) {
    super(null);
    contextMap.forEach(this::addToContext);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIteration = totalIteration;
    this.itemValue = itemValue;
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    return ExpressionEvaluatorUtils.updateExpressions(o, new StrategyResolveFunctorImpl(this, expressionMode));
  }

  @Override
  protected void initialize() {
    super.initialize();
    // Adding aliases for matrix->strategy.matrix, step->strategy.step and repeat->strategy.repeat. So when the
    // expression starts with matrix or step or repeat, the StrategyUtilFunctor will be used to resolve the values.
    addStaticAlias(StrategyConstants.MATRIX, StrategyConstants.STRATEGY + "." + StrategyConstants.MATRIX);
    addStaticAlias(StrategyConstants.STEP, StrategyConstants.STRATEGY + "." + StrategyConstants.STEP);
    addStaticAlias(StrategyConstants.REPEAT, StrategyConstants.STRATEGY + "." + StrategyConstants.REPEAT);
    addToContext(
        StrategyConstants.STRATEGY, new StrategyUtilFunctor(combinations, currentIteration, totalIteration, itemValue));
  }

  public static class StrategyResolveFunctorImpl extends ResolveFunctorImpl {
    // TODO: Combine this with AmbianceExpressionEvaluator. Extract out the common code to some commons.
    private final ParameterFieldProcessor parameterFieldProcessor;

    public StrategyResolveFunctorImpl(
        StrategyExpressionEvaluator strategyExpressionEvaluator, ExpressionMode expressionMode) {
      super(strategyExpressionEvaluator, expressionMode);
      this.parameterFieldProcessor = new ParameterFieldProcessor(getExpressionEvaluator(), null, expressionMode);
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
