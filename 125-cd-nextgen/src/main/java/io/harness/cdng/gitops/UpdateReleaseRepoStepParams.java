/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@RecasterAlias("io.harness.cdng.gitops.UpdateReleaseRepoStepParams")
@OwnedBy(HarnessTeam.GITOPS)
public class UpdateReleaseRepoStepParams extends UpdateReleaseRepoBaseStepInfo implements GitOpsSpecParameters {
  @SkipAutoEvaluation @Getter Map<String, Object> variables;
  @Builder(builderMethodName = "infoBuilder")
  public UpdateReleaseRepoStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Map<String, String>> stringMap, Map<String, Object> variables, ParameterField<String> prTitle) {
    super(stringMap, delegateSelectors, prTitle);
    this.variables = variables;
  }
}
