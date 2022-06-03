/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo.ResolvedCVConfigInfoBuilder;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;

public class DefaultVerifyStepMonitoredServiceResolutionServiceImpl
    implements VerifyStepMonitoredServiceResolutionService {
  @Inject private CVConfigService cvConfigService;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public ResolvedCVConfigInfo getResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder = ResolvedCVConfigInfo.builder();
    Optional<MonitoredService> monitoredService = getMonitoredService(serviceEnvironmentParams);
    monitoredService.ifPresent(service
        -> resolvedCVConfigInfoBuilder.monitoredServiceIdentifier(service.getIdentifier())
               .cvConfigs(getCVConfigs(serviceEnvironmentParams, service)));
    return resolvedCVConfigInfoBuilder.build();
  }

  private List<CVConfig> getCVConfigs(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredService monitoredService) {
    if (Objects.nonNull(monitoredService)
        && CollectionUtils.isNotEmpty(monitoredService.getHealthSourceIdentifiers())) {
      return getCvConfigsFromMonitoredService(serviceEnvironmentParams, monitoredService.getIdentifier());
    } else {
      return Collections.emptyList();
    }
  }

  private List<CVConfig> getCvConfigsFromMonitoredService(
      ServiceEnvironmentParams serviceEnvironmentParams, String monitoredServiceIdentifier) {
    return cvConfigService.getCVConfigs(MonitoredServiceParams.builder()
                                            .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                            .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
                                            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                            .build());
  }

  private Optional<MonitoredService> getMonitoredService(ServiceEnvironmentParams serviceEnvironmentParams) {
    return monitoredServiceService.getApplicationMonitoredService(serviceEnvironmentParams);
  }
}
