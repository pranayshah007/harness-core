/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.dependency.KubernetesDependencyMetadata;
import io.harness.cvng.core.beans.dependency.ServiceDependencyMetadata;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.sidekick.DemoActivitySideKickData;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.impl.demo.changesource.KubernetesChangeSourceDemoDataGenerator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DemoActivitySideKickExecutor implements SideKickExecutor<DemoActivitySideKickData> {
  @Inject private ActivityService activityService;
  @Inject private ChangeSourceService changeSourceService;
  @Inject private KubernetesChangeSourceDemoDataGenerator kubernetesChangeSourceDemoDataGenerator;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ChangeEventService changeEventService;

  @Override
  public void execute(DemoActivitySideKickData sideKickInfo) {
    log.info("SidekickInfo {}", sideKickInfo);
    DeploymentActivity deploymentActivity =
        (DeploymentActivity) activityService.get(sideKickInfo.getDeploymentActivityId());
    MonitoredServiceParams serviceEnvironmentParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(deploymentActivity.getAccountId())
            .orgIdentifier(deploymentActivity.getOrgIdentifier())
            .projectIdentifier(deploymentActivity.getProjectIdentifier())
            .monitoredServiceIdentifier(deploymentActivity.getMonitoredServiceIdentifier())
            .build();
    MonitoredServiceDTO monitoredService = monitoredServiceService.getMonitoredServiceDTO(serviceEnvironmentParams);
    Optional<MonitoredServiceDTO.ServiceDependencyDTO> serviceDependencyDTO =
        monitoredService.getDependencies()
            .stream()
            .filter(serviceDependency
                -> serviceDependency.getDependencyMetadata() != null
                    && serviceDependency.getDependencyMetadata().getType()
                        == ServiceDependencyMetadata.DependencyMetadataType.KUBERNETES)
            .findAny();
    if (serviceDependencyDTO.isPresent()) {
      List<ChangeSource> changeSources = changeSourceService.getEntityByType(
          MonitoredServiceParams.builder()
              .accountIdentifier(deploymentActivity.getAccountId())
              .orgIdentifier(deploymentActivity.getOrgIdentifier())
              .projectIdentifier(deploymentActivity.getProjectIdentifier())
              .monitoredServiceIdentifier(serviceDependencyDTO.get().getMonitoredServiceIdentifier())
              .build(),
          ChangeSourceType.KUBERNETES);
      if (!changeSources.isEmpty()) {
        KubernetesDependencyMetadata serviceDependencyMetadata =
            (KubernetesDependencyMetadata) serviceDependencyDTO.get().getDependencyMetadata();
        kubernetesChangeSourceDemoDataGenerator
            .generate((KubernetesChangeSource) changeSources.iterator().next(),
                serviceDependencyMetadata.getNamespace(), serviceDependencyMetadata.getWorkload())
            .forEach(changeEvent -> changeEventService.register(changeEvent));
      }
    }
  }
}
