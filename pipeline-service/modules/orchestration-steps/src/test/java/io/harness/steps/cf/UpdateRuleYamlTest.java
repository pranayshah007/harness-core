/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.cf;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.openapi.model.Serve;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.cf.UpdateRuleYaml.UpdateRuleYamlSpec;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class UpdateRuleYamlTest extends OrchestrationStepsTestBase {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetServe() {
    Serve serve = UpdateRuleYamlSpec.builder()
                      .bucketBy(ParameterField.createValueField("bucket"))
                      .variations(List.of(AddRuleYaml.VariationYamlSpec.builder()
                                              .variation(ParameterField.createValueField("var"))
                                              .weight(ParameterField.createValueField(1))
                                              .build()))
                      .build()
                      .getServe();
    assertEquals(serve.getDistribution().getVariations().get(0).getVariation(), "var");
    assertEquals(serve.getDistribution().getBucketBy(), "bucket");
    assertEquals(serve.getDistribution().getVariations().get(0).getWeight(), (Integer) 1);
  }
}
