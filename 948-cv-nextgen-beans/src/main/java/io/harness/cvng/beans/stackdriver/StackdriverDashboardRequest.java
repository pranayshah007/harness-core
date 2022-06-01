/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("STACKDRIVER_DASHBOARD_LIST")
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class StackdriverDashboardRequest extends StackdriverRequest {
  public static final String DSL =
      StackdriverDashboardRequest.readDSL("stackdriver-dashboards.datacollection", StackdriverDashboardRequest.class);

  @Override
  public String getDSL() {
    return DSL;
  }
}
