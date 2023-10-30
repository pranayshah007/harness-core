/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitops.models.Application;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.codehaus.commons.nullanalysis.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Value
@Builder
@JsonTypeName("UpdateGitOpsAppOutcome")
@RecasterAlias("io.harness.cdng.gitops.updategitopsapp.UpdateGitOpsAppOutcome")
@OwnedBy(GITOPS)
public class UpdateGitOpsAppOutcome implements Outcome, ExecutionSweepingOutput {
  @NotNull Application application;
}
