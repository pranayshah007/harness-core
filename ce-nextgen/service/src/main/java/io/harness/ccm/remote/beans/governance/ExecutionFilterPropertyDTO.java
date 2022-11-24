/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.governance;
/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.filter.FilterConstants.EXECUTION_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.views.helper.ExecutionStatus;
import io.harness.ccm.views.helper.RuleCloudProviderType;
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
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(EXECUTION_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ExecutionFilterProperty")
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CE)
@Schema(
    name = "ExecutionFilterProperty", description = "Properties of the Execution Filter Property defined in Harness")
public class ExecutionFilterPropertyDTO extends FilterPropertiesDTO {
  @Schema(description = "This is the list of Policy Names on which filter will be applied.") List<String> rulesId;
  @Schema(description = "This is the list of Policy pack name on which filter will be applied.") List<String> ruleSet;
  @Schema(description = "This is the list of region on which filter will be applied.") List<String> region;
  @Schema(description = "execution status") ExecutionStatus executionStatus;
  @Schema(description = "Cloud provider") RuleCloudProviderType cloudProvider;
  @Schema(description = "target Account") String targetAccount;
  @Schema(description = "List of filters to be applied on execution Time") List<CCMTimeFilter> timeFilters;
  @Schema(description = "Query Offset") Integer offset;
  @Schema(description = "Query Limit") Integer limit;

  @Override
  @Schema(type = "string", allowableValues = {"RuleExecution"})
  public FilterType getFilterType() {
    return FilterType.POLICYEXECUTION;
  }
}
