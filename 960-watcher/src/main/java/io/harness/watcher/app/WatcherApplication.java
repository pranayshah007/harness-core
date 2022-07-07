/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.app;

import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.delegate.message.MessageConstants.NEW_WATCHER;
import static io.harness.delegate.message.MessengerType.WATCHER;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractAndPrepareAuthority;
import static io.harness.grpc.utils.DelegateGrpcConfigExtractor.extractTarget;

import static com.google.common.base.Charsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.delegate.message.MessageService;
import io.harness.event.client.impl.tailer.TailerModule;
import io.harness.event.client.impl.tailer.TailerModule.Config;
import io.harness.managerclient.WatcherManagerClientModule;
import io.harness.serializer.YamlUtils;
import io.harness.threading.ExecutorModule;
import io.harness.watcher.service.WatcherService;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Created by brett on 10/26/17
 */
@Slf4j
public class WatcherApplication {
  private static String processId;

  private static WatcherConfiguration configuration;

  public static String getProcessId() {
    return processId;
  }

  public static WatcherConfiguration getConfiguration() {
    return configuration;
  }

  public static void main(String... args) throws Exception {
    try {
      processId = Splitter.on("@").split(ManagementFactory.getRuntimeMXBean().getName()).iterator().next();

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
      configuration = new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), WatcherConfiguration.class);

      // Optionally remove existing handlers attached to j.u.l root logger
      SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)

      // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
      // the initialization phase of your application
      SLF4JBridgeHandler.install();

      // Set logging level
      java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

      boolean upgrade = false;
      String previousWatcherProcess = null;
      if (args.length > 1 && StringUtils.equals(args[1], "upgrade")) {
        upgrade = true;
        previousWatcherProcess = args[2];
      }

      log.info("Process: {}", ManagementFactory.getRuntimeMXBean().getName());
      WatcherApplication watcherApplication = new WatcherApplication();
      watcherApplication.run(configuration, upgrade, previousWatcherProcess);
    } catch (Exception exception) {
      log.error("Watcher process initialization failed", exception);
      throw exception;
    }
  }

  private void run(WatcherConfiguration configuration, boolean upgrade, String previousWatcherProcess)
      throws Exception {
    int cores = Runtime.getRuntime().availableProcessors();
    int corePoolSize = 2 * cores;
    int maximumPoolSize = Math.max(corePoolSize, 200);

    ExecutorModule.getInstance().setExecutorService(
        new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("watcher-task-%d").build()));

    Set<Module> modules = new HashSet<>();

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(WatcherConfiguration.class).toInstance(configuration);
      }
    });

    // TODO: Remove this block once we completely deprecate accountSecret in YAML.
    String delegateToken = configuration.getDelegateToken();
    if (StringUtils.isEmpty(delegateToken)) {
      log.error("Error while reading secret");
      throw new RuntimeException("Neither delegateToken nor accountSecret present in config-watcher.yml");
    }
    modules.add(
        new WatcherManagerClientModule(configuration.getManagerUrl(), configuration.getAccountId(), delegateToken));

    modules.add(WatcherModule.getInstance());

    if (!isOnPrem(System.getenv().get(DEPLOY_MODE))) {
      String managerHostAndPort = System.getenv("MANAGER_HOST_AND_PORT");
      String publishTarget = null;
      String publishAuthority = null;
      if (configuration.getPublishTarget() != null && configuration.getPublishAuthority() != null) {
        publishTarget = configuration.getPublishTarget();
        publishAuthority = configuration.getPublishAuthority();
      } else if (managerHostAndPort != null) {
        publishTarget = extractTarget(managerHostAndPort);
        publishAuthority = extractAndPrepareAuthority(managerHostAndPort, "events", false);
      }
      if (publishTarget != null && publishAuthority != null) {
        modules.add(new TailerModule(Config.builder()
                                         .accountId(configuration.getAccountId())
                                         .accountSecret(delegateToken)
                                         .queueFilePath(configuration.getQueueFilePath())
                                         .publishTarget(publishTarget)
                                         .publishAuthority(publishAuthority)
                                         .build()));
      } else {
        log.warn("Unable to configure event publisher configs. Event publisher will be disabled");
      }
    } else {
      log.warn("Skipping event publisher configuration for on-prem deployment");
    }

    Injector injector = Guice.createInjector(modules);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      MessageService messageService = injector.getInstance(MessageService.class);
      messageService.closeChannel(WATCHER, processId);
      log.info("My watch has ended");
      LogManager.shutdown();
    }));

    if (upgrade) {
      MessageService messageService = injector.getInstance(MessageService.class);
      log.info("Sending previous watcher process {} new watcher process ID: {}", previousWatcherProcess, processId);
      messageService.writeMessageToChannel(WATCHER, previousWatcherProcess, NEW_WATCHER, processId);
    }

    WatcherService watcherService = injector.getInstance(WatcherService.class);
    watcherService.run(upgrade);

    // This should run in case of upgrade flow otherwise never called
    injector.getInstance(ExecutorService.class).shutdown();
    injector.getInstance(ExecutorService.class).awaitTermination(5, TimeUnit.MINUTES);
    log.info("Flushing logs");
    LogManager.shutdown();
    System.exit(0);
  }
}
