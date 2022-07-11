/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineVariableCreatorTest extends CategoryTest {
  PipelineVariableCreator pipelineVariableCreator = new PipelineVariableCreator();

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodesV2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");
    String stage1UUid = "J79wvZyYTFacTmbV-98-HQ";
    String stage2UUid = "Z7hglsxLT2GDzpYs9QMnWQ";

    // Check if children stage node are added to children dependencies
    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        pipelineVariableCreator.createVariablesForChildrenNodesV2(
            VariableCreationContext.builder().currentField(pipelineField).build(),
            YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class));
    assertThat(variablesForChildrenNodesV2.containsKey(stage1UUid)).isTrue();
    assertThat(variablesForChildrenNodesV2.containsKey(stage2UUid)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParentNodesV2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = pipelineVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(pipelineField).build(),
        YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList).containsAll(Arrays.asList("pipeline.description", "pipeline.name"));

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("Isxcs6g9RdalBS6gPTPwWg") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.variables", "pipeline.identifier", "pipeline.sequenceId", "pipeline.executionId",
            "pipeline.startTs", "pipeline.endTs", "pipeline.tags", "pipeline.properties", "pipeline.triggerType",
            "pipeline.triggeredBy.name", "pipeline.triggeredBy.email");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void getSupportedTypes() {
    assertThat(pipelineVariableCreator.getSupportedTypes())
        .containsEntry(YAMLFieldNameConstants.PIPELINE, Collections.singleton("__any__"));
  }
}
