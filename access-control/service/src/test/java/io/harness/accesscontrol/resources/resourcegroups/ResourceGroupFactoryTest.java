/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.REETIKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.harness.category.element.UnitTests;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceGroupFactoryTest {
  private ResourceGroupFactory resourceGroupFactory;

  @Before
  public void setup() {
    resourceGroupFactory = spy(ResourceGroupFactory.class);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testWithEmptyIncludedScopes() {
    ResourceGroupDTO resourceGroupDTO = ResourceGroupDTO.builder()
                                            .identifier("rg1")
                                            .includedScopes(null)
                                            .resourceFilter(ResourceFilter.builder().includeAllResources(true).build())
                                            .build();
    Set<ResourceSelector> resourceSelector = resourceGroupFactory.buildResourceSelector(resourceGroupDTO);
    assertThat(resourceSelector.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void ResourceGroup_WithIncludedScopesButEmptyResourcesFilter_ReturnsZeroResourceSelectors() {
    ResourceGroupDTO resourceGroupDTO =
        ResourceGroupDTO.builder()
            .identifier("rg1")
            .includedScopes(List.of(ScopeSelector.builder().filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES).build()))
            .resourceFilter(ResourceFilter.builder().includeAllResources(false).build())
            .build();
    Set<ResourceSelector> resourceSelector = resourceGroupFactory.buildResourceSelector(resourceGroupDTO);
    assertThat(resourceSelector.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testWithEmptyResourceFilter() {
    ResourceGroupDTO resourceGroupDTO =
        ResourceGroupDTO.builder()
            .identifier("rg1")
            .includedScopes(
                Arrays.asList(ScopeSelector.builder().filter(ScopeFilterType.INCLUDING_CHILD_SCOPES).build()))
            .resourceFilter(null)
            .build();
    Set<ResourceSelector> resourceSelector = resourceGroupFactory.buildResourceSelector(resourceGroupDTO);
    assertThat(resourceSelector.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testWithResourceSelectorByScope() {
    ResourceGroupDTO resourceGroupDTO =
        ResourceGroupDTO.builder()
            .identifier("rg1")
            .includedScopes(Arrays.asList(ScopeSelector.builder()
                                              .filter(ScopeFilterType.INCLUDING_CHILD_SCOPES)
                                              .accountIdentifier("testAcc1")
                                              .build()))
            .resourceFilter(ResourceFilter.builder().includeAllResources(true).build())
            .build();
    Set<ResourceSelector> resourceSelector = resourceGroupFactory.buildResourceSelector(resourceGroupDTO);
    assertThat(resourceSelector.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testBuildResourceSelector() {
    ResourceFilter resourceFilter =
        ResourceFilter.builder()
            .includeAllResources(false)
            .resources(
                Arrays.asList(io.harness.resourcegroup.v2.model.ResourceSelector.builder().resourceType("R1").build(),
                    io.harness.resourcegroup.v2.model.ResourceSelector.builder().resourceType("R2").build(),
                    io.harness.resourcegroup.v2.model.ResourceSelector.builder()
                        .resourceType("R3")
                        .identifiers(Arrays.asList("1", "2", "3"))
                        .build()))
            .build();
    List<ScopeSelector> includedScopes = Arrays.asList(
        ScopeSelector.builder().filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES).accountIdentifier("testAcc1").build(),
        ScopeSelector.builder()
            .filter(ScopeFilterType.INCLUDING_CHILD_SCOPES)
            .accountIdentifier("testAcc1")
            .orgIdentifier("testOrg1")
            .build(),
        ScopeSelector.builder()
            .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
            .accountIdentifier("testAcc1")
            .orgIdentifier("testOrg1")
            .projectIdentifier("proj1")
            .build());
    ResourceGroupDTO resourceGroupDTO = ResourceGroupDTO.builder()
                                            .identifier("testRG")
                                            .includedScopes(includedScopes)
                                            .resourceFilter(resourceFilter)
                                            .build();
    Set<ResourceSelector> resourceSelector = resourceGroupFactory.buildResourceSelector(resourceGroupDTO);
    assertThat(resourceSelector.size()).isEqualTo(15);
  }
}
