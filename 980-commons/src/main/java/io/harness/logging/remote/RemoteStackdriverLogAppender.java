/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.remote;

import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.delegate.DelegateAgentCommonVariables.UNREGISTERED;
import static io.harness.network.Localhost.getLocalHostName;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.version.VersionInfoManager;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class RemoteStackdriverLogAppender extends AppenderBase<ILoggingEvent> {
  public static final int MIN_BATCH_SIZE = 100;
  private static final String PROCESS_ID =
      Splitter.on("@").split(ManagementFactory.getRuntimeMXBean().getName()).iterator().next();
  private static final String HOST_NAME = getLocalHostName();
  private static final String VERSION = new VersionInfoManager().getFullVersion();
  private final ExecutorService appenderPool = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setNameFormat("remote-stackdriver-log-appender").build());
  private LogSubmitter<ILoggingEvent> submitter;
  private final LogAppender<ILoggingEvent> appender = new StackdriverLogAppender();
  private final Map<String, String> logLabels = new HashMap<>();
  // --- AUTO POPULATED BY LOGBACK FROM logback.xml
  @Setter private String managerHost;
  @Setter private String accountId;
  @Setter private String clientCertPath;
  @Setter private String clientCertKey;
  @Setter private boolean trustAllCerts;
  @Setter private String logStreamingUrl;
  @Setter private String delegateToken;
  @Setter private String appName;
  // --- END OF AUTO POPULATED BY LOGBACK FROM logback.xml

  @Override
  public void start() {
    synchronized (this) {
      if (isStarted()) {
        return;
      }
      if (isOnPrem(System.getenv(DEPLOY_MODE))) {
        log.info("Remote Logging will not be initiated for mode ONPREM");
        return;
      }

      super.start();

      logLabels.put("source", HOST_NAME);
      logLabels.put("processId", PROCESS_ID);
      logLabels.put("version", VERSION);
      logLabels.put("app", appName);
      logLabels.put("accountId", accountId);
      // Stackdriver has a problem with label containing 'https://' ( ':' seems to be a special character so we should
      // remove it - '/' confirmed to work )
      logLabels.put("managerHost", substringAfter(managerHost, "://"));

      Executors
          .newSingleThreadScheduledExecutor(
              new ThreadFactoryBuilder().setNameFormat("remote-stackdriver-log-submitter").build())
          .scheduleWithFixedDelay(this::submit, 1L, 3L, TimeUnit.SECONDS);
    }
  }

  private void submit() {
    final var submitter = getSubmitter();
    if (submitter != null) {
      submitter.submit(appender, MIN_BATCH_SIZE);
    }
  }

  @Override
  public void stop() {
    super.stop();

    if (submitter != null) {
      submitter.flush(appender);
    }
  }

  @Override
  protected void append(final ILoggingEvent eventObject) {
    appenderPool.submit(() -> appender.append(eventObject));
  }

  private LogSubmitter<ILoggingEvent> getSubmitter() {
    // Initialize submitter only on first send to avoid blocking the main thread (happens on a separate threadpool).
    if (submitter == null) {
      try {
        // Create Stackdriver submitter and check if stackdriver reachable.
        final LoggerConfig config = new LoggerConfig(accountId, logStreamingUrl, managerHost, delegateToken,
            clientCertPath, clientCertKey, trustAllCerts, appName);

        final var converter = new StackdriverLineConverter();
        final var gcpSubmitter = new StackdriverSubmitter(config, converter, this::getLogLabels);
        if (gcpSubmitter.isReachable()) {
          log.info("Using GCP Stackdriver as remote logging service.");
          submitter = gcpSubmitter;
        } else { // If stackdriver not reachable directly, use logging service as a proxy (has to always be reachable).
          final var logServiceConverter = new LoggingServiceLineConverter(this::getLogLabels);
          final var logServiceSubmitter = new LogServiceStackdriverSubmitter(config, logServiceConverter);

          if (logServiceSubmitter.isReachable()) {
            log.info("Using Log Service as remote logging service.");
            submitter = logServiceSubmitter;
          } else {
            log.warn("No remote logging service available.");
          }
        }
      } catch (final Exception e) {
        log.error("Exception while creating submitter", e);
      }
    }
    return submitter;
  }

  private Map<String, String> getLogLabels() {
    final String delegateId = DelegateAgentCommonVariables.getDelegateId();
    if (isNotBlank(delegateId) && !UNREGISTERED.equals(delegateId)) {
      logLabels.put("delegateId", delegateId);
    }
    return logLabels;
  }
}
