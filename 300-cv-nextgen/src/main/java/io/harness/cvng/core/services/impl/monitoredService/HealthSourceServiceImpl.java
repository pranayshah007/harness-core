/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.groupingBy;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.HealthSourceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.exception.DuplicateFieldException;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class HealthSourceServiceImpl implements HealthSourceService {
  @Inject private Map<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMap;
  @Inject private CVConfigService cvConfigService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private MetricPackService metricPackService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void create(String accountId, String orgIdentifier, String projectIdentifier, String environmentRef,
      String serviceRef, String monitoredServiceIdentifier, String nameSpaceIdentifier, Set<HealthSource> healthSources,
      boolean enabled) {
    healthSources.forEach(healthSource -> {
      CVConfigUpdateResult cvConfigUpdateResult = healthSource.getSpec().getCVConfigUpdateResult(accountId,
          orgIdentifier, projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
          HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()),
          healthSource.getName(), Collections.emptyList(), metricPackService);
      boolean isDemoEnabledForAnyCVConfig = false;
      for (CVConfig cvConfig : cvConfigUpdateResult.getAdded()) {
        cvConfig.setEnabled(enabled);
        if (cvConfig.isEligibleForDemo()
            && featureFlagService.isFeatureFlagEnabled(accountId, CVNG_MONITORED_SERVICE_DEMO)) {
          isDemoEnabledForAnyCVConfig = true;
          cvConfig.setDemo(true);
        }
      }

      cvConfigService.save(cvConfigUpdateResult.getAdded());
      // Creating MonitoringSourcePerpetualTasks for now irrespective of enable/disable status.
      // We need to rethink how these tasks are created, batched and managed together.
      monitoringSourcePerpetualTaskService.createTask(accountId, orgIdentifier, projectIdentifier,
          healthSource.getSpec().getConnectorRef(),
          HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()),
          isDemoEnabledForAnyCVConfig);
    });
  }

  @Override
  public void checkIfAlreadyPresent(String accountId, String orgIdentifier, String projectIdentifier,
      String nameSpaceIdentifier, Set<HealthSource> healthSources) {
    healthSources.forEach(healthSource -> {
      List<CVConfig> saved = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
          HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
      if (saved != null && saved.size() > 0) {
        throw new DuplicateFieldException(String.format(
            "Already Existing configs for Monitored Service  with identifier %s and orgIdentifier %s and projectIdentifier %s",
            HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()),
            orgIdentifier, projectIdentifier));
      }
    });
  }

  @Override
  public Set<HealthSource> get(String accountId, String orgIdentifier, String projectIdentifier,
      String nameSpaceIdentifier, List<String> identifiers) {
    return identifiers.stream()
        .map(identifier
            -> transformCVConfigs(accountId, orgIdentifier, projectIdentifier, nameSpaceIdentifier, identifier))
        .filter(healthSource -> healthSource != null)
        .collect(Collectors.toSet());
  }

  @Override
  public void delete(String accountId, String orgIdentifier, String projectIdentifier, String nameSpaceIdentifier,
      List<String> identifiers) {
    identifiers.forEach(identifier -> {
      String nameSpacedIdentifier = HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, identifier);
      cvConfigService.deleteByIdentifier(accountId, orgIdentifier, projectIdentifier, nameSpacedIdentifier);
    });
  }

  @Override
  public void update(String accountId, String orgIdentifier, String projectIdentifier, String environmentRef,
      String serviceRef, String monitoredServiceIdentifier, String nameSpaceIdentifier, Set<HealthSource> healthSources,
      boolean isEnabled) {
    healthSources.forEach(healthSource -> {
      List<CVConfig> saved = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
          HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()));
      CVConfigUpdateResult cvConfigUpdateResult = healthSource.getSpec().getCVConfigUpdateResult(accountId,
          orgIdentifier, projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
          HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, healthSource.getIdentifier()),
          healthSource.getName(), saved, metricPackService);
      cvConfigUpdateResult.getDeleted().forEach(cvConfig -> cvConfigService.delete(cvConfig.getUuid()));

      for (CVConfig cvConfig : cvConfigUpdateResult.getAdded()) {
        cvConfig.setEnabled(isEnabled);
      }
      for (CVConfig cvConfig : cvConfigUpdateResult.getUpdated()) {
        cvConfig.setEnabled(isEnabled);
      }
      cvConfigService.update(cvConfigUpdateResult.getUpdated());
      cvConfigService.save(cvConfigUpdateResult.getAdded());
    });
  }

  @Override
  public void setHealthMonitoringFlag(String accountId, String orgIdentifier, String projectIdentifier,
      String namespace, List<String> healthSourceIdentifiers, boolean enable) {
    cvConfigService.setHealthMonitoringFlag(accountId, orgIdentifier, projectIdentifier,
        healthSourceIdentifiers.stream()
            .map(healthSourceIdentifier
                -> HealthSourceService.getNameSpacedIdentifier(namespace, healthSourceIdentifier))
            .collect(Collectors.toList()),
        enable);
  }

  private HealthSource transformCVConfigs(
      String accountId, String orgIdentifier, String projectIdentifier, String nameSpaceIdentifier, String identifier) {
    List<CVConfig> cvConfigs = cvConfigService.list(accountId, orgIdentifier, projectIdentifier,
        HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, identifier));
    if (CollectionUtils.isEmpty(cvConfigs)) {
      String errorMessage =
          String.format("CVConfigs are not present for identifier %s, orgIdentifier %s and projectIdentifier %s",
              HealthSourceService.getNameSpacedIdentifier(nameSpaceIdentifier, identifier), orgIdentifier,
              projectIdentifier);
      log.error(errorMessage, new IllegalStateException(errorMessage));
      return null;
    }
    HealthSource healthSource = HealthSourceDTO.toHealthSource(cvConfigs, dataSourceTypeToHealthSourceTransformerMap);
    healthSource.setIdentifier(identifier);
    return healthSource;
  }

  @Override
  public List<CVConfig> getCVConfigs(String accountId, String orgIdentifier, String projectIdentifier,
      String monitoredServiceIdentifier, String healthSourceIdentifier) {
    String identifier = HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceIdentifier);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return cvConfigService.getCVConfigs(projectParams, identifier);
  }

  @Override
  public Map<String, Set<HealthSource>> getHealthSource(List<MonitoredService> monitoredServiceEntities) {
    Map<String, Set<HealthSource>> healthSourceMap = new HashMap<>();
    if (isEmpty(monitoredServiceEntities)) {
      return healthSourceMap;
    }
    List<String> identifiers = monitoredServiceEntities.stream()
                                   .map(monitoredServiceEntity
                                       -> monitoredServiceEntity.getHealthSourceIdentifiers()
                                              .stream()
                                              .map(healthSourceIdentifier
                                                  -> HealthSourceService.getNameSpacedIdentifier(
                                                      monitoredServiceEntity.getIdentifier(), healthSourceIdentifier))
                                              .collect(Collectors.toList()))
                                   .flatMap(List::stream)
                                   .collect(Collectors.toList());
    MonitoredService monitoredService = monitoredServiceEntities.get(0);
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(monitoredService.getProjectIdentifier())
                                      .accountIdentifier(monitoredService.getAccountId())
                                      .orgIdentifier(monitoredService.getOrgIdentifier())
                                      .build();
    List<CVConfig> cvConfigList = cvConfigService.list(projectParams, identifiers);
    Map<String, List<CVConfig>> cvConfigMap = cvConfigList.stream().collect(groupingBy(CVConfig::getIdentifier));
    for (Map.Entry<String, List<CVConfig>> cvConfig : cvConfigMap.entrySet()) {
      Pair<String, String> nameSpaceAndIdentifier =
          HealthSourceService.getNameSpaceAndIdentifier(cvConfig.getValue().get(0).getFullyQualifiedIdentifier());
      HealthSource healthSource =
          HealthSourceDTO.toHealthSource(cvConfig.getValue(), dataSourceTypeToHealthSourceTransformerMap);
      Set<HealthSource> healthSourceSet =
          healthSourceMap.getOrDefault(nameSpaceAndIdentifier.getKey(), new HashSet<>());
      healthSourceSet.add(healthSource);
      healthSourceMap.put(nameSpaceAndIdentifier.getKey(), healthSourceSet);
    }
    return healthSourceMap;
  }
}
