/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ParameterFieldHelperTest extends CategoryTest {
  ParameterField<String> nullField = ParameterField.createValueField(null);
  ParameterField<String> emptyField = ParameterField.createValueField("");
  ParameterField<String> stringField = ParameterField.createValueField("value");
  ParameterField<Integer> intField = ParameterField.createValueField(23);
  ParameterField<Boolean> booleanField = ParameterField.createValueField(false);
  ParameterField<List<Boolean>> booleanListField = ParameterField.createValueField(Collections.singletonList(false));

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetParameterFieldValue() {
    assertThat((String) ParameterFieldHelper.getParameterFieldValue(null)).isNull();
    String nullFieldValue = ParameterFieldHelper.getParameterFieldValue(nullField);
    assertThat(nullFieldValue).isNull();
    String emptyFieldValue = ParameterFieldHelper.getParameterFieldValue(emptyField);
    assertThat(emptyFieldValue).isEmpty();
    String stringFieldValue = ParameterFieldHelper.getParameterFieldValue(stringField);
    assertThat(stringFieldValue).isEqualTo("value");
    Integer intFieldValue = ParameterFieldHelper.getParameterFieldValue(intField);
    assertThat(intFieldValue).isEqualTo(23);
    Boolean booleanFieldValue = ParameterFieldHelper.getParameterFieldValue(booleanField);
    assertThat(booleanFieldValue).isFalse();
    List<Boolean> booleanListFieldValue = ParameterFieldHelper.getParameterFieldValue(booleanListField);
    assertThat(booleanListFieldValue).hasSize(1);
    assertThat(booleanListFieldValue.get(0)).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetBooleanParameterFieldValue() {
    assertThat(ParameterFieldHelper.getBooleanParameterFieldValue(nullField)).isFalse();

    assertThatThrownBy(() -> ParameterFieldHelper.getBooleanParameterFieldValue(stringField))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected 'true' or 'false' value, got value");

    ParameterField<String> trueStringField = ParameterField.createValueField("true");
    ParameterField<String> falseStringField = ParameterField.createValueField("false");

    assertThat(ParameterFieldHelper.getBooleanParameterFieldValue(falseStringField)).isFalse();
    assertThat(ParameterFieldHelper.getBooleanParameterFieldValue(trueStringField)).isTrue();

    assertThat(ParameterFieldHelper.getBooleanParameterFieldValue(booleanField)).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetParameterFieldValueHandleValueNull() {
    assertThat(ParameterFieldHelper.getParameterFieldValueHandleValueNull(null)).isNull();
    assertThat(ParameterFieldHelper.getParameterFieldValueHandleValueNull(nullField)).isEqualTo("");
    assertThat(ParameterFieldHelper.getParameterFieldValueHandleValueNull(stringField)).isEqualTo("value");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetParameterFieldListValue() {
    ParameterField<List<String>> listWithValue = ParameterField.createValueField(Lists.newArrayList("stringValue"));
    assertThat(ParameterFieldHelper.getParameterFieldListValue(listWithValue, true)).contains("stringValue");

    ParameterField<List<String>> emptyList = ParameterField.createValueField(Collections.emptyList());
    assertThat(ParameterFieldHelper.getParameterFieldListValue(emptyList, true)).isEmpty();

    assertThat(ParameterFieldHelper.getParameterFieldListValue(null, true)).isEmpty();

    ParameterField<List<String>> listWithExpression =
        ParameterField.createValueField(Lists.newArrayList("<+expression.value>"));
    assertThat(ParameterFieldHelper.getParameterFieldListValue(listWithExpression, true))
        .contains("<+expression.value>");

    assertThat(ParameterFieldHelper.getParameterFieldListValue(listWithExpression, false)).isEmpty();

    ParameterField<List<String>> listWithInputs = ParameterField.createValueField(Lists.newArrayList("<+input>"));
    assertThat(ParameterFieldHelper.getParameterFieldListValue(listWithInputs, true)).contains("<+input>");

    assertThat(ParameterFieldHelper.getParameterFieldListValue(listWithInputs, false)).isEmpty();
  }
}
