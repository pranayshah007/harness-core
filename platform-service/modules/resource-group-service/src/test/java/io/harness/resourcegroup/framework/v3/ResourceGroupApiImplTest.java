/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v3;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.utils.PageTestUtils.getPage;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.framework.v3.api.AccountResourceGroupApiImpl;
import io.harness.resourcegroup.framework.v3.api.FilterResourceGroupApiImpl;
import io.harness.resourcegroup.framework.v3.api.OrgResourceGroupsApiImpl;
import io.harness.resourcegroup.framework.v3.api.ProjectResourceGroupsApiImpl;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v2.model.AttributeFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.resourcegroup.v1.model.CreateResourceGroupRequest;
import io.harness.spec.server.resourcegroup.v1.model.ResourceFilter;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupFilterRequestBody.ManagedFilterEnum;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupScope;
import io.harness.spec.server.resourcegroup.v1.model.ResourceGroupsResponse;
import io.harness.spec.server.resourcegroup.v1.model.ResourceSelectorFilter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)

public class ResourceGroupApiImplTest extends CategoryTest {
  private ResourceGroupService resourceGroupService;
  private ResourceGroupValidatorImpl resourceGroupValidator;
  private AccountResourceGroupApiImpl accountResourceGroupApi;
  private OrgResourceGroupsApiImpl orgResourceGroupsApi;
  private ProjectResourceGroupsApiImpl projectResourceGroupsApi;
  private FilterResourceGroupApiImpl filterResourceGroupApi;
  private ResourceGroupResponse resourceGroupResponseAcc;
  private ResourceGroupResponse resourceGroupResponseOrg;
  private ResourceGroupResponse resourceGroupResponseProject;

  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  int page = 0;
  int limit = 1;

