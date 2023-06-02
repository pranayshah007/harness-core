/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.govern;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

public class SwitchTest extends CategoryTest implements MockableTestMixin {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void unhandled() throws IllegalAccessException {
    Logger logger = (Logger) LoggerFactory.getLogger(Switch.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    int a = 5;
    Switch.unhandled(a);

    assertThat(listAppender.list).hasSize(1);
    String expectedMessage = String.format("Unhandled switch value %s: %s\n"
            + "{}",
        "java.lang.Integer", 5);
    assertThat(listAppender.list.get(0).getFormattedMessage()).isEqualTo(expectedMessage);
    assertThat(listAppender.list.get(0).getThrowableProxy()).isNotNull();
    assertThat(listAppender.list.get(0).getThrowableProxy().getClassName())
        .isEqualTo(Exception.class.getCanonicalName());
  }
}
