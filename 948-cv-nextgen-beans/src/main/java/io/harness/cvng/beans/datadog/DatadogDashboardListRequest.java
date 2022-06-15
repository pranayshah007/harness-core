/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.datadog;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DATADOG_DASHBOARD_LIST")
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
@EqualsAndHashCode(callSuper = true)
public class DatadogDashboardListRequest extends DatadogRequest {
  private static final String DSL =
      DataCollectionRequest.readDSL("datadog-dashboard-list.datacollection", DatadogDashboardListRequest.class);

  @Override
  public String getDSL() {
    return DSL;
  }
}
