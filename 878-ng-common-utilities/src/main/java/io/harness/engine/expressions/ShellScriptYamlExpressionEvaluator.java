/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import io.harness.data.algorithm.HashGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ResolveObjectResponse;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ShellScriptYamlExpressionEvaluator extends EngineExpressionEvaluator {
  protected final String yaml;

  public ShellScriptYamlExpressionEvaluator(String yaml) {
    super(null);
    this.yaml = yaml;
  }

  @Override
  protected void initialize() {
    super.initialize();
    // Add Shell Script Yaml Expression Functor
    addToContext("__yamlExpression",
        ShellScriptYamlExpressionFunctor.builder().rootYamlField(getShellScriptYamlField()).build());
    // Add secret functor
    addToContext("secrets", new SecretFunctor(HashGenerator.generateIntegerHash()));
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add("__yamlExpression").addAll(super.fetchPrefixes()).build();
  }

  private YamlField getShellScriptYamlField() {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField("template");
    } catch (IOException e) {
      throw new InvalidRequestException("Not valid yaml passed.");
    }
  }

  @Override
  public Object resolve(Object o, boolean skipUnresolvedExpressionsCheck) {
    return ExpressionEvaluatorUtils.updateExpressions(
        o, new ShellScriptFunctorImpl(this, skipUnresolvedExpressionsCheck));
  }

  public static class ShellScriptFunctorImpl extends ResolveFunctorImpl {
    public ShellScriptFunctorImpl(
        EngineExpressionEvaluator expressionEvaluator, boolean skipUnresolvedExpressionsCheck) {
      super(expressionEvaluator, skipUnresolvedExpressionsCheck);
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

      String processedExpressionValue = processString(parameterField.getExpressionValue());
      parameterField.updateWithValue(processedExpressionValue);

      return new ResolveObjectResponse(true, parameterField);
    }
  }
}
