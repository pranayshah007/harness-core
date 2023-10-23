/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.autodiscovery.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryAsyncResponseDTO.AsyncStatus;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryRequestDTO;
import io.harness.cvng.autodiscovery.beans.AutoDiscoveryResponseDTO;
import io.harness.cvng.autodiscovery.entities.AsyncAutoDiscoveryReImport;
import io.harness.cvng.autodiscovery.entities.AsyncAutoDiscoveryReImport.AsyncAutoDiscoveryReImportKeys;
import io.harness.cvng.autodiscovery.entities.AutoDiscoveryAgent;
import io.harness.cvng.autodiscovery.entities.AutoDiscoveryAgent.AutoDiscoveryAgentKeys;
import io.harness.cvng.autodiscovery.services.AutoDiscoveryClient;
import io.harness.cvng.autodiscovery.services.AutoDiscoveryService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.persistence.HPersistence;
import io.harness.servicediscovery.client.beans.DiscoveredServiceConnectionResponse;
import io.harness.servicediscovery.client.beans.DiscoveredServiceResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;

public class AutoDiscoveryServiceImpl implements AutoDiscoveryService {
  @Inject AutoDiscoveryClient autoDiscoveryClient;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject ServiceDependencyService serviceDependencyService;
  @Inject HPersistence hPersistence;
  private static final ExecutorService service = Executors.newFixedThreadPool(8);
  @Inject Clock clock;
  private static final long oneMinuteInMills = 60 * 1000L;

  @Override
  public AutoDiscoveryResponseDTO create(ProjectParams projectParams, AutoDiscoveryRequestDTO autoDiscoveryRequestDTO)
      throws IOException {
    AutoDiscoveryResponseDTO autoDiscoveryResponseDTO = importServiceDependencies(projectParams,
        autoDiscoveryRequestDTO.getAgentIdentifier(), autoDiscoveryRequestDTO.getAutoCreateMonitoredService());
    updateAutoDiscoveryAgent(projectParams, autoDiscoveryRequestDTO.getAgentIdentifier());
    return autoDiscoveryResponseDTO;
  }

