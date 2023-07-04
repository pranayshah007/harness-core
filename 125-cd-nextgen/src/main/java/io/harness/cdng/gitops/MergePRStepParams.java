/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import io.harness.annotation.RecasterAlias;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@RecasterAlias("io.harness.cdng.gitops.MergePRStepParams")
public class MergePRStepParams extends MergePRBaseStepInfo implements GitOpsSpecParameters {
  @Getter Map<String, Object> variables;
  @Builder(builderMethodName = "infoBuilder")
  public MergePRStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Boolean> deleteSourceBranch, Map<String, Object> variables) {
    super(delegateSelectors, deleteSourceBranch);
    this.variables = variables;
  }

  @Override
  public List<String> getCommandUnits() {
    return Arrays.asList("Merge PR");
  }
}
