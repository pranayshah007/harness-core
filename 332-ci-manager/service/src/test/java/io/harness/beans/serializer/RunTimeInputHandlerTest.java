/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CiBeansTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(CI)
public class RunTimeInputHandlerTest extends CiBeansTestBase {
  @Mock private ParameterField parameterField;
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIntegerRunTimeInput() throws IOException {
    assertThat(RunTimeInputHandler.resolveIntegerParameter(
                   ParameterField.<Integer>builder().value(Integer.valueOf(10)).build(), 1))
        .isEqualTo(10);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIntegerRunTimeInvalidInput() throws IOException {
    doReturn("abc").when(parameterField).fetchFinalValue();
    doReturn("abc").when(parameterField).getValue();

    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(() -> RunTimeInputHandler.resolveIntegerParameter(parameterField, 1));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testStringRunTimeMandatoryInput() {
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(() -> RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", null, true));
    doReturn("null").when(parameterField).fetchFinalValue();
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(() -> RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", null, true));

    doReturn(null).when(parameterField).fetchFinalValue();

    when(parameterField.isExpression()).thenReturn(false);
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(
            () -> RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, true));

    when(parameterField.isExpression()).thenReturn(true);
    when(parameterField.getExpressionValue()).thenReturn("<+input>");
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(
            () -> RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, true));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testStringRunTimeNonMandatoryInput() {
    doReturn(null).when(parameterField).fetchFinalValue();
    when(parameterField.isExpression()).thenReturn(false);
    assertThat(RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, false))
        .isNull();

    doReturn("abc").when(parameterField).fetchFinalValue();
    assertThat(RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, false))
        .isEqualTo("abc");

    when(parameterField.isExpression()).thenReturn(true);
    when(parameterField.getExpressionValue()).thenReturn("abc");
    assertThat(RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, false))
        .isEqualTo("abc");

    when(parameterField.getExpressionValue()).thenReturn("<+input>");
    assertThat(RunTimeInputHandler.resolveStringParameterV2("name", "type", "identifier", parameterField, false))
        .isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResolveMapV2RuntimeInput() {
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(() -> RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier", null, true, false));
    assertThat(RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier", null, false, false)).isEmpty();

    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(()
                        -> RunTimeInputHandler.resolveMapParameterV2(
                            "name", "type", "identifier", ParameterField.ofNull(), true, false));
    assertThat(
        RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier", ParameterField.ofNull(), false, false))
        .isEmpty();
    assertThatExceptionOfType(CIStageExecutionUserException.class)
        .isThrownBy(()
                        -> RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier",
                            ParameterField.createExpressionField(true, "expression", null, true), true, false));
    assertThat(RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier",
                   ParameterField.createExpressionField(true, "expression", null, true), false, false))
        .isEmpty();

    ParameterField<Map<String, ParameterField<String>>> actual = ParameterField.createValueField(
        Map.of("key1", ParameterField.createValueField("val1"), "key2", ParameterField.createValueField("val2")));

    Map<String, String> expected = Map.of("key1", "val1", "key2", "val2");
    assertThat(RunTimeInputHandler.resolveMapParameterV2("name", "type", "identifier", actual, false, false))
        .isEqualTo(expected);
  }
}
