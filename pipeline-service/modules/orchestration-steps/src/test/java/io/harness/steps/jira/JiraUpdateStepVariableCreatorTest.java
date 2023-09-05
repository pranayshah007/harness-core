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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.jira.beans.JiraField;
import io.harness.steps.jira.update.JiraUpdateSpecParameters;
import io.harness.steps.jira.update.JiraUpdateStepInfo;
import io.harness.steps.jira.update.JiraUpdateStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class JiraUpdateStepVariableCreatorTest extends CategoryTest {
  JiraUpdateStepVariableCreator jiraUpdateStepVariableCreator = new JiraUpdateStepVariableCreator();
  @Mock private NGLogCallback mockNgLogCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(jiraUpdateStepVariableCreator.getFieldClass()).isEqualTo(JiraUpdateStepNode.class);
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
                              .get(0)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = jiraUpdateStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), JiraUpdateStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_update.name",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.description",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.timeout",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.fields.Priority",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.fields.Summary",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.transitionTo.transitionName",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.transitionTo.status",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.connectorRef",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.issueKey",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.issueType",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.spec.projectKey",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("SRwUWDRhQNavkgImk50V2A") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.jira_update.identifier",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.type",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.startTs",
            "pipeline.stages.stage1.spec.execution.steps.jira_update.endTs");
  }

  @Test
  @Owner(developers = OwnerRule.vivekveman)
  @Category(UnitTests.class)
  public void testValidateJiraForUnresolvedFields() {
    List<JiraField> fields = new ArrayList<>();
    doNothing().when(mockNgLogCallback).saveExecutionLog(any(), any());
    fields.add(JiraField.builder().name("name").value(ParameterField.createValueField("value")).build());
    fields.add(JiraField.builder().name("name2").value(ParameterField.createValueField("value2")).build());
    fields.add(JiraField.builder().name("name3").value(ParameterField.createValueField("null")).build());
    JiraUpdateStepInfo jiraUpdateStepInfo = JiraUpdateStepInfo.builder()
                                                .fields(fields)
                                                .connectorRef(ParameterField.createValueField("ConnectorRef"))
                                                .issueType("CHANGE_REQUEST")
                                                .issueKey(ParameterField.createValueField("TicketNumber"))
                                                .build();
    JiraUpdateSpecParameters specParameters = (JiraUpdateSpecParameters) (jiraUpdateStepInfo.getSpecParameters());
    Map<String, String> result =
        JiraStepUtils.processJiraFieldsInParameters(specParameters.getFields(), mockNgLogCallback);
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.containsValue("null")).isFalse();
  }
  @Test
  @Owner(developers = OwnerRule.vivekveman)
  @Category(UnitTests.class)
  public void testValidateJiraForNullFieldValues() {
    List<JiraField> fields = new ArrayList<>();
    doNothing().when(mockNgLogCallback).saveExecutionLog(any(), any());

    fields.add(JiraField.builder().name("name").value(ParameterField.createValueField("value")).build());
    fields.add(JiraField.builder().name("name2").value(ParameterField.createValueField("value2")).build());
    fields.add(JiraField.builder().name("name3").value(null).build());
    JiraUpdateStepInfo jiraUpdateStepInfo = JiraUpdateStepInfo.builder()
                                                .fields(fields)
                                                .connectorRef(ParameterField.createValueField("ConnectorRef"))
                                                .issueType("CHANGE_REQUEST")
                                                .issueKey(ParameterField.createValueField("TicketNumber"))
                                                .build();
    JiraUpdateSpecParameters specParameters = (JiraUpdateSpecParameters) (jiraUpdateStepInfo.getSpecParameters());
    Map<String, String> result =
        JiraStepUtils.processJiraFieldsInParameters(specParameters.getFields(), mockNgLogCallback);
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.containsValue("null")).isFalse();
  }
}
