/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.StepSpec;
import io.harness.delegate.core.beans.ExecutionMode;
import io.harness.delegate.core.beans.ExecutionPriority;
import io.harness.delegate.core.beans.K8SStep;

public interface K8SStepMapper {
  static K8SStep map(final StepSpec task, final String taskId) {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }

    final var stepRuntime = StepRuntimeMapper.map(task);

    return K8SStep.newBuilder()
        .setId(taskId)
        .setMode(ExecutionMode.MODE_ONCE)
        .setPriority(ExecutionPriority.PRIORITY_DEFAULT)
        .setRuntime(stepRuntime)
        .build();
  }
}
