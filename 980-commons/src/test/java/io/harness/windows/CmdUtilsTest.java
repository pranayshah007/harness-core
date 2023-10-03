/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.windows;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CmdUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testEscapeEnvironmentValue() {
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path")).isEqualTo("path");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path>")).isEqualTo("path^>");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path<")).isEqualTo("path^<");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path^1")).isEqualTo("path^^1");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path^1<")).isEqualTo("path^^1^<");
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testEscapeEnvironmentValueWithPercentage() {
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%path%")).isEqualTo("^%path^%");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%path%me")).isEqualTo("^%path^%me");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%path%%path%")).isEqualTo("^%path^%^%path^%");

    // Invalids
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path%")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("path%%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("%pa%th%")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueSpecialChars("4dGl$KJA%%cC2*XY&amp;rK5Nv2!B6p!wj")).isEqualTo("");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testEscapeEnvironmentValueWithPercentageAndLessThenGreaterThenOccurence() {
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%path%")).isEqualTo("%path%");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%path%me")).isEqualTo("%path%me");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%path%%path%")).isEqualTo("%path%%path%");

    // Invalids
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("path%")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("path%%path")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("%pa%th%")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("4dGl$KJA%%cC2*XY&amp;rK5Nv2!B6p!wj")).isEqualTo("");

    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("<var>")).isEqualTo("<var>");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("<var")).isEqualTo("<var");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("var>")).isEqualTo("var>");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("><var")).isEqualTo("><var");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("var><")).isEqualTo("var><");

    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("var<>")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("<>var")).isEqualTo("");
    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("<>")).isEqualTo("");

    assertThat(CmdUtils.escapeEnvValueIllegalSymbols("http://google.com?var=value&var2=vale^2d|and<s>d"))
        .isEqualTo("http://google.com?var=value&var2=vale^2d|and<s>d");
  }
}
