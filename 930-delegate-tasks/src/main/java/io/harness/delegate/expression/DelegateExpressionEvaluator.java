/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.expression;

import io.harness.expression.DockerConfigJsonSecretFunctor;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ImageSecretFunctor;
import io.harness.expression.JsonFunctor;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import com.google.inject.Injector;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateExpressionEvaluator extends ExpressionEvaluator {
  private TerraformPlanDelegateFunctor terraformPlanDelegateFunctor;

  public DelegateExpressionEvaluator() {
    addFunctor("json", new JsonFunctor());
  }

  public DelegateExpressionEvaluator(Map<String, char[]> evaluatedSecrets, int expressionFunctorToken) {
    addFunctor("secretDelegate",
        SecretDelegateFunctor.builder()
            .secrets(evaluatedSecrets)
            .expressionFunctorToken(expressionFunctorToken)
            .build());
    addFunctor(ImageSecretFunctor.FUNCTOR_NAME, new ImageSecretFunctor());
    addFunctor(DockerConfigJsonSecretFunctor.FUNCTOR_NAME, new DockerConfigJsonSecretFunctor());
  }

  public DelegateExpressionEvaluator(Injector injector, String accountId, int expressionFunctorToken) {
    this.terraformPlanDelegateFunctor = TerraformPlanDelegateFunctor.builder()
                                            .accountId(accountId)
                                            .expressionFunctorToken(expressionFunctorToken)
                                            .build();
    injector.injectMembers(terraformPlanDelegateFunctor);

    addFunctor(TerraformPlanExpressionInterface.DELEGATE_FUNCTOR_NAME, terraformPlanDelegateFunctor);
  }

  public void cleanup() {
    try {
      if (terraformPlanDelegateFunctor != null) {
        terraformPlanDelegateFunctor.cleanup();
      }
    } catch (Exception ex) {
      log.error("Failed to cleanup", ex);
    }
  }
}
