/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.connector.services.ConnectorService;
import io.harness.ng.core.api.AggregateOrganizationService;
import io.harness.ng.core.api.DelegateDetailsService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.OrganizationAggregateDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.remote.OrganizationMapper;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
@Singleton
@Slf4j
public class AggregateOrganizationServiceImpl implements AggregateOrganizationService {
  private static final String ORG_ADMIN_ROLE = "_organization_admin";
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final NGSecretServiceV2 secretServiceV2;
  private final ConnectorService defaultConnectorService;
  private final DelegateDetailsService delegateDetailsService;
  private final NgUserService ngUserService;
  private final ExecutorService executorService;

  @Inject
  public AggregateOrganizationServiceImpl(final OrganizationService organizationService,
      final ProjectService projectService, final NGSecretServiceV2 secretService,
      @Named(DEFAULT_CONNECTOR_SERVICE) final ConnectorService defaultConnectorService,
      final DelegateDetailsService delegateDetailsService, final NgUserService ngUserService,
      @Named("aggregate-orgs") final ExecutorService executorService) {
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.secretServiceV2 = secretService;
    this.defaultConnectorService = defaultConnectorService;
    this.delegateDetailsService = delegateDetailsService;
    this.ngUserService = ngUserService;
    this.executorService = executorService;
  }

  @Override
  public OrganizationAggregateDTO getOrganizationAggregateDTO(String accountIdentifier, String identifier) {
    Optional<Organization> organizationOptional = organizationService.get(accountIdentifier, identifier);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", identifier));
    }
    return buildAggregateDTO(organizationOptional.get());
  }

  private OrganizationAggregateDTO buildAggregateDTO(final Organization organization) {
    final String accountId = organization.getAccountIdentifier();
    final String orgId = organization.getIdentifier();

    final int projectsCount =
        projectService.getProjectsCountPerOrganization(accountId, singletonList(orgId)).getOrDefault(orgId, 0);
    final long secretsCount = secretServiceV2.count(accountId, orgId, null);
    final long connectorsCount = defaultConnectorService.count(accountId, orgId, null);
    final long delegateGroupCount = delegateDetailsService.getDelegateGroupCount(accountId, orgId, null);

    final Scope scope = Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).build();
    final List<UserMetadataDTO> orgAdmins = ngUserService.listUsersHavingRole(scope, ORG_ADMIN_ROLE);
    final List<UserMetadataDTO> collaborators = ngUserService.listUsers(scope);
    collaborators.removeAll(orgAdmins);

    return OrganizationAggregateDTO.builder()
        .organizationResponse(OrganizationMapper.toResponseWrapper(organization))
        .projectsCount(projectsCount)
        .admins(orgAdmins)
        .secretsCount(secretsCount)
        .connectorsCount(connectorsCount)
        .delegatesCount(delegateGroupCount)
        .collaborators(collaborators)
        .build();
  }

  @Override
  public Page<OrganizationAggregateDTO> listOrganizationAggregateDTO(
      String accountIdentifier, Pageable pageable, OrganizationFilterDTO organizationFilterDTO) {
    Page<Organization> permittedOrgs =
        organizationService.listPermittedOrgs(accountIdentifier, pageable, organizationFilterDTO);
    List<Organization> organizationList = permittedOrgs.getContent();

    List<Callable<OrganizationAggregateDTO>> tasks = new ArrayList<>();
    organizationList.forEach(org -> tasks.add(() -> buildAggregateDTO(org)));

    List<Future<OrganizationAggregateDTO>> futures;
    try {
      futures = executorService.invokeAll(tasks, 10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Page.empty();
    }

    List<OrganizationAggregateDTO> aggregates = new ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
      try {
        aggregates.add(futures.get(i).get());
      } catch (CancellationException e) {
        log.error("Org aggregate task cancelled for [{}/{}]", organizationList.get(i).getAccountIdentifier(),
            organizationList.get(i).getIdentifier(), e);
        aggregates.add(OrganizationAggregateDTO.builder()
                           .organizationResponse(OrganizationMapper.toResponseWrapper(organizationList.get(i)))
                           .build());
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return Page.empty();
      } catch (ExecutionException e) {
        log.error("Error occurred while computing aggregate for org [{}/{}]",
            organizationList.get(i).getAccountIdentifier(), organizationList.get(i).getIdentifier(), e);
        aggregates.add(OrganizationAggregateDTO.builder()
                           .organizationResponse(OrganizationMapper.toResponseWrapper(organizationList.get(i)))
                           .build());
      }
    }

    return new PageImpl<>(aggregates, permittedOrgs.getPageable(), permittedOrgs.getTotalElements());
  }
}
