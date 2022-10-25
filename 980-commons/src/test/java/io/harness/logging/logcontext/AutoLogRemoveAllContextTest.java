/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.logcontext;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

public class AutoLogRemoveAllContextTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRemove() {
    try (AutoLogContext add1 = new AutoLogContext("foo", "value", OVERRIDE_ERROR)) {
      assertThat(MDC.get("foo")).isEqualTo("value");
      try (AutoLogRemoveAllContext remove = new AutoLogRemoveAllContext()) {
        assertThat(MDC.get("foo")).isNull();
        try (AutoLogContext add2 = new AutoLogContext("bar", "value", OVERRIDE_ERROR)) {
          assertThat(MDC.get("bar")).isEqualTo("value");
        }
      }
      assertThat(MDC.get("foo")).isEqualTo("value");
      assertThat(MDC.get("bar")).isNull();
    }
  }
}
