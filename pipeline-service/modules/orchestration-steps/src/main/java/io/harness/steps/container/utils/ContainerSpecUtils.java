/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.delegate.TaskSelector;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.plugin.ContainerStepSpec;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ContainerSpecUtils {
  @NotNull
  public List<TaskSelector> getDelegateSelectors(ContainerStepSpec containerStepSpec) {
    if (containerStepSpec != null) {
      ParameterField<List<TaskSelectorYaml>> selectors = containerStepSpec.fetchDelegateSelectors();
      if (ParameterField.isNotNull(selectors)) {
        if (!selectors.isExpression()) {
          return TaskSelectorYaml.toTaskSelector(selectors.getValue());
        }
        log.error("Delegate selector expression {} could not be resolved", selectors.getExpressionValue());
      }
    }
    return List.of();
  }
}
