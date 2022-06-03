/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import io.harness.ccm.views.entities.ViewChartType;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewTimeRangeType;
import io.harness.ccm.views.entities.ViewType;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "Perspective", description = "Perspective ID to name mapping")
public class QLCEView {
  String id;
  String name;
  String folderId;
  @Hidden double totalCost;
  @Hidden String createdBy;
  @Hidden Long createdAt;
  @Hidden Long lastUpdatedAt;
  @Hidden ViewChartType chartType;
  @Hidden ViewType viewType;
  @Hidden ViewState viewState;
  @Hidden ViewPreferences viewPreferences;

  @Hidden QLCEViewField groupBy;
  @Hidden ViewTimeRangeType timeRange;
  @Hidden List<ViewFieldIdentifier> dataSources;
  @Hidden boolean isReportScheduledConfigured;
}
