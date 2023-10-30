/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import io.harness.logging.common.CustomJsonLayout;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.helpers.NOPAppender;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.ConsoleAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@link ConsoleAppenderFactory} extension which uses a Stackdriver compatible {@link JsonLayout}
 *
 * It outputs a json per line Stackdriver [LogEntry](https://cloud.google.com/logging/docs/view/logs_index)
 *
 */
@JsonTypeName("gke-console")
@Data
@EqualsAndHashCode(callSuper = false)
public class GkeConsoleAppenderFactory<E extends DeferredProcessingAware> extends ConsoleAppenderFactory<E> {
  private boolean stackdriverLogEnabled;

  @Override
  public Appender<E> build(LoggerContext context, String applicationName, LayoutFactory<E> layoutFactory,
      LevelFilterFactory<E> levelFilterFactory, AsyncAppenderFactory<E> asyncAppenderFactory) {
    Appender<E> appender;
    if (!stackdriverLogEnabled) {
      appender = new NOPAppender<>();
    } else {
      appender = new ConsoleAppender<>();
      ConsoleAppender<E> consoleAppender = (ConsoleAppender<E>) appender;
      consoleAppender.setName("gke-console");
      consoleAppender.setContext(context);
      consoleAppender.setTarget(getTarget().get());
      final LayoutWrappingEncoder<E> layoutEncoder = new LayoutWrappingEncoder<>();
      layoutEncoder.setLayout((Layout<E>) new CustomJsonLayout(context));
      consoleAppender.setEncoder(layoutEncoder);
      consoleAppender.addFilter(levelFilterFactory.build(threshold));
      getFilterFactories().forEach(f -> consoleAppender.addFilter(f.build()));
    }
    appender.start();
    return wrapAsync(appender, asyncAppenderFactory);
  }
}
