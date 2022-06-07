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
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class TemplateVerifyStepMonitoredServiceResolutionServiceImpl
    implements VerifyStepMonitoredServiceResolutionService {
  private static final String NULL_MONITORED_SERVICE_IDENTIFIER = "";
  @Inject private Clock clock;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private MetricPackService metricPackService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SideKickService sideKickService;

  @Override
  public ResolvedCVConfigInfo getResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder = ResolvedCVConfigInfo.builder();
    populateSourceDataFromTemplate(serviceEnvironmentParams, monitoredServiceNode, resolvedCVConfigInfoBuilder);
    return resolvedCVConfigInfoBuilder.build();
  }

  @Override
  public void managePerpetualTasks(ServiceEnvironmentParams serviceEnvironmentParams,
      ResolvedCVConfigInfo resolvedCVConfigInfo, String verificationJobInstanceId) {
    List<ResolvedCVConfigInfo.HealthSourceInfo> healthSources = resolvedCVConfigInfo.getHealthSources();
    if (CollectionUtils.isNotEmpty(healthSources)) {
      List<String> sourceIdentifiersToCleanUp = new ArrayList<>();
      healthSources.forEach(healthSource -> {
        String sourceIdentifier =
            HealthSourceService.getNameSpacedIdentifier(verificationJobInstanceId, healthSource.getIdentifier());
        monitoringSourcePerpetualTaskService.createTask(serviceEnvironmentParams.getAccountIdentifier(),
            serviceEnvironmentParams.getOrgIdentifier(), serviceEnvironmentParams.getProjectIdentifier(),
            healthSource.getConnectorRef(), sourceIdentifier, healthSource.isDemoEnabledForAnyCVConfig());
        sourceIdentifiersToCleanUp.add(sourceIdentifier);
      });
      sideKickService.schedule(VerificationJobInstanceCleanupSideKickData.builder()
                                   .verificationJobInstanceIdentifier(verificationJobInstanceId)
                                   .sourceIdentifiers(sourceIdentifiersToCleanUp)
                                   .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
                                   .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                   .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                   .build(),
          clock.instant().plus(Duration.ofMinutes(30)));
    }
  }

  private void populateSourceDataFromTemplate(ServiceEnvironmentParams serviceEnvironmentParams,
      MonitoredServiceNode monitoredServiceNode, ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder) {
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
      populateCvConfigAndHealSourceData(
          serviceEnvironmentParams, monitoredServiceDTO.getSources().getHealthSources(), resolvedCVConfigInfoBuilder);
    } else {
      resolvedCVConfigInfoBuilder.cvConfigs(Collections.emptyList()).healthSources(Collections.emptyList());
    }
  }
  private String getTemplateYaml(TemplateMonitoredServiceSpec templateMonitoredServiceSpec) {
    // TODO: Add logic to generate template yaml.
    //    String monitoredServiceTemplateRef = templateMonitoredServiceSpec.getMonitoredServiceTemplateRef().getValue();
    //    String versionLabel = templateMonitoredServiceSpec.getVersionLabel();
    return null;
  }

  private void populateCvConfigAndHealSourceData(ServiceEnvironmentParams serviceEnvironmentParams,
      Set<HealthSource> healthSources, ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder) {
    List<CVConfig> allCvConfigs = new ArrayList<>();
    List<ResolvedCVConfigInfo.HealthSourceInfo> healthSourceInfoList = new ArrayList<>();
    healthSources.forEach(healthSource -> {
      HealthSource.CVConfigUpdateResult cvConfigUpdateResult = healthSource.getSpec().getCVConfigUpdateResult(
          serviceEnvironmentParams.getAccountIdentifier(), serviceEnvironmentParams.getOrgIdentifier(),
          serviceEnvironmentParams.getProjectIdentifier(), serviceEnvironmentParams.getEnvironmentIdentifier(),
          serviceEnvironmentParams.getServiceIdentifier(), NULL_MONITORED_SERVICE_IDENTIFIER,
          HealthSourceService.getNameSpacedIdentifier(NULL_MONITORED_SERVICE_IDENTIFIER, healthSource.getIdentifier()),
          healthSource.getName(), Collections.emptyList(), metricPackService);

      boolean isDemoEnabledForAnyCVConfig = false;

      for (CVConfig cvConfig : cvConfigUpdateResult.getAdded()) {
        cvConfig.setEnabled(true);
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

      healthSourceInfoList.add(ResolvedCVConfigInfo.HealthSourceInfo.builder()
                                   .connectorRef(healthSource.getSpec().getConnectorRef())
                                   .demoEnabledForAnyCVConfig(isDemoEnabledForAnyCVConfig)
                                   .identifier(healthSource.getIdentifier())
                                   .build());
    });
    resolvedCVConfigInfoBuilder.cvConfigs(allCvConfigs).healthSources(healthSourceInfoList);
  }
}
