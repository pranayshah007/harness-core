/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("shellScriptProvisionStepParameters")
@RecasterAlias("io.harness.cdng.provision.shellscript.ShellScriptProvisionStepParameters")
public class ShellScriptProvisionStepParameters extends ShellScriptProvisionBaseStepInfo implements SpecParameters {
  Map<String, Object> environmentVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ShellScriptProvisionStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ShellScriptSourceWrapper source, Map<String, Object> environmentVariables) {
    super(source, delegateSelectors);
    this.environmentVariables = environmentVariables;
  }
}
