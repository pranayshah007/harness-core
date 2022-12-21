/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UtilsTest extends CategoryTest {
  interface A {}

  class B implements A {}

  class C implements A {}

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetFirstInstance() {
    B b1 = new B();
    B b2 = new B();
    C c = new C();
    List<A> list = Arrays.asList(b1, b2, c);
    B outB = Utils.getFirstInstance(list, B.class);
    C outC = Utils.getFirstInstance(list, C.class);

    assertThat(outB).isEqualTo(b1);
    assertThat(outB).isNotEqualTo(b2);
    assertThat(outC).isEqualTo(c);
    assertThat(outC).isNotEqualTo(b1);

    list = Collections.singletonList(b1);
    outC = Utils.getFirstInstance(list, C.class);
    assertThat(outC).isEqualTo(null);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void validateIsInstanceOf() {
    assertThat(Utils.isInstanceOf(String.class, "valid")).isTrue();
    assertThat(Utils.isInstanceOf(Number.class, Long.valueOf(1010L))).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void validateIsNotInstanceOf() {
    assertThat(Utils.isNotInstanceOf(Number.class, "valid")).isTrue();
    assertThat(Utils.isNotInstanceOf(String.class, Long.valueOf(1010L))).isTrue();
    assertThat(Utils.isNotInstanceOf(String.class, "valid")).isFalse();
  }
}
