/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import static io.harness.annotations.dev.HarnessModule._420_DELEGATE_AGENT;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.delegate.message.MessageConstants.DELEGATE_DASH;
import static io.harness.delegate.message.MessageConstants.NEW_DELEGATE;
import static io.harness.delegate.message.MessageConstants.WATCHER_DATA;
import static io.harness.delegate.message.MessageConstants.WATCHER_HEARTBEAT;
import static io.harness.delegate.message.MessageConstants.WATCHER_PROCESS;
import static io.harness.delegate.message.MessengerType.DELEGATE;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.base.Charsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.TargetModule;
import io.harness.configuration.DeployMode;
import io.harness.delegate.app.modules.DelegateAgentModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.message.MessageService;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.pingpong.PingPongClient;
import io.harness.serializer.YamlUtils;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.utils.ProcessControl;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

@Slf4j
@TargetModule(_420_DELEGATE_AGENT)
@BreakDependencyOn("io.harness.serializer.ManagerRegistrars")
@BreakDependencyOn("io.harness.serializer.kryo.CvNextGenCommonsBeansKryoRegistrar")
public class DelegateApplication {
  private static final String DEPLOY_MODE = System.getenv(DeployMode.DEPLOY_MODE);
  private static String processId = String.valueOf(ProcessControl.myProcessId());
  private static DelegateConfiguration configuration;

  public static String getProcessId() {
    return processId;
  }

  public static void main(String... args) throws IOException {
    try {
      String proxyUser = System.getenv("PROXY_USER");
      if (isNotBlank(proxyUser)) {
        System.setProperty("http.proxyUser", proxyUser);
        System.setProperty("https.proxyUser", proxyUser);
      }
      String proxyPassword = System.getenv("PROXY_PASSWORD");
      if (isNotBlank(proxyPassword)) {
        System.setProperty("http.proxyPassword", proxyPassword);
        System.setProperty("https.proxyPassword", proxyPassword);
      }

      File configFile = new File(args[0]);
      configuration = new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), DelegateConfiguration.class);

      String watcherProcess = null;
      if (args.length > 1 && StringUtils.equals(args[1], "watched")) {
        watcherProcess = args[2];
      }

      // Optionally remove existing handlers attached to j.u.l root logger
      SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)

      // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
      // the initialization phase of your application
      SLF4JBridgeHandler.install();

      // Set logging level
      java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

      initializeLogging();
      log.info("Starting Delegate in {} mode", DEPLOY_MODE);
      log.info("Process: {}", ManagementFactory.getRuntimeMXBean().getName());
      DelegateApplication delegateApplication = new DelegateApplication();
      delegateApplication.run(configuration, watcherProcess);
    } catch (RuntimeException | IOException exception) {
      log.error("Delegate process initialization failed", exception);
      throw exception;
    }
  }

  private void run(DelegateConfiguration configuration, String watcherProcess) {
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("default-task-%d").setPriority(Thread.NORM_PRIORITY).build()));

    Injector injector = Guice.createInjector(new DelegateAgentModule(configuration, false));
    MessageService messageService = injector.getInstance(MessageService.class);

    // Add JVM shutdown hook so as to have a clean shutdown
    addShutdownHook(injector, messageService);

    boolean watched = watcherProcess != null;
    if (watched) {
      log.info("Sending watcher {} new delegate process ID: {}", watcherProcess, processId);
      messageService.writeMessageToChannel(WATCHER, watcherProcess, NEW_DELEGATE, processId);
      Map<String, Object> watcherData = new HashMap<>();
      watcherData.put(WATCHER_HEARTBEAT, System.currentTimeMillis());
      watcherData.put(WATCHER_PROCESS, watcherProcess);
      messageService.putAllData(WATCHER_DATA, watcherData);
    }

    injector.getInstance(PingPongClient.class).startAsync();

    DelegateAgentService delegateService = injector.getInstance(DelegateAgentService.class);
    delegateService.run(watched, false);

    System.exit(0);
  }

  private void addShutdownHook(Injector injector, MessageService messageService) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        try {
          injector.getInstance(DelegateAgentService.class).shutdown(false);
        } catch (final InterruptedException e) {
          log.error("Delegate shutdown interrupted", e);
          Thread.currentThread().interrupt();
        }

        messageService.closeChannel(DELEGATE, processId);
        messageService.closeData(DELEGATE_DASH + processId);
        log.info("Message service has been closed.");

        injector.getInstance(PingPongClient.class).stopAsync();
        if (!isOnPrem(DEPLOY_MODE)) {
          injector.getInstance(EventPublisher.class).shutdown();
        }

        injector.getInstance(ExecutorService.class).shutdown();
        log.info("Executor services have been shut down.");

        injector.getInstance(DefaultAsyncHttpClient.class).close();
        log.info("Async HTTP client has been closed.");
        log.info("Delegate Shutdown completed successfully");
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
          LoggerContext context = (LoggerContext) loggerFactory;
          context.stop();
        }
        log.info("Log manager has been shutdown and logs have been flushed.");
      } catch (Exception ex) {
        log.error("Encountered error while executing Delegate shutdown ", ex);
      }
    }));
  }
}
