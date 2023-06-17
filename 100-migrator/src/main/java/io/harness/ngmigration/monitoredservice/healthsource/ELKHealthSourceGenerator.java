/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceVersion;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.data.structure.CollectionUtils;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;
import software.wings.sm.states.ElkAnalysisState;

import java.util.Arrays;
import java.util.Map;

public class ELKHealthSourceGenerator extends HealthSourceGenerator {
  @Override
  public HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    ElkAnalysisState state = new ElkAnalysisState(graphNode.getName());
    state.parseProperties(properties);

    return NextGenHealthSourceSpec.builder()
        .dataSourceType(DataSourceType.ELASTICSEARCH)
        .connectorRef(MigratorUtility.RUNTIME_INPUT.getValue())
        .queryDefinitions(Arrays.asList(
            QueryDefinition.builder()
                .identifier(MigratorUtility.generateIdentifier(graphNode.getName(), CaseFormat.LOWER_CASE))
                .name(graphNode.getName())
                .groupName(graphNode.getName())
                .query(state.getQuery())
                .queryParams(QueryParamsDTO.builder()
                                 .index(state.getIndices())
                                 .messageIdentifier("_source." + state.getMessageField())
                                 .timeStampFormat(state.getTimestampFormat())
                                 .serviceInstanceField("_source." + state.getHostnameField())
                                 .timeStampIdentifier("_source." + state.getTimestampField())
                                 .build())
                .build()))
        .build();
  }

  @Override
  public HealthSourceVersion getVersion() {
    return HealthSourceVersion.V2;
  }

  @Override
  public MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode) {
    return MonitoredServiceDataSourceType.ELASTICSEARCH;
  }
}
