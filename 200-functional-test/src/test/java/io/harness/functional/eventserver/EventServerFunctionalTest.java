/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.eventserver;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.category.element.FunctionalTests;
import io.harness.rule.Owner;
import io.harness.testframework.framework.EventServerExecutor;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EventServerFunctionalTest extends CategoryTest {
  private static EventServerExecutor eventServerExecutor = new EventServerExecutor();

  @Test
  @Owner(developers = AVMOHAN)
  @Category(FunctionalTests.class)
  @Ignore(value = "TODO: Enable once bazel path issue is sorted out")
  public void shouldEnsureEventServer() throws Exception {
    assertThatCode(() -> {
      eventServerExecutor.ensureEventServer(EventServerFunctionalTest.class);
    }).doesNotThrowAnyException();
  }
}
