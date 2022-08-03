/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import io.harness.beans.OrchestrationWorkflowType;

import software.wings.beans.Workflow;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;

import lombok.experimental.UtilityClass;

@UtilityClass
public class WorkflowYamlHandlerObtainer {
  public <T extends BaseYamlHandler, B> T getYamlHandler(B bean, YamlHandlerFactory yamlHandlerFactory) {
    OrchestrationWorkflowType orchestrationWorkflowType =
        ((Workflow) bean).getOrchestrationWorkflow().getOrchestrationWorkflowType();
    return yamlHandlerFactory.getYamlHandler(YamlType.WORKFLOW, orchestrationWorkflowType.name());
  }
}
