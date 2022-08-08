/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGRuntimeInputUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractParameters() {
    String text = "<+input>.default(\"value\")";
    String defaultString = "default";
    assertEquals("\"value\"", NGRuntimeInputUtils.extractParameters(text, defaultString));
    text = "<+input>.default(\"value\").executionInput()";
    assertEquals("\"value\"", NGRuntimeInputUtils.extractParameters(text, defaultString));

    // testing all InputSetValidator patterns with .default construct
    for (InputSetValidatorType validatorType : InputSetValidatorType.values()) {
      text = "<+input>.default(\"value\")." + validatorType.getYamlName() + "(\"random\")";
      assertEquals("\"value\"", NGRuntimeInputUtils.extractParameters(text, defaultString));
      assertEquals("\"random\"", NGRuntimeInputUtils.extractParameters(text, validatorType.getYamlName()));

      text = "<+input>." + validatorType.getYamlName() + "(\"random\").default(\"value\")";
      assertEquals("\"value\"", NGRuntimeInputUtils.extractParameters(text, defaultString));
      assertEquals("\"random\"", NGRuntimeInputUtils.extractParameters(text, validatorType.getYamlName()));
    }

    for (InputSetValidatorType validatorType : InputSetValidatorType.values()) {
      List<String> expressions = new ArrayList<>();
      // Testing all the combination of defualt and executionInput with all inputset validators.
      expressions.add("<+input>.default(\"value\")." + validatorType.getYamlName() + "(\"random\").executionInput()");
      expressions.add("<+input>." + validatorType.getYamlName() + "(\"random\").default(\"value\").executionInput()");
      expressions.add("<+input>.default(\"value\").executionInput()." + validatorType.getYamlName() + "(\"random\")");
      expressions.add("<+input>." + validatorType.getYamlName() + "(\"random\").executionInput().default(\"value\")");
      expressions.add("<+input>.executionInput().default(\"value\")." + validatorType.getYamlName() + "(\"random\")");
      expressions.add("<+input>.executionInput()." + validatorType.getYamlName() + "(\"random\").default(\"value\")");
      for (String expression : expressions) {
        assertEquals("\"value\"", NGRuntimeInputUtils.extractParameters(expression, defaultString));
        assertEquals("\"random\"", NGRuntimeInputUtils.extractParameters(expression, validatorType.getYamlName()));
      }
    }

    // Testing allowedValue with regex inputset validator pattern.
    text = "<+input>." + InputSetValidatorType.ALLOWED_VALUES.getYamlName() + "(\"value\")."
        + InputSetValidatorType.REGEX.getYamlName() + "(\"random\")";
    assertEquals(
        "\"value\"", NGRuntimeInputUtils.extractParameters(text, InputSetValidatorType.ALLOWED_VALUES.getYamlName()));
  }
}
