/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.resourcegroup.framework.v3.mapper;

import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO.ResourceGroupFilterDTOBuilder;
import io.harness.resourcegroup.v1.remote.dto.ResourceSelectorFilter;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.spec.server.resourcegroup.v1.model.CreateResourceGroupRequest;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody.ManagedFilterEnum;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupScope;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;

public class ResourceGroupApiUtils {
  public static ResourceGroupRequest getResourceGroupRequestAcc(CreateResourceGroupRequest body, String account) {
    return ResourceGroupRequest.builder()
        .resourceGroup(
            ResourceGroupDTO.builder()
                .accountIdentifier(account)
                .identifier(body.getSlug())
                .name(body.getName())
                .color(body.getColor())
                .tags(body.getTags())
                .description(body.getDescription())
                .allowedScopeLevels(Collections.singleton("account"))
                .includedScopes(getIncludedScopeRequest(body.getIncludedScope()))
                .resourceFilter(getResourceFilterRequest(body.getResourceFilter(), body.isIncludeAllResources()))
                .build())
        .build();
  }
  public static ResourceGroupRequest getResourceGroupRequestOrg(
      String org, CreateResourceGroupRequest body, String account) {
    return ResourceGroupRequest.builder()
        .resourceGroup(
            ResourceGroupDTO.builder()
                .accountIdentifier(account)
                .orgIdentifier(org)
                .identifier(body.getSlug())
                .name(body.getName())
                .color(body.getColor())
                .tags(body.getTags())
                .description(body.getDescription())
                .allowedScopeLevels(Collections.singleton("organization"))
                .includedScopes(getIncludedScopeRequest(body.getIncludedScope()))
                .resourceFilter(getResourceFilterRequest(body.getResourceFilter(), body.isIncludeAllResources()))
                .build())
        .build();
  }
  public static ResourceGroupRequest getResourceGroupRequestProject(
      String org, String project, CreateResourceGroupRequest body, String account) {
    return ResourceGroupRequest.builder()
        .resourceGroup(
            ResourceGroupDTO.builder()
                .accountIdentifier(account)
                .orgIdentifier(org)
                .projectIdentifier(project)
                .identifier(body.getSlug())
                .name(body.getName())
                .color(body.getColor())
                .tags(body.getTags())
                .description(body.getDescription())
                .allowedScopeLevels(Collections.singleton("project"))
                .includedScopes(getIncludedScopeRequest(body.getIncludedScope()))
                .resourceFilter(getResourceFilterRequest(body.getResourceFilter(), body.isIncludeAllResources()))
                .build())
        .build();
  }
  public static List<ScopeSelector> getIncludedScopeRequest(List<ResourceGroupScope> includedScopes) {
    if (includedScopes == null) {
      return null;
    }
    return includedScopes.stream()
        .map(scope
            -> ScopeSelector.builder()
                   .accountIdentifier(scope.getAccount())
                   .orgIdentifier(scope.getOrg())
                   .projectIdentifier(scope.getProject())
                   .filter(getFilter(scope))
                   .build())
        .collect(Collectors.toList());
  }

  private static ScopeFilterType getFilter(ResourceGroupScope scope) {
    if ((scope.getFilter()).equals(ResourceGroupScope.FilterEnum.INCLUDING_CHILD_SCOPES)) {
      return ScopeFilterType.INCLUDING_CHILD_SCOPES;
    }
    if ((scope.getFilter()).equals(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES)) {
      return ScopeFilterType.EXCLUDING_CHILD_SCOPES;
    }
    throw new InvalidRequestException(
        "Filter in Included Scope must be either INCLUDING_CHILD_SCOPES or EXCLUDING_CHILD_SCOPES");
  }

  public static ResourceFilter getResourceFilterRequest(
      List<io.harness.spec.server.resourcegroup.v1.model.ResourceFilter> filters, boolean includeAllResources) {
    if (filters == null) {
      return ResourceFilter.builder().includeAllResources(includeAllResources).build();
    }
    return ResourceFilter.builder()
        .resources(filters.stream().map(ResourceGroupApiUtils::getFilterRequest).collect(Collectors.toList()))
        .includeAllResources(includeAllResources)
        .build();
  }

