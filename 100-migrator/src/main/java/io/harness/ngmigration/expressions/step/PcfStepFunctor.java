/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.ngmigration.beans.StepOutput;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;

import java.util.List;

public class PcfStepFunctor extends StepExpressionFunctor {
  private final StepOutput stepOutput;
  private final Workflow workflow;
  private final GraphNode graphNode;
  public PcfStepFunctor(StepOutput stepOutput, Workflow workflow, GraphNode graphNode) {
    this.stepOutput = stepOutput;
    this.workflow = workflow;
    this.graphNode = graphNode;
  }

  @Override
  public synchronized Object get(Object keyObject) {
    String key = keyObject.toString();
    String newKey = key;

    if (getCgExpression().contains("infra.pcf")) {
      if ("cloudProvider".equals(key)) {
        newKey = "connector.name";
      }
      return "<+infra." + newKey + ">";
    }

    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (workflowType.equals(OrchestrationWorkflowType.BLUE_GREEN)) {
      if ("newAppRoutes".equals(key)) {
        if (containsStepType(workflow, graphNode, "PCF_BG_MAP_ROUTE")) {
          newKey = "finalRoutes";
        } else {
          newKey = "tempRoutes";
        }
      }

      if ("activeAppName".equals(key)) {
        if (containsStepType(workflow, graphNode, "PCF_BG_MAP_ROUTE")) {
          newKey = "inActiveAppName";
        } else {
          newKey = "activeAppName";
        }
      }

      if ("displayName".equals(key)) {
        newKey = "inActiveAppName";
      }
    } else {
      if ("displayName".equals(key)) {
        newKey = "newAppName";
      }
    }

    if ("applicationId".equals(key)) {
      newKey = "newAppGuid";
    }

    return "<+pcf." + newKey + ">";
  }

  private boolean containsStepType(Workflow workflow, GraphNode graphNode, String stepType) {
    List<GraphNode> steps = MigratorUtility.getSteps(workflow);
    if (isNotEmpty(steps)) {
      for (GraphNode step : steps) {
        if (stepType.equals(step.getType())) {
          return true;
        }
        if (graphNode.getId().equals(step.getId())) {
          break;
        }
      }
    }
    return false;
  }

  @Override
  public String getCgExpression() {
    return stepOutput.getExpression();
  }
}
