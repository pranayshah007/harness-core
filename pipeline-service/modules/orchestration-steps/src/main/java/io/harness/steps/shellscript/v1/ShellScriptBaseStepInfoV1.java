/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShellScriptBaseStepInfoV1 {
  ShellTypeV1 shell;
  ShellScriptSourceWrapperV1 source;
  ExecutionTargetV1 execution_target;

  ParameterField<Boolean> on_delegate;

  ParameterField<List<TaskSelectorYaml>> delegate;

  ParameterField<Boolean> include_infra_selectors;
  OutputAliasV1 output_alias;
}
