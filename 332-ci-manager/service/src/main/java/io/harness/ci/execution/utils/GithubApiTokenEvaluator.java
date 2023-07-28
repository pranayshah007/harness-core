/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.NGAccess;
import io.harness.pms.expression.EngineExpressionEvaluatorResolver;
import io.harness.pms.expression.ParameterFieldResolverFunctor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class GithubApiTokenEvaluator {
  private final ConnectorUtils connectorUtils;
  private final GithubApiFunctor.Config githubApiFunctorConfig;
  private final InputSetValidatorFactory inputSetValidatorFactory;

  public Map<String, ConnectorDetails> resolve(Object o, NGAccess ngAccess, long token) {
    GithubApiFunctor githubApiFunctor = GithubApiFunctor.builder()
                                            .connectorUtils(connectorUtils)
                                            .githubApiFunctorConfig(githubApiFunctorConfig)
                                            .ngAccess(ngAccess)
                                            .build();
    GitAppTokenExpressionEvaluator gitAppTokenExpressionEvaluator =
        new GitAppTokenExpressionEvaluator(githubApiFunctor);
    ParameterFieldResolverFunctor parameterFieldResolverFunctor =
        new ParameterFieldResolverFunctor(new EngineExpressionEvaluatorResolver(gitAppTokenExpressionEvaluator),
            inputSetValidatorFactory, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    ExpressionEvaluatorUtils.updateExpressions(o, parameterFieldResolverFunctor);
    return githubApiFunctor.getConnectorDetailsMap();
  }

  public static class GitAppTokenExpressionEvaluator extends EngineExpressionEvaluator {
    public GitAppTokenExpressionEvaluator(GithubApiFunctor githubApiFunctor) {
      super(null);
      addToContext("gitApp", githubApiFunctor);
    }
  }
}
