/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.perspectives;

import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.CCMStringFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties
@Schema(
    name = "ClusterCostDetailsQueryParams", description = "Common query parameters for all cluster cost details APIs")
public class ClusterCostDetailsQueryParamsDTO {
  @Schema(description = "Fields which will be aggregated in the response") List<CCMAggregation> aggregations;
  @Schema(description = "Filters to be applied on the response.") List<CCMStringFilter> filters;
  @Schema(description = "Fields on which the response will be grouped by.") List<CCMField> groupBy;
  @Schema(description = "Only applicable for Time Series Endpoints, defaults to DAY")
  QLCEViewTimeGroupType timeResolution;
  @Schema(description = "Limit on the number of cost values returned, 0 by default.") Integer limit;
  @Schema(description = "Order of sorting on cost, Descending by default.") CCMSortOrder sortOrder;
  @Schema(description = "Offset on the cost values returned, 10 by default.") Integer offset;
  @Schema(description = "Skip Rounding off the cost values returned, false by default.") Boolean skipRoundOff;
  @Schema(description = "The response will contain values corresponding to these labels") List<String> selectedLabels;
}
