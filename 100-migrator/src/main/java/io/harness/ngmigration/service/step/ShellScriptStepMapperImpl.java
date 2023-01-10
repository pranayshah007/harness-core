/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
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
import io.harness.steps.template.TemplateStepNode;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.ShellScriptState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ShellScriptStepMapperImpl implements StepMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    ShellScriptState state = (ShellScriptState) getState(graphNode);
    if (StringUtils.isBlank(state.getScriptString())) {
      return Collections.emptySet();
    }
    return MigratorExpressionUtils.extractAll(state.getScriptString());
  }

  @Override
  public List<CgEntityId> getReferencedEntities(GraphNode graphNode) {
    String templateId = graphNode.getTemplateUuid();
    if (StringUtils.isNotBlank(templateId)) {
      return Collections.singletonList(
          CgEntityId.builder().id(templateId).type(NGMigrationEntityType.TEMPLATE).build());
    }
    return Collections.emptyList();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.SHELL_SCRIPT;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    ShellScriptState state = new ShellScriptState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public TemplateStepNode getTemplateSpec(Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    return defaultTemplateSpecMapper(migratedEntities, graphNode);
  }

  @Override
  public AbstractStepNode getSpec(Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    ShellScriptState state = (ShellScriptState) getState(graphNode);
    ShellScriptStepNode shellScriptStepNode = new ShellScriptStepNode();
    baseSetup(graphNode, shellScriptStepNode);

    if (StringUtils.isNotBlank(graphNode.getTemplateUuid())) {
      throw new IllegalStateException("Trying to map template step to actual step");
    }

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
      outputVars.addAll(Arrays.stream(state.getOutputVars().split("\\s*,\\s*"))
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
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
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
