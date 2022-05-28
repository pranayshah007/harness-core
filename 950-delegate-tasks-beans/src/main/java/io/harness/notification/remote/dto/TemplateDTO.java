/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.dto;

import io.harness.notification.Team;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "Template", description = "Details of the Templates configured in Harness.")
public class TemplateDTO {
  @Schema(description = "Identifier of the Template.") private String identifier;
  @Schema(description = "Team associated with the notification.") private Team team;
  @Schema(description = "Time of creation of template.") private long createdAt;
  @Schema(description = "Last modified time of the template.") private long lastModifiedAt;
  @Schema(description = "file") private byte[] file;
}
