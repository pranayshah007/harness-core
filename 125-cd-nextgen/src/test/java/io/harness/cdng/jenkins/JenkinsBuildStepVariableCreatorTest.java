/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepNode;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepVariableCreator;
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
public class JenkinsBuildStepVariableCreatorTest extends CategoryTest {
  JenkinsBuildStepVariableCreator jenkinsBuildStepVariableCreator = new JenkinsBuildStepVariableCreator();

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(jenkinsBuildStepVariableCreator.getFieldClass()).isEqualTo(JenkinsBuildStepNode.class);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("jenkinsBuildVariableCreatorJson.yaml");
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
    VariableCreationResponse variablesForParentNodeV2 = jenkinsBuildStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), JenkinsBuildStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.description",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.timeout",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.jobParameter.test",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.name",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.jobParameter.name",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.connectorRef",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.jobParameter.booleankey",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.jobName",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.when",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.spec.consoleLogPollFrequency");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("ARCZa12zT7OgE9aeShFb8Q") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.type",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.identifier",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.startTs",
            "pipeline.tags.Jenkins.description.execution.steps.JenkinsBuild.endTs");
  }
}
