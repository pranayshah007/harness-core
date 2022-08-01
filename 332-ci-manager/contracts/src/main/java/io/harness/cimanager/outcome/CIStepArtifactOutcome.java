/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.outcome;

import static io.harness.beans.steps.outcome.CIOutcomeNames.CI_STEP_ARTIFACT_OUTCOME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias(CI_STEP_ARTIFACT_OUTCOME)
@JsonTypeName(CI_STEP_ARTIFACT_OUTCOME)
@OwnedBy(HarnessTeam.CI)
@RecasterAlias("io.harness.beans.steps.outcome.CIStepArtifactOutcome")
public class CIStepArtifactOutcome implements Outcome {
  StepArtifacts stepArtifacts;
}
