/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.utils.PageTestUtils.getPage;

import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class OrganizationResourceTest extends CategoryTest {
  private OrganizationService organizationService;
  private OrganizationResource organizationResource;

  String accountIdentifier = randomAlphabetic(10);
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    organizationService = mock(OrganizationService.class);
    organizationResource = new OrganizationResource(organizationService);
  }

  private OrganizationDTO getOrganizationDTO(String identifier, String name) {
    return OrganizationDTO.builder().identifier(identifier).name(name).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    OrganizationRequest organizationRequestWrapper =
        OrganizationRequest.builder().organization(organizationDTO).build();
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion((long) 0);

    when(organizationService.create(accountIdentifier, organizationDTO)).thenReturn(organization);

    ResponseDTO<OrganizationResponse> responseDTO =
        organizationResource.create(accountIdentifier, organizationRequestWrapper);

    assertEquals(organization.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(identifier, responseDTO.getData().getOrganization().getIdentifier());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion((long) 0);

    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.of(organization));

    ResponseDTO<OrganizationResponse> responseDTO = organizationResource.get(identifier, accountIdentifier);

    assertEquals(organization.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(identifier, responseDTO.getData().getOrganization().getIdentifier());

    when(organizationService.get(accountIdentifier, identifier)).thenReturn(Optional.empty());

    boolean exceptionThrown = false;
    try {
      organizationResource.get(identifier, accountIdentifier);
    } catch (NotFoundException exception) {
      exceptionThrown = true;
    }

    assertTrue(exceptionThrown);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    OrganizationRequest organizationRequestWrapper =
        OrganizationRequest.builder().organization(organizationDTO).build();
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion((long) 0);
    ArgumentCaptor<OrganizationFilterDTO> argumentCaptor = ArgumentCaptor.forClass(OrganizationFilterDTO.class);

    when(organizationService.listPermittedOrgs(eq(accountIdentifier), any(), any()))
        .thenReturn(getPage(singletonList(organization), 1));

    ResponseDTO<PageResponse<OrganizationResponse>> response =
        organizationResource.list(accountIdentifier, Collections.EMPTY_LIST, searchTerm, pageRequest);

    verify(organizationService, times(1)).listPermittedOrgs(eq(accountIdentifier), any(), argumentCaptor.capture());
    OrganizationFilterDTO organizationFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, organizationFilterDTO.getSearchTerm());
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(identifier, response.getData().getContent().get(0).getOrganization().getIdentifier());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testAllOrganizationsList() {
    String searchTerm = randomAlphabetic(10);
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion((long) 0);
    ArgumentCaptor<OrganizationFilterDTO> orgArgumentCaptor = ArgumentCaptor.forClass(OrganizationFilterDTO.class);
    ArgumentCaptor<Pageable> pageableArgumentCaptor = ArgumentCaptor.forClass(Pageable.class);

    when(organizationService.listPermittedOrgs(eq(accountIdentifier), any(), any()))
        .thenReturn(getPage(singletonList(organization), 1));

    ResponseDTO<PageResponse<OrganizationResponse>> response =
        organizationResource.listAllOrganizations(accountIdentifier, searchTerm, Collections.EMPTY_LIST);

    verify(organizationService, times(1))
        .listPermittedOrgs(eq(accountIdentifier), pageableArgumentCaptor.capture(), orgArgumentCaptor.capture());
    OrganizationFilterDTO organizationFilterDTO = orgArgumentCaptor.getValue();
    Pageable pageable = pageableArgumentCaptor.getValue();

    assertEquals(searchTerm, organizationFilterDTO.getSearchTerm());
    assertEquals(NGCommonEntityConstants.MAX_PAGE_SIZE.intValue(), pageable.getPageSize());
    assertEquals(0, pageable.getPageNumber());
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(identifier, response.getData().getContent().get(0).getOrganization().getIdentifier());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    String ifMatch = "0";
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    OrganizationRequest organizationRequestWrapper =
        OrganizationRequest.builder().organization(organizationDTO).build();
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(parseLong(ifMatch) + 1);

    when(organizationService.update(accountIdentifier, identifier, organizationDTO)).thenReturn(organization);

    ResponseDTO<OrganizationResponse> response =
        organizationResource.update(ifMatch, identifier, accountIdentifier, organizationRequestWrapper);

    assertEquals("1", response.getEntityTag());
    assertEquals(identifier, response.getData().getOrganization().getIdentifier());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String ifMatch = "0";

    when(organizationService.delete(accountIdentifier, identifier, Long.valueOf(ifMatch))).thenReturn(true);

    ResponseDTO<Boolean> response = organizationResource.delete(ifMatch, identifier, accountIdentifier);

    assertNull(response.getEntityTag());
    assertTrue(response.getData());
  }
}
