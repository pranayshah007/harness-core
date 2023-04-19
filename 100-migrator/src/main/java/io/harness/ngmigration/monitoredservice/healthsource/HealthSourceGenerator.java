/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.healthsource;

import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceVersion;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GraphNode;

public abstract class HealthSourceGenerator {
  public abstract HealthSourceSpec generateHealthSourceSpec(GraphNode graphNode);

  public abstract MonitoredServiceDataSourceType getDataSourceType(GraphNode graphNode);

  public HealthSourceVersion getVersion() {
    return null;
  }

  public HealthSource generateHealthSource(GraphNode graphNode) {
    return HealthSource.builder()
        .name(graphNode.getName())
        .identifier(MigratorUtility.generateIdentifier(graphNode.getName(), CaseFormat.LOWER_CASE))
        .type(getDataSourceType(graphNode))
        .spec(generateHealthSourceSpec(graphNode))
        .version(getVersion())
        .build();
  }
}
