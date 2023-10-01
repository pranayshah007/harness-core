/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.VINICIUS;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class RuntimeInputValuesValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateStaticValuesWithInvalidInputSetValidatorRegex() {
    TextNode templateObject =
        new TextNode("<+input>.regex(eaapps-${helmChart.version.substring(helmChart.version.indexOf('-'))})");
    TextNode inputSetObject = new TextNode("someValue");
    String expressionFqn =
        "pipeline.stages.MyDockerStage.spec.infrastructure.infrastructureDefinition.spec.releaseName";
    assertThatThrownBy(
        () -> RuntimeInputValuesValidator.validateStaticValues(templateObject, inputSetObject, expressionFqn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Invalid pattern eaapps-${helmChart.version.substring(helmChart.version.indexOf('-'))} provided in pipeline.stages.MyDockerStage.spec.infrastructure.infrastructureDefinition.spec.releaseName");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateInputValuesWithInvalidInputSetValidatorRegex() {
    TextNode templateObject =
        new TextNode("<+input>.regex(eaapps-${helmChart.version.substring(helmChart.version.indexOf('-'))})");
    TextNode inputSetObject = new TextNode("someValue");
    assertThatThrownBy(() -> RuntimeInputValuesValidator.validateInputValues(templateObject, inputSetObject))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Invalid pattern eaapps-${helmChart.version.substring(helmChart.version.indexOf('-'))} provided for validation of value someValue");
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInputSetParameterField() throws IOException {
    String inputSetValue = "\"{\\\"name\\\": \\\"abc\\\",\\\"spec\\\": {\\\"a\\\": \\\"b\\\"}}\"";
    String inputSetValue1 = "{\"name\": \"abc\",\"spec\": {\"a\": \"b\"}}"; // mismatch
    String inputSetValue2 = "{\"name\":\"abc\",\"spec\":{\"a\":\"b\"}}"; // mismatch
    String inputSetValue3 = "<+input>.default(" + inputSetValue + ")";
    String inputSetValue4 = "<+input>.default(" + inputSetValue1 + ")";
    String inputSetValue5 = "<+input>.default(" + inputSetValue2 + ")";
    String withHash = "#test";
    String inputWithHash = "<+input>.default(" + withHash + ")";
    ParameterField<String> p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue);
    assertEquals(p.getValue(), inputSetValue);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue1);
    assertEquals(p.getValue(), inputSetValue1);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue2);
    assertEquals(p.getValue(), inputSetValue2);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue3);
    assertEquals(p.getDefaultValue(), inputSetValue);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue4);
    assertEquals(p.getDefaultValue(), inputSetValue1);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputSetValue5);
    assertEquals(p.getDefaultValue(), inputSetValue2);
    p = RuntimeInputValuesValidator.getInputSetParameterField(withHash);
    assertEquals(p.getValue(), withHash);
    p = RuntimeInputValuesValidator.getInputSetParameterField(inputWithHash);
    assertEquals(p.getDefaultValue(), withHash);
  }
}
