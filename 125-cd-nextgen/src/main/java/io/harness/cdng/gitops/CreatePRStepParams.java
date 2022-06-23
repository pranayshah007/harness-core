/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellType;

import java.util.List;
import java.util.Map;
import lombok.Builder;

@OwnedBy(HarnessTeam.GITOPS)
public class CreatePRStepParams extends CreatePRBaseStepInfo implements GitOpsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public CreatePRStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, CreatePRStepUpdateConfigScriptWrapper updateConfigScriptWrapper,
      ShellType shellType, ParameterField<Boolean> overrideConfig) {
    super(shellType, overrideConfig, stringMap, updateConfigScriptWrapper, delegateSelectors);
  }
}
