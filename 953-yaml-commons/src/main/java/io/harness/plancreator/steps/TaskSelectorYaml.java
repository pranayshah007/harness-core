/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskSelector;
import io.harness.pms.yaml.ParameterField;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
@OwnedBy(HarnessTeam.PIPELINE)
public class TaskSelectorYaml {
  String delegateSelectors;
  String origin;
  public TaskSelectorYaml(String delegateSelectors) {
    this.delegateSelectors = delegateSelectors;
  }
  public static TaskSelector toTaskSelector(TaskSelectorYaml taskSelectorYaml) {
    String origin = taskSelectorYaml.origin != null ? taskSelectorYaml.origin : "default";
    return TaskSelector.newBuilder().setSelector(taskSelectorYaml.delegateSelectors).setOrigin(origin).build();
  }
  public static List<TaskSelector> toTaskSelector(List<TaskSelectorYaml> taskSelectorYaml) {
    if (taskSelectorYaml == null) {
      return Collections.emptyList();
    }
    return taskSelectorYaml.stream().map(TaskSelectorYaml::toTaskSelector).collect(Collectors.toList());
  }
  public static List<TaskSelector> toTaskSelector(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    if (delegateSelectors == null) {
      return Collections.emptyList();
    }
    return TaskSelectorYaml.toTaskSelector(delegateSelectors.getValue());
  }
}
