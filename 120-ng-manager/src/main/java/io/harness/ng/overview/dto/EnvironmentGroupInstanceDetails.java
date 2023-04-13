/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import io.harness.ng.core.environment.beans.EnvironmentType;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class EnvironmentGroupInstanceDetails {
  @NotNull List<EnvironmentGroupInstanceDetail> environmentGroupInstanceDetails;

  @Value
  @Builder
  public static class EnvironmentGroupInstanceDetail {
    @NotNull String id;
    String name;
    List<EnvironmentType> environmentTypes;
    @NotNull Boolean isEnvGroup;
    @NotNull Boolean isDrift;
    @Min(1) Integer count;
    List<ArtifactDeploymentDetail> artifactDeploymentDetails;
    Boolean isRollback;
    Boolean isRevert;
  }
}
