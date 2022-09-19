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
  private final OrganizationApiUtils organizationApiUtils;

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = CREATE_ORGANIZATION_PERMISSION)
  @Override
  public Response createOrganization(CreateOrganizationRequest request, @AccountIdentifier String account) {
    if (DEFAULT_ORG_IDENTIFIER.equals(request.getOrg().getSlug())) {
      throw new InvalidRequestException(String.format("%s cannot be used as org slug", DEFAULT_ORG_IDENTIFIER), USER);
    }
    Organization createdOrganization =
        organizationService.create(account, organizationApiUtils.getOrganizationDto(request));
    return Response.status(Response.Status.CREATED)
        .entity(organizationApiUtils.getOrganizationResponse(createdOrganization))
        .tag(createdOrganization.getVersion().toString())
        .build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = VIEW_ORGANIZATION_PERMISSION)
  @Override
  public Response getOrganization(@ResourceIdentifier String slug, @AccountIdentifier String account) {
    Optional<Organization> organizationOptional = organizationService.get(account, slug);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with slug [%s] not found", slug));
    }
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(organizationOptional.get()))
        .tag(organizationOptional.get().getVersion().toString())
        .build();
  }

  @Override
  public Response getOrganizations(String account, List org, String searchTerm, Integer page, Integer limit) {
    OrganizationFilterDTO organizationFilterDTO =
        OrganizationFilterDTO.builder().searchTerm(searchTerm).identifiers(org).ignoreCase(true).build();

    Page<Organization> orgPage = organizationService.listPermittedOrgs(
        account, organizationApiUtils.getPageRequest(page, limit), organizationFilterDTO);

    Page<OrganizationResponse> organizationResponsePage = orgPage.map(organizationApiUtils::getOrganizationResponse);

    List<OrganizationResponse> organizations = organizationResponsePage.getContent();

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        organizationApiUtils.addLinksHeader(responseBuilder, "/v1/orgs", organizations.size(), page, limit);

    return responseBuilderWithLinks.entity(organizations).build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = EDIT_ORGANIZATION_PERMISSION)
  @Override
  public Response updateOrganization(
      UpdateOrganizationRequest request, @ResourceIdentifier String slug, @AccountIdentifier String account) {
    Organization updatedOrganization =
        organizationService.update(account, slug, organizationApiUtils.getOrganizationDto(slug, request));
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(updatedOrganization))
        .tag(updatedOrganization.getVersion().toString())
        .build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = DELETE_ORGANIZATION_PERMISSION)
  @Override
  public Response deleteOrganization(@ResourceIdentifier String slug, @AccountIdentifier String account) {
    if (DEFAULT_ORG_IDENTIFIER.equals(slug)) {
      throw new InvalidRequestException(
          String.format("Delete operation not supported for Default Organization (slug: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    Optional<Organization> organizationOptional = organizationService.get(account, slug);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with slug [%s] not found", slug));
    }

    boolean deleted = organizationService.delete(account, slug, null);

    if (!deleted) {
      throw new InvalidRequestException(String.format("Organization with slug [%s] could not be deleted", slug));
    }
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(organizationOptional.get()))
        .tag(organizationOptional.get().getVersion().toString())
        .build();
  }
}