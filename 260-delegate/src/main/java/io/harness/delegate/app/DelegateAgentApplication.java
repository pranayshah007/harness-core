/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import static io.harness.logging.LoggingInitializer.initializeLogging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.configuration.DeployMode;
import io.harness.delegate.app.modules.DelegateAgentModule;
import io.harness.delegate.app.resource.HealthResource;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.metrics.DelegateAgentMetricResource;
import io.harness.delegate.metrics.DelegateAgentMetrics;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.event.client.EventPublisher;
import io.harness.health.HealthMonitor;
import io.harness.health.HealthService;
import io.harness.serializer.YamlUtils;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;

import software.wings.jersey.KryoFeature;

import ch.qos.logback.classic.LoggerContext;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

@Slf4j
public class DelegateAgentApplication extends Application<DelegateAgentConfig> {
  private static final String DEPLOY_MODE = System.getenv(DeployMode.DEPLOY_MODE);
  // TODO: Two config classes only needed while we have two entrypoints. Get rid of one when removing
  // DelegateApplication.
  private final DelegateConfiguration configuration;

  public DelegateAgentApplication(final String configFileName) throws IOException {
    final File configFile = new File(configFileName);
    try {
      configuration = new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), DelegateConfiguration.class);
    } catch (final IOException e) {
      log.error("Unable to read the delegate config file {}", configFileName, e);
      throw e;
    }
  }

  public static void main(final String... args) throws Exception {
    new DelegateAgentApplication(args[1]).run(args);
  }

  private static void setupProxyConfig() {
    final String proxyUser = System.getenv("PROXY_USER");
    if (isNotBlank(proxyUser)) {
      System.setProperty("http.proxyUser", proxyUser);
      System.setProperty("https.proxyUser", proxyUser);
    }
    final String proxyPassword = System.getenv("PROXY_PASSWORD");
    if (isNotBlank(proxyPassword)) {
      System.setProperty("http.proxyPassword", proxyPassword);
      System.setProperty("https.proxyPassword", proxyPassword);
    }
  }

  @Override
  public void run(final DelegateAgentConfig delegateAgentConfig, final Environment environment) throws Exception {
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("other-%d").setPriority(Thread.NORM_PRIORITY).build()));

    final Injector injector = Guice.createInjector(new DelegateAgentModule(configuration));

    addShutdownHook(injector);

    registerHealthChecks(environment, injector);
    registerResources(environment, injector);
    initializeMetrics(environment, injector);

    log.info("Starting Delegate in {} mode", DEPLOY_MODE);
    log.info("Process: {}", ManagementFactory.getRuntimeMXBean().getName());

    injector.getInstance(DelegateAgentService.class).run(false, true);
  }

  @Override
  public void initialize(final Bootstrap<DelegateAgentConfig> bootstrap) {
    super.initialize(bootstrap);

    setupProxyConfig();
    // Optionally remove existing handlers attached to j.u.l root logger
    SLF4JBridgeHandler.removeHandlersForRootLogger(); // (since SLF4J 1.6.5)
    // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
    // the initialization phase of your application
    SLF4JBridgeHandler.install();
    // Set logging level
    java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

    initializeLogging();

    bootstrap.setMetricRegistry(new MetricRegistry());
  }

  @Override
  protected void bootstrapLogging() {}

  private void addShutdownHook(final Injector injector) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        injector.getInstance(DelegateAgentService.class).shutdown(true);
      } catch (final InterruptedException e) {
        log.error("Delegate shutdown interrupted", e);
        Thread.currentThread().interrupt();
      }

      injector.getInstance(ExecutorService.class).shutdown();
      injector.getInstance(EventPublisher.class).shutdown();
      log.info("Executor services have been shut down.");

      injector.getInstance(DefaultAsyncHttpClient.class).close();
      log.info("Async HTTP client has been closed.");

      final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
      if (loggerFactory instanceof LoggerContext) {
        final LoggerContext context = (LoggerContext) loggerFactory;
        context.stop();
      }
      log.info("Log manager has been shutdown and logs have been flushed.");
    }));
    log.info("Delegate agent shutdown hooks registered");
  }

  private void registerHealthChecks(final Environment environment, final Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);

    healthService.registerMonitor(injector.getInstance(HealthMonitor.class));
    environment.healthChecks().register("DelegateAgentApp", healthService);
  }

  private void initializeMetrics(Environment environment, Injector injector) {
    log.info("Initializing metrics for Delegate agent.");
    DelegateAgentMetrics delegateAgentMetrics = injector.getInstance(DelegateAgentMetrics.class);
    delegateAgentMetrics.scheduleDelegateAgentMetricsPoll();
    delegateAgentMetrics.registerDelegateMetrics();
  }

  private void registerResources(final Environment environment, final Injector injector) {
    environment.jersey().register(injector.getInstance(HealthResource.class));
    environment.jersey().register(injector.getInstance(ExecutionResponseResource.class));
    environment.jersey().register(injector.getInstance(DelegateAgentMetricResource.class));
    environment.jersey().register(injector.getInstance(KryoFeature.class));
  }
}
