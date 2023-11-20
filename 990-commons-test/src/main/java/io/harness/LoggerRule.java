/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.LoggerFactory;

public class LoggerRule implements TestRule {
  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        setup();
        base.evaluate();
        teardown();
      }
    };
  }

  private void setup() {
    logger.addAppender(listAppender);
    listAppender.start();
  }

  private void teardown() {
    listAppender.stop();
    listAppender.list.clear();
    logger.detachAppender(listAppender);
  }

  public List<String> getMessages() {
    return listAppender.list.stream().map(e -> e.getMessage()).collect(Collectors.toList());
  }

  public List<String> getFormattedMessages() {
    return listAppender.list.stream().map(e -> e.getFormattedMessage()).collect(Collectors.toList());
  }
}
