package io.harness.ccm.views.entities;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.NGCommonEntityConstants;
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
@Schema(name = "PolicyPackRequest", description = "This has the query to list the policy packs")
public class PolicyPackFilter {
    @Schema(description = "account id") String accountId;
    @Schema(description = "isOOTBPolicy") Boolean isOOTB;
    @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
    @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
    @Schema(description = NGCommonEntityConstants.TAGS) String tags;
    @Schema(description = "cloudProvider") String cloudProvider;
    @Schema(description = "policyPackIds")
    List<String> policyPackIds;
    @Schema(description = "policyPackIds")
    List<String> PoliciesIdentifier;

    @Builder
    public PolicyPackFilter(String accountId, String cloudProvider, Boolean isOOTB, List<String> policyPackIds,List<String> PoliciesIdentifier) {
        this.accountId = accountId;
        this.cloudProvider = cloudProvider;
        this.isOOTB = isOOTB;
        this.policyPackIds=policyPackIds;
        this.PoliciesIdentifier=PoliciesIdentifier;
    }
}
