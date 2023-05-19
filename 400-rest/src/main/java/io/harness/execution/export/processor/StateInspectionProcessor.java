/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.processor;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.metadata.ExecutionMetadata;
import io.harness.execution.export.metadata.GraphNodeMetadata;
import io.harness.execution.export.metadata.GraphNodeVisitor;
import io.harness.state.inspection.ExpressionVariableUsage;
import io.harness.state.inspection.StateInspection;
import io.harness.state.inspection.StateInspectionData;
import io.harness.state.inspection.StateInspectionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(CDC)
@Value
public class StateInspectionProcessor implements ExportExecutionsProcessor, GraphNodeVisitor {
  @Inject @NonFinal @Setter StateInspectionService stateInspectionService;

  Map<String, GraphNodeMetadata> stateExecutionInstanceIdToNodeMetadataMap;

  public StateInspectionProcessor() {
    this.stateExecutionInstanceIdToNodeMetadataMap = new HashMap<>();
  }

  public void visitExecutionMetadata(ExecutionMetadata executionMetadata) {
    executionMetadata.accept(this);
  }

  public void visitGraphNode(GraphNodeMetadata nodeMetadata) {
    if (nodeMetadata.getId() != null && nodeMetadata.isHasInspection()) {
      stateExecutionInstanceIdToNodeMetadataMap.put(nodeMetadata.getId(), nodeMetadata);
    }
  }

  public void process() {
    if (isEmpty(stateExecutionInstanceIdToNodeMetadataMap)) {
      return;
    }

    stateInspectionService.search(stateExecutionInstanceIdToNodeMetadataMap.keySet(), this::process);
  }

  private void updateGraphNodeMetadata(GraphNodeMetadata nodeMetadata, StateInspection stateInspection) {
    if (nodeMetadata == null || stateInspection == null || isEmpty(stateInspection.getData())) {
      return;
    }

    StateInspectionData stateInspectionData = stateInspection.getData().getOrDefault("expressionVariableUsage", null);
    if (!(stateInspectionData instanceof ExpressionVariableUsage)) {
      return;
    }

    ExpressionVariableUsage expressionVariableUsage = (ExpressionVariableUsage) stateInspectionData;
    if (isEmpty(expressionVariableUsage.getVariables())) {
      return;
    }

    nodeMetadata.setExecutionContext(
        expressionVariableUsage.getVariables()
            .stream()
            .filter(variable -> variable.getExpression() != null && variable.getValue() != null)
            .collect(Collectors.toList()));
  }

  @VisibleForTesting
  void process(StateInspection stateInspection) {
    updateGraphNodeMetadata(
        stateExecutionInstanceIdToNodeMetadataMap.get(stateInspection.getStateExecutionInstanceId()), stateInspection);
  }
}
