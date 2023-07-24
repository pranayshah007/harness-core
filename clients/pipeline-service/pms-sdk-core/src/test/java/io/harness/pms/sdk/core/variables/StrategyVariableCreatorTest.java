/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class StrategyVariableCreatorTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCreateVariablesForFieldV2() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    YamlField strategyField =
        YamlUtils.readTree(pipelineYamlWithUuid).fromYamlPath("pipeline/stages/[0]/stage/strategy");
    StrategyConfig strategyFieldModified = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);
    StrategyVariableCreator variableCreator = new StrategyVariableCreator();
    VariableCreationResponse variableCreationResponse =
        variableCreator.createVariablesForFieldV2(VariableCreationContext.builder().build(), strategyFieldModified);
    assertThat(variableCreationResponse.getYamlProperties().size()).isEqualTo(5);
    Set<String> variables = variableCreationResponse.getYamlProperties()
                                .values()
                                .stream()
                                .map(YamlProperties::getFqn)
                                .collect(Collectors.toSet());
    assertThat(variables).isEqualTo(
        Set.of("strategy.iteration", "strategy.iterations", "matrix.a", "matrix.b", "matrix.c"));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() throws IOException {
    StrategyVariableCreator variableCreator = new StrategyVariableCreator();
    assertThat(variableCreator.getSupportedTypes().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFieldClass() throws IOException {
    StrategyVariableCreator variableCreator = new StrategyVariableCreator();
    assertThat(variableCreator.getFieldClass()).isEqualTo(StrategyConfig.class);
  }
}
