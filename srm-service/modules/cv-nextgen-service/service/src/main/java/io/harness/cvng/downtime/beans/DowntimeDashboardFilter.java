/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.beans;

import io.harness.cvng.CVConstants;

import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DowntimeDashboardFilter {
  @Parameter(description = CVConstants.MONITORED_SERVICE_PARAM_MESSAGE)
  @QueryParam("monitoredServiceIdentifier")
  String monitoredServiceIdentifier;
  @Parameter(description = "For filtering on the basis of name") @QueryParam("filter") String searchFilter;
}
