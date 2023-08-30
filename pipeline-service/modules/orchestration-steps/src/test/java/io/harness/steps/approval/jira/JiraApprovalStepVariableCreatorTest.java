/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.jira;

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
import io.harness.steps.approval.step.jira.JiraApprovalStepNode;
import io.harness.steps.approval.step.jira.JiraApprovalStepVariableCreator;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class JiraApprovalStepVariableCreatorTest extends CategoryTest {
  JiraApprovalStepVariableCreator jiraApprovalStepVariableCreator = new JiraApprovalStepVariableCreator();

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(jiraApprovalStepVariableCreator.getFieldClass()).isEqualTo(JiraApprovalStepNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("approvalStepsVariableCreatorUuidJson.yaml");
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
                              .get(2)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = jiraApprovalStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), JiraApprovalStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_approval.name",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.description",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.timeout",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.retryInterval",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.issueKey",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.issueType",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.projectKey",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.connectorRef",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("AXE2wdxvRPO0zRfCaQByAQ") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_approval.identifier",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.type",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.startTs",
            "pipeline.stages.stage1.spec.execution.steps.jira_approval.endTs");

    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get("AXE2wdxvRPO0zRfCaQByAQ") // pipeline uuid
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());

    // no outcome for jira approval
    assertThat(fqnOutputPropertiesList).hasSize(0);
  }
}
