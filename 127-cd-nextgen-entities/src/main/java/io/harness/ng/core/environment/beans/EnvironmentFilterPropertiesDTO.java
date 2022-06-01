/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.beans;

import static io.harness.filter.FilterConstants.ENVIRONMENT;

import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("EnvironmentFilterProperties")
@JsonTypeName(ENVIRONMENT)
public class EnvironmentFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "names of the environment") List<String> environmentNames;
  @Schema(description = "identifier of the environment") List<String> environmentIdentifiers;
  @Schema(description = "description of the environment") String description;
  @Schema(description = "type of environment") List<EnvironmentType> environmentTypes;

  @Override
  public FilterType getFilterType() {
    return FilterType.ENVIRONMENT;
  }
}
