/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cistatus.service.azurerepo;

import static io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl.BYPASS_POLICY;
import static io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl.BYPASS_REASON;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureRepoServiceImplTest extends CategoryTest {
  AzureRepoServiceImpl azureRepoService = new AzureRepoServiceImpl();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAddBypassParamsWhenParamsNotPassed() {
    JSONObject mergeStrategy = new JSONObject();
    Map<String, Object> apiParams = new HashMap<>();
    azureRepoService.addBypassParams(apiParams, mergeStrategy);
    assertThat(mergeStrategy.has(BYPASS_POLICY)).isEqualTo(false);
    assertThat(mergeStrategy.has(BYPASS_REASON)).isEqualTo(false);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testAddBypassParamsWhenParamsPassed() {
    JSONObject mergeStrategy = new JSONObject();
    Map<String, Object> apiParams = new HashMap<>();
    apiParams.put(BYPASS_POLICY, ParameterField.createValueField("true"));
    apiParams.put(BYPASS_REASON, ParameterField.createValueField("REASON"));
    azureRepoService.addBypassParams(apiParams, mergeStrategy);
    assertThat(mergeStrategy.get(BYPASS_POLICY)).isEqualTo(true);
    assertThat(mergeStrategy.get(BYPASS_REASON)).isEqualTo("REASON");
  }
}
