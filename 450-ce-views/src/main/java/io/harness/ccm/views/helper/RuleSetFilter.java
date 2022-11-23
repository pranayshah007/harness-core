package io.harness.ccm.views.helper;

import io.harness.NGCommonEntityConstants;

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

  @Builder
  public RuleSetFilter(String accountId, String cloudProvider, Boolean isOOTB, List<String> ruleSetIds) {
    this.accountId = accountId;
    this.cloudProvider = cloudProvider;
    this.isOOTB = isOOTB;
    this.ruleSetIds = ruleSetIds;
  }
}
