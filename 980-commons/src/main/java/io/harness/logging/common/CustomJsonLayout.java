/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.common;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import ch.qos.logback.contrib.json.JsonLayoutBase;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.harness.logging.AutoLogContext;
import io.harness.logging.MdcKeyLogContext;
import lombok.extern.slf4j.Slf4j;

/**
 * See {@link ch.qos.logback.contrib.json.classic.JsonLayout}
 */
@Slf4j
public class CustomJsonLayout extends JsonLayoutBase<ILoggingEvent> {
  private static final String TIMESTAMP = "timestamp";
  private static final String SEVERITY = "severity";
  private static final String VERSION_ENV_VAR = "VERSION";
  private static final String VERSION = "version";
  private static final String THREAD = "thread";
  private static final String LOGGER = "logger";
  private static final String MESSAGE = "message";
  private static final String HARNESS = "harness";
  private static final String EXCEPTION = "exception";
  private static final int MAX_BYTES = 20480;

  private final ThrowableHandlingConverter throwableProxyConverter;

  // Constructor needs to be public for initialization from outside of this package
  // from logback.xml configurations
  @SuppressWarnings("WeakerAccess")
  public CustomJsonLayout() {
    this(null);
  }

  CustomJsonLayout(LoggerContext context) {
    this.context = context;
    timestampFormat = "yyyy-MM-dd HH:mm:ss.SSS Z";
    appendLineSeparator = true;
    jsonFormatter = new JacksonJsonFormatter();
    throwableProxyConverter = new StackTraceProxyConverter();
  }

  @Override
  public void start() {
    this.throwableProxyConverter.start();
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    this.throwableProxyConverter.stop();
  }

  @Override
  public Map<String, Object> toJsonMap(ILoggingEvent event) {
    Map<String, Object> map = new HashMap<>();

    final String formattedMessage = truncateLog(event.getFormattedMessage());

    addTimestamp(TIMESTAMP, true, event.getTimeStamp(), map);
    add(SEVERITY, true, String.valueOf(event.getLevel()), map);
    add(VERSION, true, System.getenv(VERSION_ENV_VAR), map);
    add(THREAD, true, event.getThreadName(), map);
    add(LOGGER, true, event.getLoggerName(), map);
    add(MESSAGE, true, formattedMessage, map);
    addMap(HARNESS, true, event.getMDCPropertyMap(), map);
    addThrowableInfo(event, map);

    if (log.isDebugEnabled() && event.getLevel().toInt() >= Level.INFO.toInt()) {
      for (Entry<String, String> entry : event.getMDCPropertyMap().entrySet()) {
        if (formattedMessage != null && formattedMessage.contains(entry.getValue())) {
          try (AutoLogContext ignore = new MessagePatternLogContext(event.getMessage(), OVERRIDE_ERROR);
               AutoLogContext ignore2 = new MdcKeyLogContext(entry.getKey(), OVERRIDE_ERROR)) {
            log.debug("MDC table and the logging message have the same value {}", entry.getValue());
          }
        }
      }
    }

    return map;
  }

  private void addThrowableInfo(ILoggingEvent value, Map<String, Object> map) {
    if (value != null && value.getThrowableProxy() != null) {
      String ex = throwableProxyConverter.convert(value);
      if (isNotBlank(ex)) {
        map.put(EXCEPTION, ex);
      }
    }
  }

  private static String truncateLog(final String message) {
    if (message == null) {
      return null;
    }
    final Charset charset = StandardCharsets.UTF_8;
    final CharsetDecoder decoder = charset.newDecoder();
    final byte[] sba = message.getBytes(charset);
    if (sba.length <= MAX_BYTES) {
      return message;
    }
    // Ensure truncation by having byte buffer = maxBytes
    final ByteBuffer bb = ByteBuffer.wrap(sba, 0, MAX_BYTES);
    final String endMessage = "... [message truncated because it was too long]";
    // allocate slightly more so that we can append the info at the end
    final byte[] endMessageBytes = endMessage.getBytes(charset);
    final CharBuffer cb = CharBuffer.allocate(MAX_BYTES + endMessageBytes.length);
    // Ignore an incomplete character
    decoder.onMalformedInput(CodingErrorAction.IGNORE);
    decoder.decode(bb, cb, true);
    decoder.flush(cb);
    cb.append(endMessage);
    return new String(cb.array(), 0, cb.position());
  }
}
