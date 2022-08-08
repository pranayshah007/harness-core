/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class GithubApiTokenEvaluator extends ExpressionEvaluator {
  private final ConnectorUtils connectorUtils;
  private final GithubApiFunctor.Config githubApiFunctorConfig;

  public Map<String, ConnectorDetails> resolve(Object o, NGAccess ngAccess, long token) {
    GithubApiFunctor githubApiFunctor = GithubApiFunctor.builder()
                                            .connectorUtils(connectorUtils)
                                            .githubApiFunctorConfig(githubApiFunctorConfig)
                                            .ngAccess(ngAccess)
                                            .build();
    GitAppTokenResolveFunctorImpl resolveFunctor = GitAppTokenResolveFunctorImpl.builder()
                                                       .expressionEvaluator(new EngineExpressionEvaluator(null))
                                                       .githubApiFunctor(githubApiFunctor)
                                                       .build();
    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);
    return githubApiFunctor.getConnectorDetailsMap();
  }

  public static class GitAppTokenResolveFunctorImpl implements ExpressionResolveFunctor {
    private final EngineExpressionEvaluator expressionEvaluator;
    private final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    @Builder
    public GitAppTokenResolveFunctorImpl(
        EngineExpressionEvaluator expressionEvaluator, GithubApiFunctor githubApiFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("gitApp", githubApiFunctor);
    }

    @Override
    public String processString(String expression) {
      return processStringInternal(expression);
    }

    @Override
    public ResolveObjectResponse processObject(Object o) {
      if (!(o instanceof ParameterField)) {
        return new ResolveObjectResponse(false, null);
      }

      ParameterField<?> parameterField = (ParameterField) o;

      if (!parameterField.isExpression()) {
        return new ResolveObjectResponse(false, null);
      }

      String processedExpressionValue = processStringInternal(parameterField.getExpressionValue());
      parameterField.updateWithExpression(processedExpressionValue);

      return new ResolveObjectResponse(true, parameterField);
    }

    private String processStringInternal(String expression) {
      return expressionEvaluator.renderExpression(expression, evaluatorResponseContext, true);
    }
  }
}
