/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.rule.OwnerRule.SERGEY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.SecurityNode;
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

@OwnedBy(STO)
public class SecurityStepVariableCreatorTest extends CategoryTest {
  @Inject SecurityStepVariableCreator securityStepVariableCreator = new SecurityStepVariableCreator();
  @Test
  @Owner(developers = SERGEY)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("securityCreatorUuidJsonSteps.yaml");
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
    SecurityNode securityStepNode = YamlUtils.read(stepField.getNode().toString(), SecurityNode.class);
    VariableCreationResponse variablesForParentNodeV2 = securityStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), securityStepNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.security.spec.execution.steps.sto.spec.resources.limits.memory",
            "pipeline.stages.security.spec.execution.steps.sto.spec.imagePullPolicy",
            "pipeline.stages.security.spec.execution.steps.sto.name",
            "pipeline.stages.security.spec.execution.steps.sto.description",
            "pipeline.stages.security.spec.execution.steps.sto.timeout",
            "pipeline.stages.security.spec.execution.steps.sto.spec.privileged",
            "pipeline.stages.security.spec.execution.steps.sto.spec.runAsUser",
            "pipeline.stages.security.spec.execution.steps.sto.spec.resources.limits.cpu",
            "pipeline.stages.security.spec.execution.steps.sto.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(securityStepNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.security.spec.execution.steps.sto.identifier",
            "pipeline.stages.security.spec.execution.steps.sto.type",
            "pipeline.stages.security.spec.execution.steps.sto.startTs",
            "pipeline.stages.security.spec.execution.steps.sto.endTs");
  }
}
