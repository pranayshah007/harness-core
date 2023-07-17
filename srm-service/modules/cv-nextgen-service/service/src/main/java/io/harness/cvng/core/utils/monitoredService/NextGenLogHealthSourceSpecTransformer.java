/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.healthsource.HealthSourceParamsDTO;
import io.harness.cvng.core.beans.healthsource.QueryDefinition;
import io.harness.cvng.core.beans.healthsource.QueryParamsDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.NextGenHealthSourceSpec;
import io.harness.cvng.core.entities.NextGenLogCVConfig;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class NextGenLogHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<NextGenLogCVConfig, NextGenHealthSourceSpec> {
  @Override
  public NextGenHealthSourceSpec transformToHealthSourceConfig(List<NextGenLogCVConfig> cvConfigs) {
    Preconditions.checkArgument(
        cvConfigs.stream().map(NextGenLogCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    return NextGenHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .healthSourceParams(HealthSourceParamsDTO.getHealthSourceParamsDTO(cvConfigs.get(0).getHealthSourceParams()))
        .dataSourceType(cvConfigs.get(0).getType())
        .queryDefinitions(cvConfigs.stream()
                              .map(cv
                                  -> QueryDefinition.builder()
                                         .name(cv.getQueryName())
                                         .query(cv.getQuery())
                                         .groupName(cv.getGroupName())
                                         .identifier(cv.getQueryIdentifier())
                                         .queryParams(QueryParamsDTO.getQueryParamsDTO(cv.getQueryParams()))
                                         .build())
                              .collect(Collectors.toList()))
        .build();
  }
}