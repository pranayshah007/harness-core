/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkHealthSourceSpec;
import io.harness.data.structure.CollectionUtils;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.states.SplunkV2State;

import java.util.Arrays;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class SplunkHealthSourceGenerator extends HealthSourceGenerator {
  @Override
  public HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode, MigrationContext migrationContext) {
    Map<String, Object> properties = CollectionUtils.emptyIfNull(graphNode.getProperties());
    SplunkV2State state = new SplunkV2State(graphNode.getName());
    state.parseProperties(properties);

    return SplunkHealthSourceSpec.builder()
        .connectorRef(MigratorUtility.getIdentifierWithScopeDefaults(migrationContext.getMigratedEntities(),
            state.getAnalysisServerConfigId(), NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT))
        .feature("Splunk Cloud Logs")
        .queries(Arrays.asList(SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                   .name(state.getName())
                                   .query(state.getQuery())
                                   .serviceInstanceIdentifier(state.getHostnameField())
                                   .build()))
        .build();
  }

  @Override
  public MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode) {
    return MonitoredServiceDataSourceType.SPLUNK;
  }
}
