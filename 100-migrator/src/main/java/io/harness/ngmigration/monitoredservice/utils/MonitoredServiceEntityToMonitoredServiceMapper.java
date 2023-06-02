/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.monitoredservice.utils;

import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.ngmigration.monitoredservice.healthsource.HealthSourceGeneratorFactory;
import io.harness.serializer.JsonUtils;

import software.wings.beans.GraphNode;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Collections;

public class MonitoredServiceEntityToMonitoredServiceMapper {
  @Inject HealthSourceGeneratorFactory healthSourceGeneratorFactory;

  public boolean isMigrationSupported(GraphNode graphNode) {
    return healthSourceGeneratorFactory.getHealthSourceGenerator(graphNode.getType()).isPresent();
  }

  public JsonNode getMonitoredServiceJsonNode(GraphNode graphNode) {
    MonitoredServiceDTO monitoredServiceDTO =
        MonitoredServiceDTO.builder()
            .name("NG Migrated MonitoredService for CV")
            .environmentRef("<+input>")
            .serviceRef("<+input>")
            .type(MonitoredServiceType.APPLICATION)
            .sources(MonitoredServiceDTO.Sources.builder()
                         .healthSources(Collections.singleton(
                             healthSourceGeneratorFactory.getHealthSourceGenerator(graphNode.getType())
                                 .get()
                                 .generateHealthSource(graphNode)))
                         .build())
            .build();

    return JsonUtils.asTree(monitoredServiceDTO);
  }
}
