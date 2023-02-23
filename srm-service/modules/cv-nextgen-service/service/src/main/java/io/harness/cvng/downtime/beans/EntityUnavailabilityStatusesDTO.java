/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import io.harness.data.validator.EntityIdentifier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "EntityUnavailabilityStatuses",
    description = "This is the EntityUnavailabilityStatuses entity defined in Harness")
public class EntityUnavailabilityStatusesDTO {
  @ApiModelProperty(required = true) @EntityIdentifier String orgIdentifier;
  @ApiModelProperty(required = true) @EntityIdentifier String projectIdentifier;
  @ApiModelProperty(required = true) @NotNull private String entityId;
  @ApiModelProperty(required = true) @NotNull private EntityType entityType;
  @ApiModelProperty(required = true) @NotNull private long startTime;
  @ApiModelProperty(required = true) @NotNull private long endTime;
  @ApiModelProperty(required = true) @NotNull private EntityUnavailabilityStatus status;
  @ApiModelProperty(required = true) @NotNull private EntitiesRule entitiesRule;
}
