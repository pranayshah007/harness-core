/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "CostOverview", description = "Overview of the cost.")
public class CostOverviewDTO {
  @Hidden String statsLabel;
  @Hidden String statsValue;
  @Schema(name = "Trend", description = "Cost trend") double statsTrend;
  @Schema(name = "Cost", description = "Cost") double value;
}
