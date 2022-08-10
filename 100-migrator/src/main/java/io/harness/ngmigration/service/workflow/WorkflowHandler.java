/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface WorkflowHandler {
  List<Yaml> getPhases(Workflow workflow);

  List<GraphNode> getSteps(Workflow workflow);

  default List<GraphNode> getSteps(
      List<WorkflowPhase> phases, PhaseStep preDeploymentPhaseStep, PhaseStep postDeploymentPhaseStep) {
    List<GraphNode> stepYamls = new ArrayList<>();
    if (postDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(postDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(postDeploymentPhaseStep.getSteps());
    }
    if (preDeploymentPhaseStep != null && EmptyPredicate.isNotEmpty(preDeploymentPhaseStep.getSteps())) {
      stepYamls.addAll(preDeploymentPhaseStep.getSteps());
    }
    if (EmptyPredicate.isNotEmpty(phases)) {
      stepYamls.addAll(getStepsFromPhases(phases));
    }
    return stepYamls;
  }

  default List<GraphNode> getStepsFromPhases(List<WorkflowPhase> phases) {
    return phases.stream()
        .filter(phase -> isNotEmpty(phase.getPhaseSteps()))
        .flatMap(phase -> phase.getPhaseSteps().stream())
        .filter(phaseStep -> isNotEmpty(phaseStep.getSteps()))
        .flatMap(phaseStep -> phaseStep.getSteps().stream())
        .collect(Collectors.toList());
  }
}
