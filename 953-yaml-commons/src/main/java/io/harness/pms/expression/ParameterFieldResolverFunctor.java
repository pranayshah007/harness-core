/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.ParameterFieldExpressionProcessor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
/**
 * This resolver functor helps in resolving expressions inside Object which is of type ParameterField which can be done
 * inside respective service as Internal object type is known by respective service. This shouldn't be used by
 * AmbianceExpressionEvaluator as their object is converted into ParameterDocumentField.
 */
public class ParameterFieldResolverFunctor implements ExpressionResolveFunctor {
  private final ExpressionMode expressionMode;
  private final EngineExpressionResolver engineExpressionResolver;
  private final ParameterFieldExpressionProcessor parameterFieldExpressionProcessor;

  public ParameterFieldResolverFunctor(EngineExpressionResolver engineExpressionResolver,
      InputSetValidatorFactory inputSetValidatorFactory, ExpressionMode expressionMode) {
    this.engineExpressionResolver = engineExpressionResolver;
    this.expressionMode = expressionMode;
    this.parameterFieldExpressionProcessor =
        new ParameterFieldExpressionProcessor(engineExpressionResolver, inputSetValidatorFactory, expressionMode);
  }

  @Override
  public String processString(String expression) {
    if (EngineExpressionEvaluator.hasExpressions(expression)) {
      return engineExpressionResolver.renderExpression(expression, expressionMode);
    }
    return expression;
  }

  @Override
  public ResolveObjectResponse processObject(Object o) {
    if (!(o instanceof ParameterField)) {
      return new ResolveObjectResponse(false, null);
    }

    ParameterField<?> parameterField = (ParameterField<?>) o;
    if (!parameterField.isExpression()) {
      return new ResolveObjectResponse(false, null);
    }
    processObjectInternal(parameterField);

    return new ResolveObjectResponse(true, parameterField);
  }

  private void processObjectInternal(ParameterField<?> field) {
    ProcessorResult processorResult = parameterFieldExpressionProcessor.process(field);
    if (processorResult.isError()) {
      throw new EngineExpressionEvaluationException(processorResult.getMessage(), processorResult.getExpression());
    }
  }
}
