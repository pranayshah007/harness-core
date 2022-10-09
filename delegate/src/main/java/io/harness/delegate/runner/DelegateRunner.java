/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner;

import ch.qos.logback.classic.LoggerContext;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.runner.modules.DelegateRunnerModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static io.harness.delegate.clienttools.InstallUtils.setupClientTools;

@Slf4j
public class DelegateRunner {
  public void run(final String configFileName) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    final Injector injector = Guice.createInjector(new DelegateRunnerModule(configFileName));
    DelegateConfiguration delegateConfiguration = injector.getInstance(DelegateConfiguration.class);
    setupClientTools(delegateConfiguration);
    injector.getInstance(DelegateRunnerCommand.class).run();
    shutdown(injector);
  }

  public static void main(String[] args) {
    log.info("Delegate runner started", args);
    (new DelegateRunner()).run("runner_config.yml");
    log.info("Delegate runner exited");
    // FIXME: properly shutdown every process
    System.exit(0);
  }

  private void shutdown(final Injector injector) {
      injector.getInstance(ExecutorService.class).shutdown();
      log.info("Executor services have been shut down.");

      injector.getInstance(DefaultAsyncHttpClient.class).close();
      log.info("Async HTTP client has been closed.");

      final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
      if (loggerFactory instanceof LoggerContext) {
        final LoggerContext context = (LoggerContext) loggerFactory;
        context.stop();
      }
      log.info("Log manager has been shutdown and logs have been flushed.");
  }

}
