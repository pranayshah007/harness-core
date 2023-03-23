/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.PcfStepFunctor;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.WorkflowPhase;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;

public abstract class PcfAbstractStepMapper extends StepMapper {
  @Override
  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    List<String> pcfNames = Lists.newArrayList("pcf", "infra.pcf", "host.pcfElement");
    List<String> allKeys =
        pcfNames.stream()
            .map(key -> Lists.newArrayList(String.format("context.%s", key), String.format("%s", key)))
            .flatMap(List::stream)
            .collect(Collectors.toList());

    return allKeys.stream()
        .map(exp
            -> StepOutput.builder()
                   .stageIdentifier(
                       MigratorUtility.generateIdentifier(phase.getName(), context.getIdentifierCaseFormat()))
                   .stepIdentifier(
                       MigratorUtility.generateIdentifier(graphNode.getName(), context.getIdentifierCaseFormat()))
                   .stepGroupIdentifier(
                       MigratorUtility.generateIdentifier(phaseStep.getName(), context.getIdentifierCaseFormat()))
                   .expression(exp)
                   .build())
        .map(so -> new PcfStepFunctor(so, context.getWorkflow(), graphNode))
        .collect(Collectors.toList());
  }
}
