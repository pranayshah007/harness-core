/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.HINGER;

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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ShellScriptStepVariableCreatorTest extends CategoryTest {
  ShellScriptStepVariableCreator shellScriptStepVariableCreator = new ShellScriptStepVariableCreator();

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(shellScriptStepVariableCreator.getFieldClass()).isEqualTo(ShellScriptStepNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJsonSteps.yaml");
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
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = shellScriptStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), ShellScriptStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.shellScriptStep.name",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.description",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.timeout",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.onDelegate",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.environmentVariables.e2",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.environmentVariables.e1",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.source.spec.script",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.when",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.includeInfraSelectors");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("xtkQAaoNRkCgtI5mU8KnEQ") // uuid for step node
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.shellScriptStep.identifier",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.type",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.startTs",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.endTs");

    List<String> fqnExtraPropertiesForShellScriptSource =
        variablesForParentNodeV2.getYamlExtraProperties()
            .get("neREpx2mmQ14G7y3pKAQzW") // uuid for shell script source pojo
            .getPropertiesList()
            .stream()
            .map(YamlProperties::getFqn)
            .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesForShellScriptSource)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.source.type");

    List<String> fqnExtraPropertiesForShellEnum =
        variablesForParentNodeV2.getYamlExtraProperties()
            .get("M6HHtApvRa6cscRUnJ5NqA") // uuid for shell script source pojo
            .getPropertiesList()
            .stream()
            .map(YamlProperties::getFqn)
            .collect(Collectors.toList());

    assertThat(fqnExtraPropertiesForShellEnum)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.shell",
            "pipeline.stages.stage1.spec.execution.steps.shellScriptStep.spec.executionTarget");

    // yaml output properties
    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get("xtkQAaoNRkCgtI5mU8KnEQ") // uuid for step node
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.shellScriptStep.output.outputVariables.o1");
  }
}
