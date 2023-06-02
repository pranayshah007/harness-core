/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.pms.merger.helpers.YamlRefreshHelper.refreshNodeFromSourceNode;
import static io.harness.pms.merger.helpers.YamlRefreshHelper.refreshYamlFromSourceYaml;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class YamlRefreshHelperTest extends CategoryTest {
  private JsonNode convertYamlToJsonNode(String yaml) throws IOException {
    return YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
  }

  private String convertToYaml(JsonNode jsonNode) {
    if (jsonNode == null) {
      return "";
    }
    String yaml = YamlUtils.write(jsonNode).replaceFirst("---\n", "");
    // removing last \n from string to simplify test
    return yaml.substring(0, yaml.length() - 1);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshNodeFromSourceNode() throws IOException {
    // all true scenarios
    assertThat(refreshNodeFromSourceNode(null, null)).isNull();
    assertThat(refreshNodeFromSourceNode(null, convertYamlToJsonNode("field: abc"))).isNull();
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: abc");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: abc"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(abc,b,c)"))))
        .isEqualTo("field: abc");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo("field: abc");
    String yamlToValidate = "field:\n"
        + "- a\n"
        + "- b";
    String expectedYaml = "field:\n"
        + "  - a\n"
        + "  - b";
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo(expectedYaml);
    yamlToValidate = "field:\n"
        + "- a\n"
        + "- ab";
    expectedYaml = "field:\n"
        + "  - a\n"
        + "  - ab";
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo(expectedYaml);
    yamlToValidate = "field:\n"
        + "  identifier: id\n"
        + "  name: name";
    expectedYaml = "field:\n"
        + "  identifier: id\n"
        + "  name: name";
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode(yamlToValidate), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo(expectedYaml);
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: a.allowedValues(a,b,c)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"a.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: a.regex(a.*)"), convertYamlToJsonNode("field: <+input>.regex(a.*)"))))
        .isEqualTo("field: a.regex(a.*)");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: yes"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)"))))
        .isEqualTo("field: \"<+input>.allowedValues(yes, no)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: 01"), convertYamlToJsonNode("field: <+input>.allowedValues(01, 2)"))))
        .isEqualTo("field: \"<+input>.allowedValues(01, 2)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(yes, no)"),
                   convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: \"<+input>.allowedValues(yes, no)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: <+input>.regex(a.*)"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: <+input>.regex(a.*)");

    // all false scenarios
    assertThat(convertToYaml(refreshNodeFromSourceNode(null, convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: <+input>");
    assertThat(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>"), null)).isNull();
    assertThat(convertToYaml(
                   refreshNodeFromSourceNode(convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field2: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(
                   refreshNodeFromSourceNode(convertYamlToJsonNode("field: def"), convertYamlToJsonNode("field: abc"))))
        .isEqualTo("");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field1: abc"), convertYamlToJsonNode("field: <+input>"))))
        .isEqualTo("field: <+input>");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: abc"), convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: <+input>.regex(b.*)");
    assertThat(convertToYaml(refreshNodeFromSourceNode(
                   convertYamlToJsonNode("field: <+input>"), convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: <+input>.regex(b.*)");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.regex(a.*)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: <+input>.regex(b.*)");
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c,d)"),
                   convertYamlToJsonNode("field: <+input>.regex(b.*)"))))
        .isEqualTo("field: <+input>.regex(b.*)");
    yamlToValidate = "field:\n"
        + "  - a\n"
        + "  - ab";
    assertThat(convertToYaml(refreshNodeFromSourceNode(convertYamlToJsonNode(yamlToValidate),
                   convertYamlToJsonNode("field: <+input>.allowedValues(a,b,c)"))))
        .isEqualTo("field: \"<+input>.allowedValues(a,b,c)\"");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRefreshNodeFromSourceNodeWithUseFromStage() throws IOException {
    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"<+input>\"\n    serviceInputs: \"<+input>\"\n"),
            convertYamlToJsonNode(
                "type: \"Deployment\"\nspec:\n  service:\n    serviceRef: \"<+input>\"\n    serviceInputs: \"<+input>\"\n"))))
        .isEqualTo("type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode("type: Deployment\nspec:\n  service:\n    serviceRef: prod_service\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo("type: Deployment\nspec:\n  service:\n    serviceRef: prod_service\n    serviceInputs: <+input>");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr\n    serviceRef: two\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo(
            "type: Deployment\nspec:\n  service:\n    serviceRef: two\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode("type: Deployment\nspec:\n  service:\n    useFromStage:\n      stage: s1\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: <+input>\n    serviceInputs: <+input>\n"))))
        .isEqualTo("type: Deployment\nspec:\n  service:\n    useFromStage:\n      stage: s1");

    assertThat(
        convertToYaml(refreshNodeFromSourceNode(
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr\n"),
            convertYamlToJsonNode(
                "type: Deployment\nspec:\n  service:\n    serviceRef: fixedService\n    serviceInputs: <+input>\n"))))
        .isEqualTo(
            "type: Deployment\nspec:\n  service:\n    serviceInputs:\n      serviceDefinition:\n        type: Kubernetes\n        spec:\n          variables:\n            - name: ghcgh\n              type: String\n              value: ewfrvgdbgr");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testDefaultKeyOverriding() throws JsonProcessingException {
    String linkedYaml = "pipeline:\n"
        + "  name: temp-pipe\n"
        + "  identifier: temppipe\n"
        + "  projectIdentifier: V\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s2\n"
        + "        identifier: s2\n"
        + "        template:\n"
        + "          templateRef: dep\n"
        + "          versionLabel: v1\n"
        + "          templateInputs:\n"
        + "            type: Deployment\n"
        + "            spec:\n"
        + "              service:\n"
        + "                serviceInputs:\n"
        + "                  serviceDefinition:\n"
        + "                    type: Kubernetes\n"
        + "                    spec:\n"
        + "                      artifacts:\n"
        + "                        primary:\n"
        + "                          primaryArtifactRef: <+input>\n"
        + "                          sources: <+input>\n"
        + "            variables:\n"
        + "              - name: stgvar123\n"
        + "                type: Number\n"
        + "                default: 1\n"
        + "                value: <+input>.allowedValues(1,2)\n"
        + "    - stage:\n"
        + "        name: s23\n"
        + "        identifier: s23\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo hi\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        name: s3\n"
        + "        identifier: s3\n"
        + "        description: \"\"\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          deploymentType: Ssh\n"
        + "          service:\n"
        + "            serviceRef: AMAZON_S3_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Ssh\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: SSH_INFRA\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - stepGroup:\n"
        + "                  name: Phase\n"
        + "                  identifier: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: Phase Group\n"
        + "                        identifier: phase_group\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              name: Deploy\n"
        + "                              identifier: Deploy\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "            rollbackSteps:\n"
        + "              - stepGroup:\n"
        + "                  name: Phase\n"
        + "                  identifier: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: Phase Group Rollback\n"
        + "                        identifier: phase_group_rollback\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              name: Rollback\n"
        + "                              identifier: Rollback\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        name: approval\n"
        + "        identifier: approval\n"
        + "        description: \"\"\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: appro\n"
        + "                  identifier: appro\n"
        + "                  type: HarnessApproval\n"
        + "                  timeout: 1d\n"
        + "                  spec:\n"
        + "                    approvalMessage: |-\n"
        + "                      Please review the following information\n"
        + "                      and approve the pipeline progression\n"
        + "                    includePipelineExecutionHistory: true\n"
        + "                    approvers:\n"
        + "                      minimumCount: 1\n"
        + "                      disallowPipelineExecutor: false\n"
        + "                      userGroups:\n"
        + "                        - account._account_all_users\n"
        + "                    isAutoRejectEnabled: false\n"
        + "                    approverInputs: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        name: s4\n"
        + "        identifier: s4\n"
        + "        description: \"\"\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          deploymentType: Kubernetes\n"
        + "          service:\n"
        + "            serviceRef: GITHUB_PACKAGES_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Kubernetes\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: name123\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Rollout Deployment\n"
        + "                  identifier: rolloutDeployment\n"
        + "                  type: K8sRollingDeploy\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    skipDryRun: false\n"
        + "                    pruningEnabled: false\n"
        + "            rollbackSteps:\n"
        + "              - step:\n"
        + "                  name: Rollback Rollout Deployment\n"
        + "                  identifier: rollbackRolloutDeployment\n"
        + "                  type: K8sRollingRollback\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    pruningEnabled: false\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        name: ssh\n"
        + "        identifier: ssh\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo <+pipeline.stages.s2.spec.artifacts.primary.version>\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n";

    String sourceYaml = "pipeline:\n"
        + "  name: temp-pipe\n"
        + "  identifier: temppipe\n"
        + "  projectIdentifier: V\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s2\n"
        + "        identifier: s2\n"
        + "        template:\n"
        + "          templateRef: dep\n"
        + "          versionLabel: v1\n"
        + "          templateInputs:\n"
        + "            type: Deployment\n"
        + "            spec:\n"
        + "              service:\n"
        + "                serviceInputs:\n"
        + "                  serviceDefinition:\n"
        + "                    type: Kubernetes\n"
        + "                    spec:\n"
        + "                      artifacts:\n"
        + "                        primary:\n"
        + "                          primaryArtifactRef: <+input>\n"
        + "                          sources: <+input>\n"
        + "            variables:\n"
        + "              - name: stgvar123\n"
        + "                type: Number\n"
        + "                default: 2\n"
        + "                value: <+input>.allowedValues(1,2,3)\n"
        + "    - stage:\n"
        + "        name: s23\n"
        + "        identifier: s23\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo hi\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        name: s3\n"
        + "        identifier: s3\n"
        + "        description: \"\"\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          deploymentType: Ssh\n"
        + "          service:\n"
        + "            serviceRef: AMAZON_S3_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Ssh\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: SSH_INFRA\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - stepGroup:\n"
        + "                  name: Phase\n"
        + "                  identifier: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: Phase Group\n"
        + "                        identifier: phase_group\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              name: Deploy\n"
        + "                              identifier: Deploy\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "            rollbackSteps:\n"
        + "              - stepGroup:\n"
        + "                  name: Phase\n"
        + "                  identifier: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        name: Phase Group Rollback\n"
        + "                        identifier: phase_group_rollback\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              name: Rollback\n"
        + "                              identifier: Rollback\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        name: approval\n"
        + "        identifier: approval\n"
        + "        description: \"\"\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: appro\n"
        + "                  identifier: appro\n"
        + "                  type: HarnessApproval\n"
        + "                  timeout: 1d\n"
        + "                  spec:\n"
        + "                    approvalMessage: |-\n"
        + "                      Please review the following information\n"
        + "                      and approve the pipeline progression\n"
        + "                    includePipelineExecutionHistory: true\n"
        + "                    approvers:\n"
        + "                      minimumCount: 1\n"
        + "                      disallowPipelineExecutor: false\n"
        + "                      userGroups:\n"
        + "                        - account._account_all_users\n"
        + "                    isAutoRejectEnabled: false\n"
        + "                    approverInputs: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        name: s4\n"
        + "        identifier: s4\n"
        + "        description: \"\"\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          deploymentType: Kubernetes\n"
        + "          service:\n"
        + "            serviceRef: GITHUB_PACKAGES_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Kubernetes\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: name123\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Rollout Deployment\n"
        + "                  identifier: rolloutDeployment\n"
        + "                  type: K8sRollingDeploy\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    skipDryRun: false\n"
        + "                    pruningEnabled: false\n"
        + "            rollbackSteps:\n"
        + "              - step:\n"
        + "                  name: Rollback Rollout Deployment\n"
        + "                  identifier: rollbackRolloutDeployment\n"
        + "                  type: K8sRollingRollback\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    pruningEnabled: false\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        name: ssh\n"
        + "        identifier: ssh\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo <+pipeline.stages.s2.spec.artifacts.primary.version>\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n";

    JsonNode refreshedNode = refreshYamlFromSourceYaml(linkedYaml, sourceYaml);

    String expectedYaml = "pipeline:\n"
        + "  identifier: temppipe\n"
        + "  name: temp-pipe\n"
        + "  projectIdentifier: V\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        name: s2\n"
        + "        template:\n"
        + "          templateRef: dep\n"
        + "          versionLabel: v1\n"
        + "          templateInputs:\n"
        + "            type: Deployment\n"
        + "            spec:\n"
        + "              service:\n"
        + "                serviceInputs:\n"
        + "                  serviceDefinition:\n"
        + "                    type: Kubernetes\n"
        + "                    spec:\n"
        + "                      artifacts:\n"
        + "                        primary:\n"
        + "                          primaryArtifactRef: <+input>\n"
        + "                          sources: <+input>\n"
        + "            variables:\n"
        + "              - name: stgvar123\n"
        + "                type: Number\n"
        + "                default: 2\n"
        + "                value: \"<+input>.allowedValues(1,2,3)\"\n"
        + "    - stage:\n"
        + "        identifier: s23\n"
        + "        type: Custom\n"
        + "        name: s23\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: ShellScript_1\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo hi\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "        type: Deployment\n"
        + "        name: s3\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          deploymentType: Ssh\n"
        + "          service:\n"
        + "            serviceRef: AMAZON_S3_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Ssh\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: SSH_INFRA\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - stepGroup:\n"
        + "                  identifier: Phase\n"
        + "                  name: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        identifier: phase_group\n"
        + "                        name: Phase Group\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              identifier: Deploy\n"
        + "                              name: Deploy\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "            rollbackSteps:\n"
        + "              - stepGroup:\n"
        + "                  identifier: Phase\n"
        + "                  name: Phase\n"
        + "                  strategy:\n"
        + "                    repeat:\n"
        + "                      items: <+stage.output.hosts>\n"
        + "                      maxConcurrency: 1\n"
        + "                      partitionSize: 1\n"
        + "                      unit: Count\n"
        + "                  steps:\n"
        + "                    - stepGroup:\n"
        + "                        identifier: phase_group_rollback\n"
        + "                        name: Phase Group Rollback\n"
        + "                        strategy:\n"
        + "                          repeat:\n"
        + "                            items: <+repeat.partition>\n"
        + "                        steps:\n"
        + "                          - step:\n"
        + "                              identifier: Rollback\n"
        + "                              name: Rollback\n"
        + "                              timeout: 10m\n"
        + "                              template:\n"
        + "                                templateRef: account.Default_Install_Jar_Bash\n"
        + "                                templateInputs:\n"
        + "                                  type: Command\n"
        + "                                  spec:\n"
        + "                                    environmentVariables:\n"
        + "                                      - name: DestinationDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>\n"
        + "                                      - name: WorkingDirectory\n"
        + "                                        type: String\n"
        + "                                        value: $HOME/<+service.name>/<+env.name>/tomcat/bin\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        identifier: approval\n"
        + "        type: Approval\n"
        + "        name: approval\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: appro\n"
        + "                  type: HarnessApproval\n"
        + "                  name: appro\n"
        + "                  timeout: 1d\n"
        + "                  spec:\n"
        + "                    approvalMessage: |-\n"
        + "                      Please review the following information\n"
        + "                      and approve the pipeline progression\n"
        + "                    includePipelineExecutionHistory: true\n"
        + "                    approvers:\n"
        + "                      minimumCount: 1\n"
        + "                      disallowPipelineExecutor: false\n"
        + "                      userGroups:\n"
        + "                        - account._account_all_users\n"
        + "                    isAutoRejectEnabled: false\n"
        + "                    approverInputs: []\n"
        + "        tags: {}\n"
        + "    - stage:\n"
        + "        identifier: s4\n"
        + "        type: Deployment\n"
        + "        name: s4\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          deploymentType: Kubernetes\n"
        + "          service:\n"
        + "            serviceRef: GITHUB_PACKAGES_SERVICE\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Kubernetes\n"
        + "                spec:\n"
        + "                  artifacts:\n"
        + "                    primary:\n"
        + "                      primaryArtifactRef: <+input>\n"
        + "                      sources: <+input>\n"
        + "          environment:\n"
        + "            environmentRef: ENV\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: name123\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: rolloutDeployment\n"
        + "                  type: K8sRollingDeploy\n"
        + "                  name: Rollout Deployment\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    skipDryRun: false\n"
        + "                    pruningEnabled: false\n"
        + "            rollbackSteps:\n"
        + "              - step:\n"
        + "                  identifier: rollbackRolloutDeployment\n"
        + "                  type: K8sRollingRollback\n"
        + "                  name: Rollback Rollout Deployment\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    pruningEnabled: false\n"
        + "        tags: {}\n"
        + "        failureStrategies:\n"
        + "          - onFailure:\n"
        + "              errors:\n"
        + "                - AllErrors\n"
        + "              action:\n"
        + "                type: StageRollback\n"
        + "    - stage:\n"
        + "        identifier: ssh\n"
        + "        type: Custom\n"
        + "        name: ssh\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: ShellScript_1\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  timeout: 10m\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo <+pipeline.stages.s2.spec.artifacts.primary.version>\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "        tags: {}\n";

    String refreshedYaml = YamlPipelineUtils.writeYamlString(refreshedNode);

    assertThat(expectedYaml).isEqualTo(refreshedYaml);
  }
}
