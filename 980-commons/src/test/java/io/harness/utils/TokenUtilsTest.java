/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class TokenUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetDecodedString() {
    String base64EncodedHexadecimalString = "OThkOGZlZGIwNGM1NzRlNjg5MzkxNjRiZjQ2M2U3M2Q=";
    String hexadecimalString = "98d8fedb04c574e68939164bf463e73d";
    assertThat(TokenUtils.getDecodedTokenString(base64EncodedHexadecimalString)).isEqualTo(hexadecimalString);
    assertThat(TokenUtils.getDecodedTokenString(hexadecimalString)).isEqualTo(hexadecimalString);
  }
}
