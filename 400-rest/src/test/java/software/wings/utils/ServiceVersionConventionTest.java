/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.utils.nonkryos.ServiceVersionConvention;

public class ServiceVersionConventionTest extends CategoryTest {
  public static final int LEN = 10;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetServiceName() {
    String rev = "";
    assertThat(ServiceVersionConvention.getServiceName("p", rev)).isEqualTo("p");
    rev = null;
    assertThat(ServiceVersionConvention.getServiceName("p", rev)).isEqualTo("p");
    rev = "12";
    assertThat(ServiceVersionConvention.getServiceName("p", rev)).isEqualTo("p__12");
  }
}
