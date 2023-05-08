/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.runner;

import io.harness.delegate.core.beans.ExecutionInfrastructure;
import io.harness.delegate.core.beans.TaskDescriptor;

import java.util.List;

public interface TaskRunner {
  void init(String taskGroupId, List<TaskDescriptor> tasks, ExecutionInfrastructure resources);
  void execute(String taskGroupId, List<TaskDescriptor> tasks);
  void cleanup(String taskGroupId);
}
