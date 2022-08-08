/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.common;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import static software.wings.beans.ServiceVariableType.ARTIFACT;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;

import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.api.InstanceElement;
import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.sm.ContextElement;
import software.wings.sm.WorkflowStandardParams;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.mongodb.morphia.annotations.Transient;

/**
 * The type Variable processor.
 */
@Singleton
@TargetModule(_957_CG_BEANS)
public class VariableProcessor {
  @Inject @Transient private ServiceTemplateService serviceTemplateService;

  /**
   * Gets variables.
   *
   * @param contextElements the context elements
   * @param workflowExecutionId
   * @return the variables
   */
  public Map<String, String> getVariables(Deque<ContextElement> contextElements, String workflowExecutionId) {
    Map<String, String> variables = new HashMap<>();
    Optional<ContextElement> instanceElement =
        contextElements.stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.INSTANCE)
            .findFirst();

    if (instanceElement.isPresent()) {
      WorkflowStandardParams standardParam =
          (WorkflowStandardParams) contextElements.stream()
              .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
              .findFirst()
              .get();

      InstanceElement instance = (InstanceElement) instanceElement.get();
      if (instance == null || instance.getServiceTemplateElement() == null) {
        return variables;
      }
      List<ServiceVariable> serviceSettingMap = serviceTemplateService.computeServiceVariables(standardParam.getAppId(),
          standardParam.getEnvId(), instance.getServiceTemplateElement().getUuid(), workflowExecutionId, OBTAIN_VALUE);
      variables = serviceSettingMap.stream()
                      .filter(serviceVariable -> ARTIFACT != serviceVariable.getType())
                      .collect(Collectors.toMap(ServiceVariable::getName,
                          serviceVariable -> new String(serviceVariable.getValue()), (a, b) -> b));
    }

    return variables;
  }
}
