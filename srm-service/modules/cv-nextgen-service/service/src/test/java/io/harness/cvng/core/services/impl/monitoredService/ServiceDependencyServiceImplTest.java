/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.BuilderFactory.Context;
import io.harness.cvng.core.beans.dependency.KubernetesDependencyMetadata;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.entities.ServiceDependency;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceDependencyServiceImplTest extends CvNextGenTestBase {
  @Inject private ServiceDependencyService serviceDependencyService;

  private BuilderFactory builderFactory;
  private Context context;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    context = builderFactory.getContext();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateDependencies_empty() {
    createOrDeleteFromContext(context, new HashSet<>());
    Set<ServiceDependencyDTO> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateDependencies_create() {
    Set<ServiceDependencyDTO> serviceDependencyDTOS =
        Sets.newHashSet(ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    createOrDeleteFromContext(context, serviceDependencyDTOS);
    Set<ServiceDependencyDTO> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(serviceDependencyDTOS);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateDependencies_createWithMetadata() {
    Set<ServiceDependencyDTO> serviceDependencyDTOS =
        Sets.newHashSet(ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceDependencyDTO.builder()
                .monitoredServiceIdentifier(randomAlphanumeric(20))
                .dependencyMetadata(
                    KubernetesDependencyMetadata.builder().namespace("namespce").workload("workload").build())
                .build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    createOrDeleteFromContext(context, serviceDependencyDTOS);
    Set<ServiceDependencyDTO> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(serviceDependencyDTOS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateDependencies_existingDependencies() {
    List<ServiceDependencyDTO> serviceDependencyDTOS = generateRandomRefs(4);
    createOrDeleteFromContext(context, new HashSet<>(serviceDependencyDTOS));
    Set<ServiceDependencyDTO> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(new HashSet<>(serviceDependencyDTOS));

    serviceDependencyDTOS =
        Lists.newArrayList(ServiceDependencyDTO.builder()
                               .monitoredServiceIdentifier(serviceDependencyDTOS.get(0).getMonitoredServiceIdentifier())
                               .build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build(),
            ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    createOrDeleteFromContext(context, new HashSet<>(serviceDependencyDTOS));
    Set<ServiceDependencyDTO> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>(serviceDependencyDTOS));
    assertThat(updatedRefs).isNotEqualTo(newRefs);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testDeleteDependenciesForService() {
    Set<ServiceDependencyDTO> serviceDependencyDTOS = new HashSet<>(generateRandomRefs(3));
    createOrDeleteFromContext(context, serviceDependencyDTOS);
    Set<ServiceDependencyDTO> newRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(newRefs).isEqualTo(serviceDependencyDTOS);

    serviceDependencyService.deleteDependenciesForService(context.getProjectParams(), context.getServiceIdentifier());
    Set<ServiceDependencyDTO> updatedRefs = serviceDependencyService.getDependentServicesForMonitoredService(
        context.getProjectParams(), context.getServiceIdentifier());
    assertThat(updatedRefs).isEqualTo(new HashSet<>());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetServiceDependencies() {
    Set<ServiceDependencyDTO> serviceDependencyDTOS = new HashSet<>(generateRandomRefs(3));
    createOrDeleteFromContext(context, serviceDependencyDTOS);

    List<ServiceDependency> serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);

    serviceDependencies = serviceDependencyService.getServiceDependencies(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(serviceDependencies.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceToDependentServicesMap() {
    Set<ServiceDependencyDTO> serviceDependencyDTOS = new HashSet<>(generateRandomRefs(3));
    createOrDeleteFromContext(context, serviceDependencyDTOS);
    Map<String, List<String>> monitoredServiceToDependentServicesMap =
        serviceDependencyService.getMonitoredServiceToDependentServicesMap(
            context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(monitoredServiceToDependentServicesMap.get(context.getServiceIdentifier()).size()).isEqualTo(3);

    createOrDeleteFromContext(context, Sets.newHashSet());
    monitoredServiceToDependentServicesMap = serviceDependencyService.getMonitoredServiceToDependentServicesMap(
        context.getProjectParams(), Lists.newArrayList(context.getServiceIdentifier()));
    assertThat(monitoredServiceToDependentServicesMap.get(context.getServiceIdentifier()).size()).isEqualTo(0);
  }

  private void createOrDeleteFromContext(Context context, Set<ServiceDependencyDTO> serviceDependencyDTOS) {
    serviceDependencyService.updateDependencies(
        context.getProjectParams(), context.getServiceIdentifier(), serviceDependencyDTOS);
  }

  private List<ServiceDependencyDTO> generateRandomRefs(int num) {
    List<ServiceDependencyDTO> random = new ArrayList<>();
    for (int i = 0; i < num; i++) {
      random.add(ServiceDependencyDTO.builder().monitoredServiceIdentifier(randomAlphanumeric(20)).build());
    }
    return random;
  }
}
