/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import software.wings.beans.Workflow;

import com.google.inject.Inject;

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
        throw new UnsupportedOperationException();
    }
  }
}
