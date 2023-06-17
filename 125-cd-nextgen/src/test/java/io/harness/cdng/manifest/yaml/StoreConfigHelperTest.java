/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StoreConfigHelperTest extends CategoryTest {
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckListOfStringsParameterNullOrInput_withNull() {
    assertTrue(StoreConfigHelper.checkListOfStringsParameterNullOrInput(null));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckListOfStringsParameterNullOrInput_withNullValue() {
    ParameterField<List<String>> parameterField = ParameterField.createValueField(null);
    assertTrue(StoreConfigHelper.checkListOfStringsParameterNullOrInput(parameterField));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckListOfStringsParameterNullOrInput_withListOfStrings() {
    List<String> list = new ArrayList<>();
    list.add("test1");
    list.add("test2");
    ParameterField<List<String>> parameterField = ParameterField.createValueField(list);
    assertFalse(StoreConfigHelper.checkListOfStringsParameterNullOrInput(parameterField));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringParameterNullOrInput_withNull() {
    assertTrue(StoreConfigHelper.checkStringParameterNullOrInput(null));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringParameterNullOrInput_withNullValue() {
    ParameterField<String> parameterField = ParameterField.createValueField(null);
    assertTrue(StoreConfigHelper.checkStringParameterNullOrInput(parameterField));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringParameterNullOrInput_withString() {
    ParameterField<String> parameterField = ParameterField.createValueField("test");
    assertFalse(StoreConfigHelper.checkStringParameterNullOrInput(parameterField));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringNullOrInput_withNull() {
    assertTrue(StoreConfigHelper.checkStringNullOrInput(null));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringNullOrInput_withEmptyString() {
    assertTrue(StoreConfigHelper.checkStringNullOrInput(""));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringNullOrInput_withInputSetPattern() {
    assertTrue(StoreConfigHelper.checkStringNullOrInput("<+input>"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckStringNullOrInput_withValidString() {
    assertFalse(StoreConfigHelper.checkStringNullOrInput("test"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateGitStoreType_withNullValues() {
    Set<String> invalidParameters = StoreConfigHelper.validateGitStoreType(ParameterField.createValueField(null),
        ParameterField.createValueField("<+input>"), ParameterField.createValueField(null),
        ParameterField.createValueField("branch"), ParameterField.createValueField(null), FetchType.BRANCH);

    assertTrue(invalidParameters.contains("connectorRef"));
    assertFalse(invalidParameters.contains("branch"));
    assertTrue(invalidParameters.contains("folderPath"));
  }
}
