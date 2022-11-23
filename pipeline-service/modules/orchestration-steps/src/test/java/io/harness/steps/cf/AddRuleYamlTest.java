/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.cf;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.openapi.model.Distribution;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.cf.AddRuleYaml.AddRuleYamlSpec;
import io.harness.steps.cf.AddRuleYaml.DistributionYamlSpec;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AddRuleYamlTest extends OrchestrationStepsTestBase {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetServe() {
    DistributionYamlSpec distributionYamlSpec = DistributionYamlSpec.builder()
                                                    .bucketBy(ParameterField.createValueField("bucket"))
                                                    .variations(new ArrayList<>())
                                                    .build();
    assertNull(AddRuleYamlSpec.builder().build().getServe().getDistribution());
    AddRuleYamlSpec addRuleYamlSpec = AddRuleYamlSpec.builder().distribution(distributionYamlSpec).build();
    assertEquals(addRuleYamlSpec.getServe().getDistribution().getBucketBy(), "bucket");
    assertEquals(addRuleYamlSpec.getServe().getDistribution().getVariations().size(), 0);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetClauses() {
    assertEquals(AddRuleYamlSpec.builder().build().getClauses().size(), 0);
    DistributionYamlSpec distributionYamlSpec =
        DistributionYamlSpec.builder()
            .bucketBy(ParameterField.createValueField("bucket"))
            .variations(new ArrayList<>())
            .clauses(List.of(AddRuleYaml.ClauseYamlSpec.builder()
                                 .attribute(ParameterField.createValueField("attribute"))
                                 .values(ParameterField.createValueField(List.of("value")))
                                 .op(ParameterField.createValueField("op"))
                                 .build()))
            .build();
    assertEquals(
        AddRuleYamlSpec.builder().distribution(distributionYamlSpec).build().getClauses().get(0).getAttribute(),
        "attribute");
    assertEquals(
        AddRuleYamlSpec.builder().distribution(distributionYamlSpec).build().getClauses().get(0).getOp(), "op");
    assertEquals(
        AddRuleYamlSpec.builder().distribution(distributionYamlSpec).build().getClauses().get(0).getValues().get(0),
        "value");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testBuildDistribution() {
    Distribution distribution = DistributionYamlSpec.builder()
                                    .bucketBy(ParameterField.createValueField("bucket"))
                                    .variations(List.of(AddRuleYaml.VariationYamlSpec.builder()
                                                            .variation(ParameterField.createValueField("var"))
                                                            .weight(ParameterField.createValueField(1))
                                                            .build()))
                                    .build()
                                    .build();
    assertEquals(distribution.getBucketBy(), "bucket");
    assertEquals(distribution.getVariations().get(0).getVariation(), "var");
    assertEquals(distribution.getVariations().get(0).getWeight(), (Integer) 1);
  }
}
