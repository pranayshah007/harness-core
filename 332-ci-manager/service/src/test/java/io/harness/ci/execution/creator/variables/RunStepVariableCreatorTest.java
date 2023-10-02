/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.RunStepNode;
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
public class RunStepVariableCreatorTest extends CategoryTest {
  @Inject RunStepVariableCreator runStepVariableCreator = new RunStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
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
    RunStepNode runStepNode = YamlUtils.read(stepField.getNode().toString(), RunStepNode.class);
    VariableCreationResponse variablesForParentNodeV2 = runStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), runStepNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.setupdb.spec.execution.steps.f.name",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.connectorRef",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.command",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.shell",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.envVariables",
            "pipeline.stages.setupdb.spec.execution.steps.f.description",
            "pipeline.stages.setupdb.spec.execution.steps.f.timeout",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.image",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.privileged",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.runAsUser",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.reports",
            "pipeline.stages.setupdb.spec.execution.steps.f.spec.imagePullPolicy",
            "pipeline.stages.setupdb.spec.execution.steps.f.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("G5KttixVQK-iMalGjiorvg") // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.setupdb.spec.execution.steps.f.identifier",
            "pipeline.stages.setupdb.spec.execution.steps.f.type",
            "pipeline.stages.setupdb.spec.execution.steps.f.startTs",
            "pipeline.stages.setupdb.spec.execution.steps.f.endTs",
            "pipeline.stages.setupdb.spec.execution.steps.f.status");

    List<String> fqnExtraPropertiesList1 = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get(runStepNode.getRunStepInfo().getUuid()) // step uuid
                                               .getPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList1).containsOnly("pipeline.stages.setupdb.spec.execution.steps.f.spec.resources");

    // yaml extra properties
    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get("G5KttixVQK-iMalGjiorvg") // step uuid
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .containsOnly("pipeline.stages.setupdb.spec.execution.steps.f.output.outputVariables.value",
            "pipeline.stages.setupdb.spec.execution.steps.f.output.outputVariables.name");
  }
}
