/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.template.yaml.TemplateRefHelper;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class NGTemplateConstants {
  public static final String TEMPLATE = TemplateRefHelper.TEMPLATE;
  public static final String CUSTOM_DEPLOYMENT_TEMPLATE = TemplateRefHelper.CUSTOM_DEPLOYMENT_TEMPLATE;
  public static final String TEMPLATE_REF = TemplateRefHelper.TEMPLATE_REF;
  public static final String TEMPLATE_VERSION_LABEL = "versionLabel";
  public static final String TEMPLATE_INPUTS = "templateInputs";
  public static final String DUMMY_NODE = "dummy";
  public static final String SPEC = "spec";
  public static final String IDENTIFIER = "identifier";
  public static final String NAME = "name";
  public static final String STABLE_VERSION = "__STABLE__";
  public static final String TYPE = "type";
  public static final String STAGE_TYPE = "stageType";
  public static final String STAGES = "stages";
  public static final String GIT_BRANCH = "gitBranch";
  public static final String API_SAMPLE_TEMPLATE_YAML = "template:\n"
      + "  name: pipelineTemplate\n"
      + "  identifier: pipelineTemplate\n"
      + "  versionLabel: v1\n"
      + "  type: Pipeline\n"
      + "  projectIdentifier: TemplateDemo\n"
      + "  orgIdentifier: default\n"
      + "  tags: {}\n"
      + "  spec:\n"
      + "    stages:\n"
      + "      - stage:\n"
      + "          name: stage1\n"
      + "          identifier: stage1\n"
      + "          description: \"\"\n"
      + "          type: Deployment\n"
      + "          spec:\n"
      + "            deploymentType: Kubernetes\n"
      + "            service:\n"
      + "              serviceRef: <+input>\n"
      + "              serviceInputs: <+input>\n"
      + "            environment:\n"
      + "              environmentRef: <+input>\n"
      + "              deployToAll: false\n"
      + "              environmentInputs: <+input>\n"
      + "              infrastructureDefinitions: <+input>\n"
      + "            execution:\n"
      + "              steps:\n"
      + "                - step:\n"
      + "                    type: ShellScript\n"
      + "                    name: Shell Script_1\n"
      + "                    identifier: ShellScript_1\n"
      + "                    spec:\n"
      + "                      shell: Bash\n"
      + "                      onDelegate: true\n"
      + "                      source:\n"
      + "                        type: Inline\n"
      + "                        spec:\n"
      + "                          script: <+input>\n"
      + "                      environmentVariables: []\n"
      + "                      outputVariables: []\n"
      + "                    timeout: 10m\n"
      + "              rollbackSteps: []\n"
      + "          tags: {}\n"
      + "          failureStrategies:\n"
      + "            - onFailure:\n"
      + "                errors:\n"
      + "                  - AllErrors\n"
      + "                action:\n"
      + "                  type: StageRollback\n";
}
