/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.runners.itfc;

import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.service.handlermapping.context.Context;

public interface Runner {
  /**
   * Set up execution infrastructure
   * @param infraId  The designated infra id for the execution infra to be created
   * @param infra  Input data for infra config
   * @param context  Context of this task, including delegate information, task id, or decrypted secrets
   */
  void init(String infraId, InputData infra, Context context);
  /**
   * Execute a task
   * @param infraId  The execution infra to execute the task on
   * @param infra  Input data for the task
   * @param context  Context of this task, including delegate information, task id, or decrypted secrets
   */
  void execute(String infraId, InputData tasks, Context context);
  /**
   * Clean up an execution infra
   * @param infraId  The execution infra to clean up
   * @param context  Context of this task, including delegate information, task id, or decrypted secrets
   */
  void cleanup(String infraId, Context context);
}