  public static ResourceSelector getFilterRequest(io.harness.spec.server.resourcegroup.v1.model.ResourceFilter filter) {
    if (filter.getAttributeName() == null && filter.getAttributeValues().isEmpty()) {
      return ResourceSelector.builder()
          .resourceType(filter.getResourceType())
          .identifiers(filter.getIdentifiers())
          .build();
    }
    return ResourceSelector.builder()
        .resourceType(filter.getResourceType())
        .identifiers(filter.getIdentifiers())
        .attributeFilter(AttributeFilter.builder()
                             .attributeName(filter.getAttributeName())
                             .attributeValues(filter.getAttributeValues())
                             .build())
        .build();
  }

  public static io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse getResourceGroupResponse(
      ResourceGroupResponse response) {
    if (response == null || response.getResourceGroup() == null) {
      return null;
    }
    ResourceGroupsResponse resourceGroupsResponse = new ResourceGroupsResponse();
    resourceGroupsResponse.setSlug(response.getResourceGroup().getIdentifier());
    resourceGroupsResponse.setName(response.getResourceGroup().getName());
    resourceGroupsResponse.setColor(response.getResourceGroup().getColor());
    resourceGroupsResponse.setTags(response.getResourceGroup().getTags());
    resourceGroupsResponse.setDescription(response.getResourceGroup().getDescription());
    resourceGroupsResponse.setAllowedScopeLevels(response.getResourceGroup()
                                                     .getAllowedScopeLevels()
                                                     .stream()
                                                     .map(ResourceGroupApiUtils::getAllowedScopeEnum)
                                                     .collect(Collectors.toList()));
    if (response.getResourceGroup().getIncludedScopes() != null) {
      resourceGroupsResponse.setIncludedScope(response.getResourceGroup()
                                                  .getIncludedScopes()
                                                  .stream()
                                                  .map(ResourceGroupApiUtils::getIncludedScopeResponse)
                                                  .collect(Collectors.toList()));
    }
    resourceGroupsResponse.setResourceFilter(getResourceFilters(response));
    if (response.getResourceGroup().getResourceFilter() != null) {
      resourceGroupsResponse.setIncludeAllResources(
          response.getResourceGroup().getResourceFilter().isIncludeAllResources());
    }
    resourceGroupsResponse.setCreated(response.getCreatedAt());
    resourceGroupsResponse.setUpdated(response.getLastModifiedAt());
    resourceGroupsResponse.setHarnessManaged(response.isHarnessManaged());
    return resourceGroupsResponse;
  }

  private static List<io.harness.spec.server.resourcegroup.v1.model.ResourceFilter> getResourceFilters(
      ResourceGroupResponse response) {
    if (response.getResourceGroup().getResourceFilter() == null
        || response.getResourceGroup().getResourceFilter().getResources() == null) {
      return null;
    }
    return response.getResourceGroup()
        .getResourceFilter()
        .getResources()
        .stream()
        .map(ResourceGroupApiUtils::getResourceFilterResponse)
        .collect(Collectors.toList());
  }

  public static ResourceGroupsResponse.AllowedScopeLevelsEnum getAllowedScopeEnum(String scope) {
    if (scope.equals("account")) {
      return ResourceGroupsResponse.AllowedScopeLevelsEnum.ACCOUNT;
    }
    if (scope.equals("organization")) {
      return ResourceGroupsResponse.AllowedScopeLevelsEnum.ORGANIZATION;
    }
    if (scope.equals("project")) {
      return ResourceGroupsResponse.AllowedScopeLevelsEnum.PROJECT;
    }
    throw new InvalidRequestException("Unknown Scope in Allowed Scope Level");
  }

