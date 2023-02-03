/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
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
import io.harness.spec.server.ng.v1.OrganizationApi;
import io.harness.spec.server.ng.v1.model.CreateOrganizationRequest;
import io.harness.spec.server.ng.v1.model.OrganizationResponse;
import io.harness.spec.server.ng.v1.model.UpdateOrganizationRequest;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
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
    if (DEFAULT_ORG_IDENTIFIER.equals(request.getOrg().getIdentifier())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as org identifier", DEFAULT_ORG_IDENTIFIER), USER);
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
  public Response getOrganization(@ResourceIdentifier String identifier, @AccountIdentifier String account) {
    Optional<Organization> organizationOptional = organizationService.get(account, identifier);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", identifier));
    }
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(organizationOptional.get()))
        .tag(organizationOptional.get().getVersion().toString())
        .build();
  }

  @Override
  public Response getOrganizations(
      List<String> org, String searchTerm, Integer page, Integer limit, String account, String sort, String order) {
    OrganizationFilterDTO organizationFilterDTO =
        OrganizationFilterDTO.builder().searchTerm(searchTerm).identifiers(org).ignoreCase(true).build();

    Page<Organization> orgPage = organizationService.listPermittedOrgs(
        account, organizationApiUtils.getPageRequest(page, limit, sort, order), organizationFilterDTO);

    Page<OrganizationResponse> organizationResponsePage = orgPage.map(organizationApiUtils::getOrganizationResponse);

    List<OrganizationResponse> organizations = organizationResponsePage.getContent();

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, organizationResponsePage.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(organizations).build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = EDIT_ORGANIZATION_PERMISSION)
  @Override
  public Response updateOrganization(
      UpdateOrganizationRequest request, @ResourceIdentifier String identifier, @AccountIdentifier String account) {
    if (!Objects.equals(request.getOrg().getIdentifier(), identifier)) {
      throw new InvalidRequestException(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (DEFAULT_ORG_IDENTIFIER.equals(identifier)) {
      throw new InvalidRequestException(
          String.format(
              "Update operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    Organization updatedOrganization =
        organizationService.update(account, identifier, organizationApiUtils.getOrganizationDto(request));
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(updatedOrganization))
        .tag(updatedOrganization.getVersion().toString())
        .build();
  }

  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = DELETE_ORGANIZATION_PERMISSION)
  @Override
  public Response deleteOrganization(@ResourceIdentifier String identifier, @AccountIdentifier String account) {
    if (DEFAULT_ORG_IDENTIFIER.equals(identifier)) {
      throw new InvalidRequestException(
          String.format(
              "Delete operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    Optional<Organization> organizationOptional = organizationService.get(account, identifier);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", identifier));
    }

    boolean deleted = organizationService.delete(account, identifier, null);

    if (!deleted) {
      throw new InvalidRequestException(
          String.format("Organization with identifier [%s] could not be deleted", identifier));
    }
    return Response.ok()
        .entity(organizationApiUtils.getOrganizationResponse(organizationOptional.get()))
        .tag(organizationOptional.get().getVersion().toString())
        .build();
  }
}