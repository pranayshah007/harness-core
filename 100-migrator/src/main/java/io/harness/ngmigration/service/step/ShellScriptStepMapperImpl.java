/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.shell.ScriptType;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.shellscript.ExecutionTarget;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.steps.shellscript.ShellScriptStepNode;
import io.harness.steps.shellscript.ShellType;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.sm.State;
import software.wings.sm.states.ShellScriptState;
import software.wings.yaml.workflow.StepYaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ShellScriptStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.SHELL_SCRIPT;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ShellScriptState state = new ShellScriptState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    ShellScriptState state = (ShellScriptState) getState(stepYaml);
    ShellScriptStepNode shellScriptStepNode = new ShellScriptStepNode();
    baseSetup(stepYaml, shellScriptStepNode);

    ExecutionTarget executionTarget = null;

    if (!state.isExecuteOnDelegate()) {
      executionTarget = ExecutionTarget.builder()
                            .host(ParameterField.createValueField(state.getHost()))
                            .connectorRef(ParameterField.createValueField("<+input>"))
                            .workingDirectory(ParameterField.createValueField(state.getCommandPath()))
                            .build();
    }

    List<NGVariable> outputVars = new ArrayList<>();
    if (StringUtils.isNotBlank(state.getOutputVars())) {
      outputVars.addAll(Arrays.stream(state.getSecretOutputVars().split("\\s*,\\s*"))
                            .filter(StringUtils::isNotBlank)
                            .map(str
                                -> StringNGVariable.builder()
                                       .name(str)
                                       .type(NGVariableType.STRING)
                                       .value(ParameterField.createValueField(str))
                                       .build())
                            .collect(Collectors.toList()));
    }
    if (StringUtils.isNotBlank(state.getSecretOutputVars())) {
      outputVars.addAll(Arrays.stream(state.getSecretOutputVars().split("\\s*,\\s*"))
                            .filter(StringUtils::isNotBlank)
                            .map(str
                                -> StringNGVariable.builder()
                                       .name(str)
                                       .type(NGVariableType.SECRET)
                                       .value(ParameterField.createValueField(str))
                                       .build())
                            .collect(Collectors.toList()));
    }

    shellScriptStepNode.setShellScriptStepInfo(
        ShellScriptStepInfo.infoBuilder()
            .onDelegate(ParameterField.createValueField(state.isExecuteOnDelegate()))
            .shell(ScriptType.BASH.equals(state.getScriptType()) ? ShellType.Bash : ShellType.PowerShell)
            .source(ShellScriptSourceWrapper.builder()
                        .type("Inline")
                        .spec(ShellScriptInlineSource.builder()
                                  .script(ParameterField.createValueField(state.getScriptString()))
                                  .build())
                        .build())
            .environmentVariables(new ArrayList<>())
            .outputVariables(outputVars)
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .executionTarget(executionTarget)
            .build());
    return shellScriptStepNode;
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    ShellScriptState state1 = (ShellScriptState) getState(stepYaml1);
    ShellScriptState state2 = (ShellScriptState) getState(stepYaml2);
    if (!state1.getScriptType().equals(state2.getScriptType())) {
      return false;
    }
    if (state1.isExecuteOnDelegate() != state2.isExecuteOnDelegate()) {
      return false;
    }
    if (StringUtils.equals(state1.getScriptString(), state2.getScriptString())) {
      return false;
    }
    // No going to compare output vars. Because more output does not impact execution of step.
    // Customers can compare multi similar outputs & they can combine the output.
    return true;
  }
}
