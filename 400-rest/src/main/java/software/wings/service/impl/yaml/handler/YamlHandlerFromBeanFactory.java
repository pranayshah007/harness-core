/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
