/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.rule.OwnerRule.SERGEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.SecurityStageNode;
import io.harness.beans.steps.nodes.SecurityNode;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.properties.CIProperties;
import io.harness.yaml.core.properties.NGProperties;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(STO)
public class STOPmsPipelineYamlTest extends CategoryTest {
  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void testPipelineConversion() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("securitypms.yml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField ngPipelineActual = YamlUtils.readTree(pipelineJson);
    assertThat(ngPipelineActual).isNotNull();

    YamlField propertiesField = ngPipelineActual.getNode().getField("pipeline").getNode().getField("properties");
    NGProperties properties =
        YamlPipelineUtils.read(propertiesField.getNode().getCurrJsonNode().toString(), NGProperties.class);
    CIProperties ciProperties = properties.getCi();
    log.info(ciProperties.toString());

    List<YamlNode> stagesNodes =
        ngPipelineActual.getNode().getField("pipeline").getNode().getField("stages").getNode().asArray();
    for (YamlNode stageParentNode : stagesNodes) {
      YamlField stageNodeField = stageParentNode.getField("stage");
      SecurityStageNode securityStageNode =
          YamlPipelineUtils.read(stageNodeField.getNode().getCurrJsonNode().toString(), SecurityStageNode.class);
      assertThat(securityStageNode).isNotNull();
      if (securityStageNode.getSecurityStageConfig().getExecution() != null) {
        for (ExecutionWrapperConfig executionWrapperConfig :
            securityStageNode.getSecurityStageConfig().getExecution().getSteps()) {
          SecurityNode stepElementConfig =
              YamlPipelineUtils.read(executionWrapperConfig.getStep().toString(), SecurityNode.class);
          assertThat(stepElementConfig).isNotNull();
        }
      }
    }
  }
}
