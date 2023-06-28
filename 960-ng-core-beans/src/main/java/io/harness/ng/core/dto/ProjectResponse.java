/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ProjectConstants;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@Schema(description = "This has Project details along with its metadata as defined in Harness .")
public class ProjectResponse {
  @NotNull private ProjectDTO project;
  @Schema(description = ProjectConstants.CREATED_AT) private Long createdAt;
  @Schema(description = ProjectConstants.LAST_MODIFIED_AT) private Long lastModifiedAt;
  @NotNull Boolean isFavorite;

  @Builder
  public ProjectResponse(ProjectDTO project, Long createdAt, Long lastModifiedAt, Boolean isFavorite) {
    this.project = project;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
    this.isFavorite = isFavorite;
  }
}
