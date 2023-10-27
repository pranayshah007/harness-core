/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import io.harness.delegate.StepSpec;
import io.harness.delegate.core.beans.PluginSource;
import io.harness.delegate.core.beans.StepRuntime;

public interface StepRuntimeMapper {
  // TODO: Convert to MapStruct
  static StepRuntime map(final StepSpec task) {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }

    final var securityContext = SecurityContextMapper.map(task.getSecurityContext());
    final var taskCompute = ComputeMapper.INSTANCE.map(task.getComputeResource());
    return StepRuntime.newBuilder()
        .setCompute(taskCompute)
        .setSource(PluginSource.SOURCE_IMAGE)
        .setUses(task.getImage())
        .addAllCommand(task.getCommandsList())
        .addAllArg(task.getArgsList())
        .setSecurityContext(securityContext)
        .putAllEnv(task.getEnvMap())
        .build();
  }
}
