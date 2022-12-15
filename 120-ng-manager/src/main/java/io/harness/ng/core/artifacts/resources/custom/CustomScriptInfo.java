/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.yaml.core.variables.NGVariable;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDC)
@Schema(name = "CustomScriptInfo", description = "This has details of the Custom Script")
public class CustomScriptInfo {
  @NotNull @Schema(description = "Script") String script;
  @Schema(description = "Inputs to the script") List<NGVariable> inputs;
  @Schema(description = "runtimeInputYaml") String runtimeInputYaml;
  @Schema(description = "Delegate Selectors") List<TaskSelectorYaml> delegateSelector;
}
