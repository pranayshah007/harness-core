/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RunTestStepVariableCreatorTest extends CategoryTest {
  @Inject RunTestStepVariableCreator runTestStepVariableCreator = new RunTestStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("runTestCreatorUuidJsonSteps.yaml");
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
    RunTestStepNode runStepNode = YamlUtils.read(stepField.getNode().toString(), RunTestStepNode.class);
    VariableCreationResponse variablesForParentNodeV2 = runTestStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), runStepNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.run_test.spec.execution.steps.ti.spec.resources.limits.memory",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.envVariables.secret",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.testAnnotations",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.imagePullPolicy",
            "pipeline.stages.run_test.spec.execution.steps.ti.name",
            "pipeline.stages.run_test.spec.execution.steps.ti.description",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.buildTool",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.runOnlySelectedTests",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.envVariables.foo",
            "pipeline.stages.run_test.spec.execution.steps.ti.timeout",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.args",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.privileged",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.preCommand",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.postCommand",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.language",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.connectorRef",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.reports.spec.paths",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.runAsUser",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.packages",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.shell",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.image",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.resources.limits.cpu",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.namespaces",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.frameworkVersion",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.buildEnvironment",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.testSplitStrategy",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.enableTestSplitting",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.testGlobs",
            "pipeline.stages.run_test.spec.execution.steps.ti.when",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.testRoot",
            "pipeline.stages.run_test.spec.execution.steps.ti.spec.pythonVersion");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(runStepNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.run_test.spec.execution.steps.ti.identifier",
            "pipeline.stages.run_test.spec.execution.steps.ti.type",
            "pipeline.stages.run_test.spec.execution.steps.ti.startTs",
            "pipeline.stages.run_test.spec.execution.steps.ti.endTs");

    // yaml extra properties
    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get(runStepNode.getUuid()) // step uuid
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .containsOnly("pipeline.stages.run_test.spec.execution.steps.ti.output.outputVariables.hello");
  }
}
