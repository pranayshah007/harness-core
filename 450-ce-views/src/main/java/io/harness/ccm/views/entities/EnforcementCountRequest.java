package io.harness.ccm.views.entities;


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
@Schema(name = "EnforcementCountRequest", description = "This has the query to list Enforcement Count")

public class EnforcementCountRequest {
  @Schema(description = "account id") String accountId;
  @Schema(description = "policyName") List<String> policyIds;
  @Schema(description = "policySetName") List<String> policyPackIds;

  @Builder
  public EnforcementCountRequest(String accountId, List<String> policyIds, List<String> policyPackIds) {
    this.accountId = accountId;
    this.policyIds = policyIds;
    this.policyPackIds = policyPackIds;
  }
}
