/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.filter.FilterConstants.TEMPLATE_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.FilterType;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateEntityType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Value
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("TemplateFilterPropertiesEntity")
@JsonTypeName(TEMPLATE_FILTER)
@OwnedBy(CDC)
public class TemplateFilterProperties extends FilterProperties {
  List<String> templateNames;
  List<String> templateIdentifiers;
  String description;
  List<TemplateEntityType> templateEntityTypes;
  List<String> childTypes;
  String repoName;

  @Builder
  public TemplateFilterProperties(List<NGTag> tags, FilterType type, List<String> templateNames,
      List<String> templateIdentifiers, String description, List<TemplateEntityType> templateEntityTypes,
      List<String> childTypes, String repoName) {
    super(tags, type);
    this.templateNames = templateNames;
    this.templateIdentifiers = templateIdentifiers;
    this.description = description;
    this.templateEntityTypes = templateEntityTypes;
    this.childTypes = childTypes;
    this.repoName = repoName;
  }
}
