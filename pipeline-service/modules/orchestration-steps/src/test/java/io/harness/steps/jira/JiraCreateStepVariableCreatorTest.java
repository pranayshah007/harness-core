/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira;

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
import io.harness.steps.jira.create.JiraCreateStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class JiraCreateStepVariableCreatorTest extends CategoryTest {
  JiraStepVariableCreator jiraStepVariableCreator = new JiraStepVariableCreator();

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(jiraStepVariableCreator.getFieldClass()).isEqualTo(JiraCreateStepNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("jiraVariableCreatorUuidJson.yaml");
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
                              .get(1)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = jiraStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), JiraCreateStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_create.name",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.description",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.timeout",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.fields.Priority",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.fields.Summary",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.fields.Description",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.issueType",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.connectorRef",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.spec.projectKey",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("ARCZa12zT7OgE9aeShFb8Q") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_create.identifier",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.type",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.startTs",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.endTs",
            "pipeline.stages.stage1.spec.execution.steps.jira_create.status");
  }
}
