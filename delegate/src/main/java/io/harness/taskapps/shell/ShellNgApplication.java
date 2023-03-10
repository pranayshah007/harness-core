/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskapps.shell;

import io.harness.delegate.executor.DelegateTaskExecutor;
import io.harness.delegate.executor.bundle.BootstrapBundle;
import io.harness.taskapps.common.kryo.CommonTaskKryoRegistrar;
import io.harness.taskapps.shell.kryo.ShellScriptNgTaskKryoRegistrars;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.bash.BashScriptTask;

import java.util.Set;

public class ShellNgApplication extends DelegateTaskExecutor {
  @Override
  public void init(final BootstrapBundle taskBundle) {
    taskBundle.registerTask(TaskType.SHELL_SCRIPT_TASK_NG, BashScriptTask.class);
    taskBundle.registerKryos(Set.of(CommonTaskKryoRegistrar.class, ShellScriptNgTaskKryoRegistrars.class));
  }

  public static void main(String[] args) {
    new ShellNgApplication().run(args);
  }
}
