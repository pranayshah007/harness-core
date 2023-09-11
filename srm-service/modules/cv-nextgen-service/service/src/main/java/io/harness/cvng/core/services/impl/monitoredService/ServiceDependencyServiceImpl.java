/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.ServiceDependency;
import io.harness.cvng.core.entities.ServiceDependency.Key;
import io.harness.cvng.core.entities.ServiceDependency.ServiceDependencyKeys;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.persistence.HPersistence;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
public class ServiceDependencyServiceImpl implements ServiceDependencyService {
  @Inject private HPersistence hPersistence;

  @Override
  public void updateDependencies(ProjectParams projectParams, String toMonitoredServiceIdentifier,
      Set<ServiceDependencyDTO> fromMonitoredServiceIdentifiers) {
    if (isEmpty(fromMonitoredServiceIdentifiers)) {
      deleteToDependency(projectParams, toMonitoredServiceIdentifier);
      return;
    }
    List<ServiceDependency> dependencies = new ArrayList<>();
    fromMonitoredServiceIdentifiers.forEach(fromServiceIdentifier -> {
      if (fromServiceIdentifier.getDependencyMetadata() != null) {
        fromServiceIdentifier.getDependencyMetadata().setType(fromServiceIdentifier.getType());
      }
      dependencies.add(ServiceDependency.builder()
                           .accountId(projectParams.getAccountIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .fromMonitoredServiceIdentifier(fromServiceIdentifier.getMonitoredServiceIdentifier())
                           .toMonitoredServiceIdentifier(toMonitoredServiceIdentifier)
                           .serviceDependencyMetadata(fromServiceIdentifier.getDependencyMetadata())
                           .build());
    });
    List<ServiceDependency> oldDependencies =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceDependencyKeys.toMonitoredServiceIdentifier, toMonitoredServiceIdentifier)
            .asList();
    executeDBOperations(dependencies, oldDependencies);
  }

  private void deleteToDependency(ProjectParams projectParams, String monitoredServiceIdentifier) {
    Query<ServiceDependency> toServiceQuery =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceDependencyKeys.toMonitoredServiceIdentifier, monitoredServiceIdentifier);
    hPersistence.delete(toServiceQuery);
  }

  private void executeDBOperations(List<ServiceDependency> newDependencies, List<ServiceDependency> oldDependencies) {
    Map<Key, ServiceDependency> newDependencyMap =
        newDependencies.stream().collect(Collectors.toMap(ServiceDependency::getKey, x -> x));
    Map<Key, ServiceDependency> oldDependencyMap =
        oldDependencies.stream().collect(Collectors.toMap(ServiceDependency::getKey, x -> x));

    Set<Key> deleteKeys = Sets.difference(oldDependencyMap.keySet(), newDependencyMap.keySet());
    deleteKeys.forEach(
        deleteKey -> hPersistence.delete(ServiceDependency.class, oldDependencyMap.get(deleteKey).getUuid()));

    Set<Key> createKeys = Sets.difference(newDependencyMap.keySet(), oldDependencyMap.keySet());
    List<ServiceDependency> createDependencies =
        createKeys.stream().map(newDependencyMap::get).collect(Collectors.toList());
    hPersistence.saveBatch(createDependencies);
  }

  @Override
  public void deleteDependenciesForService(ProjectParams projectParams, String monitoredServiceIdentifier) {
    deleteToDependency(projectParams, monitoredServiceIdentifier);

    Query<ServiceDependency> fromServiceQuery =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceDependencyKeys.fromMonitoredServiceIdentifier, monitoredServiceIdentifier);
    hPersistence.delete(fromServiceQuery);
  }

  @Override
  public Set<ServiceDependencyDTO> getDependentServicesForMonitoredService(
      ProjectParams projectParams, String monitoredServiceIdentifier) {
    Query<ServiceDependency> query =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceDependencyKeys.toMonitoredServiceIdentifier, monitoredServiceIdentifier);
    List<ServiceDependency> dependencies = query.asList();
    return dependencies.stream()
        .map(d
            -> ServiceDependencyDTO.builder()
                   .monitoredServiceIdentifier(d.getFromMonitoredServiceIdentifier())
                   .type(d.getServiceDependencyMetadata() == null ? null : d.getServiceDependencyMetadata().getType())
                   .dependencyMetadata(d.getServiceDependencyMetadata())
                   .build())
        .collect(Collectors.toSet());
  }

  @Override
  public Set<ServiceDependencyDTO> getDependentServicesToMonitoredService(
      @NonNull ProjectParams projectParams, String monitoredServiceIdentifier) {
    Query<ServiceDependency> query =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(ServiceDependencyKeys.fromMonitoredServiceIdentifier, monitoredServiceIdentifier);
    List<ServiceDependency> dependencies = query.asList();
    return dependencies.stream()
        .map(
            d -> ServiceDependencyDTO.builder().monitoredServiceIdentifier(d.getToMonitoredServiceIdentifier()).build())
        .collect(Collectors.toSet());
  }

  @Override
  public List<ServiceDependency> getServiceDependencies(
      @NonNull ProjectParams projectParams, @Nullable List<String> monitoredServiceIdentifiers) {
    Query<ServiceDependency> query =
        hPersistence.createQuery(ServiceDependency.class)
            .filter(ServiceDependencyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceDependencyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceDependencyKeys.projectIdentifier, projectParams.getProjectIdentifier());
    if (monitoredServiceIdentifiers != null) {
      query.field(ServiceDependencyKeys.toMonitoredServiceIdentifier).in(monitoredServiceIdentifiers);
    }

    return query.asList();
  }

  @Override
  public Map<String, List<String>> getMonitoredServiceToDependentServicesMap(
      @NonNull ProjectParams projectParams, List<String> monitoredServiceIdentifiers) {
    Map<String, List<String>> monitoredServiceToDependentServicesMap = new HashMap<>();
    monitoredServiceIdentifiers.forEach(monitoredServiceIdentifier -> {
      Set<ServiceDependencyDTO> serviceDependencyDTOS =
          getDependentServicesForMonitoredService(projectParams, monitoredServiceIdentifier);
      List<String> dependentServiceIdentifiers = new ArrayList<>();
      serviceDependencyDTOS.forEach(serviceDependencyDTO
          -> dependentServiceIdentifiers.add(serviceDependencyDTO.getMonitoredServiceIdentifier()));
      monitoredServiceToDependentServicesMap.put(monitoredServiceIdentifier, dependentServiceIdentifiers);
    });
    return monitoredServiceToDependentServicesMap;
  }
}
