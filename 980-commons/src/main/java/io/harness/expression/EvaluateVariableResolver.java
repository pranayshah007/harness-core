/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.exception.FunctorException;

import software.wings.expression.SecretManagerMode;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.text.StrLookup;

@Builder
@Slf4j
public class EvaluateVariableResolver extends StrLookup<Object> {
  private JexlContext context;
  private ExpressionEvaluator expressionEvaluator;
  private int varIndex;
  private String prefix;
  private String suffix;
  private VariableResolverTracker variableResolverTracker;

  @Override
  public String lookup(String variable) {
    String name = prefix + ++varIndex + suffix;
    try {
      final Object evaluated = expressionEvaluator.evaluate(variable, context);
      context.set(name, evaluated);
      if (variableResolverTracker != null && evaluated != null) {
        variableResolverTracker.observed(variable, context.get(name));
      }
    } catch (JexlException exception) {
      if (exception.getCause() instanceof FunctorException) {
        FunctorException functorException = (FunctorException) exception.getCause();
        functorException.addParam(FunctorException.EXPRESSION_ARG, variable);
        throw functorException;
      }
      if (expressionEvaluator.getEvaluationMode() == SecretManagerMode.APPLY) {
        log.error("Encountered error while evaluating secret in APPLY mode ", exception);
      }
      context.set(name, "${" + variable + "}");
    }
    return name;
  }
}
