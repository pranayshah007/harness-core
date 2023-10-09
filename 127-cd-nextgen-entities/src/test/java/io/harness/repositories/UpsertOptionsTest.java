/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UpsertOptionsTest extends CategoryTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testConstructorWithParameters() {
    UpsertOptions options = new UpsertOptions(true, false);

    assertThat(options.sendOutboxEvent).isTrue();
    assertThat(options.publishSetupUsages).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testWithNoOutbox() {
    UpsertOptions options = UpsertOptions.DEFAULT.withNoOutbox();

    assertThat(options.sendOutboxEvent).isFalse();
    assertThat(options.publishSetupUsages).isTrue();

    options = options.withNoSetupUsage();
    assertThat(options.sendOutboxEvent).isFalse();
    assertThat(options.publishSetupUsages).isFalse();

    // check that DEFAULT options remain unmodified
    assertThat(UpsertOptions.DEFAULT.sendOutboxEvent).isTrue();
    assertThat(UpsertOptions.DEFAULT.publishSetupUsages).isTrue();
  }
}