  public static ResourceGroupScope getIncludedScopeResponse(ScopeSelector includedScopes) {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setAccount(includedScopes.getAccountIdentifier());
    resourceGroupScope.setOrg(includedScopes.getOrgIdentifier());
    resourceGroupScope.setProject(includedScopes.getProjectIdentifier());
    resourceGroupScope.setFilter((includedScopes.getFilter().equals(ScopeFilterType.INCLUDING_CHILD_SCOPES))
            ? ResourceGroupScope.FilterEnum.INCLUDING_CHILD_SCOPES
            : ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    return resourceGroupScope;
  }

  public static io.harness.spec.server.resourcegroup.v1.model.ResourceFilter getResourceFilterResponse(
      ResourceSelector resourceSelector) {
    io.harness.spec.server.resourcegroup.v1.model.ResourceFilter resourceFilter =
        new io.harness.spec.server.resourcegroup.v1.model.ResourceFilter();
    resourceFilter.setResourceType(resourceSelector.getResourceType());
    resourceFilter.setIdentifiers(resourceSelector.getIdentifiers());
    if (resourceSelector.getAttributeFilter() != null) {
      resourceFilter.setAttributeName(resourceSelector.getAttributeFilter().getAttributeName());
      resourceFilter.setAttributeValues(resourceSelector.getAttributeFilter().getAttributeValues());
    }
    return resourceFilter;
  }

  public static ResourceGroupFilterDTO getResourceFilterDTO(ResourceGroupFilterRequestBody requestBody) {
    ResourceGroupFilterDTOBuilder builder = ResourceGroupFilterDTO.builder()
                                                .accountIdentifier(requestBody.getAccount())
                                                .orgIdentifier(requestBody.getOrg())
                                                .projectIdentifier(requestBody.getProject())
                                                .searchTerm(requestBody.getSearchTerm());
    if (requestBody.getIdentifierFilter() != null) {
      builder.identifierFilter(new HashSet<>(requestBody.getIdentifierFilter()));
    }
    if (requestBody.getResourceSelectorFilter() != null) {
      builder.resourceSelectorFilterList(requestBody.getResourceSelectorFilter()
                                             .stream()
                                             .map(ResourceGroupApiUtils::getResourceSelectorFilter)
                                             .collect(Collectors.toSet()));
    }
    return builder.managedFilter(getManagedFilter(requestBody.getManagedFilter())).build();
  }

  public static ResourceSelectorFilter getResourceSelectorFilter(
      io.harness.spec.server.resourcegroup.v1.model.ResourceSelectorFilter resourceSelectorFilter) {
    return ResourceSelectorFilter.builder()
        .resourceType(resourceSelectorFilter.getResourceType())
        .resourceIdentifier(resourceSelectorFilter.getResourceSlug())
        .build();
  }

  public static ManagedFilter getManagedFilter(ManagedFilterEnum managedFilter) {
    if (managedFilter == null) {
      return null;
    }
    if (managedFilter.equals(ManagedFilterEnum.NO_FILTER)) {
      return ManagedFilter.NO_FILTER;
    }
    if (managedFilter.equals(ManagedFilterEnum.ONLY_MANAGED)) {
      return ManagedFilter.ONLY_MANAGED;
    }
    if (managedFilter.equals(ManagedFilterEnum.ONLY_CUSTOM)) {
      return ManagedFilter.ONLY_CUSTOM;
    }
    throw new InvalidRequestException("Managed Filter type unidentified.");
  }

  public static PageRequest getPageRequest(Integer page, Integer limit, String field, String order) {
    if (field == null && order == null) {
      return PageRequest.builder().pageIndex(page).pageSize(limit).build();
    }
    if (order == null || (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc"))) {
      throw new InvalidRequestException("Order of sorting unidentified or null. Accepted values: ASC / DESC");
    }
    List<SortOrder> sortOrders = null;
    if (field != null) {
      switch (field) {
        case "slug":
          field = "identifier";
          break;
        case "name":
          break;
        case "created":
          field = "createdAt";
          break;
        case "updated":
          field = "lastUpdatedAt";
          break;
        default:
          throw new InvalidRequestException(
              "Field provided for sorting unidentified. Accepted values: slug / name / created / updated");
      }
      SortOrder sortOrder = new SortOrder(field + "," + order);
      sortOrders = Collections.singletonList(sortOrder);
    }
    return PageRequest.builder().pageIndex(page).pageSize(limit).sortOrders(sortOrders).build();
  }

  public static ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList();
    links.add(Link.fromUri(UriBuilder.fromPath(path)
                               .queryParam("page", new Object[] {page})
                               .queryParam("page_size", new Object[] {limit})
                               .build(new Object[0]))
                  .rel("self")
                  .build(new Object[0]));
    if (page >= 1) {
      links.add(Link.fromUri(UriBuilder.fromPath(path)
                                 .queryParam("page", new Object[] {page - 1})
                                 .queryParam("page_size", new Object[] {limit})
                                 .build(new Object[0]))
                    .rel("previous")
                    .build(new Object[0]));
    }

    if (limit == currentResultCount) {
      links.add(Link.fromUri(UriBuilder.fromPath(path)
                                 .queryParam("page", new Object[] {page + 1})
                                 .queryParam("page_size", new Object[] {limit})
                                 .build(new Object[0]))
                    .rel("next")
                    .build(new Object[0]));
    }

    return responseBuilder.links((Link[]) links.toArray(new Link[links.size()]));
  }
}
