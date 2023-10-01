/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.resource;

import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.resourcegroup.beans.ScopeFilterType.EXCLUDING_CHILD_SCOPES;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class HarnessResourceGroupResourceImplTest extends ResourceGroupTestBase {
  ResourceGroupService resourceGroupService;
  ResourceGroupValidatorImpl resourceGroupValidator;
  HarnessResourceGroupResourceImpl harnessResourceGroupResource;
  AccessControlClient accessControlClient;

  @Before
  public void setup() {
    resourceGroupService = mock(ResourceGroupService.class);
    resourceGroupValidator = mock(ResourceGroupValidatorImpl.class);
    accessControlClient = mock(AccessControlClient.class);
    harnessResourceGroupResource =
        new HarnessResourceGroupResourceImpl(resourceGroupService, resourceGroupValidator, accessControlClient);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void create_WithoutIncludedScopes_CreatesResourceGroup_WithIncludedScopes() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    ResourceGroupRequest resourceGroupRequest =
        ResourceGroupRequest.builder().resourceGroup(ResourceGroupDTO.builder().build()).build();
    ResourceGroupRequest expectedResourceGroupRequest =
        ResourceGroupRequest.builder().resourceGroup(ResourceGroupDTO.builder().build()).build();
    expectedResourceGroupRequest.getResourceGroup().setAllowedScopeLevels(
        Sets.newHashSet(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase()));

    List<ScopeSelector> includedScopes = new ArrayList<>();
    ScopeSelector scopeSelector = ScopeSelector.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .filter(EXCLUDING_CHILD_SCOPES)
                                      .build();
    includedScopes.add(scopeSelector);
    expectedResourceGroupRequest.getResourceGroup().setIncludedScopes(includedScopes);
    doNothing().when(resourceGroupValidator).validateResourceGroup(resourceGroupRequest);
    ResourceGroupResponse resourceGroupResponse = ResourceGroupResponse.builder().build();
    when(resourceGroupService.create(resourceGroupRequest.getResourceGroup(), false)).thenReturn(resourceGroupResponse);
    ResponseDTO<ResourceGroupResponse> resourceGroupResponseResponseDTO =
        harnessResourceGroupResource.create(accountIdentifier, orgIdentifier, projectIdentifier, resourceGroupRequest);
    ResponseDTO<ResourceGroupResponse> expectedResourceGroupResponseResponseDTO =
        ResponseDTO.newResponse(resourceGroupResponse);

    assertEquals(resourceGroupResponseResponseDTO.getData(), expectedResourceGroupResponseResponseDTO.getData());
    verify(resourceGroupValidator, times(1)).validateResourceGroup(resourceGroupRequest);
    verify(resourceGroupService, times(1)).create(resourceGroupRequest.getResourceGroup(), false);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testListWithViewPermission() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .build();
    PageRequest pageRequest = PageRequest.builder().build();
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(true);
    when(resourceGroupService.list(resourceGroupFilterDTO, pageRequest))
        .thenReturn(new PageImpl<>(List.of(mock(ResourceGroupResponse.class))));

    harnessResourceGroupResource.list(resourceGroupFilterDTO, accountIdentifier, pageRequest);
    verify(resourceGroupService, times(1)).list(resourceGroupFilterDTO, pageRequest);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testListWithoutViewPermission() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .build();
    PageRequest pageRequest = PageRequest.builder().pageSize(1).build();
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(false);

    List<ResourceGroup> resourceGroupsList = List.of(ResourceGroup.builder().build());
    Page<ResourceGroup> allResourceGroups = new PageImpl<>(resourceGroupsList);
    when(resourceGroupService.listAll(resourceGroupFilterDTO, PageRequest.builder().pageSize(50000).build()))
        .thenReturn(allResourceGroups);

    when(resourceGroupService.getPermittedResourceGroups(resourceGroupsList)).thenReturn(resourceGroupsList);
    harnessResourceGroupResource.list(resourceGroupFilterDTO, accountIdentifier, pageRequest);

    verify(resourceGroupService, times(1))
        .listAll(resourceGroupFilterDTO, PageRequest.builder().pageSize(50000).build());
    verify(resourceGroupService, times(1)).getPermittedResourceGroups(resourceGroupsList);
  }

  @Test(expected = AccessDeniedException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testListWithoutViewPermissionException() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .build();
    PageRequest pageRequest = PageRequest.builder().build();
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(false);
    List<ResourceGroup> resourceGroupsList = List.of(mock(ResourceGroup.class));
    Page<ResourceGroup> allResourceGroups = new PageImpl<>(resourceGroupsList);
    when(resourceGroupService.listAll(resourceGroupFilterDTO, PageRequest.builder().pageSize(50000).build()))
        .thenReturn(allResourceGroups);

    when(resourceGroupService.getPermittedResourceGroups(resourceGroupsList)).thenReturn(new ArrayList<>());
    harnessResourceGroupResource.list(resourceGroupFilterDTO, accountIdentifier, pageRequest);

    verify(resourceGroupService, times(1))
        .listAll(resourceGroupFilterDTO, PageRequest.builder().pageSize(50000).build());
    verify(resourceGroupService, times(1)).getPermittedResourceGroups(resourceGroupsList);
  }
}
