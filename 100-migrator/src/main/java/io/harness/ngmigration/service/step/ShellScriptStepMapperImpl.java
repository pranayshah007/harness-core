/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.StepSpecType;

import software.wings.yaml.workflow.StepYaml;

import java.util.Map;

public class ShellScriptStepMapperImpl implements StepMapper {
  @Override
  public String getStepType() {
    return StepSpecTypeConstants.SHELL_SCRIPT;
  }

  @Override
  public StepSpecType getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    // TODO: add mappers for other fields in shell script
    return ShellScriptStepInfo.infoBuilder()
        .onDelegate(ParameterField.createValueField((boolean) properties.get("executeOnDelegate")))
        .shell(ShellType.Bash)
        .source(ShellScriptSourceWrapper.builder()
                    .type("Inline")
                    .spec(ShellScriptInlineSource.builder()
                              .script(ParameterField.createValueField((String) properties.get("scriptString")))
                              .build())
                    .build())
        .build();
  }
}
