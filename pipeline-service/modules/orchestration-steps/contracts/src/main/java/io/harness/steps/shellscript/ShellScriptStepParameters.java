/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.WinRmShellScriptTaskNG;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("shellScriptStepParameters")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.steps.shellscript.ShellScriptStepParameters")
public class ShellScriptStepParameters extends ShellScriptBaseStepInfo implements SpecParameters {
  Map<String, Object> outputVariables;
  Map<String, Object> environmentVariables;
  Set<String> secretOutputVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptStepParameters(ShellType shellType, ShellScriptSourceWrapper source,
      ExecutionTarget executionTarget, ParameterField<Boolean> onDelegate, Map<String, Object> outputVariables,
      Map<String, Object> environmentVariables, ParameterField<List<TaskSelectorYaml>> delegateSelectors, String uuid,
      Set<String> secretOutputVariables) {
    super(uuid, shellType, source, executionTarget, onDelegate, delegateSelectors);
    this.outputVariables = outputVariables;
    this.environmentVariables = environmentVariables;
    this.secretOutputVariables = secretOutputVariables;
  }

  @Override
  public SpecParameters getViewJsonObject() {
    // omit secretOutputVars since they should not be visible to users
    return ShellScriptStepParameters.infoBuilder()
        .executionTarget(this.executionTarget)
        .onDelegate(this.onDelegate)
        .outputVariables(this.outputVariables)
        .environmentVariables(this.environmentVariables)
        .shellType(this.shell)
        .source(this.source.toBuilder().uuid(null).build())
        .delegateSelectors(this.delegateSelectors)
        .build();
  }

  @NotNull
  public List<String> getAllCommandUnits() {
    if (ShellType.Bash == getShell()) {
      return List.of(ShellScriptTaskNG.COMMAND_UNIT);
    } else if (ShellType.PowerShell == getShell()) {
      return List.of(WinRmShellScriptTaskNG.INIT_UNIT, WinRmShellScriptTaskNG.COMMAND_UNIT);
    }
    return List.of();
  }
}
