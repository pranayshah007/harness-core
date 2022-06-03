/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.cvng.core.utils.FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO;

import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo.ResolvedCVConfigInfoBuilder;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class TemplateVerifyStepMonitoredServiceResolutionServiceImpl
    implements VerifyStepMonitoredServiceResolutionService {
  private static final String NULL_MONITORED_SVC_IDENTIFIER = "";
  @Inject private CVConfigService cvConfigService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private MetricPackService metricPackService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public ResolvedCVConfigInfo getResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder = ResolvedCVConfigInfo.builder();
    resolvedCVConfigInfoBuilder.cvConfigs(
        getCVConfigsAndCreatePerpetualTasks(serviceEnvironmentParams, monitoredServiceNode));
    return resolvedCVConfigInfoBuilder.build();
  }

  private List<CVConfig> getCVConfigsAndCreatePerpetualTasks(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    TemplateMonitoredServiceSpec templateMonitoredServiceSpec =
        (TemplateMonitoredServiceSpec) monitoredServiceNode.getSpec();
    MonitoredServiceDTO monitoredServiceDTO = monitoredServiceService.getExpandedMonitoredServiceFromYaml(
        ProjectParams.builder()
            .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .build(),
        getTemplateYaml(templateMonitoredServiceSpec));
    if (Objects.nonNull(monitoredServiceDTO) && Objects.nonNull(monitoredServiceDTO.getSources())
        && CollectionUtils.isNotEmpty(monitoredServiceDTO.getSources().getHealthSources())) {
      return getCvConfigsFromHealthSources(
          serviceEnvironmentParams, monitoredServiceDTO.getSources().getHealthSources(), false);
    } else {
      return Collections.emptyList();
    }
  }
  private String getTemplateYaml(TemplateMonitoredServiceSpec templateMonitoredServiceSpec) {
    // TODO: Add logic to generate template yaml.
    //    String monitoredServiceTemplateRef = templateMonitoredServiceSpec.getMonitoredServiceTemplateRef().getValue();
    //    String versionLabel = templateMonitoredServiceSpec.getVersionLabel();
    return null;
  }

  private List<CVConfig> getCvConfigsFromHealthSources(
      ServiceEnvironmentParams serviceEnvironmentParams, Set<HealthSource> healthSources, boolean enabled) {
    List<CVConfig> allCvConfigs = new ArrayList<>();
    healthSources.forEach(healthSource -> {
      HealthSource.CVConfigUpdateResult cvConfigUpdateResult = healthSource.getSpec().getCVConfigUpdateResult(
          serviceEnvironmentParams.getAccountIdentifier(), serviceEnvironmentParams.getOrgIdentifier(),
          serviceEnvironmentParams.getProjectIdentifier(), serviceEnvironmentParams.getEnvironmentIdentifier(),
          serviceEnvironmentParams.getServiceIdentifier(), NULL_MONITORED_SVC_IDENTIFIER,
          HealthSourceService.getNameSpacedIdentifier(NULL_MONITORED_SVC_IDENTIFIER, healthSource.getIdentifier()),
          healthSource.getName(), Collections.emptyList(), metricPackService);

      boolean isDemoEnabledForAnyCVConfig = false;

      for (CVConfig cvConfig : cvConfigUpdateResult.getAdded()) {
        cvConfig.setEnabled(enabled);
        if (cvConfig.isEligibleForDemo()
            && featureFlagService.isFeatureFlagEnabled(
                serviceEnvironmentParams.getAccountIdentifier(), CVNG_MONITORED_SERVICE_DEMO)) {
          isDemoEnabledForAnyCVConfig = true;
          cvConfig.setDemo(true);
        }
      }

      if (CollectionUtils.isNotEmpty(cvConfigUpdateResult.getAdded())) {
        allCvConfigs.addAll(cvConfigUpdateResult.getAdded());
      }

      createPerpetualTasks(serviceEnvironmentParams, healthSource, isDemoEnabledForAnyCVConfig);
    });
    return allCvConfigs;
  }

  // TODO: Add code to delete done tasks.
  private void createPerpetualTasks(ServiceEnvironmentParams serviceEnvironmentParams, HealthSource healthSource,
      boolean isDemoEnabledForAnyCVConfig) {
    monitoringSourcePerpetualTaskService.createTask(serviceEnvironmentParams.getAccountIdentifier(),
        serviceEnvironmentParams.getOrgIdentifier(), serviceEnvironmentParams.getProjectIdentifier(),
        healthSource.getSpec().getConnectorRef(),
        HealthSourceService.getNameSpacedIdentifier(NULL_MONITORED_SVC_IDENTIFIER, healthSource.getIdentifier()),
        isDemoEnabledForAnyCVConfig);
  }
}
