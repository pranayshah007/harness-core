/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.yaml;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.app.impl.STOManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YAMLTest extends STOManagerTestBase {
  private String yamlString = "dummyYamlString";

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void getPipelineYAML() {
    YAML yaml = YAML.builder().pipelineYAML(yamlString).build();
    assertThat(yaml.getPipelineYAML()).isEqualTo(yamlString);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testEquals() {
    YAML yaml = YAML.builder().pipelineYAML(yamlString).build();
    YAML otherYaml = YAML.builder().pipelineYAML(yamlString).build();
    assertThat(yaml.equals(otherYaml)).isTrue();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void testHashCode() {
    YAML yaml = YAML.builder().pipelineYAML(yamlString).build();
    YAML otherYaml = YAML.builder().pipelineYAML(yamlString).build();
    assertThat(yaml.hashCode()).isEqualTo(otherYaml.hashCode());
  }
}
