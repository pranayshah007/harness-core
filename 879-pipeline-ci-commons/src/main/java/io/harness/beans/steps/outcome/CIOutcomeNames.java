/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CI)
public class CIOutcomeNames {
  public static final String CI_STEP_OUTCOME = "ciStepOutcome";
  public static final String CI_STEP_ARTIFACT_OUTCOME = "ciStepArtifact";
  public static final String INTEGRATION_STAGE_OUTCOME = "integrationStageOutcome";
}
