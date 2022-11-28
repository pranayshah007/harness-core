/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Workflow;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkflowHandlerFactory {
  @Inject RollingWorkflowHandlerImpl rollingWorkflowHandler;
  @Inject BuildWorkflowHandlerImpl buildWorkflowYamlHandler;
  @Inject MultiServiceWorkflowHandlerImpl multiServiceWorkflowHandler;
  @Inject CanaryWorkflowHandlerImpl canaryWorkflowHandler;
  @Inject BlueGreenWorkflowHandlerImpl blueGreenWorkflowHandler;
  @Inject BasicWorkflowHandlerImpl basicWorkflowHandler;

  public WorkflowHandler getWorkflowHandler(Workflow workflow) {
    switch (workflow.getOrchestration().getOrchestrationWorkflowType()) {
      case ROLLING:
        return rollingWorkflowHandler;
      case BUILD:
        return buildWorkflowYamlHandler;
      case BASIC:
        return basicWorkflowHandler;
      case CANARY:
        return canaryWorkflowHandler;
      case MULTI_SERVICE:
        return multiServiceWorkflowHandler;
      case BLUE_GREEN:
        return blueGreenWorkflowHandler;
      case CUSTOM:
      default:
        throw new InvalidRequestException("Unsupported WF type");
    }
  }

  public boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    OrchestrationWorkflowType type1 = workflow1.getOrchestration().getOrchestrationWorkflowType();
    OrchestrationWorkflowType type2 = workflow2.getOrchestration().getOrchestrationWorkflowType();

    if (!type1.equals(type2)) {
      return false;
    }
    try {
      return getWorkflowHandler(workflow1).areSimilar(workflow1, workflow2);
    } catch (Exception e) {
      log.error(
          String.format("There was an error with comparing Worflows %s & %s", workflow2.getName(), workflow1.getName()),
          e);
      return false;
    }
  }
}
