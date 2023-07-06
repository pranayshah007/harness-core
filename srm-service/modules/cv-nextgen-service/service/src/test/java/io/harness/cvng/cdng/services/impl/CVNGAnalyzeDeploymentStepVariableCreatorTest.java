/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.core.beans.CVAnalyzeDeploymentStepNode;
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

public class CVNGAnalyzeDeploymentStepVariableCreatorTest {
  CVNGAnalyzeDeploymentStepVariableCreator variableCreator = new CVNGAnalyzeDeploymentStepVariableCreator();

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    final URL testFile =
        CVNGAnalyzeDeploymentStepVariableCreator.class.getResource("pipeline-with-analyze-deployment-step.json");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.injectUuidInYamlField(pipelineJson);

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
                              .get(2)
                              .getField("stepGroup")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = variableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), CVAnalyzeDeploymentStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.name",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.spec.duration",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.description",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.when",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.timeout");

    String stepUuid = stepField.getNode().getUuid();

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(stepUuid)
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.type",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.identifier",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.startTs",
            "pipeline.stages.QA_deployment.spec.execution.steps.analyze.steps.AnalyzeDeployment.endTs");
  }
}
