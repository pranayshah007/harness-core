/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.ConnectorService;
import io.harness.ng.core.api.DelegateDetailsService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.OrganizationAggregateDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class AggregateOrganizationServiceImplTest extends CategoryTest {
  private ProjectService projectService;
  private OrganizationService organizationService;
  private NgUserService ngUserService;
  private AggregateOrganizationServiceImpl aggregateOrganizationService;

  @Before
  public void setup() {
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    ngUserService = mock(NgUserService.class);
    final NGSecretServiceV2 secretServiceV2 = mock(NGSecretServiceV2.class);
    final ConnectorService defaultConnectorService = mock(ConnectorService.class);
    final DelegateDetailsService delegateDetailsService = mock(DelegateDetailsService.class);
    final ExecutorService executorService = Executors.newFixedThreadPool(1);
    aggregateOrganizationService = spy(new AggregateOrganizationServiceImpl(organizationService, projectService,
        secretServiceV2, defaultConnectorService, delegateDetailsService, ngUserService, executorService));
  }

  private Organization getOrganization(String accountIdentifier, String orgIdentifier) {
    return Organization.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(orgIdentifier)
        .name(randomAlphabetic(10))
        .build();
  }

  private void setupNgUserService() {
    List<UserMetadataDTO> userMetadataDTOs = new ArrayList<>();
    IntStream.range(0, 8).forEach(
        e -> userMetadataDTOs.add(UserMetadataDTO.builder().uuid(randomAlphabetic(10)).build()));
    when(ngUserService.listUsers(any())).thenReturn(userMetadataDTOs);
    when(ngUserService.listUsersHavingRole(any(), any())).thenReturn(new ArrayList<>(userMetadataDTOs.subList(0, 4)));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);

    Organization organization = getOrganization(accountIdentifier, orgIdentifier);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(organization));

    Map<String, Integer> projectsCount = singletonMap(organization.getIdentifier(), 3);
    when(projectService.getProjectsCountPerOrganization(eq(accountIdentifier), any())).thenReturn(projectsCount);

    setupNgUserService();

    OrganizationAggregateDTO organizationAggregateDTO =
        aggregateOrganizationService.getOrganizationAggregateDTO(accountIdentifier, orgIdentifier);

    // organization
    assertEquals(orgIdentifier, organizationAggregateDTO.getOrganizationResponse().getOrganization().getIdentifier());
    assertEquals(
        organization.getName(), organizationAggregateDTO.getOrganizationResponse().getOrganization().getName());

    // projects
    assertEquals(3, organizationAggregateDTO.getProjectsCount());

    // admins and collaborators
    assertEquals(4, organizationAggregateDTO.getAdmins().size());
    assertEquals(4, organizationAggregateDTO.getCollaborators().size());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet_OtherFieldsMissing() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);

    Organization organization = getOrganization(accountIdentifier, orgIdentifier);
    when(organizationService.get(accountIdentifier, orgIdentifier)).thenReturn(Optional.of(organization));

    when(projectService.getProjectsCountPerOrganization(eq(accountIdentifier), any())).thenReturn(emptyMap());

    when(ngUserService.listUsers(any())).thenReturn(emptyList());

    when(ngUserService.listCurrentGenUsers(any(), any())).thenReturn(emptyList());

    OrganizationAggregateDTO organizationAggregateDTO =
        aggregateOrganizationService.getOrganizationAggregateDTO(accountIdentifier, orgIdentifier);

    // organization
    assertEquals(orgIdentifier, organizationAggregateDTO.getOrganizationResponse().getOrganization().getIdentifier());
    assertEquals(
        organization.getName(), organizationAggregateDTO.getOrganizationResponse().getOrganization().getName());

    // projects
    assertEquals(0, organizationAggregateDTO.getProjectsCount());

    // admins and collaborators
    assertEquals(0, organizationAggregateDTO.getAdmins().size());
    assertEquals(0, organizationAggregateDTO.getCollaborators().size());
  }

  private List<Organization> getOrganizations(String accountIdentifier, int count) {
    List<Organization> organizations = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      organizations.add(getOrganization(accountIdentifier, randomAlphabetic(10)));
    }
    return organizations;
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String accountIdentifier = randomAlphabetic(10);

    List<Organization> organizations = getOrganizations(accountIdentifier, 3);
    when(organizationService.listPermittedOrgs(accountIdentifier, Pageable.unpaged(), null))
        .thenReturn(getPage(organizations, 3));

    Map<String, Integer> projectsCount = new HashMap<>();
    organizations.forEach(organization -> projectsCount.put(organization.getIdentifier(), 3));
    when(projectService.getProjectsCountPerOrganization(eq(accountIdentifier), any())).thenReturn(projectsCount);

    setupNgUserService();

    Page<OrganizationAggregateDTO> organizationAggregateDTOs =
        aggregateOrganizationService.listOrganizationAggregateDTO(accountIdentifier, Pageable.unpaged(), null);

    // organizations
    assertEquals(3, organizationAggregateDTOs.getContent().size());

    // projects
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(3, organizationAggregateDTO.getProjectsCount()));

    // admins and collaborators
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(4, organizationAggregateDTO.getAdmins().size()));
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(4, organizationAggregateDTO.getCollaborators().size()));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList_OtherFieldsMissing() {
    String accountIdentifier = randomAlphabetic(10);

    List<Organization> organizations = getOrganizations(accountIdentifier, 3);
    when(organizationService.listPermittedOrgs(accountIdentifier, Pageable.unpaged(), null))
        .thenReturn(getPage(organizations, 3));

    when(projectService.getProjectsCountPerOrganization(eq(accountIdentifier), any())).thenReturn(emptyMap());

    when(ngUserService.listUsers(any())).thenReturn(emptyList());

    when(ngUserService.listCurrentGenUsers(any(), any())).thenReturn(emptyList());

    Page<OrganizationAggregateDTO> organizationAggregateDTOs =
        aggregateOrganizationService.listOrganizationAggregateDTO(accountIdentifier, Pageable.unpaged(), null);

    // organizations
    assertEquals(3, organizationAggregateDTOs.getContent().size());

    // projects
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(0, organizationAggregateDTO.getProjectsCount()));

    // admins and collaborators
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(0, organizationAggregateDTO.getAdmins().size()));
    organizationAggregateDTOs.getContent().forEach(
        organizationAggregateDTO -> assertEquals(0, organizationAggregateDTO.getCollaborators().size()));
  }
}
