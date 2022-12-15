/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Comparator;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGObjectUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMax() {
    assertThat(CVNGObjectUtils.max(1, 2, Comparator.comparingInt(a -> a))).isEqualTo(2);
    assertThat(CVNGObjectUtils.max(null, 2, Comparator.comparingInt(a -> a))).isEqualTo(2);
    assertThat(CVNGObjectUtils.max(1, null, Comparator.comparingInt(a -> a))).isEqualTo(1);
    assertThat(CVNGObjectUtils.max((Integer) null, null, Comparator.comparingInt(a -> a))).isNull();
  }
}
