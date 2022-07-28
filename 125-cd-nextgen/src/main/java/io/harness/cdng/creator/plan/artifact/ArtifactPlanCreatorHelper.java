/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.cdng.creator.plan.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.steps.ArtifactStep;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.artifact.steps.ArtifactSyncStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class ArtifactPlanCreatorHelper {
  public StepType getStepType(ArtifactStepParameters artifactStepParameters) {
    if (ArtifactSourceType.CUSTOM_ARTIFACT == artifactStepParameters.getType()
        && !isCustomArtifactIsDelegateTask(artifactStepParameters)) {
      return ArtifactSyncStep.STEP_TYPE;
    }

    return ArtifactStep.STEP_TYPE;
  }

  public FacilitatorType getFacilitatorType(ArtifactStepParameters artifactStepParameters) {
    if (ArtifactSourceType.CUSTOM_ARTIFACT == artifactStepParameters.getType()
        && !isCustomArtifactIsDelegateTask(artifactStepParameters)) {
      return FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build();
    }

    return FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.TASK).build();
  }

  public boolean isCustomArtifactIsDelegateTask(ArtifactStepParameters artifactStepParameters) {
    if (((CustomArtifactConfig) artifactStepParameters.getSpec()).getScripts() != null
        && EmptyPredicate.isNotEmpty(
            ((CustomArtifactConfig) artifactStepParameters.getSpec()).getScripts().toString())) {
      return true;
    }
    return false;
  }
}
