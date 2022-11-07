package io.harness.ccm.views.entities;

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
@Schema(name = "PolicyExecutionFilter", description = "This has the query to list the PolicyExecution")
public class PolicyExecutionFilter {
  @Schema(description = "accountId") String accountId;
  @Schema(description = "Account Name") String targetAccount;
  @Schema(description = "region") List<String> region;
  @Schema(description = "cloudProvider") PolicyCloudProviderType cloudProvider;
  @Schema(description = "policyName") List<String> policyName;
  @Schema(description = "policySetName") List<String> policyEnforcementId;
  @Schema(description = "Time") List<CCMTimeFilter> time;
  @Schema(description = "limit") int limit;
  @Schema(description = "offset") int offset;

  @Builder
  public PolicyExecutionFilter(String accountId, String accountName, List<String> region, List<String> policyName,
                               PolicyCloudProviderType cloudProvider, List<String> policyEnforcementId, List<CCMTimeFilter> time, int limit, int offset) {
    this.accountId = accountId;
    this.targetAccount = accountName;
    this.region = region;
    this.policyName = policyName;
    this.cloudProvider = cloudProvider;
    this.policyEnforcementId = policyEnforcementId;
    this.time = time;
    this.limit = limit;
    this.offset = offset;
  }
}
