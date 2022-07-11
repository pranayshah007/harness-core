/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.io.StepOutcomeProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class EndNodeExecutionHelper {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanNodeExecutionStrategy executionStrategy;

  public void endNodeExecutionWithNoAdvisers(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    NodeExecution updatedNodeExecution = processStepResponseWithNoAdvisers(ambiance, stepResponse);
    if (updatedNodeExecution == null) {
      log.warn("Cannot process step response for nodeExecution {}", AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      return;
    }
    executionStrategy.endNodeExecution(updatedNodeExecution.getAmbiance());
  }

  private NodeExecution processStepResponseWithNoAdvisers(Ambiance ambiance, StepResponseProto stepResponse) {
    // Start a transaction here
    List<StepOutcomeRef> outcomeRefs =
        handleOutcomes(ambiance, stepResponse.getStepOutcomesList(), stepResponse.getGraphOutcomesList());

    // End transaction here
    return nodeExecutionService.updateStatusWithOps(
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), stepResponse.getStatus(), ops -> {
          setUnset(ops, NodeExecutionKeys.failureInfo, stepResponse.getFailureInfo());
          setUnset(ops, NodeExecutionKeys.unitProgresses, stepResponse.getUnitProgressList());
        }, EnumSet.noneOf(Status.class));
  }

  private List<StepOutcomeRef> handleOutcomes(
      Ambiance ambiance, List<StepOutcomeProto> stepOutcomeProtos, List<StepOutcomeProto> graphOutcomesList) {
    List<StepOutcomeRef> outcomeRefs = new ArrayList<>();
    if (isEmpty(stepOutcomeProtos)) {
      return outcomeRefs;
    }

    stepOutcomeProtos.forEach(proto -> {
      if (isNotEmpty(proto.getOutcome())) {
        String instanceId = pmsOutcomeService.consume(ambiance, proto.getName(), proto.getOutcome(), proto.getGroup());
        outcomeRefs.add(StepOutcomeRef.newBuilder().setName(proto.getName()).setInstanceId(instanceId).build());
      }
    });
    graphOutcomesList.forEach(proto -> {
      if (isNotEmpty(proto.getOutcome())) {
        String instanceId = pmsOutcomeService.consume(ambiance, proto.getName(), proto.getOutcome(), proto.getGroup());
        outcomeRefs.add(StepOutcomeRef.newBuilder().setName(proto.getName()).setInstanceId(instanceId).build());
      }
    });
    return outcomeRefs;
  }

  public NodeExecution handleStepResponsePreAdviser(Ambiance ambiance, StepResponseProto stepResponse) {
    log.info("Handling Step response before calling advisers");
    return processStepResponsePreAdvisers(ambiance, stepResponse);
  }

  private NodeExecution processStepResponsePreAdvisers(Ambiance ambiance, StepResponseProto stepResponse) {
    handleOutcomes(ambiance, stepResponse.getStepOutcomesList(), stepResponse.getGraphOutcomesList());

    return nodeExecutionService.updateStatusWithOps(
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), stepResponse.getStatus(), ops -> {
          setUnset(ops, NodeExecutionKeys.failureInfo, stepResponse.getFailureInfo());
          setUnset(ops, NodeExecutionKeys.unitProgresses, stepResponse.getUnitProgressList());
        }, EnumSet.noneOf(Status.class));
  }
}
