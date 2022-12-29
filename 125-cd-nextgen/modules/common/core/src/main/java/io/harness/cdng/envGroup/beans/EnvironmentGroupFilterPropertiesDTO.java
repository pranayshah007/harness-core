/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.beans;

import static io.harness.filter.FilterConstants.ENVIRONMENT_GROUP_FILTER;

import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.common.beans.NGTag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
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
@ApiModel("EnvironmentGroupFilterProperties")
@JsonTypeName(ENVIRONMENT_GROUP_FILTER)
public class EnvironmentGroupFilterPropertiesDTO extends FilterPropertiesDTO {
  private String description;
  private String envGroupName;
  private List<String> envIdentifiers;
  private List<NGTag> envGroupTags;

  @Override
  public FilterType getFilterType() {
    return FilterType.ENVIRONMENTGROUP;
  }
}
