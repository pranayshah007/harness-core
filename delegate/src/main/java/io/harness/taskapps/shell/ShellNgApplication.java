package io.harness.taskapps.shell;

import io.harness.delegate.executor.DelegateTaskExecutor;
import io.harness.delegate.executor.bundle.BootstrapBundle;
import io.harness.taskapps.common.kryo.CommonTaskKryoRegistrar;
import io.harness.taskapps.shell.kryo.ShellScriptNgTaskKryoRegistrars;
import io.harness.taskapps.shell.module.ShellScriptNgTaskModule;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.bash.BashScriptTask;

import java.util.Set;

public class ShellNgApplication extends DelegateTaskExecutor {
  @Override
  public void init(final BootstrapBundle taskBundle) {
    taskBundle.registerTask(TaskType.SHELL_SCRIPT_TASK_NG, BashScriptTask.class);
    taskBundle.registerKryos(Set.of(CommonTaskKryoRegistrar.class, ShellScriptNgTaskKryoRegistrars.class));
    taskBundle.addModule(new ShellScriptNgTaskModule());
  }

  public static void main(String[] args) {
    new ShellNgApplication().run(args);
  }
}
