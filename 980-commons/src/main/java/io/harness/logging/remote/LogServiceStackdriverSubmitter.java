/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.remote;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.logging.client.LogServiceStackdriverClient;
import io.harness.logging.client.LogServiceStackdriverClientFactory;
import io.harness.logging.client.LoggingTokenClient;
import io.harness.logging.client.LoggingTokenClientFactory;
import io.harness.logging.client.StackdriverLogLine;
import io.harness.logging.common.AccessTokenBean;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogServiceStackdriverSubmitter implements LogSubmitter<ILoggingEvent> {
  private static final long ONE_MIN = Duration.ofMinutes(1).toMillis();
  private static final int MAX_BATCH_SIZE = 1000;
  private static final long MAX_SEND_INTERVAL = Duration.ofSeconds(10).toMillis();
  private final List<Map<String, ?>> logLines = new ArrayList<>(MAX_BATCH_SIZE + 1);
  private final LogServiceStackdriverClient logServiceClient;
  private final LoggingTokenClient loggingTokenClient;
  private final LoggerConfig config;
  private final LineConverter<StackdriverLogLine> converter;
  private AccessTokenBean logServiceToken;
  private long lastSentTime = 0;

  public LogServiceStackdriverSubmitter(final LoggerConfig config, final LineConverter<StackdriverLogLine> converter) {
    this.config = config;
    this.converter = converter;
    this.logServiceClient = new LogServiceStackdriverClientFactory(config.getLoggingServiceUrl(),
        config.getClientCertPath(), config.getClientCertKeyPath(), config.isTrustAllCerts())
                                .get();
    this.loggingTokenClient = new LoggingTokenClientFactory(config.getAccountId(), config.getDelegateToken(),
        config.getLoggingTokenUrl() + "/api/", config.getClientCertPath(), config.getClientCertKeyPath(),
        config.isTrustAllCerts())
                                  .get();
  }

  @Override
  synchronized public void submit(final LogAppender<ILoggingEvent> appender, final int minimum) {
    try {
      // We could have log lines from the previous failed send attempt
      if (logLines.isEmpty() || logLines.size() < minimum) {
        appender.drain(logLines, MAX_BATCH_SIZE - logLines.size());
      }

      // If we have enough lines for minimum batch then send, otherwise skip till next iteration
      if (logLines.size() >= minimum || System.currentTimeMillis() - lastSentTime > MAX_SEND_INTERVAL) {
        sendLogs();
        lastSentTime = System.currentTimeMillis();
        logLines.clear();
      }
    } catch (final Exception ex) {
      log.error("Failed to submit logs.", ex);
    }
  }

  @Override
  public boolean isReachable() {
    try {
      return execute(logServiceClient.pingStackdriver(getLogServiceToken()));
    } catch (final Exception e) {
      log.warn("Stackdriver not reachable through logging service", e);
      return false;
    }
  }

  private void sendLogs() throws IOException {
    final var serviceToken = getLogServiceToken();
    final var stackdriverLines = logLines.stream().map(converter::convert).collect(Collectors.toList());
    execute(logServiceClient.sendLogs(serviceToken, config.getAccountId(), config.getAppName(), stackdriverLines));
  }

  private String getLogServiceToken() throws IOException {
    if (logServiceToken == null || logServiceToken.getExpirationTimeMillis() < System.currentTimeMillis() - ONE_MIN) {
      final var response = execute(loggingTokenClient.getLoggingServiceToken(config.getAccountId()));
      if (response == null || response.getResource() == null) {
        throw new IOException("Failed to get log service token for submitting logs");
      }
      logServiceToken = response.getResource();
      log.debug("refreshed log service token. Valid till: {}", new Date(logServiceToken.getExpirationTimeMillis()));
    }
    return logServiceToken.getTokenValue();
  }
}
