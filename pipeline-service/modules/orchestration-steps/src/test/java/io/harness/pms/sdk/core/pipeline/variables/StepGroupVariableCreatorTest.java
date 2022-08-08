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
import io.harness.plancreator.steps.StepGroupElementConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepGroupVariableCreatorTest extends CategoryTest {
  StepGroupVariableCreator stepGroupVariableCreator = new StepGroupVariableCreator();

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(stepGroupVariableCreator.getFieldClass()).isEqualTo(StepGroupElementConfig.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParentNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJson.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stepGroupField = fullYamlField.getNode()
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
                                   .get(1)
                                   .getField("stepGroup");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = stepGroupVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepGroupField).build(),
        YamlUtils.read(stepGroupField.getNode().toString(), StepGroupElementConfig.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.sg1.name",
            "pipeline.stages.stage1.spec.execution.steps.sg1.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.sg1.when.condition");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("cX5YIAPUQqOlyuSFyKOVGA") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.sg1.identifier",
            "pipeline.stages.stage1.spec.execution.steps.sg1.startTs",
            "pipeline.stages.stage1.spec.execution.steps.sg1.endTs");

    // check for childrenVariableCreator
    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        stepGroupVariableCreator.createVariablesForChildrenNodesV2(
            VariableCreationContext.builder().currentField(stepGroupField).build(), null);

    // step dependency should be present
    String uuidForStepInsideSG = "VgWSDz31S3eZdAtFvTHHoA";
    assertThat(variablesForChildrenNodesV2.containsKey(uuidForStepInsideSG)).isTrue();
  }
}
