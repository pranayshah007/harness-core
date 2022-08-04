/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema.beans;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlSchemaConstantsTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRuntimeInputPattern() {
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>")).isTrue();
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.default(val)")).isTrue();
    // False, because no value is provided inside default.
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.default()")).isFalse();
    // Invalid pattern. missing parenthesis.
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.default")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.allowedValues(val1)"))
        .isTrue();
    // False, because no value is provided inside allowedValues.
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.allowedValues()"))
        .isFalse();
    assertThat(checkPattern(
                   SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.default(val).allowedValues(val1)"))
        .isTrue();
    assertThat(checkPattern(
                   SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.allowedValues(val1).default(val)"))
        .isTrue();

    // Invalid
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+inpufdt>")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "input")).isFalse();
    // All false because matches executionInput pattern.
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.executionInput()"))
        .isFalse();
    assertThat(
        checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.default(val).executionInput()"))
        .isFalse();
    assertThat(
        checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN, "<+input>.executionInput().default(val)"))
        .isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN,
                   "<+input>.allowedValues(val).executionInput()"))
        .isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN,
                   "<+input>.executionInput().allowedValues(val)"))
        .isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExecutionInputPattern() {
    // True, normal runtime input string.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>")).isTrue();
    // True, execution input.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput()")).isTrue();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.default(val)")).isTrue();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.default(val).executionInput()")).isTrue();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput().default(val)")).isTrue();
    // False, executionInput but default does not have any value.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput().default()")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput().allowedValues(val)"))
        .isTrue();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.allowedValues(val).executionInput()"))
        .isTrue();

    // False, executionInput but allowedValues does not have any value.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput().allowedValues()"))
        .isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.default(val).allowedValues(val)"))
        .isTrue();

    // False, Invalid string.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.random(val)")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.random(val).executionInput()")).isFalse();
    // False, does not end with allowed character. So invalid.
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput().s")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>a")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.executionInput()s")).isFalse();
    assertThat(checkPattern(SchemaConstants.RUNTIME_INPUT_PATTERN, "<+input>.default(val).")).isFalse();
  }
  private boolean checkPattern(String regex, String str) {
    Pattern pattern = Pattern.compile(regex);
    return pattern.matcher(str).matches();
  }
}
