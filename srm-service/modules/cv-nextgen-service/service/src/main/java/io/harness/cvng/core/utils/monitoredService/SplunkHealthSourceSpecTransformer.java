/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.SplunkHealthSourceSpec;
import io.harness.cvng.core.entities.SplunkCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;

public class SplunkHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<SplunkCVConfig, SplunkHealthSourceSpec> {
  @Override
  public SplunkHealthSourceSpec transformToHealthSourceConfig(List<SplunkCVConfig> cvConfigs) {
    Preconditions.checkArgument(cvConfigs.stream().map(SplunkCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(SplunkCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");

    return SplunkHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .queries(cvConfigs.stream()
                     .map(cv
                         -> SplunkHealthSourceSpec.SplunkHealthSourceQueryDTO.builder()
                                .name(cv.getQueryName())
                                .query(cv.getQuery())
                                .serviceInstanceIdentifier(cv.getServiceInstanceIdentifier())
                                .build())
                     .collect(Collectors.toList()))
        .build();
  }
}
