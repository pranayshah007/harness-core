/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnvironmentYamlV2Test extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getChildrenToWalk_0() {
    EnvironmentYamlV2 envYaml = EnvironmentYamlV2.builder().build();
    assertThat(envYaml.getChildrenToWalk().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getChildrenToWalk_1() {
    EnvironmentYamlV2 envYaml =
        EnvironmentYamlV2.builder()
            .infrastructureDefinitions(ParameterField.createValueField(List.of(
                InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("i1")).build())))
            .build();
    assertThat(envYaml.getChildrenToWalk().getVisitableChildList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getChildrenToWalk_2() {
    EnvironmentYamlV2 envYaml =
        EnvironmentYamlV2.builder()
            .infrastructureDefinitions(ParameterField.createExpressionField(true, "<+input>", null, false))
            .build();
    assertThat(envYaml.getChildrenToWalk().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getChildrenToWalk_3() {
    EnvironmentYamlV2 envYaml =
        EnvironmentYamlV2.builder()
            .infrastructureDefinition(ParameterField.createValueField(
                InfraStructureDefinitionYaml.builder().identifier(ParameterField.createValueField("i1")).build()))
            .build();
    assertThat(envYaml.getChildrenToWalk().getVisitableChildList().size()).isEqualTo(1);
  }
}
