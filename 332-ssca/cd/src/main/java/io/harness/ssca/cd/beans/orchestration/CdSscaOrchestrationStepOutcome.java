/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.beans.orchestration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(HarnessTeam.SSCA)
@TypeAlias("sscaOrchestrationStepOutcome")
@JsonTypeName("sscaOrchestrationStepOutcome")
public class CdSscaOrchestrationStepOutcome implements Outcome, StepDetailsInfo {
  PublishedSbomArtifact sbomArtifact;

  @Override
  public String toViewJson() {
    return RecastOrchestrationUtils.toJson(this);
  }
}
