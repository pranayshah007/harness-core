/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ORGANIZATION;
import static io.harness.ng.core.remote.OrganizationApiMapper.addLinksHeader;
import static io.harness.ng.core.remote.OrganizationApiMapper.getOrganizationDto;
import static io.harness.ng.core.remote.OrganizationApiMapper.getOrganizationResponse;
import static io.harness.ng.core.remote.OrganizationApiMapper.getPageRequest;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.OrganizationApi;
import io.harness.spec.server.ng.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.model.OrganizationResponse;
import io.harness.spec.server.ng.model.UpdateOrganizationRequest;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
public class OrganizationApiImpl implements OrganizationApi {
  private final OrganizationService organizationService;

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = CREATE_ORGANIZATION_PERMISSION)
  @Override
  public Response createOrganization(CreateOrganizationRequest request, @AccountIdentifier String account) {
    if (DEFAULT_ORG_IDENTIFIER.equals(request.getSlug())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as org identifier", DEFAULT_ORG_IDENTIFIER), USER);
    }
    Organization createdOrganization = organizationService.create(account, getOrganizationDto(request));
    return Response.ok().entity(getOrganizationResponse(createdOrganization)).build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = VIEW_ORGANIZATION_PERMISSION)
  @Override
  public Response getOrganization(@ResourceIdentifier String id, @AccountIdentifier String account) {
    Optional<Organization> organizationOptional = organizationService.get(account, id);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", id));
    }
    return Response.ok().entity(getOrganizationResponse(organizationOptional.get())).build();
  }

  @Override
  public Response getOrganizations(String account, List org, String searchTerm, Integer page, Integer limit) {
    OrganizationFilterDTO organizationFilterDTO =
        OrganizationFilterDTO.builder().searchTerm(searchTerm).identifiers(org).ignoreCase(true).build();

    Page<Organization> orgPage =
        organizationService.listPermittedOrgs(account, getPageRequest(page, limit), organizationFilterDTO);

    Page<OrganizationResponse> organizationResponsePage = orgPage.map(OrganizationApiMapper::getOrganizationResponse);

    List<OrganizationResponse> organizations = organizationResponsePage.getContent();

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        addLinksHeader(responseBuilder, "/v1/orgs", organizations.size(), page, limit);

    return responseBuilderWithLinks.entity(organizations).build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = EDIT_ORGANIZATION_PERMISSION)
  @Override
  public Response updateOrganization(
      UpdateOrganizationRequest request, @ResourceIdentifier String id, @AccountIdentifier String account) {
    Organization updatedOrganization =
        organizationService.update(account, id, OrganizationApiMapper.getOrganizationDto(id, request));
    return Response.ok().entity(getOrganizationResponse(updatedOrganization)).build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = DELETE_ORGANIZATION_PERMISSION)
  @Override
  public Response deleteOrganization(@ResourceIdentifier String id, @AccountIdentifier String account) {
    if (DEFAULT_ORG_IDENTIFIER.equals(id)) {
      throw new InvalidRequestException(
          String.format(
              "Delete operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    Optional<Organization> organizationOptional = organizationService.get(account, id);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", id));
    }

    boolean deleted = organizationService.delete(account, id, null);

    if (!deleted) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", id));
    }
    return Response.ok().entity(getOrganizationResponse(organizationOptional.get())).build();
  }
}
