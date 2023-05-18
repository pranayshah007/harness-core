/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.ELKHealthSourceSpec;
import io.harness.cvng.core.entities.ELKCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;

public class ELKHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<ELKCVConfig, ELKHealthSourceSpec> {
  @Override
  public ELKHealthSourceSpec transformToHealthSourceConfig(List<ELKCVConfig> cvConfigs) {
    Preconditions.checkArgument(cvConfigs.stream().map(ELKCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(ELKCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");
    return ELKHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .queries(cvConfigs.stream()
                     .map(cv
                         -> ELKHealthSourceSpec.ELKHealthSourceQueryDTO.builder()
                                .name(cv.getQueryName())
                                .query(cv.getQuery())
                                .index(cv.getIndex())
                                .serviceInstanceIdentifier(cv.getServiceInstanceIdentifier())
                                .timeStampIdentifier(cv.getTimeStampIdentifier())
                                .timeStampFormat(cv.getTimeStampFormat())
                                .messageIdentifier(cv.getMessageIdentifier())
                                .build())
                     .collect(Collectors.toList()))
        .build();
  }
}
