package software.wings.service.impl.yaml.handler;

import software.wings.beans.Workflow;
import software.wings.service.impl.yaml.handler.workflow.WorkflowYamlHandlerObtainer;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class YamlHandlerFromBeanFactory {
  @Inject YamlHandlerFactory yamlHandlerFactory;

  public <T extends BaseYamlHandler, B> T getYamlHandler(B bean) {
    if (bean instanceof Workflow) {
      return WorkflowYamlHandlerObtainer.getYamlHandler(bean, yamlHandlerFactory);
    } else {
      return null;
    }
  }
}
