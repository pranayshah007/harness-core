/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ExecutionTarget;
import io.harness.steps.shellscript.ExportScope;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.OutputAlias;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Value
@EqualsAndHashCode(callSuper = true)
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.steps.shellscript.v1.ShellScriptStepParameters")
public class ShellScriptStepParameters extends ShellScriptBaseStepInfoV1 implements SpecParameters {
  Map<String, Object> output_vars;
  Map<String, Object> env_vars;
  Set<String> secret_output_vars;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptStepParameters(ShellTypeV1 shell, ShellScriptSourceWrapperV1 source,
      ExecutionTargetV1 executionTarget, ParameterField<Boolean> onDelegate, Map<String, Object> output_vars,
      Map<String, Object> env_vars, ParameterField<List<TaskSelectorYaml>> delegate, Set<String> secret_output_vars,
      ParameterField<Boolean> include_infra_selectors, OutputAliasV1 outputAlias) {
    super(shell, source, executionTarget, onDelegate, delegate, include_infra_selectors, outputAlias);
    this.output_vars = output_vars;
    this.env_vars = env_vars;
    this.secret_output_vars = secret_output_vars;
  }

  @Override
  public List<String> stepInputsKeyExclude() {
    return new LinkedList<>(Arrays.asList("spec.secretOutputVariables"));
  }

  public io.harness.steps.shellscript.ShellScriptStepParameters toShellScriptParametersV0() {
    return io.harness.steps.shellscript.ShellScriptStepParameters.infoBuilder()
        .executionTarget(toExecutionTarget(getExecution_target()))
        .onDelegate(getOn_delegate())
        .outputVariables(getOutput_vars())
        .environmentVariables(getEnv_vars())
        .secretOutputVariables(getSecret_output_vars())
        .shellType(toShellType(getShell()))
        .source(toShellScriptSourceWrapper(getSource()))
        .delegateSelectors(getDelegate())
        .includeInfraSelectors(getInclude_infra_selectors())
        .outputAlias(toOutputAlias(getOutput_alias()))
        .delegateSelectors(getDelegate())
        .includeInfraSelectors(getInclude_infra_selectors())
        .outputAlias(toOutputAlias(getOutput_alias()))
        .build();
  }

  private ExecutionTarget toExecutionTarget(ExecutionTargetV1 executionTargetV1) {
    if (executionTargetV1 == null) {
      return null;
    }
    return ExecutionTarget.builder()
        .host(executionTargetV1.getHost())
        .connectorRef(executionTargetV1.getConnector())
        .workingDirectory(executionTargetV1.getDir())
        .build();
  }

  private ShellType toShellType(ShellTypeV1 shellTypeV1) {
    switch (shellTypeV1) {
      case Bash:
        return ShellType.Bash;
      case PowerShell:
        return ShellType.PowerShell;
      default:
        log.error("Shell type {} not supported", shellTypeV1);
        return null;
    }
  }

  private OutputAlias toOutputAlias(OutputAliasV1 outputAliasV1) {
    if (outputAliasV1 == null) {
      return null;
    }
    return OutputAlias.builder().key(outputAliasV1.getKey()).scope(toExportScope(outputAliasV1.getScope())).build();
  }

  private ExportScope toExportScope(ExportScopeV1 exportScopeV1) {
    if (exportScopeV1 == null) {
      return null;
    }
    switch (exportScopeV1) {
      case PIPELINE:
        return ExportScope.PIPELINE;
      case STAGE:
        return ExportScope.STAGE;
      case STEP_GROUP:
        return ExportScope.STEP_GROUP;
      default:
        log.error("Export scope type {} not supported", exportScopeV1);
        return null;
    }
  }

  private ShellScriptSourceWrapper toShellScriptSourceWrapper(ShellScriptSourceWrapperV1 shellScriptSourceWrapperV1) {
    if (shellScriptSourceWrapperV1 == null) {
      return null;
    }
    return ShellScriptSourceWrapper.builder()
        .spec(toShellScriptBaseSource(shellScriptSourceWrapperV1.getSpec()))
        .type(toShellScriptSourceWrapperType(shellScriptSourceWrapperV1.getType()))
        .build();
  }

  private String toShellScriptSourceWrapperType(String type) {
    if (type.equals(ShellScriptBaseSourceV1.HARNESS)) {
      return ShellScriptBaseSource.HARNESS;
    }
    return ShellScriptBaseSource.INLINE;
  }

  private ShellScriptBaseSource toShellScriptBaseSource(ShellScriptBaseSourceV1 shellScriptBaseSourceV1) {
    if (shellScriptBaseSourceV1 instanceof HarnessFileStoreSourceV1) {
      return HarnessFileStoreSource.builder()
          .file(((HarnessFileStoreSourceV1) shellScriptBaseSourceV1).getFile())
          .build();
    }
    return ShellScriptInlineSource.builder()
        .script(((ShellScriptInlineSourceV1) shellScriptBaseSourceV1).getScript())
        .build();
  }

  @Override
  public String getVersion() {
    return HarnessYamlVersion.V1;
  }
}
