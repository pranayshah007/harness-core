/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.timeout.Timeout;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class TimeoutUtilsTest extends CategoryTest {
  private static final String TIME = "5h";
  private static final String EXPRESSION = "<+abc>";
  private static final String INPUT_EXPRESSION = "<+input>";

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutInSeconds() {
    long duration = 10;

    long timeoutInSeconds = TimeoutUtils.getTimeoutInSeconds(
        Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build(), 15);

    assertThat(timeoutInSeconds).isEqualTo(TimeUnit.HOURS.toSeconds(duration));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutInSecondsWhenTimeoutNull() {
    long duration = 15;

    long timeoutInSeconds = TimeoutUtils.getTimeoutInSeconds((Timeout) null, duration);

    assertThat(timeoutInSeconds).isEqualTo(duration);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutInSecondsWhenTimeoutParameterFieldNull() {
    long duration = 15;

    long timeoutInSeconds = TimeoutUtils.getTimeoutInSeconds((ParameterField<Timeout>) null, duration);

    assertThat(timeoutInSeconds).isEqualTo(duration);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutParameterFieldStringIsNull() {
    ParameterField<String> expected = ParameterField.createValueField(Timeout.fromString("10h").getTimeoutString());
    ParameterField<String> actual = TimeoutUtils.getTimeoutParameterFieldString(null);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutParameterFieldStringIsExpression() {
    ParameterField<Timeout> timeout =
        ParameterField.createExpressionField(true, Timeout.fromString("10h").getTimeoutString(), null, true);

    ParameterField<String> expected =
        ParameterField.createExpressionField(true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);

    ParameterField<String> actual = TimeoutUtils.getTimeoutParameterFieldString(timeout);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetTimeoutParameterFieldString() {
    ParameterField<Timeout> timeout = ParameterField.createValueField(Timeout.fromString("10h"));
    ParameterField<String> expected = ParameterField.createValueField(timeout.getValue().getTimeoutString());
    ParameterField<String> actual = TimeoutUtils.getTimeoutParameterFieldString(timeout);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValue_DefaultValue() {
    ParameterField<Timeout> timeoutParameterField = TimeoutUtils.getTimeoutWithDefaultValue(null, TIME);
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo(TIME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValue_DefaultValueInputExpression() {
    ParameterField<Timeout> timeoutParameterField = TimeoutUtils.getTimeoutWithDefaultValue(
        ParameterField.createExpressionField(true, INPUT_EXPRESSION, null, true), TIME);
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo(TIME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValue_DefaultValueExpression() {
    ParameterField<Timeout> timeoutParameterField = ParameterField.createExpressionField(true, EXPRESSION, null, true);
    ParameterField<Timeout> result = TimeoutUtils.getTimeoutWithDefaultValue(timeoutParameterField, TIME);
    assertThat(result).isSameAs(timeoutParameterField);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValueParameterField_DefaultValue() {
    ParameterField<String> timeoutParameterField =
        TimeoutUtils.getTimeoutParameterFieldStringWithDefaultValue(null, TIME);
    assertThat(timeoutParameterField.getValue()).isEqualTo(TIME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValueParameterField_DefaultValueInputExpression() {
    ParameterField<String> timeoutParameterField = TimeoutUtils.getTimeoutParameterFieldStringWithDefaultValue(
        ParameterField.createExpressionField(true, INPUT_EXPRESSION, null, true), TIME);
    assertThat(timeoutParameterField.getValue()).isEqualTo(TIME);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetTimeoutWithDefaultValueParameterField_DefaultValueExpression() {
    ParameterField<Timeout> timeoutParameterField = ParameterField.createExpressionField(true, EXPRESSION, null, true);
    ParameterField<String> result =
        TimeoutUtils.getTimeoutParameterFieldStringWithDefaultValue(timeoutParameterField, TIME);
    assertThat(result.getExpressionValue()).isEqualTo(EXPRESSION);
  }
}