  @Before
  public void setup() {
    resourceGroupService = mock(ResourceGroupService.class);
    resourceGroupValidator = mock(ResourceGroupValidatorImpl.class);
    accountResourceGroupApi = new AccountResourceGroupApiImpl(resourceGroupService, resourceGroupValidator);
    orgResourceGroupsApi = new OrgResourceGroupsApiImpl(resourceGroupService, resourceGroupValidator);
    projectResourceGroupsApi = new ProjectResourceGroupsApiImpl(resourceGroupService, resourceGroupValidator);
    filterResourceGroupApi = new FilterResourceGroupApiImpl(resourceGroupService);
    resourceGroupResponseAcc =
        ResourceGroupResponse.builder()
            .resourceGroup(
                ResourceGroupDTO.builder()
                    .accountIdentifier(account)
                    .identifier(slug)
                    .name(name)
                    .allowedScopeLevels(Collections.singleton("account"))
                    .includedScopes(Collections.singletonList(ScopeSelector.builder()
                                                                  .accountIdentifier(account)
                                                                  .orgIdentifier(org)
                                                                  .projectIdentifier(project)
                                                                  .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                                                                  .build()))
                    .resourceFilter(
                        io.harness.resourcegroup.v2.model.ResourceFilter.builder()
                            .resources(Collections.singletonList(
                                ResourceSelector.builder()
                                    .resourceType("RESOURCE")
                                    .identifiers(Collections.singletonList("identifier"))
                                    .attributeFilter(AttributeFilter.builder()
                                                         .attributeName("name")
                                                         .attributeValues(Collections.singletonList("resource1"))
                                                         .build())
                                    .build()))
                            .includeAllResources(false)
                            .build())
                    .build())
            .build();
    resourceGroupResponseOrg =
        ResourceGroupResponse.builder()
            .resourceGroup(
                ResourceGroupDTO.builder()
                    .accountIdentifier(account)
                    .identifier(slug)
                    .name(name)
                    .allowedScopeLevels(Collections.singleton("organization"))
                    .includedScopes(Collections.singletonList(ScopeSelector.builder()
                                                                  .accountIdentifier(account)
                                                                  .orgIdentifier(org)
                                                                  .projectIdentifier(project)
                                                                  .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                                                                  .build()))
                    .resourceFilter(
                        io.harness.resourcegroup.v2.model.ResourceFilter.builder()
                            .resources(Collections.singletonList(
                                ResourceSelector.builder()
                                    .resourceType("RESOURCE")
                                    .identifiers(Collections.singletonList("identifier"))
                                    .attributeFilter(AttributeFilter.builder()
                                                         .attributeName("name")
                                                         .attributeValues(Collections.singletonList("resource1"))
                                                         .build())
                                    .build()))
                            .includeAllResources(false)
                            .build())
                    .build())
            .build();
    resourceGroupResponseProject =
        ResourceGroupResponse.builder()
            .resourceGroup(
                ResourceGroupDTO.builder()
                    .accountIdentifier(account)
                    .identifier(slug)
                    .name(name)
                    .allowedScopeLevels(Collections.singleton("project"))
                    .includedScopes(Collections.singletonList(ScopeSelector.builder()
                                                                  .accountIdentifier(account)
                                                                  .orgIdentifier(org)
                                                                  .projectIdentifier(project)
                                                                  .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                                                                  .build()))
                    .resourceFilter(
                        io.harness.resourcegroup.v2.model.ResourceFilter.builder()
                            .resources(Collections.singletonList(
                                ResourceSelector.builder()
                                    .resourceType("RESOURCE")
                                    .identifiers(Collections.singletonList("identifier"))
                                    .attributeFilter(AttributeFilter.builder()
                                                         .attributeName("name")
                                                         .attributeValues(Collections.singletonList("resource1"))
                                                         .build())
                                    .build()))
                            .includeAllResources(false)
                            .build())
                    .build())
            .build();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGCreate() {
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

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setResourceFilter(Collections.singletonList(resourceFilter));
    request.setIncludeAllResources(false);

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.create(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(resourceGroupResponseAcc);

    Response response = accountResourceGroupApi.createResourceGroupAcc(request, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ACCOUNT),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(Collections.singletonList(resourceFilter), newResourceGroupResponse.getResourceFilter());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGDelete() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseAcc));
    when(resourceGroupService.delete(any(Scope.class), any(String.class))).thenReturn(true);

    Response response = accountResourceGroupApi.deleteResourceGroupAcc(slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ACCOUNT),
        newResourceGroupResponse.getAllowedScopeLevels());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGGet() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseAcc));

    Response response = accountResourceGroupApi.getResourceGroupAcc(slug, account);
    ResourceGroupsResponse resourceGroupsResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(response.getStatus(), 200);
    assertEquals(slug, resourceGroupsResponse.getSlug());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGGetFail() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.empty());
    try {
      accountResourceGroupApi.getResourceGroupAcc(slug, account);
    } catch (InvalidRequestException e) {
      assertEquals("Resource Group with given identifier not found.", e.getMessage());
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGUpdate() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setIncludeAllResources(true);

    resourceGroupResponseAcc.getResourceGroup().setResourceFilter(
        io.harness.resourcegroup.v2.model.ResourceFilter.builder().resources(null).includeAllResources(true).build());

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.update(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseAcc));

    Response response = accountResourceGroupApi.updateResourceGroupAcc(request, slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ACCOUNT),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(true, newResourceGroupResponse.isIncludeAllResources().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testAccountScopedRGList() {
    String searchTerm = randomAlphabetic(10);
    String sort = "name";
    String order = "ASC";
    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder()
            .resourceGroup(ResourceGroupDTO.builder()
                               .accountIdentifier(account)
                               .identifier(slug)
                               .name(name)
                               .allowedScopeLevels(Collections.singleton("account"))
                               .build())
            .build();

    when(resourceGroupService.list(any(), any(), any()))
        .thenReturn(getPage(Collections.singletonList(resourceGroupResponse), 1));

    Response response = accountResourceGroupApi.listResourceGroupsAcc(page, limit, searchTerm, account, sort, order);
    List<ResourceGroupsResponse> entity = (List<ResourceGroupsResponse>) response.getEntity();

    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
    assertEquals(name, entity.get(0).getName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGCreate() {
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

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setResourceFilter(Collections.singletonList(resourceFilter));
    request.setIncludeAllResources(false);

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.create(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(resourceGroupResponseOrg);

    Response response = orgResourceGroupsApi.createResourceGroupOrg(request, org, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ORGANIZATION),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(Collections.singletonList(resourceFilter), newResourceGroupResponse.getResourceFilter());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGDelete() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseOrg));
    when(resourceGroupService.delete(any(Scope.class), any(String.class))).thenReturn(true);

    Response response = orgResourceGroupsApi.deleteResourceGroupOrg(org, slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ORGANIZATION),
        newResourceGroupResponse.getAllowedScopeLevels());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGGet() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseOrg));

    Response response = orgResourceGroupsApi.getResourceGroupOrg(org, slug, account);
    ResourceGroupsResponse resourceGroupsResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(response.getStatus(), 200);
    assertEquals(slug, resourceGroupsResponse.getSlug());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGGetFail() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.empty());
    try {
      orgResourceGroupsApi.getResourceGroupOrg(org, slug, account);
    } catch (InvalidRequestException e) {
      assertEquals("Resource Group with given identifier not found.", e.getMessage());
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGUpdate() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setIncludeAllResources(true);

    resourceGroupResponseOrg.getResourceGroup().setResourceFilter(
        io.harness.resourcegroup.v2.model.ResourceFilter.builder().resources(null).includeAllResources(true).build());

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.update(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseOrg));

    Response response = orgResourceGroupsApi.updateResourceGroupOrg(request, org, slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.ORGANIZATION),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(true, newResourceGroupResponse.isIncludeAllResources().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testOrgScopedRGList() {
    String searchTerm = randomAlphabetic(10);
    String sort = "name";
    String order = "ASC";
    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder()
            .resourceGroup(ResourceGroupDTO.builder()
                               .accountIdentifier(account)
                               .orgIdentifier(org)
                               .identifier(slug)
                               .name(name)
                               .allowedScopeLevels(Collections.singleton("organization"))
                               .build())
            .build();

    when(resourceGroupService.list(any(), any(), any()))
        .thenReturn(getPage(Collections.singletonList(resourceGroupResponse), 1));

    Response response = orgResourceGroupsApi.listResourceGroupsOrg(org, page, limit, searchTerm, account, sort, order);
    List<ResourceGroupsResponse> entity = (List<ResourceGroupsResponse>) response.getEntity();

    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
    assertEquals(name, entity.get(0).getName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGCreate() {
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

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setResourceFilter(Collections.singletonList(resourceFilter));
    request.setIncludeAllResources(false);

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.create(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(resourceGroupResponseProject);

    Response response = projectResourceGroupsApi.createResourceGroupProject(request, org, project, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.PROJECT),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(Collections.singletonList(resourceFilter), newResourceGroupResponse.getResourceFilter());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGDelete() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseProject));
    when(resourceGroupService.delete(any(Scope.class), any(String.class))).thenReturn(true);

    Response response = projectResourceGroupsApi.deleteResourceGroupProject(org, project, slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.PROJECT),
        newResourceGroupResponse.getAllowedScopeLevels());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGGet() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseProject));

    Response response = projectResourceGroupsApi.getResourceGroupProject(org, project, slug, account);
    ResourceGroupsResponse resourceGroupsResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(response.getStatus(), 200);
    assertEquals(slug, resourceGroupsResponse.getSlug());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGGetFail() {
    when(resourceGroupService.get(any(Scope.class), any(String.class), any(ManagedFilter.class)))
        .thenReturn(Optional.empty());
    try {
      projectResourceGroupsApi.getResourceGroupProject(org, project, slug, account);
    } catch (InvalidRequestException e) {
      assertEquals("Resource Group with given identifier not found.", e.getMessage());
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGUpdate() {
    ResourceGroupScope resourceGroupScope = new ResourceGroupScope();
    resourceGroupScope.setFilter(ResourceGroupScope.FilterEnum.EXCLUDING_CHILD_SCOPES);
    resourceGroupScope.setAccount(account);
    resourceGroupScope.setOrg(org);
    resourceGroupScope.setProject(project);
    List<ResourceGroupScope> includedScopes = Collections.singletonList(resourceGroupScope);

    CreateResourceGroupRequest request = new CreateResourceGroupRequest();
    request.setSlug(slug);
    request.setName(name);
    request.setIncludedScope(includedScopes);
    request.setIncludeAllResources(true);

    resourceGroupResponseProject.getResourceGroup().setResourceFilter(
        io.harness.resourcegroup.v2.model.ResourceFilter.builder().resources(null).includeAllResources(true).build());

    doNothing().when(resourceGroupValidator).validateResourceGroup(any());
    when(resourceGroupService.update(any(ResourceGroupDTO.class), any(Boolean.class)))
        .thenReturn(Optional.ofNullable(resourceGroupResponseProject));

    Response response = projectResourceGroupsApi.updateResourceGroupProject(request, org, project, slug, account);
    ResourceGroupsResponse newResourceGroupResponse = (ResourceGroupsResponse) response.getEntity();
    assertEquals(slug, newResourceGroupResponse.getSlug());
    assertEquals(name, newResourceGroupResponse.getName());
    assertEquals(Collections.singletonList(ResourceGroupsResponse.AllowedScopeLevelsEnum.PROJECT),
        newResourceGroupResponse.getAllowedScopeLevels());
    assertEquals(includedScopes, newResourceGroupResponse.getIncludedScope());
    assertEquals(true, newResourceGroupResponse.isIncludeAllResources().booleanValue());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testProjectScopedRGList() {
    String searchTerm = randomAlphabetic(10);
    String sort = "name";
    String order = "ASC";
    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder()
            .resourceGroup(ResourceGroupDTO.builder()
                               .accountIdentifier(account)
                               .orgIdentifier(org)
                               .projectIdentifier(project)
                               .identifier(slug)
                               .name(name)
                               .allowedScopeLevels(Collections.singleton("project"))
                               .build())
            .build();

    when(resourceGroupService.list(any(), any(), any()))
        .thenReturn(getPage(Collections.singletonList(resourceGroupResponse), 1));

    Response response =
        projectResourceGroupsApi.listResourceGroupsProject(org, project, page, limit, searchTerm, account, sort, order);
    List<ResourceGroupsResponse> entity = (List<ResourceGroupsResponse>) response.getEntity();

    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
    assertEquals(name, entity.get(0).getName());
  }
  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testFilterRG() {
    String searchTerm = randomAlphabetic(10);
    ManagedFilterEnum managed = ManagedFilterEnum.NO_FILTER;
    ResourceSelectorFilter selectorFilter = new ResourceSelectorFilter();
    selectorFilter.setResourceType("RESOURCE");
    selectorFilter.setResourceSlug(randomAlphabetic(10));
    List<ResourceSelectorFilter> selector = Collections.singletonList(selectorFilter);
    ResourceGroupFilterRequestBody requestBody = new ResourceGroupFilterRequestBody();
    requestBody.setAccount(account);
    requestBody.setOrg(org);
    requestBody.setProject(project);
    requestBody.setSearchTerm(searchTerm);
    requestBody.setResourceSelectorFilter(selector);
    requestBody.setManagedFilter(managed);
    String sort = "name";
    String order = "ASC";

    ResourceGroupResponse resourceGroupResponse =
        ResourceGroupResponse.builder()
            .resourceGroup(ResourceGroupDTO.builder()
                               .accountIdentifier(account)
                               .orgIdentifier(org)
                               .projectIdentifier(project)
                               .identifier(slug)
                               .name(name)
                               .allowedScopeLevels(Collections.singleton("project"))
                               .build())
            .build();

    when(resourceGroupService.list(any(), any()))
        .thenReturn(getPage(Collections.singletonList(resourceGroupResponse), 1));

    Response response = filterResourceGroupApi.filterResourceGroups(requestBody, account, page, limit, sort, order);
    List<ResourceGroupsResponse> entity = (List<ResourceGroupsResponse>) response.getEntity();

    assertEquals(2, response.getLinks().size());
    assertEquals(1, entity.size());
    assertEquals(slug, entity.get(0).getSlug());
    assertEquals(name, entity.get(0).getName());
  }
}
