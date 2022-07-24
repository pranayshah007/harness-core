/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGTimeConversionHelperTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testTimeInMilliseconds() {
    String time = "10w2d3h2m5ms";
    long result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6231720005L);

    // testing with spaces
    time = "10w 2d 3h 2m 5ms";
    result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6231720005L);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testTimeInMillisecondsWith0d() {
    String time = "10w0d2h";
    long result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6055200000L);

    // testing with spaces
    time = "10w 0d 2h";
    result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6055200000L);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testTimeInMillisecondsWithOrderMismatch() {
    String time = "2d10w2m5ms3h";
    long result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6231720005L);

    // testing with spaces
    time = "2d 10w 2m 5ms 3h";
    result = io.harness.common.NGTimeConversionHelper.convertTimeStringToMilliseconds(time);
    assertThat(result).isEqualTo(6231720005L);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testTimeInMilliSecondsIncorrectTimeString() {
    String time = "2fdd10w2m5ms3h";
    assertThatThrownBy(() -> NGTimeConversionHelper.convertTimeStringToMilliseconds(time))
        .isInstanceOf(InvalidRequestException.class);
  }
}
