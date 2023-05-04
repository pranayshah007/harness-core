/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.resources.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.filter.FilterConstants.TEMPLATE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.ng.core.template.ListingScope;
import io.harness.ng.core.template.TemplateEntityType;

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
@ApiModel("TemplateFilterProperties")
@Schema(name = "TemplateFilterProperties", description = "This contains details of the Template Filter")
@JsonTypeName(TEMPLATE_FILTER)
@OwnedBy(CDC)
public class TemplateFilterPropertiesDTO extends FilterPropertiesDTO {
  List<String> templateNames;
  List<String> templateIdentifiers;
  String description;
  List<TemplateEntityType> templateEntityTypes;
  List<String> childTypes;
  ListingScope listingScope;
  String repoName;

  @Override
  public FilterType getFilterType() {
    return FilterType.TEMPLATE;
  }
}
