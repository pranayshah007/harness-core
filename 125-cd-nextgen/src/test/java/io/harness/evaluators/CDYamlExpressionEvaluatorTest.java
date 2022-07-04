/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.evaluators;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class CDYamlExpressionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testExpressionRender() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("inputset/pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    CDYamlExpressionEvaluator CDYamlExpressionEvaluator = new CDYamlExpressionEvaluator(yamlContent,
        "pipeline.stages.stage1.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.tag",
        new ArrayList<>());

    // Partial expression
    String renderExpression = CDYamlExpressionEvaluator.renderExpression("<+serviceConfig.service.name>");
    assertThat(renderExpression).isEqualTo("service1");

    // Partial expression
    renderExpression =
        CDYamlExpressionEvaluator.renderExpression("<+serviceConfig.serviceDefinition.spec.variables.serviceN1>");
    assertThat(renderExpression).isEqualTo("stringValue3");

    // Partial expression
    renderExpression = CDYamlExpressionEvaluator.renderExpression(
        "<+stages.stage1.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar1.spec.connectorRef>",
        true);
    assertThat(renderExpression).isEqualTo("myDocker2");

    // FQN expression
    renderExpression = CDYamlExpressionEvaluator.renderExpression(
        "<+pipeline.stages.stage1.spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar1.spec.connectorRef>",
        true);
    assertThat(renderExpression).isEqualTo("myDocker2");
  }
}
