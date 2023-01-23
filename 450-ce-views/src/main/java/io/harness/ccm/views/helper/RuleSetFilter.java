/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.harness.NGCommonEntityConstants;
import io.harness.ccm.commons.entities.CCMSort;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "RuleSetRequest", description = "This has the query to list the policy packs")
public class RuleSetFilter {
  @Schema(description = "account id") String accountId;
  @Schema(description = "isOOTBPolicy") Boolean isOOTB;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.TAGS) String tags;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "ruleSetIds") List<String> ruleSetIds;
  @Schema(description = "search") String search;
  @Schema(description = "limit") int limit;
  @Schema(description = "offset") int offset;
  @Schema(description = "The order by condition for Rule Set query") List<CCMSort> orderBy;

  @Builder
  public RuleSetFilter(String accountId, String cloudProvider, Boolean isOOTB, List<String> ruleSetIds, String search,
      int limit, int offset, List<CCMSort> orderBy) {
    this.accountId = accountId;
    this.cloudProvider = cloudProvider;
    this.isOOTB = isOOTB;
    this.ruleSetIds = ruleSetIds;
    this.search = search;
    this.limit = limit;
    this.offset = offset;
    this.orderBy = orderBy;
  }
}
