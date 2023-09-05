/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.syncstep.AgentApplicationTargets;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.GITOPS)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@RecasterAlias("io.harness.cdng.gitops.updategitopsapp.UpdateGitOpsAppStepParameters")
public class UpdateGitOpsAppStepParameters
    extends UpdateGitOpsAppBaseStepInfo implements StepParameters, SpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public UpdateGitOpsAppStepParameters(ParameterField<AgentApplicationTargets> application,
      ParameterField<List<String>> helmValueFiles, ParameterField<List<UpdateGitOpsAppHelmParams>> helmParameters) {
    super(application, helmValueFiles, helmParameters);
    this.application = application;
    this.helmValueFiles = helmValueFiles;
    this.helmParameters = helmParameters;
  }
}
