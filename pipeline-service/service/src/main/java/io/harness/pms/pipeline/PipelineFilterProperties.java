/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.filter.FilterConstants.PIPELINE_SETUP_FILTER;

import io.harness.filter.FilterType;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.common.beans.NGTag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.bson.Document;

@Value
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PipelineFilterProperties")
@JsonTypeName(PIPELINE_SETUP_FILTER)
public class PipelineFilterProperties extends FilterProperties {
  private List<NGTag> pipelineTags;
  private List<String> pipelineIdentifiers;
  private String name;
  private String description;

  private org.bson.Document moduleProperties;

  @Builder
  public PipelineFilterProperties(List<NGTag> pipelineTags, List<String> pipelineIdentifiers, String name,
      String description, Document moduleProperties, List<NGTag> tags, FilterType type) {
    super(tags, type);
    this.pipelineTags = pipelineTags;
    this.pipelineIdentifiers = pipelineIdentifiers;
    this.name = name;
    this.description = description;
    this.moduleProperties = moduleProperties;
  }
}
