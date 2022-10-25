/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.logcontext;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

public class AutoLogContextTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPreserveSameValue() {
    String key = "foo";

    try (AutoLogContext level1 = new AutoLogContext("foo", "value", OVERRIDE_ERROR)) {
      assertThat(MDC.get(key)).isEqualTo("value");
      try (AutoLogContext level2 = new AutoLogContext("foo", "value", OVERRIDE_ERROR)) {
        assertThat(MDC.get(key)).isEqualTo("value");
      }
      assertThat(MDC.get(key)).isEqualTo("value");
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPreserveSameValueOnOverride() {
    String key = "foo";

    try (AutoLogContext level1 = new AutoLogContext("foo", "value", OVERRIDE_ERROR)) {
      assertThat(MDC.get(key)).isEqualTo("value");
      try (AutoLogContext level2 = new AutoLogContext("foo", "bar", OVERRIDE_ERROR)) {
        assertThat(MDC.get(key)).isEqualTo("value");
      }
      assertThat(MDC.get(key)).isEqualTo("value");
    }
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNestDifferentValueOnOverride() {
    String key = "foo";

    try (AutoLogContext level1 = new AutoLogContext("foo", "value", OVERRIDE_ERROR)) {
      assertThat(MDC.get(key)).isEqualTo("value");
      try (AutoLogContext level2 = new AutoLogContext("foo", "bar", OVERRIDE_NESTS)) {
        assertThat(MDC.get(key)).isEqualTo("bar");
      }
      assertThat(MDC.get(key)).isEqualTo("value");
    }
  }
}
