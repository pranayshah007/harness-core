/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.beans.CVVerifyStepNode;
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

public class CVNGStepVariableCreatorTest extends CvNextGenTestBase {
  CVNGStepVariableCreator cvngStepVariableCreator = new CVNGStepVariableCreator();

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    final URL testFile = CVNGStepVariableCreator.class.getResource("pipeline-with-verify-step.json");
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
                              .get(0)
                              .getField("stepGroup")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(1)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = cvngStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), CVVerifyStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly(
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.spec.spec.deploymentTag",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.spec.spec.duration",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.timeout",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.description",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.name",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.spec.spec.sensitivity",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.spec.spec.failOnNoAnalysis",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.when",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.spec.spec.baseline");

    String stepUuid = stepField.getNode().getUuid();

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(stepUuid)
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.type",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.identifier",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.startTs",
            "pipeline.stages.QA_deployment.spec.execution.steps.canaryDepoyment.steps.Verify.endTs");
  }
}
