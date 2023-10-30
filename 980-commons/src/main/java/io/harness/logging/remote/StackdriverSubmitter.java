/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.logging.client.LoggingTokenClient;
import io.harness.logging.client.LoggingTokenClientFactory;
import io.harness.logging.common.AccessTokenBean;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingException;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Synchronicity;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StackdriverSubmitter implements LogSubmitter<ILoggingEvent> {
  private static final int MAX_BATCH_SIZE = 1000;
  private static final String LOG_NAME = "delegate";
  private static final String GLOBAL_RESOURCE = "global";
  private static final long MAX_SEND_INTERVAL = Duration.ofSeconds(10).toMillis();
  private Logging stackdriver;
  private long lastSentTime = 0;
  private final LoggingTokenClient loggingTokenClient;
  private final List<Map<String, ?>> logLines = new ArrayList<>(MAX_BATCH_SIZE + 1);
  private final LoggerConfig config;
  private final LineConverter<LogEntry> converter;
  private final Supplier<Map<String, String>> labelSupplier;

  public StackdriverSubmitter(final LoggerConfig config, final LineConverter<LogEntry> converter,
      final Supplier<Map<String, String>> labelSupplier) {
    this.config = config;
    this.converter = converter;
    this.labelSupplier = labelSupplier;
    this.loggingTokenClient = new LoggingTokenClientFactory(config.getAccountId(), config.getDelegateToken(),
        config.getLoggingTokenUrl() + "/api/", config.getClientCertPath(), config.getClientCertKeyPath(),
        config.isTrustAllCerts())
                                  .get();
  }

  @Override
  public synchronized void submit(final LogAppender<ILoggingEvent> appender, final int minimum) {
    try {
      // We could have log lines from the previous failed send attempt
      if (logLines.isEmpty() || logLines.size() < minimum) {
        appender.drain(logLines, MAX_BATCH_SIZE - logLines.size());
      }

      // If we have enough lines for minimum batch then send, otherwise skip till next iteration
      if (logLines.size() >= minimum || System.currentTimeMillis() - lastSentTime > MAX_SEND_INTERVAL) {
        sendLogs(logLines);
        lastSentTime = System.currentTimeMillis();
        logLines.clear();
      }
    } catch (final Exception ex) {
      log.error("Failed to submit logs to stackdriver", ex);
    }
  }

  @Override
  public boolean isReachable() {
    try {
      return pingStackdriver();
    } catch (Exception ex) {
      log.warn("Connectivity test for Stack Driver failed.", ex);
    }
    return false;
  }

  private boolean pingStackdriver() throws IOException {
    try {
      if (getStackdriver() == null) {
        return false;
      }
      getStackdriver().setWriteSynchronicity(Synchronicity.SYNC);
      // Send a test log line to stack driver
      final var pingLine = new HashMap<String, String>();
      pingLine.put("message", "Stack Driver connectivity test successful");
      pingLine.put("timestamp_millis", String.valueOf(System.currentTimeMillis()));
      sendLogs(Collections.singletonList(pingLine));
      return true;
    } finally {
      if (stackdriver != null) { // Can still be null if we failed to get the client
        getStackdriver().setWriteSynchronicity(Synchronicity.ASYNC);
      }
    }
  }

  private void sendLogs(final List<Map<String, ?>> logs) {
    try {
      final var stackdriverLines = logs.stream().map(converter::convert).collect(Collectors.toList());
      getStackdriver().write(stackdriverLines, Logging.WriteOption.logName(LOG_NAME),
          Logging.WriteOption.resource(MonitoredResource.newBuilder(GLOBAL_RESOURCE).build()),
          Logging.WriteOption.labels(labelSupplier.get()));

    } catch (final LoggingException e) {
      log.error("Failed to submit logs. Stack driver logging will be temporarily disabled.", e);
    } catch (final Exception e) {
      log.error("Failed to get Logging client for StackdriverLogging", e);
    }
  }

  private Logging getStackdriver() throws IOException {
    if (stackdriver == null) {
      final OAuth2CredentialsWithRefresh.OAuth2RefreshHandler handler = () -> {
        final var accessToken = getStackdriverLoggingToken();
        return new AccessToken(accessToken.getTokenValue(), new Date(accessToken.getExpirationTimeMillis()));
      };

      final AccessTokenBean accessTokenBean = getStackdriverLoggingToken();

      final var accessToken =
          new AccessToken(accessTokenBean.getTokenValue(), new Date(accessTokenBean.getExpirationTimeMillis()));
      //      final var transportOptions = HttpTransportOptions.newBuilder().build();
      final var credentials =
          OAuth2CredentialsWithRefresh.newBuilder().setAccessToken(accessToken).setRefreshHandler(handler).build();
      final var loggingOptionsBuilder =
          LoggingOptions
              .newBuilder()
              //                                            .setTransportOptions(transportOptions)
              .setProjectId(accessTokenBean.getProjectId())
              .setCredentials(credentials);

      stackdriver = loggingOptionsBuilder.build().getService();
    }
    return stackdriver;
  }

  private AccessTokenBean getStackdriverLoggingToken() throws IOException {
    final var response = execute(loggingTokenClient.getLoggingToken(config.getAccountId()));
    if (response != null && response.getResource() != null) {
      final var token = response.getResource();
      log.debug("Successfully refreshed stackdriver logging token. Valid until {}",
          new Date(token.getExpirationTimeMillis()));
      return token;
    }
    throw new IOException("Failed to get access token for stackdriver logging");
  }
}
