/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.model.OrganizationResponse;
import io.harness.spec.server.ng.model.UpdateOrganizationRequest;

import java.util.List;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class OrganizationApiImplTest extends CategoryTest {
  private OrganizationService organizationService;
  private OrganizationApiImpl organizationApi;
  private OrganizationApiUtils organizationApiUtils;

  private Validator validator;

  String account = randomAlphabetic(10);
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    organizationService = mock(OrganizationService.class);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();

    organizationApiUtils = new OrganizationApiUtils(validator);
    organizationApi = new OrganizationApiImpl(organizationService, organizationApiUtils);
  }

  private OrganizationDTO getOrganizationDTO(String identifier, String name) {
    return OrganizationDTO.builder().identifier(identifier).name(name).build();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreate() {
    CreateOrganizationRequest organizationRequest = new CreateOrganizationRequest();
    io.harness.spec.server.ng.model.Organization org = new io.harness.spec.server.ng.model.Organization();
    org.setSlug(slug);
    org.setName(name);
    organizationRequest.setOrg(org);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(organizationRequest);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.create(account, organizationDTO)).thenReturn(organization);

    Response response = organizationApi.createOrganization(organizationRequest, account);
    assertEquals(201, response.getStatus());

    assertEquals(organization.getVersion().toString(), response.getEntityTag().getValue());
    OrganizationResponse entity = (OrganizationResponse) response.getEntity();
    assertEquals(slug, entity.getOrg().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGet() {
    OrganizationDTO organizationDTO = getOrganizationDTO(slug, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.get(account, slug)).thenReturn(Optional.of(organization));

    Response response = organizationApi.getOrganization(slug, account);

    assertEquals(organization.getVersion().toString(), response.getEntityTag().getValue());
    OrganizationResponse entity = (OrganizationResponse) response.getEntity();
    assertEquals(slug, entity.getOrg().getSlug());

    when(organizationService.get(account, slug)).thenReturn(Optional.empty());

    boolean exceptionThrown = false;
    try {
      organizationApi.getOrganization(slug, account);
    } catch (NotFoundException exception) {
      exceptionThrown = true;
    }

    assertTrue(exceptionThrown);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);

    CreateOrganizationRequest organizationRequest = new CreateOrganizationRequest();
    io.harness.spec.server.ng.model.Organization org = new io.harness.spec.server.ng.model.Organization();
    organizationRequest.setOrg(org);
    org.setSlug(slug);
    org.setName(name);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(organizationRequest);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    ArgumentCaptor<OrganizationFilterDTO> argumentCaptor = ArgumentCaptor.forClass(OrganizationFilterDTO.class);

    when(organizationService.listPermittedOrgs(eq(account), any(), any()))
        .thenReturn(getPage(singletonList(organization), 1));

    Response response = organizationApi.getOrganizations(account, EMPTY_LIST, searchTerm, 0, 10);

    verify(organizationService, times(1)).listPermittedOrgs(eq(account), any(), argumentCaptor.capture());
    OrganizationFilterDTO organizationFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, organizationFilterDTO.getSearchTerm());
    assertEquals(1, response.getLinks().size());
    assertNotNull(response.getLink(SELF_REL));
    List<OrganizationResponse> entity = (List<OrganizationResponse>) response.getEntity();
    assertEquals(slug, entity.get(0).getOrg().getSlug());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdate() {
    UpdateOrganizationRequest request = new UpdateOrganizationRequest();
    io.harness.spec.server.ng.model.Organization org = new io.harness.spec.server.ng.model.Organization();
    org.setSlug(slug);
    org.setName("updated_name");
    request.setOrg(org);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(slug, request);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.update(account, slug, organizationDTO)).thenReturn(organization);

    Response response = organizationApi.updateOrganization(request, slug, account);

    OrganizationResponse entity = (OrganizationResponse) response.getEntity();

    assertEquals(slug, entity.getOrg().getSlug());
    assertEquals("updated_name", entity.getOrg().getName());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDelete() {
    OrganizationDTO organizationDTO = getOrganizationDTO(slug, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.get(account, slug)).thenReturn(Optional.of(organization));
    when(organizationService.delete(account, slug, null)).thenReturn(true);

    Response response = organizationApi.deleteOrganization(slug, account);

    OrganizationResponse entity = (OrganizationResponse) response.getEntity();

    assertEquals(slug, entity.getOrg().getSlug());
    assertEquals(organization.getVersion().toString(), response.getEntityTag().getValue());
  }
}
