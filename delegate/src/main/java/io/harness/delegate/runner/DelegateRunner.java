/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner;

import io.harness.delegate.runner.modules.DelegateRunnerModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateRunner {
  public void run(final String configFileName) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    final Injector injector = Guice.createInjector(new DelegateRunnerModule(configFileName));
    injector.getInstance(DelegateRunnerCommand.class).run();
  }

  public static void main(String[] args) {
    log.info("Delegate runner started", args);
    (new DelegateRunner()).run("runner_config.yml");
  }
}
