/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.resourcegroup.framework.v3.api;

import static io.harness.resourcegroup.ResourceGroupPermissions.DELETE_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.EDIT_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.resourcegroup.v1.remote.dto.ManagedFilter.NO_FILTER;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.framework.v3.mapper.ResourceGroupApiUtils;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.resourcegroup.v1.OrganizationResourceGroupsApi;
import io.harness.spec.server.resourcegroup.v1.model.CreateResourceGroupRequest;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse;

import com.google.inject.Inject;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@OwnedBy(HarnessTeam.PL)
public class OrgResourceGroupsApiImpl implements OrganizationResourceGroupsApi {
  ResourceGroupService resourceGroupService;
  ResourceGroupValidatorImpl resourceGroupValidator;

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = EDIT_RESOURCEGROUP_PERMISSION)
  @FeatureRestrictionCheck(FeatureRestrictionName.CUSTOM_RESOURCE_GROUPS)
  public Response createResourceGroupOrg(
      CreateResourceGroupRequest body, @OrgIdentifier String org, @AccountIdentifier String account) {
    ResourceGroupRequest resourceGroupRequest = ResourceGroupApiUtils.getResourceGroupRequestOrg(org, body, account);
    resourceGroupValidator.validateResourceGroup(resourceGroupRequest);
    ResourceGroupsResponse resourceGroupsResponse = ResourceGroupApiUtils.getResourceGroupResponse(
        resourceGroupService.create(resourceGroupRequest.getResourceGroup(), false));
    return Response.status(201).entity(resourceGroupsResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = DELETE_RESOURCEGROUP_PERMISSION)
  public Response deleteResourceGroupOrg(
      @OrgIdentifier String org, @ResourceIdentifier String resourceGroup, @AccountIdentifier String account) {
    ResourceGroupsResponse resourceGroupsResponse = ResourceGroupApiUtils.getResourceGroupResponse(
        resourceGroupService.get(Scope.of(account, org, null), resourceGroup, NO_FILTER).orElse(null));
    if (resourceGroupService.delete(Scope.of(account, org, null), resourceGroup)) {
      return Response.ok().entity(resourceGroupsResponse).build();
    }
    throw new InvalidRequestException("Unable to delete Resource Group.");
  }

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = VIEW_RESOURCEGROUP_PERMISSION)
  public Response getResourceGroupOrg(
      @OrgIdentifier String org, @ResourceIdentifier String resourceGroup, @AccountIdentifier String account) {
    ResourceGroupsResponse resourceGroupsResponse = ResourceGroupApiUtils.getResourceGroupResponse(
        resourceGroupService.get(Scope.of(account, org, null), resourceGroup, NO_FILTER).orElse(null));
    if (resourceGroupsResponse == null) {
      throw new InvalidRequestException("Resource Group with given identifier not found.");
    }
    return Response.ok().entity(resourceGroupsResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = VIEW_RESOURCEGROUP_PERMISSION)
  public Response listResourceGroupsOrg(@OrgIdentifier String org, Integer page, Integer limit, String searchTerm,
      @AccountIdentifier String account, String sort, String order) {
    PageRequest pageRequest = ResourceGroupApiUtils.getPageRequest(page, limit, sort, order);
    Page<ResourceGroupResponse> pageResponse =
        resourceGroupService.list(Scope.of(account, org, null), pageRequest, searchTerm);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = ResourceGroupApiUtils.addLinksHeader(
        responseBuilder, format("/v1/orgs/%s/resource-groups)", org), pageResponse.getContent().size(), page, limit);
    return responseBuilderWithLinks
        .entity(pageResponse.getContent()
                    .stream()
                    .map(ResourceGroupApiUtils::getResourceGroupResponse)
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = RESOURCE_GROUP, permission = EDIT_RESOURCEGROUP_PERMISSION)
  public Response updateResourceGroupOrg(CreateResourceGroupRequest body, @OrgIdentifier String org,
      @ResourceIdentifier String resourceGroup, @AccountIdentifier String account) {
    if (!resourceGroup.equals(body.getSlug())) {
      throw new InvalidRequestException("Resource Group identifier in the request body and the URL do not match.");
    }
    ResourceGroupRequest resourceGroupRequest = ResourceGroupApiUtils.getResourceGroupRequestOrg(org, body, account);
    resourceGroupValidator.validateResourceGroup(resourceGroupRequest);
    ResourceGroupsResponse resourceGroupsResponse = ResourceGroupApiUtils.getResourceGroupResponse(
        resourceGroupService.update(resourceGroupRequest.getResourceGroup(), false).orElse(null));
    if (resourceGroupsResponse == null) {
      throw new InvalidRequestException("Resource Group with given identifier not found.");
    }
    return Response.ok().entity(resourceGroupsResponse).build();
  }
}
