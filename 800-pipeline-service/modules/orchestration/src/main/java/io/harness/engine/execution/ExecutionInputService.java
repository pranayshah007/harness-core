/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionInputInstance;

import java.util.List;
import java.util.Set;

@OwnedBy(PIPELINE)
public interface ExecutionInputService {
  boolean continueExecution(String nodeExecutionId, String executionInputYaml);
  ExecutionInputInstance getExecutionInputInstance(String nodeExecutionId);
  ExecutionInputInstance save(ExecutionInputInstance executionInputInstance);
  List<ExecutionInputInstance> getExecutionInputInstances(Set<String> nodeExecutionIds);
}
