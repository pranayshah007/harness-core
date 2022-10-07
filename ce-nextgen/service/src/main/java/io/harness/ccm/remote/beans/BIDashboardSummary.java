/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("BIDashboardSummary")
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CE)
@Schema(name = "BIDashboardSummary", description = "BI Dashboard Summary")
public class BIDashboardSummary {
  @Schema(description = "Name of the BI Dashboard") String dashboardName;
  @Schema(description = "Static Dashboard ID used in the dashboard's URL") String dashboardId;
  @Schema(description = "Cloud Provider associated with the dashboard") String cloudProvider;
  @Schema(description = "Brief Description about the dashboard") String description;
  @Schema(description = "Service Type") String serviceType;
  @Schema(description = "URL of the dashboard page to which user should be redirected") String redirectionURL;
}
