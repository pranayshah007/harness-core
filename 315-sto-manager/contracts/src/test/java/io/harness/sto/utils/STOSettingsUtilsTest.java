/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.utils;

import static io.harness.rule.OwnerRule.SERGEY;
import static io.harness.sto.utils.STOSettingsUtils.getSTOKey;
import static io.harness.sto.utils.STOSettingsUtils.getSTOPluginEnvVariables;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo;
import io.harness.beans.steps.stepinfo.security.MendStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAdvancedSettings;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.sto.variables.STOYamlGenericConfig;
import io.harness.yaml.sto.variables.STOYamlImageType;
import io.harness.yaml.sto.variables.STOYamlScanMode;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class STOSettingsUtilsTest {
  static final String PRODUCT_NAME = "product_name";

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getBlackDuckEnvVariablesTest() {
    BlackDuckStepInfo emptyStep = new BlackDuckStepInfo();
    STOYamlImage imageData = new STOYamlImage();
    imageData.setType(STOYamlImageType.AWS_ECR);

    emptyStep.setImage(imageData);
    assertContainerScanEnvVariables(emptyStep);
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getMendEnvVariablesTest() {
    MendStepInfo emptyStep = new MendStepInfo();
    STOYamlImage imageData = new STOYamlImage();
    imageData.setType(STOYamlImageType.AWS_ECR);

    emptyStep.setImage(imageData);
    assertContainerScanEnvVariables(emptyStep);
  }

  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void getSTOKeyTest() {
    assertEquals(getSTOKey(PRODUCT_NAME), "SECURITY_PRODUCT_NAME");
  }

  private void assertContainerScanEnvVariables(STOGenericStepInfo emptyStep) {
    STOYamlAdvancedSettings advanced = new STOYamlAdvancedSettings();
    advanced.setIncludeRaw(ParameterField.createValueField(true));
    emptyStep.setAdvanced(advanced);

    Map<String, String> actual = getSTOPluginEnvVariables(emptyStep, "1");
    Map<String, String> expected = new HashMap<>();
    expected.put(getSTOKey("product_config_name"), STOYamlGenericConfig.DEFAULT.getYamlName());
    expected.put(getSTOKey("policy_type"), STOYamlScanMode.ORCHESTRATION.getPluginName());
    expected.put(getSTOKey(PRODUCT_NAME), emptyStep.getProductName());
    expected.put(getSTOKey("include_raw"), "true");
    expected.put(getSTOKey("container_type"), STOYamlImageType.AWS_ECR.getYamlName());
    expected.put(getSTOKey("fail_on_severity"), "0");

    MapDifference<String, String> difference = Maps.difference(actual, expected);
    if (!difference.entriesOnlyOnLeft().isEmpty()) {
      log.error("Diff in actual: {}", difference.entriesOnlyOnLeft());
    }
    if (!difference.entriesOnlyOnRight().isEmpty()) {
      log.error("Diff in expected: {}", difference.entriesOnlyOnRight());
    }
    assertTrue(difference.entriesOnlyOnLeft().isEmpty());
    assertTrue(difference.entriesOnlyOnRight().isEmpty());
  }
}
