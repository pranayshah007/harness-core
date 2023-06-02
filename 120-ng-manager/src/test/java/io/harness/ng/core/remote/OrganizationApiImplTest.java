/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.v1.model.OrganizationResponse;
import io.harness.spec.server.ng.v1.model.UpdateOrganizationRequest;

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
  String identifier = randomAlphabetic(10);
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
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setIdentifier(identifier);
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
    assertEquals(identifier, entity.getOrg().getIdentifier());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateForDefaultOrg() {
    CreateOrganizationRequest organizationRequest = new CreateOrganizationRequest();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setIdentifier(DEFAULT_ORG_IDENTIFIER);
    org.setName(name);
    organizationRequest.setOrg(org);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(organizationRequest);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    Throwable thrown = catchThrowableOfType(
        () -> organizationApi.createOrganization(organizationRequest, account), InvalidRequestException.class);

    assertThat(thrown).hasMessage(String.format("%s cannot be used as org identifier", DEFAULT_ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGet() {
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.get(account, identifier)).thenReturn(Optional.of(organization));

    Response response = organizationApi.getOrganization(identifier, account);

    assertEquals(organization.getVersion().toString(), response.getEntityTag().getValue());
    OrganizationResponse entity = (OrganizationResponse) response.getEntity();
    assertEquals(identifier, entity.getOrg().getIdentifier());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetOrgNotFoundException() {
    Throwable thrown =
        catchThrowableOfType(() -> organizationApi.getOrganization(identifier, account), NotFoundException.class);

    assertThat(thrown).hasMessage(String.format("Organization with identifier [%s] not found", identifier));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);

    CreateOrganizationRequest organizationRequest = new CreateOrganizationRequest();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    organizationRequest.setOrg(org);
    org.setIdentifier(identifier);
    org.setName(name);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(organizationRequest);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    ArgumentCaptor<OrganizationFilterDTO> argumentCaptor = ArgumentCaptor.forClass(OrganizationFilterDTO.class);

    when(organizationService.listPermittedOrgs(eq(account), any(), any()))
        .thenReturn(getPage(singletonList(organization), 1));

    Response response = organizationApi.getOrganizations(EMPTY_LIST, searchTerm, 0, 10, account, null, null);

    verify(organizationService, times(1)).listPermittedOrgs(eq(account), any(), argumentCaptor.capture());
    OrganizationFilterDTO organizationFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, organizationFilterDTO.getSearchTerm());
    assertEquals(3, response.getHeaders().size());
    List<OrganizationResponse> entity = (List<OrganizationResponse>) response.getEntity();
    assertEquals(identifier, entity.get(0).getOrg().getIdentifier());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdate() {
    UpdateOrganizationRequest request = new UpdateOrganizationRequest();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setIdentifier(identifier);
    org.setName("updated_name");
    request.setOrg(org);

    OrganizationDTO organizationDTO = organizationApiUtils.getOrganizationDto(request);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.update(account, identifier, organizationDTO)).thenReturn(organization);

    Response response = organizationApi.updateOrganization(request, identifier, account);

    OrganizationResponse entity = (OrganizationResponse) response.getEntity();

    assertEquals(identifier, entity.getOrg().getIdentifier());
    assertEquals("updated_name", entity.getOrg().getName());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateForDefaultOrg() {
    UpdateOrganizationRequest request = new UpdateOrganizationRequest();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setIdentifier(DEFAULT_ORG_IDENTIFIER);
    org.setName("updated_name");
    request.setOrg(org);

    Throwable thrown =
        catchThrowableOfType(()
                                 -> organizationApi.updateOrganization(request, DEFAULT_ORG_IDENTIFIER, account),
            InvalidRequestException.class);

    assertThat(thrown).hasMessage(String.format(
        "Update operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateForInvalidRequest() {
    UpdateOrganizationRequest request = new UpdateOrganizationRequest();
    io.harness.spec.server.ng.v1.model.Organization org = new io.harness.spec.server.ng.v1.model.Organization();
    org.setIdentifier(DEFAULT_ORG_IDENTIFIER);
    org.setName("updated_name");
    request.setOrg(org);

    Throwable thrown = catchThrowableOfType(
        () -> organizationApi.updateOrganization(request, identifier, account), InvalidRequestException.class);

    assertThat(thrown).hasMessage(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDelete() {
    OrganizationDTO organizationDTO = getOrganizationDTO(identifier, name);
    Organization organization = toOrganization(organizationDTO);
    organization.setVersion(0L);

    when(organizationService.get(account, identifier)).thenReturn(Optional.of(organization));
    when(organizationService.delete(account, identifier, null)).thenReturn(true);

    Response response = organizationApi.deleteOrganization(identifier, account);

    OrganizationResponse entity = (OrganizationResponse) response.getEntity();

    assertEquals(identifier, entity.getOrg().getIdentifier());
    assertEquals(organization.getVersion().toString(), response.getEntityTag().getValue());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteForDefaultOrg() {
    Throwable thrown = catchThrowableOfType(
        () -> organizationApi.deleteOrganization(DEFAULT_ORG_IDENTIFIER, account), InvalidRequestException.class);

    assertThat(thrown).hasMessage(String.format(
        "Delete operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteOrgNotFoundException() {
    Throwable thrown =
        catchThrowableOfType(() -> organizationApi.deleteOrganization(identifier, account), NotFoundException.class);

    assertThat(thrown).hasMessage(String.format("Organization with identifier [%s] not found", identifier));
  }
}
