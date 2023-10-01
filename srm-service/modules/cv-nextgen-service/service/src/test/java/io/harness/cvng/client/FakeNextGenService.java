/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FakeNextGenService implements NextGenService {
  @Override
  public ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public Optional<ConnectorInfoDTO> get(
      String accountIdentifier, String connectorIdentifier, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public EnvironmentResponseDTO getEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    return EnvironmentResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(environmentIdentifier)
        .name("Mocked env name")
        .build();
  }

  @Override
  public ServiceResponseDTO getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return ServiceResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(serviceIdentifier)
        .name("Mocked service name")
        .build();
  }

  @Override
  public List<ServiceResponse> listService(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    return serviceIdentifiers.stream()
        .map(serviceIdentifier
            -> ServiceResponse.builder()
                   .service(getService(accountId, projectIdentifier, projectIdentifier, serviceIdentifier))
                   .createdAt(System.currentTimeMillis())
                   .lastModifiedAt(System.currentTimeMillis())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public List<EnvironmentResponse> listEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> environmentIdentifiers) {
    return environmentIdentifiers.stream()
        .map(environmentIdentifier
            -> EnvironmentResponse.builder()
                   .environment(getEnvironment(accountId, projectIdentifier, projectIdentifier, environmentIdentifier))
                   .createdAt(System.currentTimeMillis())
                   .lastModifiedAt(System.currentTimeMillis())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public int getServicesCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return 1;
  }

  @Override
  public int getEnvironmentCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return 1;
  }

  @Override
  public List<ConnectorResponseDTO> listConnector(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdListWithScope) {
    return connectorIdListWithScope.stream()
        .map(connectorRef
            -> ConnectorResponseDTO.builder()
                   .connector(ConnectorInfoDTO.builder()
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(connectorRef)
                                  .build())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public ProjectDTO getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ProjectDTO.builder()
        .orgIdentifier(orgIdentifier)
        .identifier(projectIdentifier)
        .name("Mocked project name")
        .build();
  }

  @Override
  public ProjectDTO getCachedProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public boolean isProjectDeleted(String accountId, String orgIdentifier, String projectIdentifier) {
    return true;
  }

  @Override
  public OrganizationDTO getOrganization(String accountIdentifier, String orgIdentifier) {
    return OrganizationDTO.builder().identifier(orgIdentifier).name("Mocked org name").build();
  }

  @Override
  public Map<String, String> getServiceIdNameMap(ProjectParams projectParams, List<String> serviceIdentifiers) {
    Map<String, String> serviceIdNameMap = new HashMap<>();
    serviceIdentifiers.forEach(serviceIdentifier
        -> serviceIdNameMap.put(serviceIdentifier,
            getService(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier(), serviceIdentifier)
                .getName()));
    return serviceIdNameMap;
  }

  @Override
  public Map<String, String> getEnvironmentIdNameMap(ProjectParams projectParams, List<String> environmentIdentifiers) {
    Map<String, String> envIdNameMap = new HashMap<>();
    environmentIdentifiers.forEach(envIdentifier
        -> envIdNameMap.put(envIdentifier,
            getEnvironment(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
                projectParams.getProjectIdentifier(), envIdentifier)
                .getName()));
    return envIdNameMap;
  }

  @Override
  public void validateConnectorIdList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdListWithScope) {
    return;
  }

  @Override
  public List<ProjectDTO> listAccessibleProjects(String accountIdentifier) {
    List<ProjectDTO> projectDTOS = new ArrayList<>();
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier").identifier("project").build());
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier").identifier("project1").build());
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier").identifier("project3").build());
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier1").identifier("project").build());
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier1").identifier("project1").build());
    projectDTOS.add(ProjectDTO.builder().orgIdentifier("orgIdentifier1").identifier("project3").build());
    return projectDTOS;
  }

  @Override
  public List<StepExecutionInstanceInfo> getCDStageInstanceInfo(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineExecutionId, String stageExecutionId) {
    return new ArrayList<>();
  }
}
