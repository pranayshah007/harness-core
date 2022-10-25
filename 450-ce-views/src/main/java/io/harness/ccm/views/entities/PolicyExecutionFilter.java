package io.harness.ccm.views.entities;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.NGCommonEntityConstants;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.filter.FilterType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "PolicyExecutionFilter", description = "This has the query to list the PolicyExecution")
public class PolicyExecutionFilter {
    @Schema(description = "accountId") String accountId;
    @Schema(description = "Account Name") List<String>accountName;
    @Schema(description = "region") List<String> region;
    @Schema(description = "cloudProvider") String cloudProvider;
    @Schema(description = "policyName") List<String> policyName;
    @Schema(description = "policySetName") List<String> policyEnforcementId;
    @Schema(description = "Time") List<CCMTimeFilter> time;
    @Schema(description = "limit") long LIMIT ;
    @Schema(description = "offset") long offset;

    @Builder
    public PolicyExecutionFilter(String accountId, List<String> accountName, List<String> region, List<String> policyName,String cloudProvider,
                                 List<String> policyEnforcementId,List<CCMTimeFilter> time, long LIMIT,long offset  )
    {
        this.accountId=accountId;
        this.accountName=accountName;
        this.region=region;
        this.policyName= policyName;
        this.cloudProvider=cloudProvider;
        this.policyEnforcementId=policyEnforcementId;
        this.time=time;
        this.LIMIT= LIMIT;
        this.offset=offset;
    }
}
