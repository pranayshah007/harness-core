/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
@Schema(name = "stageRequest", description = "stage details defined in Harness with service-env v1.")
public class StageRequestDto {
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull @Schema(description = "YAML for the stage") String yaml;
  @NotNull @Schema(description = "infra identifier") String infraIdentifier;
}
