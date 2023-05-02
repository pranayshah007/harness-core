/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.MOUNIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ApprovalStepVariableCreatorTest extends CategoryTest {
  ApprovalStepVariableCreator harnessApprovalStepVariableCreator = new ApprovalStepVariableCreator();

  String approvalYaml1 = "pipeline:\n"
      + "  step:\n"
      + "    name: Approval\n"
      + "    identifier: approval\n"
      + "    type: HarnessApproval\n"
      + "    timeout: 1d\n"
      + "    spec:\n"
      + "      approvalMessage: |-\n"
      + "        Please review the following information\n"
      + "      approvers:\n"
      + "        minimumCount: 1\n"
      + "        disallowPipelineExecutor: false\n"
      + "        userGroups:\n"
      + "          - UG1\n"
      + "      approverInputs:\n"
      + "        - name: a1\n"
      + "          defaultValue: b2\n"
      + "          __uuid: uuid1\n"
      + "        - name: a2\n"
      + "          defaultValue: b1\n"
      + "          __uuid: uuid2";

  String approvalYaml2 = "pipeline:\n"
      + "  step:\n"
      + "    name: Approval\n"
      + "    identifier: approval\n"
      + "    type: HarnessApproval\n"
      + "    timeout: 1d\n"
      + "    spec:\n"
      + "      approvalMessage: |-\n"
      + "        Please review the following information\n"
      + "      approvers:\n"
      + "        minimumCount: 1\n"
      + "        disallowPipelineExecutor: false\n"
      + "        userGroups:\n"
      + "          - UG1";

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes =
        new io.harness.steps.approval.ApprovalStepVariableCreator().getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains("HarnessApproval");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testAddVariablesInComplexObject() throws IOException {
    io.harness.steps.approval.ApprovalStepVariableCreator approvalStepVariableCreator =
        new ApprovalStepVariableCreator();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    Map<String, YamlOutputProperties> yamlOutputPropertiesMap = new HashMap<>();
    YamlNode step1 = getStepNode(approvalYaml1);
    YamlNode step2 = getStepNode(approvalYaml2);
    approvalStepVariableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step1);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(3);
    assertThat(yamlPropertiesMap.values().stream().map(YamlProperties::getFqn))
        .containsExactlyInAnyOrder(
            "pipeline.spec.approvalMessage", "pipeline.output.approverInputs.a1", "pipeline.output.approverInputs.a2");
    yamlPropertiesMap.clear();
    yamlOutputPropertiesMap.clear();
    approvalStepVariableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step2);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(1);
    assertThat(yamlPropertiesMap.values().stream().map(YamlProperties::getFqn))
        .containsExactlyInAnyOrder("pipeline.spec.approvalMessage");
  }

  private YamlNode getStepNode(String yaml) throws IOException {
    return YamlUtils.readTree(yaml)
        .getNode()
        .getField("pipeline")
        .getNode()
        .getField("step")
        .getNode()
        .getField("spec")
        .getNode();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(harnessApprovalStepVariableCreator.getFieldClass()).isEqualTo(HarnessApprovalStepNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("approvalStepsVariableCreatorUuidJson.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node for harness approval step
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
    VariableCreationResponse variablesForParentNodeV2 =
        harnessApprovalStepVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(stepField).build(),
            YamlUtils.read(stepField.getNode().toString(), HarnessApprovalStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.harness_approval.timeout",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.spec.approvalMessage",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.description",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.spec.includePipelineExecutionHistory",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.name",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.when",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.spec.isAutoRejectEnabled");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("n05uwEJVQ3aMH_lGYWz3QA") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.harness_approval.identifier",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.type",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.startTs",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.endTs");

    // yaml output properties
    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get("n05uwEJVQ3aMH_lGYWz3QA") // pipeline uuid
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.harness_approval.output.approverInputs.var1",
            "pipeline.stages.stage1.spec.execution.steps.harness_approval.output.approverInputs.var2");
  }
}
