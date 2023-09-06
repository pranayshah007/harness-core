/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.jsonwebtoken.lang.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CV)
public class NextGenServiceImpl implements NextGenService {
  @Inject private NextGenPrivilegedClient nextGenPrivilegedClient;

  @Inject private NextGenNonPrivilegedClient nextGenNonPrivilegedClient;
  @Inject private RequestExecutor requestExecutor;

  private LoadingCache<EntityKey, EnvironmentResponseDTO> environmentCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, EnvironmentResponseDTO>() {
            @Override
            public EnvironmentResponseDTO load(EntityKey entityKey) {
              EnvironmentResponse environmentResponse =
                  requestExecutor
                      .execute(nextGenPrivilegedClient.getEnvironment(entityKey.getEntityIdentifier(),
                          entityKey.getAccountId(), entityKey.getOrgIdentifier(), entityKey.getProjectIdentifier()))
                      .getData();
              Preconditions.checkNotNull(environmentResponse, "Environment Response from Ng Manager cannot be null");
              return environmentResponse.getEnvironment();
            }
          });

  private LoadingCache<EntityKey, ServiceResponseDTO> serviceCache =
      CacheBuilder.newBuilder()
          .maximumSize(5000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, ServiceResponseDTO>() {
            @Override
            public ServiceResponseDTO load(EntityKey entityKey) {
              ServiceResponse serviceResponse =
                  requestExecutor
                      .execute(nextGenPrivilegedClient.getService(entityKey.getEntityIdentifier(),
                          entityKey.getAccountId(), entityKey.getOrgIdentifier(), entityKey.getProjectIdentifier()))
                      .getData();
              Preconditions.checkNotNull(serviceResponse, "Service Response from Ng Manager cannot be null");
              return serviceResponse.getService();
            }
          });

  private LoadingCache<EntityKey, ProjectDTO> projectCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, ProjectDTO>() {
            @Override
            public ProjectDTO load(EntityKey entityKey) {
              return requestExecutor
                  .execute(nextGenPrivilegedClient.getProject(
                      entityKey.getProjectIdentifier(), entityKey.getAccountId(), entityKey.getOrgIdentifier()))
                  .getData()
                  .getProject();
            }
          });

  private LoadingCache<EntityKey, OrganizationDTO> orgCache =
      CacheBuilder.newBuilder()
          .maximumSize(100)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, OrganizationDTO>() {
            @Override
            public OrganizationDTO load(EntityKey entityKey) {
              return requestExecutor
                  .execute(
                      nextGenPrivilegedClient.getOrganization(entityKey.getOrgIdentifier(), entityKey.getAccountId()))
                  .getData()
                  .getOrganization();
            }
          });

  @Override
  public ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    return requestExecutor.execute(nextGenPrivilegedClient.create(connectorRequestDTO, accountIdentifier)).getData();
  }

  @Override
  public Optional<ConnectorInfoDTO> get(
      String accountIdentifier, String connectorIdentifier, String orgIdentifier, String projectIdentifier) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    ConnectorResponseDTO connectorResponse =
        requestExecutor
            .execute(nextGenPrivilegedClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
                identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()))
            .getData();
    return connectorResponse != null ? Optional.of(connectorResponse.getConnector()) : Optional.empty();
  }

  @Override
  public EnvironmentResponseDTO getEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    try {
      return environmentCache.get(EntityKey.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .entityIdentifier(environmentIdentifier)
                                      .build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public ServiceResponseDTO getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    try {
      return serviceCache.get(EntityKey.builder()
                                  .accountId(accountId)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .entityIdentifier(serviceIdentifier)
                                  .build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public List<EnvironmentResponse> listEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> environmentIdentifiers) {
    PageResponse<EnvironmentResponse> environmentsResponse =
        requestExecutor
            .execute(nextGenPrivilegedClient.listEnvironment(
                accountId, orgIdentifier, projectIdentifier, environmentIdentifiers))
            .getData();

    return environmentsResponse.getContent();
  }

  @Override
  public List<ServiceResponse> listService(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    PageResponse<ServiceResponse> servicesResponse = requestExecutor
                                                         .execute(nextGenPrivilegedClient.listService(accountId,
                                                             orgIdentifier, projectIdentifier, serviceIdentifiers))
                                                         .getData();

    return servicesResponse.getContent();
  }

  @Override
  public List<ConnectorResponseDTO> listConnector(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdListWithScope) {
    List<String> fnqIdentifierList = new ArrayList<>();
    for (String identifierWithScope : connectorIdListWithScope) {
      fnqIdentifierList.add(ScopedInformation.getFNQFromScopedIdentifier(
          accountId, orgIdentifier, projectIdentifier, identifierWithScope));
    }
    List<ConnectorResponseDTO> connectorResponse =
        requestExecutor.execute(nextGenPrivilegedClient.listConnector(accountId, fnqIdentifierList)).getData();

    return connectorResponse;
  }

  @Override
  public ProjectDTO getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier, false);
  }

  @Override
  public ProjectDTO getCachedProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier, true);
  }

  private ProjectDTO getProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, boolean isCached) {
    if (isCached) {
      try {
        return projectCache.get(EntityKey.builder()
                                    .accountId(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build());
      } catch (ExecutionException ex) {
        throw new RuntimeException(ex);
      }
    }

    return requestExecutor
        .execute(nextGenPrivilegedClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier))
        .getData()
        .getProject();
  }

  @Override
  public boolean isProjectDeleted(String accountId, String orgIdentifier, String projectIdentifier) {
    try {
      getProject(accountId, orgIdentifier, projectIdentifier);
    } catch (Exception ex) {
      return true;
    }
    return false;
  }

  @Override
  public OrganizationDTO getOrganization(String accountIdentifier, String orgIdentifier) {
    try {
      return orgCache.get(EntityKey.builder().accountId(accountIdentifier).orgIdentifier(orgIdentifier).build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public int getServicesCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return (int) requestExecutor
        .execute(
            nextGenPrivilegedClient.listServicesForProject(0, 1000, accountId, orgIdentifier, projectIdentifier, null))
        .getData()
        .getTotalItems();
  }

  @Override
  public int getEnvironmentCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return (int) requestExecutor
        .execute(nextGenPrivilegedClient.listEnvironmentsForProject(
            0, 1000, accountId, orgIdentifier, projectIdentifier, null, null))
        .getData()
        .getTotalItems();
  }

  @Override
  public List<StepExecutionInstanceInfo> getCDStageInstanceInfo(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineExecutionId, String stageExecutionId) {
    return requestExecutor
        .execute(nextGenPrivilegedClient.getCDStageInstanceInfo(
            accountId, orgIdentifier, projectIdentifier, pipelineExecutionId, stageExecutionId))
        .getData();
  }

  @Override
  public Map<String, String> getServiceIdNameMap(ProjectParams projectParams, List<String> serviceIdentifiers) {
    Map<String, String> serviceIdNameMap = new HashMap<>();
    listService(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), serviceIdentifiers)
        .forEach(serviceResponse
            -> serviceIdNameMap.put(
                serviceResponse.getService().getFullyQualifiedIdentifier(), serviceResponse.getService().getName()));
    return serviceIdNameMap;
  }

  @Override
  public Map<String, String> getEnvironmentIdNameMap(ProjectParams projectParams, List<String> environmentIdentifiers) {
    Map<String, String> environmentIdNameMap = new HashMap<>();
    listEnvironment(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), environmentIdentifiers)
        .forEach(environmentResponse
            -> environmentIdNameMap.put(environmentResponse.getEnvironment().getFullyQualifiedIdentifier(),
                environmentResponse.getEnvironment().getName()));
    return environmentIdNameMap;
  }

  @SuppressWarnings("checkstyle:UnnecessaryParentheses")
  @Override
  public List<ProjectDTO> listAccessibleProjects(String accountIdentifier) {
    List<ProjectDTO> projectDTOList = new ArrayList<>();

    int page = 0;
    int pageSize = 100;
    boolean morePages = true;
    while (morePages) {
      List<ProjectDTO> projects =
          requestExecutor
              .execute(nextGenNonPrivilegedClient.listAccessibleProjects(accountIdentifier, null, page, pageSize))
              .getData()
              .getContent()
              .stream()
              .map(projectAggregateDTO -> projectAggregateDTO.getProjectResponse().getProject())
              .collect(Collectors.toList());

      projectDTOList.addAll(projects);

      page++;
      morePages = projects.size() == pageSize;
    }

    return projectDTOList;
  }

  @Override
  public void validateConnectorIdList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdListWithScope) {
    List<ConnectorResponseDTO> connectorResponseDTOList =
        listConnector(accountId, orgIdentifier, projectIdentifier, connectorIdListWithScope);
    if (connectorResponseDTOList.size() < connectorIdListWithScope.size()) {
      Set<String> connectorIdSetWithScope = new HashSet<>(connectorIdListWithScope);
      for (ConnectorResponseDTO connectorResponseDTO : connectorResponseDTOList) {
        ConnectorInfoDTO connectorInfoDTO = connectorResponseDTO.getConnector();
        String scopedConnectorId = ScopedInformation.getScopedIdentifier(accountId, connectorInfoDTO.getOrgIdentifier(),
            connectorInfoDTO.getProjectIdentifier(), connectorInfoDTO.getIdentifier());
        connectorIdSetWithScope.remove(scopedConnectorId);
      }
      if (!Collections.isEmpty(connectorIdSetWithScope)) {
        throw new InvalidArgumentsException(
            String.format("Invalid connector refs: %s", String.join(", ", connectorIdSetWithScope)));
      }
    }
  }

  @Value
  @Builder
  public static class EntityKey {
    private String accountId;
    private String orgIdentifier;
    private String projectIdentifier;
    private String entityIdentifier;
  }
}
