/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SkipPreFacilitationChecker extends ExpressionEvalPreFacilitationChecker {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  protected ExecutionCheck performCheck(Ambiance ambiance, Node node) {
    log.info("Checking If Node should be Skipped");
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    String skipCondition = node.getSkipCondition();
    if (EmptyPredicate.isNotEmpty(skipCondition)) {
      try {
        boolean skipConditionValue = (Boolean) engineExpressionService.evaluateExpression(ambiance, skipCondition);
        nodeExecutionService.updateV2(nodeExecutionId, ops -> {
          ops.set(NodeExecutionKeys.skipInfo,
              SkipInfo.newBuilder().setEvaluatedCondition(skipConditionValue).setSkipCondition(skipCondition).build());
        });
        if (skipConditionValue) {
          log.info(String.format("Skipping node: %s", nodeExecutionId));
          StepResponseProto response =
              StepResponseProto.newBuilder()
                  .setStatus(Status.SKIPPED)
                  .setSkipInfo(
                      SkipInfo.newBuilder().setSkipCondition(skipCondition).setEvaluatedCondition(true).build())
                  .build();
          orchestrationEngine.processStepResponse(ambiance, response);
          return ExecutionCheck.builder().proceed(false).reason("Skip Condition Evaluated to true").build();
        }
        return ExecutionCheck.builder().proceed(true).reason("Skip Condition Evaluated to false").build();
      } catch (Exception ex) {
        return handleExpressionEvaluationError(ex, skipCondition, ambiance);
      }
    }
    return ExecutionCheck.builder().proceed(true).reason("No Skip Condition Configured").build();
  }
}
