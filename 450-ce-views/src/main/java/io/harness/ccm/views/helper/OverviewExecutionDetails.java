/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a Cloud Cost OverviewExecutionDetails")

public class OverviewExecutionDetails {
  @Schema(description = "Total Rules") int totalRules;
  @Schema(description = "Total Enforcements") int totalRuleEnforcements;
  @Schema(description = "ResourceTypeExecution") Map<String, Integer> topResourceTypeExecution;
  @Schema(description = "monthlyRealizedSavings") Map<String, String> monthlyRealizedSavings;
}
