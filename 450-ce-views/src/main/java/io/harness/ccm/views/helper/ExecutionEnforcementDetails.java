/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
  @Schema(description = "isDryRun") Boolean isDryRun;
  @Schema(description = "isEnabled") Boolean isEnabled;
  @Schema(description = "executionTimezone") String executionTimezone;

  public ExecutionEnforcementDetails toDTO() {
    return ExecutionEnforcementDetails.builder()
        .enforcementName(getEnforcementName())
        .ruleIds(getRuleIds())
        .schedule(getSchedule())
        .ruleSetIds(getRuleSetIds())
        .description(getDescription())
        .isDryRun(getIsDryRun())
        .isEnabled(getIsEnabled())
        .executionTimezone(getExecutionTimezone())
        .build();
  }
}
