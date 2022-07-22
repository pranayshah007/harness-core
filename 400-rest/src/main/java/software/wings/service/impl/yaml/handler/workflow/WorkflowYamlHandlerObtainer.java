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
