/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.KubernetesClusterActivity;
import io.harness.cvng.activity.entities.KubernetesClusterActivity.RelatedAppMonitoredService;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.cvng.core.beans.dependency.KubernetesDependencyMetadata;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KubernetesClusterActivityUpdateHandlerTest extends CvNextGenTestBase {
  @Inject KubernetesClusterActivityUpdateHandler kubernetesClusterActivityUpdateHandler;
  @Inject MonitoredServiceService monitoredServiceService;
  BuilderFactory builderFactory;
  String serviceIdentifier;
  String envIdentifier;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreate() {
    KubernetesClusterActivity clusterActivity = builderFactory.getKubernetesClusterActivityBuilder().build();
    assertThat(clusterActivity.getRelatedAppServices()).isNullOrEmpty();

    MonitoredServiceDTO infraService = builderFactory.monitoredServiceDTOBuilder()
                                           .type(MonitoredServiceType.INFRASTRUCTURE)
                                           .serviceRef(builderFactory.getContext().getServiceIdentifier() + "infra")
                                           .environmentRef(builderFactory.getContext().getEnvIdentifier())
                                           .build();
    infraService.getSources().setHealthSources(null);
    infraService.getSources().setChangeSources(
        Sets.newHashSet(builderFactory.getKubernetesChangeSourceDTOBuilder().build()));

    monitoredServiceService.create(clusterActivity.getAccountId(), infraService);

    MonitoredServiceDTO appService =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier(generateUuid())
            .serviceRef(serviceIdentifier)
            .environmentRef(envIdentifier)
            .dependencies(Sets.newHashSet(
                ServiceDependencyDTO.builder()
                    .monitoredServiceIdentifier(
                        builderFactory.getContext().getMonitoredServiceParams().getMonitoredServiceIdentifier())
                    .dependencyMetadata(KubernetesDependencyMetadata.builder()
                                            .namespace(clusterActivity.getNamespace())
                                            .workload(clusterActivity.getWorkload())
                                            .build())
                    .build()))
            .build();
    appService.getSources().setHealthSources(null);
    appService.getSources().setChangeSources(
        Sets.newHashSet(builderFactory.getHarnessCDCurrentGenChangeSourceDTOBuilder().build()));
    monitoredServiceService.create(clusterActivity.getAccountId(), appService);

    kubernetesClusterActivityUpdateHandler.handleCreate(clusterActivity);
    assertThat(clusterActivity.getRelatedAppServices()).isNotEmpty();
    assertThat(clusterActivity.getRelatedAppServices().size()).isEqualTo(1);
    RelatedAppMonitoredService serviceEnvironment = clusterActivity.getRelatedAppServices().get(0);

    assertThat(serviceEnvironment.getMonitoredServiceIdentifier()).isEqualTo(appService.getIdentifier());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreate_noDependency() {
    KubernetesClusterActivity clusterActivity = builderFactory.getKubernetesClusterActivityBuilder().build();
    assertThat(clusterActivity.getRelatedAppServices()).isNullOrEmpty();

    MonitoredServiceDTO infraService = builderFactory.monitoredServiceDTOBuilder()
                                           .type(MonitoredServiceType.INFRASTRUCTURE)
                                           .serviceRef(builderFactory.getContext().getServiceIdentifier() + "infra")
                                           .environmentRef(builderFactory.getContext().getEnvIdentifier())
                                           .build();
    infraService.getSources().setHealthSources(null);
    infraService.getSources().setChangeSources(
        Sets.newHashSet(builderFactory.getKubernetesChangeSourceDTOBuilder().build()));

    monitoredServiceService.create(clusterActivity.getAccountId(), infraService);

    MonitoredServiceDTO appService = builderFactory.monitoredServiceDTOBuilder()
                                         .identifier(generateUuid())
                                         .serviceRef(serviceIdentifier)
                                         .environmentRef(envIdentifier)
                                         .build();
    appService.getSources().setHealthSources(null);
    appService.getSources().setChangeSources(null);
    monitoredServiceService.create(clusterActivity.getAccountId(), appService);

    kubernetesClusterActivityUpdateHandler.handleCreate(clusterActivity);
    assertThat(clusterActivity.getRelatedAppServices()).isNullOrEmpty();
  }
}
