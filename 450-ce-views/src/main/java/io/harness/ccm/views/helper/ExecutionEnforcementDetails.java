package io.harness.ccm.views.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a ExecutionEnforcementDetails")

public final class ExecutionEnforcementDetails {
  @Schema(description = "Enforcement Name") String enforcementName;
  @Schema(description = "schedule") String schedule;
  @Schema(description = "description") String description;
  @Schema(description = "Target Account") List<String> accounts;
  @Schema(description = "Target Region") List<String> regions;
  @Schema(description = "rules ids and list of enforcement") HashMap<String, String> ruleIds;
  @Schema(description = "rules pack ids and list of enforcement") HashMap<String, String> ruleSetIds;

  public ExecutionEnforcementDetails toDTO() {
    return ExecutionEnforcementDetails.builder()
        .enforcementName(getEnforcementName())
        .ruleIds(getRuleIds())
        .schedule(getSchedule())
        .ruleSetIds(getRuleSetIds())
        .description(getDescription())
        .build();
  }
}