/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.resourcegroup.framework.v3;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MANKRIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.framework.v3.mapper.ResourceGroupApiUtils;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.resourcegroup.v1.model.CreateResourceGroupRequest;
import io.harness.spec.server.resourcegroup.v1.model.ResourceFilter;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody.ManagedFilterEnum;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupScope;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupScope.FilterEnum;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse.AllowedScopeLevelsEnum;
import io.harness.spec.server.resourcegroup.v1.model.ResourceSelectorFilter;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ResourceGroupApiUtilsTest extends CategoryTest {
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  int page = 0;
  int limit = 1;

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRGReqAcc() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    ResourceFilter resourceFilter = new ResourceFilter();
    resourceFilter.setResourceType("RESOURCE");
    resourceFilter.setIdentifiers(Collections.singletonList("identifier"));
    resourceFilter.setAttributeName("name");
    resourceFilter.setAttributeValues(Collections.singletonList("resource1"));

    CreateResourceGroupRequest createResourceGroupRequest = new CreateResourceGroupRequest();
    createResourceGroupRequest.setSlug(slug);
    createResourceGroupRequest.setName(name);
    createResourceGroupRequest.setIncludedScope(includedScopes);
    createResourceGroupRequest.setResourceFilter(Collections.singletonList(resourceFilter));
    createResourceGroupRequest.setIncludeAllResources(false);

    ResourceGroupRequest resourceGroupRequest =
        ResourceGroupApiUtils.getResourceGroupRequestAcc(createResourceGroupRequest, account);

    assertEquals(account, resourceGroupRequest.getResourceGroup().getAccountIdentifier());
    assertEquals(slug, resourceGroupRequest.getResourceGroup().getIdentifier());
    assertEquals(name, resourceGroupRequest.getResourceGroup().getName());
    assertTrue(resourceGroupRequest.getResourceGroup().getAllowedScopeLevels().contains("account"));
    assertEquals(account, resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getAccountIdentifier());
    assertEquals(ScopeFilterType.EXCLUDING_CHILD_SCOPES,
        resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getFilter());
    assertEquals("RESOURCE",
        resourceGroupRequest.getResourceGroup().getResourceFilter().getResources().get(0).getResourceType());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRGReqOrg() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    ResourceFilter resourceFilter = new ResourceFilter();
    resourceFilter.setResourceType("RESOURCE");
    resourceFilter.setIdentifiers(Collections.singletonList("identifier"));
    resourceFilter.setAttributeName("name");
    resourceFilter.setAttributeValues(Collections.singletonList("resource1"));

    CreateResourceGroupRequest createResourceGroupRequest = new CreateResourceGroupRequest();
    createResourceGroupRequest.setSlug(slug);
    createResourceGroupRequest.setName(name);
    createResourceGroupRequest.setIncludedScope(includedScopes);
    createResourceGroupRequest.setResourceFilter(Collections.singletonList(resourceFilter));
    createResourceGroupRequest.setIncludeAllResources(false);

    ResourceGroupRequest resourceGroupRequest =
        ResourceGroupApiUtils.getResourceGroupRequestOrg(org, createResourceGroupRequest, account);

    assertEquals(org, resourceGroupRequest.getResourceGroup().getOrgIdentifier());
    assertEquals(slug, resourceGroupRequest.getResourceGroup().getIdentifier());
    assertEquals(name, resourceGroupRequest.getResourceGroup().getName());
    assertTrue(resourceGroupRequest.getResourceGroup().getAllowedScopeLevels().contains("organization"));
    assertEquals(org, resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getOrgIdentifier());
    assertEquals(ScopeFilterType.EXCLUDING_CHILD_SCOPES,
        resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getFilter());
    assertEquals("name",
        resourceGroupRequest.getResourceGroup()
            .getResourceFilter()
            .getResources()
            .get(0)
            .getAttributeFilter()
            .getAttributeName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRGReqProject() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    ResourceFilter resourceFilter = new ResourceFilter();
    resourceFilter.setResourceType("RESOURCE");
    resourceFilter.setIdentifiers(Collections.singletonList("identifier"));
    resourceFilter.setAttributeName("name");
    resourceFilter.setAttributeValues(Collections.singletonList("resource1"));

    CreateResourceGroupRequest createResourceGroupRequest = new CreateResourceGroupRequest();
    createResourceGroupRequest.setSlug(slug);
    createResourceGroupRequest.setName(name);
    createResourceGroupRequest.setIncludedScope(includedScopes);
    createResourceGroupRequest.setResourceFilter(Collections.singletonList(resourceFilter));
    createResourceGroupRequest.setIncludeAllResources(false);

    ResourceGroupRequest resourceGroupRequest =
        ResourceGroupApiUtils.getResourceGroupRequestProject(org, project, createResourceGroupRequest, account);

    assertEquals(project, resourceGroupRequest.getResourceGroup().getProjectIdentifier());
    assertEquals(slug, resourceGroupRequest.getResourceGroup().getIdentifier());
    assertEquals(name, resourceGroupRequest.getResourceGroup().getName());
    assertTrue(resourceGroupRequest.getResourceGroup().getAllowedScopeLevels().contains("project"));
    assertEquals(project, resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getProjectIdentifier());
    assertEquals(ScopeFilterType.EXCLUDING_CHILD_SCOPES,
        resourceGroupRequest.getResourceGroup().getIncludedScopes().get(0).getFilter());
    assertEquals("RESOURCE",
        resourceGroupRequest.getResourceGroup().getResourceFilter().getResources().get(0).getResourceType());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRGResponse() {
    ResourceGroupResponse response =
        ResourceGroupResponse.builder()
            .resourceGroup(
                ResourceGroupDTO.builder()
                    .identifier(slug)
                    .name(name)
                    .accountIdentifier(account)
                    .orgIdentifier(org)
                    .projectIdentifier(project)
                    .allowedScopeLevels(Collections.singleton("project"))
                    .includedScopes(Collections.singletonList(
                        ScopeSelector.builder().filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES).build()))
                    .resourceFilter(
                        io.harness.resourcegroup.v2.model.ResourceFilter.builder()
                            .includeAllResources(false)
                            .resources(Collections.singletonList(
                                ResourceSelector.builder()
                                    .resourceType("RESOURCE")
                                    .attributeFilter(AttributeFilter.builder().attributeName("Attribute").build())
                                    .build()))
                            .build())
                    .build())
            .createdAt(123456789L)
            .lastModifiedAt(987654321L)
            .harnessManaged(false)
            .build();
    ResourceGroupsResponse resourceGroupsResponse = ResourceGroupApiUtils.getResourceGroupResponse(response);
    assertEquals(slug, resourceGroupsResponse.getSlug());
    assertEquals(name, resourceGroupsResponse.getName());
    assertEquals(AllowedScopeLevelsEnum.PROJECT, resourceGroupsResponse.getAllowedScopeLevels().get(0));
    assertEquals(FilterEnum.EXCLUDING_CHILD_SCOPES, resourceGroupsResponse.getIncludedScope().get(0).getFilter());
    assertEquals("RESOURCE", resourceGroupsResponse.getResourceFilter().get(0).getResourceType());
    assertEquals("Attribute", resourceGroupsResponse.getResourceFilter().get(0).getAttributeName());
    assertEquals(123456789L, resourceGroupsResponse.getCreated().longValue());
    assertEquals(987654321L, resourceGroupsResponse.getUpdated().longValue());
    assertFalse(resourceGroupsResponse.isHarnessManaged());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResourceFilterDTO() {
    ResourceSelectorFilter resourceSelectorFilter = new ResourceSelectorFilter();
    resourceSelectorFilter.setResourceSlug("resourceSlug");
    resourceSelectorFilter.setResourceType("RESOURCE");
    ResourceGroupFilterRequestBody requestBody = new ResourceGroupFilterRequestBody();
    requestBody.setAccount(account);
    requestBody.setOrg(org);
    requestBody.setProject(project);
    requestBody.setManagedFilter(ManagedFilterEnum.NO_FILTER);
    requestBody.setIdentifierFilter(Collections.singletonList("identifier"));
    requestBody.setResourceSelectorFilter(Collections.singletonList(resourceSelectorFilter));

    ResourceGroupFilterDTO filterDTO = ResourceGroupApiUtils.getResourceFilterDTO(requestBody);

    assertEquals(account, filterDTO.getAccountIdentifier());
    assertEquals(org, filterDTO.getOrgIdentifier());
    assertEquals(project, filterDTO.getProjectIdentifier());
    assertEquals(ManagedFilter.NO_FILTER, filterDTO.getManagedFilter());
    assertTrue(filterDTO.getIdentifierFilter().contains("identifier"));
    assertTrue(filterDTO.getResourceSelectorFilterList().contains(
        io.harness.resourcegroup.v1.remote.dto.ResourceSelectorFilter.builder()
            .resourceType("RESOURCE")
            .resourceIdentifier("resourceSlug")
            .build()));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPageRequest() {
    int page = 0;
    int limit = 1;
    String sort = "name";
    String order = "desc";

    PageRequest pageRequest = ResourceGroupApiUtils.getPageRequest(page, limit, sort, order);
    assertEquals(pageRequest.getPageIndex(), page);
    assertEquals(pageRequest.getPageSize(), limit);
    assertEquals(pageRequest.getSortOrders().get(0).getFieldName(), sort);
    assertEquals(pageRequest.getSortOrders().get(0).getOrderType(), OrderType.DESC);
  }
}
