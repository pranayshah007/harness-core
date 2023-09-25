/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NewRelicHealthSourceSpec;
import io.harness.data.structure.CollectionUtils;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.sm.states.NewRelicState;

import java.util.Collections;
import java.util.Map;

public class NewRelicSourceGenerator extends HealthSourceGenerator {
  @Override
  public HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode, MigrationContext migrationContext) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    NewRelicState state = new NewRelicState(graphNode.getName());
    state.parseProperties(properties);

    return NewRelicHealthSourceSpec.builder()
        .connectorRef(MigratorUtility.RUNTIME_INPUT.getValue())
        .applicationName(state.getApplicationId())
        .applicationId(state.getApplicationId())
        .metricPacks(Collections.singleton(TimeSeriesMetricPackDTO.builder().identifier("Performance").build()))
        .build();
  }

  @Override
  public MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode) {
    return MonitoredServiceDataSourceType.NEW_RELIC;
  }
}
