/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ParameterFieldUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getBooleanValue() {
    assertThat(ParameterFieldUtils.getBooleanValue(ParameterField.createValueField(true))).isTrue();
    assertThat(ParameterFieldUtils.getBooleanValue(ParameterField.createValueField("true"))).isTrue();
    assertThat(ParameterFieldUtils.getBooleanValue(ParameterField.createValueField("false"))).isFalse();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> ParameterFieldUtils.getBooleanValue(
                            ParameterField.createExpressionField(true, "<+abc>", null, true)));
  }
}
