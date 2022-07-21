/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks;

import io.harness.delegate.task.citasks.cik8handler.CIK8CleanupTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.CIK8ExecuteStepTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.CIK8InitializeTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.K8ExecuteCommandTaskHandler;
import io.harness.delegate.task.citasks.vm.CIVMExecuteStepTaskHandler;
import io.harness.delegate.task.citasks.vm.CIVmCleanupTaskHandler;
import io.harness.delegate.task.citasks.vm.CIVmInitializeTaskHandler;
import io.harness.delegate.task.citasks.docker.CIDockerInitializeTaskHandler;
import io.harness.threading.Sleeper;
import io.harness.threading.ThreadSleeper;
import io.harness.time.ClockTimer;
import io.harness.time.Timer;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class CITaskFactoryModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ExecuteCommandTaskHandler.class).to(K8ExecuteCommandTaskHandler.class);
    bind(Sleeper.class).to(ThreadSleeper.class);
    bind(Timer.class).to(ClockTimer.class);

    bind(CIInitializeTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.INIT_VM))
        .to(CIVmInitializeTaskHandler.class);
    bind(CIInitializeTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.INIT_K8))
        .to(CIK8InitializeTaskHandler.class);
    bind(CIInitializeTaskHandler.class)
            .annotatedWith(Names.named(CITaskConstants.INIT_DOCKER))
            .to(CIDockerInitializeTaskHandler.class);

    bind(CIExecuteStepTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.EXECUTE_STEP_VM))
        .to(CIVMExecuteStepTaskHandler.class);
    bind(CIExecuteStepTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.EXECUTE_STEP_K8))
        .to(CIK8ExecuteStepTaskHandler.class);

    bind(CICleanupTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.CLEANUP_VM))
        .to(CIVmCleanupTaskHandler.class);
    bind(CICleanupTaskHandler.class)
        .annotatedWith(Names.named(CITaskConstants.CLEANUP_K8))
        .to(CIK8CleanupTaskHandler.class);
  }
}
