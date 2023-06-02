/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployMode.isOnPrem;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.network.Http.connectableHttpUrl;
import static io.harness.network.Localhost.getLocalHostName;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.version.VersionInfoManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.WriteOption;
import com.google.cloud.logging.LoggingException;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.LoggingOptions.Builder;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.Synchronicity;
import com.google.cloud.logging.v2.LoggingSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class RemoteStackdriverLogAppender<E> extends AppenderBase<E> {
  public static final int MIN_BATCH_SIZE = 100;
  private static final int MAX_BATCH_SIZE = 1000;
  private static final String SEVERITY = "severity";
  private static final String LOG_NAME = "delegate";
  private static final String LOG_PROXY_HOST = "logs.harness.io:443";
  private static final long LOG_QUEUE_BUFFER_MILLIS = 60000L;

  private static Logging logging;

  private AtomicBoolean useLogProxy;
  private String localhostName = "localhost";
  private CustomJsonLayout layout;
  private BlockingQueue<LogEntry> logQueue;
  private Map<String, String> logLabels;
  private final LogLines logLines = new LogLines();
  private final ExecutorService appenderPool = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setNameFormat("remote-stackdriver-log-appender").build());
  private final VersionInfoManager versionInfoManager = new VersionInfoManager();
  private final String processId =
      Splitter.on("@").split(ManagementFactory.getRuntimeMXBean().getName()).iterator().next();
  private final AtomicInteger failedAttempts = new AtomicInteger(0);
  private final AtomicLong nextAttempt = new AtomicLong(0);
  private final AtomicBoolean stackDriverReachable = new AtomicBoolean();
  private final AtomicLong nextConnectivityTestAttempt = new AtomicLong(0);

  @Override
  public void start() {
    synchronized (this) {
      if (started) {
        return;
      }
      if (isOnPrem(System.getenv().get(DEPLOY_MODE))) {
        log.info("Log will not be initiated for mode ONPREM");
        return;
      }

      layout = new CustomJsonLayout();
      super.start();
      localhostName = getLocalHostName();
      logQueue = Queues.newLinkedBlockingQueue(500000);
      Executors
          .newSingleThreadScheduledExecutor(
              new ThreadFactoryBuilder().setNameFormat("remote-stackdriver-log-submitter").build())
          .scheduleWithFixedDelay(this::send, 1L, 3L, TimeUnit.SECONDS);
    }
  }
  @Override
  public void stop() {
    super.stop();
    flush();
  }

  @Override
  protected void append(E eventObject) {
    if (eventObject instanceof ILoggingEvent) {
      appenderPool.submit(() -> {
        try {
          synchronized (this) {
            Map<String, ?> jsonMap = layout.toJsonMap((ILoggingEvent) eventObject);
            Level logLevel = Level.valueOf((String) jsonMap.remove(SEVERITY));
            if (!logQueue.offer(LogEntry.newBuilder(JsonPayload.of(jsonMap))
                                    .setSeverity(logLevelToSeverity(logLevel))
                                    .setTimestamp(System.currentTimeMillis())
                                    .build())) {
              logQueue.clear();
              log.error("No space left in log queue. Cleared.");
            }
          }
        } catch (Exception ex) {
          log.error("Error appending log entry", ex);
        }
      });
    } else {
      log.error("Logging event is not ILogEvent: {}", eventObject);
    }
  }

  @VisibleForTesting
  public static Severity logLevelToSeverity(Level logLevel) {
    switch (logLevel.toInt()) {
      case Level.TRACE_INT:
      case Level.DEBUG_INT:
        return Severity.DEBUG;
      case Level.INFO_INT:
        return Severity.INFO;
      case Level.WARN_INT:
        return Severity.WARNING;
      case Level.ERROR_INT:
        return Severity.ERROR;
      default:
        unhandled(logLevel.toString());
        return Severity.INFO;
    }
  }

  private void send() {
    submitLogs(MIN_BATCH_SIZE);
  }

  // TODO: This whole class needs a complete rework. Right now it causes a huge delay on delegate startup with
  // dropwizard
  private void flush() {
    while (true) {
      synchronized (this) {
        // If logging is not initialized, just ignore the queue
        // adding null check for logQueue in case of onPrem deploy type
        if (logging == null || logQueue == null) {
          return;
        }

        if (logQueue.isEmpty()) {
          return;
        }
        submitLogs(0);
      }
    }
  }

  private void submitLogs(int minimum) {
    synchronized (this) {
      if (!isStackDriverReachable() && System.currentTimeMillis() < nextConnectivityTestAttempt.get()) {
        logQueue.clear();
        return;
      }

      if (logQueue.isEmpty()
          || (logQueue.size() < minimum
              && (logQueue.peek().getTimestamp() + LOG_QUEUE_BUFFER_MILLIS > System.currentTimeMillis()))) {
        return;
      }

      if (System.currentTimeMillis() < nextAttempt.get()) {
        return;
      }

      ensureLoggingInitialized();
      if (logging == null) {
        int attempts = failedAttempts.incrementAndGet();
        if (attempts >= 50 && attempts % 10 == 0) {
          logQueue.clear();
          log.error("Failed to initialize logging after {} attempts. Cleared log queue.", attempts);
        }
        // Exponential backoff. Delay by 2 ^ (attempts / 2) seconds, max 60. (12 attempts to reach the max)
        long delayMillis = (long) (Math.min(Math.pow(2, (double) attempts / 2d), 60d) * 1000d);
        nextAttempt.set(System.currentTimeMillis() + delayMillis);
        return;
      }

      failedAttempts.set(0);
      nextAttempt.set(0);

      try {
        if (isStackDriverReachable()) {
          if (logQueue.size() > MAX_BATCH_SIZE) {
            logLines.add(LogEntry
                             .newBuilder(JsonPayload.of(ImmutableMap.of("message",
                                 "Log queue exceeds max batch size (" + MAX_BATCH_SIZE
                                     + "). Current queue size: " + logQueue.size())))
                             .setSeverity(Severity.WARNING)
                             .build());
          }
          logQueue.drainTo(logLines.getLines(), MAX_BATCH_SIZE);
          logging.write(logLines.getLines(), WriteOption.logName(LOG_NAME),
              WriteOption.resource(MonitoredResource.newBuilder("global").build()), WriteOption.labels(getLogLabels()));
        }
      } catch (LoggingException ex) {
        log.error("Failed to submit logs. Stack driver logging will be temporarily disabled.", ex);
        markStackDriverUnreachable();
        logQueue.clear();
      } catch (Exception ex) {
        log.error("Failed to submit logs.", ex);
      } finally {
        logLines.clear();
      }
    }
  }

  private void ensureLoggingInitialized() {
    if (logging != null) {
      Date nineMinutesFromNow = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(9));
      GoogleCredentials credentials = (GoogleCredentials) logging.getOptions().getCredentials();
      Date expirationTime = credentials.getAccessToken().getExpirationTime();
      if (expirationTime.before(nineMinutesFromNow)) {
        log.info("Logging token expires {}. Refreshing.", expirationTime);
        try {
          logging.close();
        } catch (Exception e) {
          log.error("Error closing logging", e);
        }
        logging = null;
      } else {
        testStackDriverConnectivity();
        return;
      }
    }

    AccessTokenBean accessTokenBean = getLoggingToken();
    if (accessTokenBean == null) {
      return;
    }

    if (useLogProxy == null) {
      boolean cannotConnectStackdriver = !connectableHttpUrl("https://" + LoggingSettings.getDefaultEndpoint(), false);
      if (cannotConnectStackdriver) {
        return;
        // TODO (brett) - Enable log proxy check after verified working with grpc
        //        if (!connectableHttpUrl("https://" + LOG_PROXY_HOST)) {
        //          return;
        //        }
      }
      useLogProxy = new AtomicBoolean(cannotConnectStackdriver);
    }

    Builder loggingOptionsBuilder =
        LoggingOptions.newBuilder()
            .setProjectId(accessTokenBean.getProjectId())
            .setCredentials(GoogleCredentials.create(
                new AccessToken(accessTokenBean.getTokenValue(), new Date(accessTokenBean.getExpirationTimeMillis()))));

    if (useLogProxy.get()) {
      loggingOptionsBuilder.setHost(LOG_PROXY_HOST);
    }

    try {
      logging = loggingOptionsBuilder.build().getService();
    } catch (Exception e) {
      log.error("Failed to build Logging client for StackdriverLogging", e);
    }

    testStackDriverConnectivity();
  }

  private void testStackDriverConnectivity() {
    if (logging != null && !stackDriverReachable.get()) {
      logging.setWriteSynchronicity(Synchronicity.SYNC);

      try {
        logging.write(Arrays.asList(LogEntry
                                        .newBuilder(JsonPayload.of(
                                            ImmutableMap.of("message", "Stack Driver connectivity test successful")))
                                        .setSeverity(Severity.INFO)
                                        .build()),
            WriteOption.logName(LOG_NAME), WriteOption.resource(MonitoredResource.newBuilder("global").build()),
            WriteOption.labels(getLogLabels()));

        logging.setWriteSynchronicity(Synchronicity.ASYNC);
        markStackDriverReachable();
      } catch (LoggingException ex) {
        log.warn("Connectivity test for Stack Driver failed. Stack driver logging will be temporarily disabled.", ex);
        markStackDriverUnreachable();
        logQueue.clear();
      } catch (Exception ex) {
        log.warn("Connectivity test for Stack Driver failed.", ex);
      }
    }
  }

  private boolean isStackDriverReachable() {
    return stackDriverReachable.get();
  }

  private void markStackDriverReachable() {
    stackDriverReachable.set(true);
  }

  private void markStackDriverUnreachable() {
    stackDriverReachable.set(false);
    nextConnectivityTestAttempt.set(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60L));
  }

  private Map<String, String> getLogLabels() {
    String delegateId = getDelegateId();
    if (isEmpty(logLabels) || !StringUtils.equals(delegateId, logLabels.get("delegateId"))) {
      // Stackdriver has a problem with label containing 'https://' ( ':' seems to be a special character so we should
      // remove it - '/' confirmed to work )
      ImmutableMap.Builder<String, String> labelsBuilder =
          ImmutableMap.<String, String>builder()
              .put("source", localhostName)
              .put("processId", processId)
              .put("version", versionInfoManager.getFullVersion())
              .put("app", getAppName())
              .put("accountId", getAccountId())
              .put("managerHost", substringAfter(getManagerHost(), "://"));

      if (isNotBlank(delegateId)) {
        labelsBuilder.put("delegateId", delegateId);
      }
      logLabels = labelsBuilder.build();
      log.info("Logging labels {}", logLabels);
    }
    return logLabels;
  }

  protected abstract String getAppName();
  protected abstract String getAccountId();
  protected abstract String getManagerHost();
  protected abstract String getDelegateId();
  protected abstract AccessTokenBean getLoggingToken();

  @VisibleForTesting
  public BlockingQueue<LogEntry> getLogQueue() {
    return logQueue;
  }

  public static class LogLines {
    private final List<LogEntry> lines = new ArrayList<>(MAX_BATCH_SIZE + 1);

    public List<LogEntry> getLines() {
      return lines;
    }

    public void add(LogEntry logLine) {
      lines.add(logLine);
    }

    public void clear() {
      lines.clear();
    }
  }
}
