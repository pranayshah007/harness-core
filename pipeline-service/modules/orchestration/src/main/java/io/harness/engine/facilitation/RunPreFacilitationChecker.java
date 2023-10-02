/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.Level.ERROR;

import io.harness.OrchestrationRestrictionConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.run.ExpressionBlock;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class RunPreFacilitationChecker extends ExpressionEvalPreFacilitationChecker {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  private static final String MAX_LEVELS_LIMIT_REACHED_ERROR =
      "The pipeline has reached the maximum nesting allowed for an execution. Please simplify the pipeline configuration so that it does not breach the allowed limit of nesting";
  @Override
  protected ExecutionCheck performCheck(Ambiance ambiance, Node node) {
    if (ambiance.getLevelsCount() > orchestrationRestrictionConfiguration.getMaxNestedLevelsCount()) {
      log.error(MAX_LEVELS_LIMIT_REACHED_ERROR);
      StepResponseProto response =
          StepResponseProto.newBuilder()
              .setStatus(Status.FAILED)
              .setFailureInfo(FailureInfo.newBuilder()
                                  .setErrorMessage(MAX_LEVELS_LIMIT_REACHED_ERROR)
                                  .addFailureData(FailureData.newBuilder()
                                                      .setLevel(ERROR.name())
                                                      .setCode(INVALID_REQUEST.name())
                                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                      .setMessage(MAX_LEVELS_LIMIT_REACHED_ERROR)
                                                      .build())
                                  .build())
              .build();
      orchestrationEngine.processStepResponse(ambiance, response);
      return ExecutionCheck.builder().proceed(false).reason(MAX_LEVELS_LIMIT_REACHED_ERROR).build();
    }
    log.info("Checking If Node should be Run with When Condition.");
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String whenCondition = node.getWhenCondition();
    if (EmptyPredicate.isNotEmpty(whenCondition)) {
      try {
        EngineExpressionEvaluator engineExpressionEvaluator =
            pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
        Object evaluatedExpression = engineExpressionEvaluator.evaluateExpression(whenCondition);
        boolean whenConditionValue = (Boolean) evaluatedExpression;
        nodeExecutionService.updateV2(nodeExecutionId, ops -> {
          ops.set(NodeExecutionKeys.nodeRunInfo,
              NodeRunInfo.newBuilder()
                  .setEvaluatedCondition(whenConditionValue)
                  .setWhenCondition(whenCondition)
                  .addAllExpressions(getAllExpressions(engineExpressionEvaluator))
                  .build());
        });
        if (!whenConditionValue) {
          log.info(String.format("Skipping node: %s", nodeExecutionId));
          StepResponseProto response =
              StepResponseProto.newBuilder()
                  .setStatus(Status.SKIPPED)
                  .setNodeRunInfo(
                      NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(false).build())
                  .build();
          orchestrationEngine.processStepResponse(ambiance, response);
          return ExecutionCheck.builder().proceed(false).reason("When Condition Evaluated to false").build();
        }
        return ExecutionCheck.builder().proceed(true).reason("When Condition Evaluated to true").build();
      } catch (Exception ex) {
        return handleExpressionEvaluationError(ex, whenCondition, ambiance);
      }
    }
    return ExecutionCheck.builder().proceed(true).reason("No when Condition Configured").build();
  }

  @VisibleForTesting
  List<ExpressionBlock> getAllExpressions(EngineExpressionEvaluator engineExpressionEvaluator) {
    Map<String, Map<Object, Integer>> usedExpressionsMap =
        engineExpressionEvaluator.getVariableResolverTracker().getUsage();
    List<ExpressionBlock> resultExpressionsList = new LinkedList<>();
    for (Map.Entry<String, Map<Object, Integer>> stringMapEntry : usedExpressionsMap.entrySet()) {
      String expression = stringMapEntry.getKey();
      // Removing one internal expression.
      if (expression.contains(OrchestrationConstants.CURRENT_STATUS)) {
        continue;
      }
      Set<Object> expressionValueSet = stringMapEntry.getValue().keySet();
      for (Object value : expressionValueSet) {
        String expressionValue = String.valueOf(value);
        ExpressionBlock expressionBlock = ExpressionBlock.newBuilder()
                                              .setExpression(expression)
                                              .setExpressionValue(expressionValue)
                                              .setCount(stringMapEntry.getValue().get(value))
                                              .build();
        resultExpressionsList.add(expressionBlock);
      }
    }
    return resultExpressionsList;
  }
}
