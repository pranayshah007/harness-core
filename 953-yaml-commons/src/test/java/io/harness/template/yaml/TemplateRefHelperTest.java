/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TemplateRefHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckIfYamlHasTemplateRef() {
    assertThat(TemplateRefHelper.hasTemplateRef(getPipelineWithNoRef())).isFalse();
    assertThat(TemplateRefHelper.hasTemplateRef(getPipelineWithStepTemplate())).isTrue();
    assertThat(TemplateRefHelper.hasTemplateRef(getPipelineWithStageTemplate())).isTrue();
  }

  String getPipelineWithNoRef() {
    return "pipeline:\n"
        + "    name: noTemplateRef\n"
        + "    identifier: noTemplateRef\n"
        + "    projectIdentifier: def\n"
        + "    orgIdentifier: default\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: a1\n"
        + "              identifier: a1\n"
        + "              type: Approval\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                name: step name\n"
        + "                                identifier: step_name\n"
        + "                                type: HarnessApproval\n"
        + "                                timeout: 1d\n"
        + "                                spec:\n"
        + "                                    approvalMessage: simple message\n"
        + "                                    includePipelineExecutionHistory: true\n"
        + "                                    approvers:\n"
        + "                                        minimumCount: 1\n"
        + "                                        disallowPipelineExecutor: false\n"
        + "                                        userGroups: <+input>\n"
        + "                                    approverInputs: []\n"
        + "              tags: {}\n";
  }

  String getPipelineWithStepTemplate() {
    return "pipeline:\n"
        + "    name: stepTemplateRef\n"
        + "    identifier: stepTemplateRef\n"
        + "    projectIdentifier: def\n"
        + "    orgIdentifier: default\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: a1\n"
        + "              identifier: a1\n"
        + "              type: Approval\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                name: step name\n"
        + "                                identifier: step_name\n"
        + "                                template:\n"
        + "                                    templateRef: template1\n"
        + "                                    versionLabel: v1\n"
        + "                                    templateInputs:\n"
        + "                                        type: ShellScript\n"
        + "                                        timeout: <+input>\n"
        + "                  serviceDependencies: []\n"
        + "              tags: {}\n";
  }

  String getPipelineWithStageTemplate() {
    return "pipeline:\n"
        + "    name: stageTemplateRef\n"
        + "    identifier: stageTemplateRef\n"
        + "    projectIdentifier: def\n"
        + "    orgIdentifier: default\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              name: stage name\n"
        + "              identifier: stage_name\n"
        + "              template:\n"
        + "                  templateRef: D1\n"
        + "                  versionLabel: Version1\n"
        + "                  templateInputs:\n"
        + "                      type: Deployment\n"
        + "                      spec:\n"
        + "                          serviceConfig:\n"
        + "                              serviceRef: <+input>\n";
  }
}
