/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineStageVariableCreatorTest extends CategoryTest {
  PipelineStageVariableCreator pipelineStageVariableCreator = new PipelineStageVariableCreator();
  PipelineStageOutputsVariableCreator pipelineStageOutputsVariableCreator = new PipelineStageOutputsVariableCreator();

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(pipelineStageVariableCreator.getFieldClass()).isEqualTo(PipelineStageNode.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineStageNodeVariableCreator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = pipelineStageVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), PipelineStageNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.childPipeline.delegateSelectors", "pipeline.stages.childPipeline.spec.pipeline",
            "pipeline.stages.childPipeline.name", "pipeline.stages.childPipeline.spec.project",
            "pipeline.stages.childPipeline.description", "pipeline.stages.childPipeline.spec.org",
            "pipeline.stages.childPipeline.spec.pipelineInputs", "pipeline.stages.childPipeline.when");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void createVariablesForChildren() throws IOException {
    // Pipeline Node
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n"
        + "  outputs:\n"
        + "   - name: var1\n"
        + "     __uuid: uuid1\n"
        + "     value: value1\n"
        + "   - name: var2\n"
        + "     __uuid: uuid1\n"
        + "     value: value2\n";
    YamlField fullYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    YamlField outputYamlField = fullYamlField.fromYamlPath("spec").fromYamlPath("outputs");
    // yaml input expressions
    VariableCreationResponse variablesForChildrenNodes =
        pipelineStageOutputsVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(outputYamlField).build(), outputYamlField);

    List<String> fqnPropertiesList = variablesForChildrenNodes.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());

    assertThat(fqnPropertiesList).containsOnly("output.var2", "output.var1");
    assertThat(variablesForChildrenNodes.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void createVariablesForChildrenWithoutOutputs() throws IOException {
    // Pipeline Node
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n"
        + "  outputs: []";
    YamlField fullYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    YamlField outputYamlField = fullYamlField.fromYamlPath("spec").fromYamlPath("outputs");
    // yaml input expressions
    VariableCreationResponse variablesForChildrenNodes =
        pipelineStageOutputsVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(outputYamlField).build(), outputYamlField);

    List<String> fqnPropertiesList = variablesForChildrenNodes.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());

    assertThat(fqnPropertiesList).isEmpty();
  }
}
