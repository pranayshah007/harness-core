/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.serializer.HObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ResourceGroupServiceImplTest extends AccessControlCoreTestBase {
  private ResourceGroupDao resourceGroupDao;
  private RoleAssignmentService roleAssignmentService;
  private TransactionTemplate transactionTemplate;
  private ResourceGroupServiceImpl resourceGroupService;

  @Before
  public void setup() {
    resourceGroupDao = mock(ResourceGroupDao.class);
    roleAssignmentService = mock(RoleAssignmentService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    resourceGroupService =
        spy(new ResourceGroupServiceImpl(resourceGroupDao, roleAssignmentService, transactionTemplate));
  }

  private ResourceGroup getResourceGroup(int count, boolean managed) {
    Set<String> resourceSelectors = new HashSet<>();
    for (int i = 0; i < count; i++) {
      resourceSelectors.add(randomAlphabetic(10));
    }
    return ResourceGroup.builder()
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .allowedScopeLevels(Sets.newHashSet(TestScopeLevels.TEST_SCOPE.toString()))
        .resourceSelectors(resourceSelectors)
        .managed(managed)
        .version(17L)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsert() {
    ResourceGroup currentResourceGroup = getResourceGroup(5, false);
    ResourceGroup currentResourceGroupClone = (ResourceGroup) HObjectMapper.clone(currentResourceGroup);
    Set<String> newResourceSelectors = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      newResourceSelectors.add(randomAlphabetic(10));
    }
    ResourceGroup resourceGroupUpdate = ResourceGroup.builder()
                                            .identifier(currentResourceGroupClone.getIdentifier())
                                            .name(currentResourceGroupClone.getName())
                                            .scopeIdentifier(currentResourceGroupClone.getScopeIdentifier())
                                            .allowedScopeLevels(currentResourceGroupClone.getAllowedScopeLevels())
                                            .resourceSelectors(newResourceSelectors)
                                            .managed(false)
                                            .build();
    ResourceGroup updatedResourceGroup = (ResourceGroup) HObjectMapper.clone(resourceGroupUpdate);
    updatedResourceGroup.setVersion(currentResourceGroup.getVersion() + 1);

    when(resourceGroupDao.get(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier(), ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.of(currentResourceGroup));

    when(resourceGroupDao.upsert(resourceGroupUpdate)).thenReturn(updatedResourceGroup);

    ResourceGroup resourceGroupUpdateResult = resourceGroupService.upsert(resourceGroupUpdate);

    assertEquals(updatedResourceGroup, resourceGroupUpdateResult);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(resourceGroupDao, times(1)).upsert(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertNotFound() {
    ResourceGroup resourceGroupUpdate = getResourceGroup(5, false);
    ResourceGroup updatedResourceGroup = (ResourceGroup) HObjectMapper.clone(resourceGroupUpdate);

    when(resourceGroupDao.get(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier(), ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.empty());

    when(resourceGroupDao.upsert(resourceGroupUpdate)).thenReturn(updatedResourceGroup);

    ResourceGroup resourceGroupUpdateResult = resourceGroupService.upsert(resourceGroupUpdate);

    assertEquals(updatedResourceGroup, resourceGroupUpdateResult);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(resourceGroupDao, times(1)).upsert(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertScopeLevelsUpdated() {
    ResourceGroup currentResourceGroup = getResourceGroup(5, true);
    ResourceGroup currentResourceGroupClone = (ResourceGroup) HObjectMapper.clone(currentResourceGroup);
    Set<String> newResourceSelectors = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      newResourceSelectors.add(randomAlphabetic(10));
    }
    ResourceGroup resourceGroupUpdate = ResourceGroup.builder()
                                            .identifier(currentResourceGroupClone.getIdentifier())
                                            .name(currentResourceGroupClone.getName())
                                            .scopeIdentifier(currentResourceGroupClone.getScopeIdentifier())
                                            .allowedScopeLevels(new HashSet<>())
                                            .resourceSelectors(newResourceSelectors)
                                            .managed(true)
                                            .build();
    ResourceGroup updatedResourceGroup = (ResourceGroup) HObjectMapper.clone(resourceGroupUpdate);
    updatedResourceGroup.setVersion(currentResourceGroup.getVersion() + 1);

    when(resourceGroupDao.get(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier(), ManagedFilter.ONLY_MANAGED))
        .thenReturn(Optional.of(currentResourceGroup));

    when(transactionTemplate.execute(any())).thenReturn(updatedResourceGroup);

    ResourceGroup resourceGroupUpdateResult = resourceGroupService.upsert(resourceGroupUpdate);

    assertEquals(updatedResourceGroup, resourceGroupUpdateResult);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertInvalid() {
    ResourceGroup currentResourceGroup = getResourceGroup(5, false);
    ResourceGroup resourceGroupUpdate = ResourceGroup.builder()
                                            .identifier(currentResourceGroup.getIdentifier())
                                            .name(currentResourceGroup.getName())
                                            .scopeIdentifier(currentResourceGroup.getScopeIdentifier())
                                            .allowedScopeLevels(new HashSet<>())
                                            .resourceSelectors(currentResourceGroup.getResourceSelectors())
                                            .managed(false)
                                            .build();
    when(resourceGroupDao.get(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier(), ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.of(currentResourceGroup));

    resourceGroupService.upsert(resourceGroupUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    List<ManagedFilter> managedFiltersList =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (ManagedFilter managedFilter : managedFiltersList) {
      invocations += 1;
      when(resourceGroupDao.get(identifier, scopeIdentifier, managedFilter))
          .thenReturn(
              Optional.of(ResourceGroup.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build()));
      Optional<ResourceGroup> resourceGroup = resourceGroupService.get(identifier, scopeIdentifier, managedFilter);
      assertTrue(resourceGroup.isPresent());
      assertEquals(identifier, resourceGroup.get().getIdentifier());
      assertEquals(scopeIdentifier, resourceGroup.get().getScopeIdentifier());
      verify(resourceGroupDao, times(1)).get(identifier, scopeIdentifier, managedFilter);
      verify(resourceGroupDao, times(invocations)).get(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    List<String> identifiers = Lists.newArrayList(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    List<ResourceGroup> resourceGroups =
        Lists.newArrayList(getResourceGroup(5, false), getResourceGroup(5, false), getResourceGroup(5, false));
    String scopeIdentifier = randomAlphabetic(10);
    List<ManagedFilter> managedFiltersList =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (ManagedFilter managedFilter : managedFiltersList) {
      invocations += 1;
      when(resourceGroupDao.list(identifiers, scopeIdentifier, managedFilter)).thenReturn(resourceGroups);
      List<ResourceGroup> response = resourceGroupService.list(identifiers, scopeIdentifier, managedFilter);
      assertEquals(3, response.size());
      verify(resourceGroupDao, times(1)).list(identifiers, scopeIdentifier, managedFilter);
      verify(resourceGroupDao, times(invocations)).list(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListScopeIdentifier() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).build();
    List<String> scopeIdentifiers = Lists.newArrayList(randomAlphabetic(10), null);
    int invocations = 0;
    for (String scopeIdentifier : scopeIdentifiers) {
      invocations += 1;
      when(resourceGroupDao.list(pageRequest, scopeIdentifier))
          .thenReturn(PageResponse.getEmptyPageResponse(pageRequest));
      PageResponse<ResourceGroup> response = resourceGroupService.list(pageRequest, scopeIdentifier);
      assertTrue(response.isEmpty());
      verify(resourceGroupDao, times(1)).list(pageRequest, scopeIdentifier);
      verify(resourceGroupDao, times(invocations)).list(any(), any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    ResourceGroup resourceGroup =
        ResourceGroup.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build();
    when(resourceGroupDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.of(resourceGroup));
    when(transactionTemplate.execute(any())).thenReturn(resourceGroup);
    ResourceGroup deletedResourceGroup = resourceGroupService.delete(identifier, scopeIdentifier);
    assertEquals(resourceGroup, deletedResourceGroup);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(resourceGroupDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM)).thenReturn(Optional.empty());
    resourceGroupService.delete(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteManagedIfPresent() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = ResourceGroup.builder().identifier(identifier).build();
    when(resourceGroupDao.get(identifier, null, ManagedFilter.ONLY_MANAGED)).thenReturn(Optional.of(resourceGroup));
    when(transactionTemplate.execute(any())).thenReturn(resourceGroup);
    resourceGroupService.deleteManagedIfPresent(identifier);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteManagedIfPresentNotFound() {
    String identifier = randomAlphabetic(10);
    when(resourceGroupDao.get(identifier, null, ManagedFilter.ONLY_MANAGED)).thenReturn(Optional.empty());
    resourceGroupService.deleteManagedIfPresent(identifier);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresent() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    ResourceGroup resourceGroup =
        ResourceGroup.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build();
    when(resourceGroupDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.of(resourceGroup));
    when(transactionTemplate.execute(any())).thenReturn(resourceGroup);
    resourceGroupService.deleteIfPresent(identifier, scopeIdentifier);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresentNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(resourceGroupDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM)).thenReturn(Optional.empty());
    resourceGroupService.deleteIfPresent(identifier, scopeIdentifier);
    verify(resourceGroupDao, times(1)).get(any(), any(), any());
  }
}
