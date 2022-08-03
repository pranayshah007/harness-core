/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.exception.runtime.JexlRuntimeException;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import org.apache.commons.jexl3.JexlException;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class ExpressionEvalPreFacilitationChecker extends AbstractPreFacilitationChecker {
  @Inject private OrchestrationEngine orchestrationEngine;

  ExecutionCheck handleExpressionEvaluationError(Exception ex, String conditionExpression, Ambiance ambiance) {
    Exception cascadedException = ex;
    if (ex instanceof JexlException) {
      cascadedException = new JexlRuntimeException(conditionExpression, ex);
    }
    orchestrationEngine.handleError(ambiance, cascadedException);
    return ExecutionCheck.builder()
        .proceed(false)
        .reason("Error in evaluating configured when condition on step")
        .build();
  }
}
