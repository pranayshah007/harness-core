/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.WithSecretRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.SHELL_SCRIPT)
@SimpleVisitorHelper(helperClass = ShellScriptStepInfoVisitorHelper.class)
@TypeAlias("shellScriptStepInfo")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.cdng.pipeline.stepinfo.ShellScriptStepInfo")
public class ShellScriptStepInfo
    extends ShellScriptBaseStepInfo implements PMSStepInfo, Visitable, WithDelegateSelector, WithSecretRef {
  @VariableExpression(skipVariableExpression = true) List<NGVariable> outputVariables;
  List<NGVariable> environmentVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptStepInfo(ShellType shell, ShellScriptSourceWrapper source, ExecutionTarget executionTarget,
      ParameterField<Boolean> onDelegate, List<NGVariable> outputVariables, List<NGVariable> environmentVariables,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String uuid) {
    super(uuid, shell, source, executionTarget, onDelegate, delegateSelectors);
    this.outputVariables = outputVariables;
    this.environmentVariables = environmentVariables;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.SHELL_SCRIPT_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ShellScriptStepParameters.infoBuilder()
        .executionTarget(getExecutionTarget())
        .onDelegate(getOnDelegate())
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(outputVariables))
        .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
        .secretOutputVariables(NGVariablesUtils.getSetOfSecretVars(outputVariables))
        .shellType(getShell())
        .source(getSource())
        .delegateSelectors(getDelegateSelectors())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<String>> extractSecretRefs() {
    Map<String, ParameterField<String>> secretRefMap = new HashMap<>();
    if (executionTarget != null && executionTarget.getConnectorRef() != null) {
      secretRefMap.put("executionTarget.connectorRef", executionTarget.getConnectorRef());
    }

    return secretRefMap;
  }
}
