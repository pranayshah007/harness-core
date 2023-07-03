/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter.dto;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@ApiModel("FilterProperties")
@JsonTypeInfo(use = Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "filterType")
@OwnedBy(DX)
@Schema(name = "FilterProperties", description = "Properties of the Filter entity defined in Harness.")
public abstract class FilterPropertiesDTO {
  @Schema(description = "Filter tags as a key-value pair.") Map<String, String> tags;
  @Schema(description = "Filter tags as a key-value pair.", hidden = true) Map<String, String> labels;
  @Schema(description = "This specifies the corresponding Entity of the filter.", required = true)
  FilterType filterType;
}
