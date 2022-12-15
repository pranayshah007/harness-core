/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import software.wings.beans.WorkflowPhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class WorkflowYamlHandlerValidator {
  /**
   * Validate that every phase was its own rollback phase declared.
   */
  public void validatePhaseAndRollbackPhase(
      Map<String, WorkflowPhase> workflowPhaseMap, Map<String, WorkflowPhase> rollbackPhaseMap) {
    if (workflowPhaseMap.isEmpty() && rollbackPhaseMap.isEmpty()) {
      return;
    }

    final List<String> phaseNames = new ArrayList<>(workflowPhaseMap.keySet());
    final List<String> rollbackNames =
        rollbackPhaseMap.values().stream().map(WorkflowPhase::getPhaseNameForRollback).collect(toList());

    phaseNames.removeAll(rollbackNames);
    if (!phaseNames.isEmpty()) {
      throw new InvalidArgumentsException(
          format("Missing rollback phase for one or more phases: %s", phaseNames), USER);
    }
  }
}
