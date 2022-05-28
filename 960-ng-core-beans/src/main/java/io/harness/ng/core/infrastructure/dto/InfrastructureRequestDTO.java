/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.dto;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ng.core.infrastructure.InfrastructureType;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@Schema(name = "InfrastructureRequest", description = "This is the InfrastructureRequest entity defined in Harness")
public class InfrastructureRequestDTO {
  @EntityIdentifier @Schema(description = "identifier of the infrastructure") String identifier;
  @Schema(description = "organisation identifier of the infrastructure") String orgIdentifier;
  @Schema(description = "project identifier of the infrastructure") String projectIdentifier;
  @Schema(description = "environment reference of the infrastructure") String environmentRef;

  @EntityName @Schema(description = "name of the infrastructure") String name;
  @Schema(description = "description of the infrastructure") String description;
  @Schema(description = "tags associated with the infrastructure") Map<String, String> tags;

  @Schema(description = "type of the infrastructure") InfrastructureType type;

  @Schema(description = "yaml spec of the infrastructure") String yaml;
}
