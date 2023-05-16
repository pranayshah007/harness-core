/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.harness.ccm.commons.entities.CCMTimeFilter;

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
@Schema(name = "RuleExecutionFilter", description = "This has the query to list the RuleExecution")
public class RuleExecutionFilter {
  @Schema(description = "accountId") String accountId;
  @Schema(description = "Account Name") List<String> targetAccount;
  @Schema(description = "Execution Status") ExecutionStatus executionStatus;
  @Schema(description = "region") List<String> region;
  @Schema(description = "cloudProvider") RuleCloudProviderType cloudProvider;
  @Schema(description = "ruleId") List<String> ruleIds;
  @Schema(description = "rulePackId") List<String> ruleSetIds;
  @Schema(description = "executionIds") List<String> executionIds;
  @Schema(description = "ruleEnforcementId") List<String> ruleEnforcementId;
  @Schema(description = "Time") List<CCMTimeFilter> time;
  @Schema(description = "limit") int limit;
  @Schema(description = "offset") int offset;
  @Schema(description = "savings") Double savings;
  @Schema(description = "sortByCost") Boolean sortByCost;

  @Builder
  public RuleExecutionFilter(String accountId, List<String> accountName, List<String> region, List<String> rulesId,
      List<String> rulePackId, RuleCloudProviderType cloudProvider, List<String> ruleEnforcementId,
      List<CCMTimeFilter> time, int limit, int offset, ExecutionStatus executionStatus, List<String> executionIds) {
    this.accountId = accountId;
    this.targetAccount = accountName;
    this.region = region;
    this.ruleIds = rulesId;
    this.ruleSetIds = rulePackId;
    this.cloudProvider = cloudProvider;
    this.ruleEnforcementId = ruleEnforcementId;
    this.time = time;
    this.limit = limit;
    this.offset = offset;
    this.executionStatus = executionStatus;
    this.executionIds = executionIds;
  }
}
