/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionResolver;
import io.harness.pms.expression.ProcessorResult;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.pms.yaml.validation.RuntimeValidator;
import io.harness.pms.yaml.validation.RuntimeValidatorResponse;

import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ParameterFieldExpressionProcessor {
  /**
   * This resolver helps in resolving expressions by calling its child impl (EngineExpressionService or
   * EngineExpressionEvaluator)
   * Example of EngineExpressionService - Makes grpc call to pipeline-service if object is of type ParameterField.
   * Example: CD service wants to pass resolve its expression inside nested object like infra, service, env (like
   * CDExpressionResolver, etc.) to resolve.
   *
   * Example of EngineExpressionEvaluator - CI service wants to pass its expressionEvaluator (like
   * StrategyExpressionEvaluator, etc.) to resolve using only current evaluator
   */
  private final EngineExpressionResolver engineExpressionResolver;
  private final InputSetValidatorFactory inputSetValidatorFactory;
  private final ExpressionMode expressionMode;

  public ParameterFieldExpressionProcessor(EngineExpressionResolver engineExpressionResolver,
      InputSetValidatorFactory inputSetValidatorFactory, ExpressionMode expressionMode) {
    this.engineExpressionResolver = engineExpressionResolver;
    this.inputSetValidatorFactory = inputSetValidatorFactory;
    this.expressionMode = expressionMode;
  }

  public ProcessorResult process(ParameterField<?> field) {
    // SkipAutoEvaluation for ParameterField is not done as if those are expression, this processor would be used only
    // for fields which are known to respective services thus we should evaluate
    // In future if needs arises that we need to skip here as well, annotation can be passed down from
    // EngineExpressionEvaluator to this processor
    if (field == null) {
      return ProcessorResult.builder().build();
    }

    Object newValue;
    InputSetValidator inputSetValidator = field.getInputSetValidator();
    if (field.isExpression()) {
      if (field.isTypeString()) {
        newValue = engineExpressionResolver.renderExpression(field.getExpressionValue(), expressionMode);
      } else {
        newValue = engineExpressionResolver.evaluateExpression(field.getExpressionValue(), expressionMode);
      }

      if (newValue instanceof String && EngineExpressionEvaluator.hasExpressions((String) newValue)) {
        String newExpression = (String) newValue;
        if (newExpression.equals(field.getExpressionValue())) {
          return ProcessorResult.builder().build();
        }

        field.updateWithExpression(newExpression);
        return validateUsingValidator(newValue, inputSetValidator);
      }

      field.updateWithValue(newValue);
    }

    if (field.getValue() == null) {
      return ProcessorResult.builder().build();
    }

    Object valueField = field.getValue();
    if (valueField != null) {
      Object finalValue = engineExpressionResolver.resolve(valueField, expressionMode);
      if (finalValue != null) {
        field.updateWithValue(finalValue);
        ProcessorResult processorResult = validateUsingValidator(finalValue, inputSetValidator);
        if (processorResult.isError()) {
          return processorResult;
        }
      }
    }

    return ProcessorResult.builder().build();
  }

  private ProcessorResult validateUsingValidator(Object value, InputSetValidator inputSetValidator) {
    if (inputSetValidator == null) {
      return ProcessorResult.builder().build();
    }

    RuntimeValidator runtimeValidator =
        inputSetValidatorFactory.obtainValidator(inputSetValidator, engineExpressionResolver, expressionMode);
    RuntimeValidatorResponse validatorResponse =
        runtimeValidator.isValidValue(value, inputSetValidator.getParameters());
    if (!validatorResponse.isValid()) {
      return ProcessorResult.builder()
          .error(true)
          .expression(String.format(
              "<+input>.%s(%s)", inputSetValidator.getValidatorType().getYamlName(), inputSetValidator.getParameters()))
          .message(validatorResponse.getErrorMessage())
          .build();
    }
    return ProcessorResult.builder().build();
  }
}