  @Override
  public AutoDiscoveryAsyncResponseDTO reImport(ProjectParams projectParams) {
    List<AsyncAutoDiscoveryReImport> existingAutoDiscoveryReImport =
        hPersistence.createQuery(AsyncAutoDiscoveryReImport.class)
            .filter(AsyncAutoDiscoveryReImportKeys.accountId, projectParams.getAccountIdentifier())
            .filter(AsyncAutoDiscoveryReImportKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(AsyncAutoDiscoveryReImportKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(AsyncAutoDiscoveryReImportKeys.status, AsyncStatus.RUNNING)
            .field(AsyncAutoDiscoveryReImportKeys.createdAt)
            .greaterThanOrEq(clock.millis() - oneMinuteInMills)
            .asList();
    if (!CollectionUtils.isEmpty(existingAutoDiscoveryReImport)) {
      throw new IllegalStateException(
          String.format("Reimport already in progress for accountId %s and orgIdentifier %s and projectIdentifier %s",
              projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
              projectParams.getProjectIdentifier()));
    }
    String correlationId = generateUuid();
    AsyncAutoDiscoveryReImport autoDiscoveryReImport =
        AsyncAutoDiscoveryReImport.builder()
            .autoDiscoveryResponse(AutoDiscoveryResponseDTO.builder()
                                       .monitoredServicesCreated(new ArrayList<>())
                                       .serviceDependenciesImported(0L)
                                       .build())
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .correlationId(correlationId)
            .status(AsyncStatus.RUNNING)
            .build();
    hPersistence.save(autoDiscoveryReImport);
    service.submit(() -> executeReImportInParallel(projectParams, autoDiscoveryReImport));
    return AutoDiscoveryAsyncResponseDTO.builder()
        .correlationId(correlationId)
        .status(AsyncStatus.RUNNING)
        .serviceDependenciesImported(0L)
        .build();
  }

  private void executeReImportInParallel(
      ProjectParams projectParams, AsyncAutoDiscoveryReImport autoDiscoveryReImport) {
    List<AutoDiscoveryAgent> autoDiscoveryAgents = getAutoDiscoveryAgents(projectParams);
    long serviceDependenciesImported = 0L;
    for (AutoDiscoveryAgent autoDiscoveryAgent : autoDiscoveryAgents) {
      AutoDiscoveryResponseDTO autoDiscoveryResponseDTO =
          importServiceDependencies(projectParams, autoDiscoveryAgent.getAgentIdentifier(), false);
      serviceDependenciesImported += autoDiscoveryResponseDTO.getServiceDependenciesImported();
    }
    autoDiscoveryReImport.getAutoDiscoveryResponse().setServiceDependenciesImported(serviceDependenciesImported);
    autoDiscoveryReImport.setStatus(AsyncStatus.COMPLETED);
  }

  @Override
  public AutoDiscoveryAsyncResponseDTO status(String correlationId) {
    AsyncAutoDiscoveryReImport autoDiscoveryReImport =
        hPersistence.createQuery(AsyncAutoDiscoveryReImport.class)
            .filter(AsyncAutoDiscoveryReImportKeys.correlationId, correlationId)
            .get();
    if (Objects.isNull(autoDiscoveryReImport)) {
      throw new IllegalStateException("No entry in the system for the given correlationId");
    }
    return AutoDiscoveryAsyncResponseDTO.builder()
        .correlationId(correlationId)
        .status(autoDiscoveryReImport.getStatus())
        .serviceDependenciesImported(autoDiscoveryReImport.getAutoDiscoveryResponse().getServiceDependenciesImported())
        .monitoredServicesCreated(autoDiscoveryReImport.getAutoDiscoveryResponse().getMonitoredServicesCreated())
        .build();
  }

  private void updateAutoDiscoveryAgent(ProjectParams projectParams, String agentIdentifier) {
    List<AutoDiscoveryAgent> autoDiscoveryAgents = getAutoDiscoveryAgents(projectParams);
    Set<String> uniqueAutoDiscoveryAgents =
        autoDiscoveryAgents.stream().map(AutoDiscoveryAgent::getAgentIdentifier).collect(Collectors.toSet());
    if (!uniqueAutoDiscoveryAgents.contains(agentIdentifier)) {
      AutoDiscoveryAgent autoDiscoveryAgentEntity = AutoDiscoveryAgent.builder()
                                                        .agentIdentifier(agentIdentifier)
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .accountId(projectParams.getAccountIdentifier())
                                                        .build();
      hPersistence.save(autoDiscoveryAgentEntity);
    }
  }

  private List<AutoDiscoveryAgent> getAutoDiscoveryAgents(@NonNull ProjectParams projectParams) {
    Query<AutoDiscoveryAgent> query =
        hPersistence.createQuery(AutoDiscoveryAgent.class)
            .filter(AutoDiscoveryAgentKeys.accountId, projectParams.getAccountIdentifier())
            .filter(AutoDiscoveryAgentKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(AutoDiscoveryAgentKeys.projectIdentifier, projectParams.getProjectIdentifier());
    return query.asList();
  }

  private AutoDiscoveryResponseDTO importServiceDependencies(
      ProjectParams projectParams, String agentIdentifier, boolean createMonitoredServices) {
    List<String> createdMonitoredServices = new ArrayList<>();
    long serviceDependenciesImported = 0;
    List<MonitoredService> existingMonitoredServices = monitoredServiceService.list(projectParams, null);
    Set<ServiceEnvironmentParams> existingMonitoredServicesIdentifiers =
        existingMonitoredServices.stream()
            .map(monitoredService
                -> ServiceEnvironmentParams.builderWithProjectParams(projectParams)
                       .serviceIdentifier(monitoredService.getServiceIdentifier())
                       .environmentIdentifier(monitoredService.getEnvironmentIdentifier())
                       .build())
            .collect(Collectors.toSet());
    List<DiscoveredServiceResponse> discoveredServiceResponses =
        autoDiscoveryClient.getDiscoveredServices(projectParams, agentIdentifier);
    Set<ServiceEnvironmentParams> discoveredServiceIdentifiers =
        discoveredServiceResponses.stream()
            .filter(this::isHarnessServiceMapped)
            .map(discoveredService
                -> ServiceEnvironmentParams.builderWithProjectParams(projectParams)
                       .serviceIdentifier(
                           discoveredService.getSpec().getHarnessServiceIdentity().getFullyQualifiedIdentifier())
                       .environmentIdentifier(
                           discoveredService.getSpec().getHarnessEnvironmentIdentity().getFullyQualifiedIdentifier())
                       .build())
            .collect(Collectors.toSet());
    Map<String, ServiceEnvironmentParams> serviceIdToServiceEnvironmentParams = new HashMap<>();
    for (DiscoveredServiceResponse discoveredServiceResponse : discoveredServiceResponses) {
      if (isHarnessServiceMapped(discoveredServiceResponse)) {
        serviceIdToServiceEnvironmentParams.put(discoveredServiceResponse.getId(),
            ServiceEnvironmentParams.builderWithProjectParams(projectParams)
                .serviceIdentifier(
                    discoveredServiceResponse.getSpec().getHarnessServiceIdentity().getFullyQualifiedIdentifier())
                .environmentIdentifier(
                    discoveredServiceResponse.getSpec().getHarnessEnvironmentIdentity().getFullyQualifiedIdentifier())
                .build());
      }
    }
    Set<ServiceEnvironmentParams> servicesNotPresentInTheSystem =
        Sets.difference(discoveredServiceIdentifiers, existingMonitoredServicesIdentifiers);
    if (createMonitoredServices) {
      for (ServiceEnvironmentParams serviceNotPresentInTheSystem : servicesNotPresentInTheSystem) {
        MonitoredServiceDTO createDefault =
            monitoredServiceService
                .createDefault(projectParams, serviceNotPresentInTheSystem.getServiceIdentifier(),
                    serviceNotPresentInTheSystem.getEnvironmentIdentifier())
                .getMonitoredServiceDTO();
        existingMonitoredServicesIdentifiers.add(ServiceEnvironmentParams.builderWithProjectParams(projectParams)
                                                     .serviceIdentifier(createDefault.getServiceRef())
                                                     .environmentIdentifier(createDefault.getEnvironmentRef())
                                                     .build());
        createdMonitoredServices.add(createDefault.getName());
      }
    }
    List<DiscoveredServiceConnectionResponse> discoveredServiceConnectionResponses =
        autoDiscoveryClient.getDiscoveredServiceConnections(projectParams, agentIdentifier);
    for (DiscoveredServiceConnectionResponse discoveredServiceConnection : discoveredServiceConnectionResponses) {
      if (serviceIdToServiceEnvironmentParams.containsKey(discoveredServiceConnection.getSourceID())
          && existingMonitoredServicesIdentifiers.contains(
              serviceIdToServiceEnvironmentParams.get(discoveredServiceConnection.getSourceID()))
          && serviceIdToServiceEnvironmentParams.containsKey(discoveredServiceConnection.getDestinationID())
          && existingMonitoredServicesIdentifiers.contains(
              serviceIdToServiceEnvironmentParams.get(discoveredServiceConnection.getDestinationID()))) {
        ServiceEnvironmentParams sourceService =
            serviceIdToServiceEnvironmentParams.get(discoveredServiceConnection.getSourceID());
        ServiceEnvironmentParams destinationService =
            serviceIdToServiceEnvironmentParams.get(discoveredServiceConnection.getDestinationID());
        serviceDependencyService.updateDependencies(projectParams,
            MonitoredService.getIdentifier(
                sourceService.getServiceIdentifier(), sourceService.getEnvironmentIdentifier()),
            Collections.singleton(
                MonitoredServiceDTO.ServiceDependencyDTO.builder()
                    .monitoredServiceIdentifier(MonitoredService.getIdentifier(
                        destinationService.getServiceIdentifier(), destinationService.getEnvironmentIdentifier()))
                    .build()));
        serviceDependenciesImported++;
      }
    }
    return AutoDiscoveryResponseDTO.builder()
        .monitoredServicesCreated(createdMonitoredServices)
        .serviceDependenciesImported(serviceDependenciesImported)
        .build();
  }

  private boolean isHarnessServiceMapped(DiscoveredServiceResponse discoveredService) {
    return Objects.nonNull(discoveredService.getSpec())
        && Objects.nonNull(discoveredService.getSpec().getHarnessServiceIdentity())
        && Objects.nonNull(discoveredService.getSpec().getHarnessEnvironmentIdentity());
  }
}
