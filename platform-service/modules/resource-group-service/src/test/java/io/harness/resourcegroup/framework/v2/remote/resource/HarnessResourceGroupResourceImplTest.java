/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.resource;

import static io.harness.resourcegroup.beans.ScopeFilterType.EXCLUDING_CHILD_SCOPES;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
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

public class HarnessResourceGroupResourceImplTest extends ResourceGroupTestBase {
  ResourceGroupService resourceGroupService;
  ResourceGroupValidatorImpl resourceGroupValidator;
  HarnessResourceGroupResourceImpl harnessResourceGroupResource;

  @Before
  public void setup() {
    resourceGroupService = mock(ResourceGroupService.class);
    resourceGroupValidator = mock(ResourceGroupValidatorImpl.class);
    harnessResourceGroupResource = new HarnessResourceGroupResourceImpl(resourceGroupService, resourceGroupValidator);
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
}
