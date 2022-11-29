/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.ErrorTrackingHealthSourceSpec;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;

public class ErrorTrackingHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<ErrorTrackingCVConfig, ErrorTrackingHealthSourceSpec> {
  @Override
  public ErrorTrackingHealthSourceSpec transformToHealthSourceConfig(List<ErrorTrackingCVConfig> cvConfigs) {
    Preconditions.checkArgument(
        cvConfigs.stream().map(ErrorTrackingCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(ErrorTrackingCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.size() == 1, "Multiple configs not supported.");

    return ErrorTrackingHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .build();
  }
}
