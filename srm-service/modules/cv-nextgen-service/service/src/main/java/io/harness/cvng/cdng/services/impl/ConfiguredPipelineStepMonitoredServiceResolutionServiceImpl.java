/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.common.NGExpressionUtils;
import io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo.ResolvedCVConfigInfoBuilder;
import io.harness.cvng.cdng.services.api.PipelineStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections.CollectionUtils;

public class ConfiguredPipelineStepMonitoredServiceResolutionServiceImpl
    implements PipelineStepMonitoredServiceResolutionService {
  @Inject private CVConfigService cvConfigService;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public ResolvedCVConfigInfo fetchAndPersistResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder = ResolvedCVConfigInfo.builder();
    String monitoredServiceIdentifier = getMonitoredServiceIdentifier(monitoredServiceNode);
    resolvedCVConfigInfoBuilder.monitoredServiceIdentifier(monitoredServiceIdentifier)
        .cvConfigs(getCVConfigs(serviceEnvironmentParams, monitoredServiceIdentifier));
    return resolvedCVConfigInfoBuilder.build();
  }

  @Override
  public List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGStepInfo cvngStepInfo, ProjectParams projectParams) {
    ConfiguredMonitoredServiceSpec configuredMonitoredServiceSpec =
        (ConfiguredMonitoredServiceSpec) cvngStepInfo.getMonitoredService().getSpec();
    return getReferredEntities(filterCreationContext, configuredMonitoredServiceSpec, projectParams);
  }

  @Override
  public List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGDeploymentStepInfo cvngStepInfo, ProjectParams projectParams) {
    ConfiguredMonitoredServiceSpec configuredMonitoredServiceSpec =
        (ConfiguredMonitoredServiceSpec) cvngStepInfo.getMonitoredService().getSpec();
    return getReferredEntities(filterCreationContext, configuredMonitoredServiceSpec, projectParams);
  }

  private List<EntityDetailProtoDTO> getReferredEntities(FilterCreationContext filterCreationContext,
      ConfiguredMonitoredServiceSpec configuredMonitoredServiceSpec, ProjectParams projectParams) {
    ParameterField<String> monitoredServiceRef = configuredMonitoredServiceSpec.getMonitoredServiceRef();
    List<EntityDetailProtoDTO> result = new ArrayList<>();
    if (monitoredServiceRef.isExpression()
        || NGExpressionUtils.matchesInputSetPattern(monitoredServiceRef.getExpressionValue())) {
      return result;
    }
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builderWithProjectParams(projectParams)
                                                        .monitoredServiceIdentifier(monitoredServiceRef.getValue())
                                                        .build();
    MonitoredServiceDTO monitoredServiceDTO = monitoredServiceService.getMonitoredServiceDTO(monitoredServiceParams);
    Preconditions.checkNotNull(monitoredServiceDTO, "MonitoredService does not exist for identifier %s",
        monitoredServiceParams.getMonitoredServiceIdentifier());
    Preconditions.checkState(!monitoredServiceDTO.getSources().getHealthSources().isEmpty(),
        "No health sources exists for monitoredService for identifier %s ",
        monitoredServiceParams.getMonitoredServiceIdentifier());
    CVNGStepUtils.addReferredEntities(monitoredServiceDTO, result, filterCreationContext, projectParams);
    return result;
  }
  private String getMonitoredServiceIdentifier(MonitoredServiceNode monitoredServiceNode) {
    ConfiguredMonitoredServiceSpec configuredMonitoredServiceSpec =
        (ConfiguredMonitoredServiceSpec) monitoredServiceNode.getSpec();
    return configuredMonitoredServiceSpec.getMonitoredServiceRef().getValue();
  }

  private List<CVConfig> getCVConfigs(
      ServiceEnvironmentParams serviceEnvironmentParams, String monitoredServiceIdentifier) {
    MonitoredService monitoredService = getMonitoredService(serviceEnvironmentParams, monitoredServiceIdentifier);
    if (Objects.nonNull(monitoredService)
        && CollectionUtils.isNotEmpty(monitoredService.getHealthSourceIdentifiers())) {
      return getCvConfigsFromMonitoredService(serviceEnvironmentParams, monitoredServiceIdentifier);
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

  private MonitoredService getMonitoredService(
      ServiceEnvironmentParams serviceEnvironmentParams, String monitoredServiceIdentifier) {
    return monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builder()
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .build());
  }
}
