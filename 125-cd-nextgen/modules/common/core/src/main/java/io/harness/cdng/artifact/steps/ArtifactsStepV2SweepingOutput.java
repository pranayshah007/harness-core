/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("ArtifactsStepV2SweepingOutput")
@JsonTypeName("artifactsStepV2SweepingOutput")
@RecasterAlias("io.harness.cdng.artifact.steps.ArtifactsStepV2SweepingOutput")
public class ArtifactsStepV2SweepingOutput implements ExecutionSweepingOutput {
  String primaryArtifactTaskId;
  @Builder.Default Map<String, ArtifactConfig> artifactConfigMap = new HashMap<>();
  @Builder.Default List<ArtifactConfig> artifactConfigMapForNonDelegateTaskTypes = new ArrayList<>();

  String primaryArtifactRefValue;
}
