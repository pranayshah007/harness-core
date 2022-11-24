package io.harness.ccm.views.helper;

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
  @Schema(description = "rulesName") List<String> ruleIds;
  @Schema(description = "rulesSetName") List<String> ruleSetIds;

  @Builder
  public EnforcementCountRequest(String accountId, List<String> rulesIds, List<String> ruleSetIds) {
    this.accountId = accountId;
    this.ruleIds = rulesIds;
    this.ruleSetIds = ruleSetIds;
  }
}
