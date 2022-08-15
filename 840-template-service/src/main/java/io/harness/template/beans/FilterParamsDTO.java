package io.harness.template.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.template.TemplateListType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("TemplateFilterParams")
@Schema(name = "TemplateFilterParams", description = "Template Filter Params")
public class FilterParamsDTO {
  String searchTerm;
  String filterIdentifier;
  TemplateListType templateListType;
  TemplateFilterProperties templateFilterProperties;
  boolean includeAllTemplatesAccessibleAtScope;
  boolean getDistinctFromBranches;
}
