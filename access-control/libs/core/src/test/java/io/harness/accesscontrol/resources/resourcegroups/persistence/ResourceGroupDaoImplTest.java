/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.persistence;

import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO.ResourceGroupDBOKeys;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.BasicDBList;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class ResourceGroupDaoImplTest {
  private ResourceGroupRepository resourceGroupRepository;
  private ScopeService scopeService;
  private ResourceGroupDaoImpl resourceGroupDao;

  @Before
  public void setup() {
    resourceGroupRepository = mock(ResourceGroupRepository.class);
    scopeService = mock(ScopeService.class);
    resourceGroupDao = spy(new ResourceGroupDaoImpl(resourceGroupRepository, scopeService));
  }

  private ResourceGroup getResourceGroup(int count) {
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
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsert() {
    ResourceGroup currentResourceGroup = getResourceGroup(5);
    ResourceGroupDBO currentResourceGroupDBO = toDBO(currentResourceGroup);
    Set<String> newResourceSelectors = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      newResourceSelectors.add(randomAlphabetic(10));
    }
    ResourceGroup resourceGroupUpdate = ResourceGroup.builder()
                                            .identifier(currentResourceGroup.getIdentifier())
                                            .name(currentResourceGroup.getName())
                                            .scopeIdentifier(currentResourceGroup.getScopeIdentifier())
                                            .allowedScopeLevels(currentResourceGroup.getAllowedScopeLevels())
                                            .resourceSelectors(newResourceSelectors)
                                            .build();
    ResourceGroup resourceGroupUpdateClone = (ResourceGroup) HObjectMapper.clone(resourceGroupUpdate);
    ResourceGroupDBO resourceGroupUpdateCloneDBO = toDBO(resourceGroupUpdateClone);
    resourceGroupUpdateCloneDBO.setId(currentResourceGroupDBO.getId());
    resourceGroupUpdateCloneDBO.setVersion(currentResourceGroupDBO.getVersion());
    resourceGroupUpdateCloneDBO.setCreatedAt(currentResourceGroupDBO.getCreatedAt());
    resourceGroupUpdateCloneDBO.setLastModifiedAt(currentResourceGroupDBO.getLastModifiedAt());
    resourceGroupUpdateCloneDBO.setNextReconciliationIterationAt(
        currentResourceGroupDBO.getNextReconciliationIterationAt());
    when(resourceGroupRepository.findByIdentifierAndScopeIdentifier(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier()))
        .thenReturn(Optional.of(currentResourceGroupDBO));
    assertUpsert(resourceGroupUpdate, resourceGroupUpdateCloneDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertNotFound() {
    ResourceGroup resourceGroupUpdate = getResourceGroup(5);
    ResourceGroup resourceGroupUpdateClone = (ResourceGroup) HObjectMapper.clone(resourceGroupUpdate);
    ResourceGroupDBO resourceGroupUpdateCloneDBO = toDBO(resourceGroupUpdateClone);
    when(resourceGroupRepository.findByIdentifierAndScopeIdentifier(
             resourceGroupUpdate.getIdentifier(), resourceGroupUpdate.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    assertUpsert(resourceGroupUpdate, resourceGroupUpdateCloneDBO);
  }

  private void assertUpsert(ResourceGroup resourceGroupUpdate, ResourceGroupDBO resourceGroupForValidation) {
    when(resourceGroupRepository.save(resourceGroupForValidation)).thenReturn(resourceGroupForValidation);
    ResourceGroup savedResourceGroup = resourceGroupDao.upsert(resourceGroupUpdate);
    verify(resourceGroupRepository, times(1)).findByIdentifierAndScopeIdentifier(any(), any());
    verify(resourceGroupRepository, times(1)).save(any());
    assertEquals(resourceGroupForValidation.getIdentifier(), savedResourceGroup.getIdentifier());
    assertEquals(resourceGroupForValidation.getScopeIdentifier(), savedResourceGroup.getScopeIdentifier());
    assertEquals(resourceGroupForValidation.getAllowedScopeLevels(), savedResourceGroup.getAllowedScopeLevels());
    assertEquals(resourceGroupForValidation.getName(), savedResourceGroup.getName());
    assertEquals(resourceGroupForValidation.getResourceSelectors(), savedResourceGroup.getResourceSelectors());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    ResourceGroup resourceGroup = getResourceGroup(5);
    ResourceGroupDBO resourceGroupDBO = toDBO(resourceGroup);
    when(scopeService.buildScopeFromScopeIdentifier(resourceGroup.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    when(resourceGroupRepository.find(any())).thenReturn(Optional.of(resourceGroupDBO));
    Set<ManagedFilter> managedFilters =
        Sets.newHashSet(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    managedFilters.forEach(managedFilter -> {
      Optional<ResourceGroup> resourceGroupOptional =
          resourceGroupDao.get(resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier(), managedFilter);
      assertTrue(resourceGroupOptional.isPresent());
      assertEquals(resourceGroup, resourceGroupOptional.get());
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    when(resourceGroupRepository.find(any())).thenReturn(Optional.empty());
    Set<ManagedFilter> managedFilters =
        Sets.newHashSet(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    managedFilters.forEach(managedFilter -> {
      Optional<ResourceGroup> resourceGroupOptional = resourceGroupDao.get(identifier, scopeIdentifier, managedFilter);
      assertFalse(resourceGroupOptional.isPresent());
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetCriteria() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    List<ManagedFilter> managedFilters =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (ManagedFilter managedFilter : managedFilters) {
      invocations += 1;
      ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
      when(resourceGroupRepository.find(any())).thenReturn(Optional.empty());
      when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
          .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
      resourceGroupDao.get(identifier, scopeIdentifier, managedFilter);
      verify(resourceGroupRepository, times(invocations)).find(criteriaArgumentCaptor.capture());
      assertFilterCriteria(singletonList(identifier), scopeIdentifier, managedFilter, criteriaArgumentCaptor);
    }
  }

  private void assertFilterCriteria(List<String> identifiers, String scopeIdentifier, ManagedFilter managedFilter,
      ArgumentCaptor<Criteria> criteriaArgumentCaptor) {
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    int expectedCount = 0;

    if (ManagedFilter.NO_FILTER.equals(managedFilter)) {
      expectedCount += 1;
      BasicDBList orDBList = (BasicDBList) document.get("$or");
      assertNotNull(orDBList);
      assertEquals(2, orDBList.size());
      Document managedCriteria = (Document) orDBList.get(0);
      assertNull(managedCriteria.get(ResourceGroupDBOKeys.scopeIdentifier));
      assertEquals(true, managedCriteria.get(ResourceGroupDBOKeys.managed));
      if (isNotEmpty(scopeIdentifier)) {
        assertEquals(
            TestScopeLevels.TEST_SCOPE.toString(), managedCriteria.get(ResourceGroupDBOKeys.allowedScopeLevels));
      }
      Document customCriteria = (Document) orDBList.get(1);
      assertEquals(false, customCriteria.get(ResourceGroupDBOKeys.managed));
      assertEquals(scopeIdentifier, customCriteria.get(ResourceGroupDBOKeys.scopeIdentifier));
    } else if (ManagedFilter.ONLY_CUSTOM.equals(managedFilter)) {
      expectedCount += 2;
      assertEquals(false, document.get(ResourceGroupDBOKeys.managed));
      assertEquals(scopeIdentifier, document.get(ResourceGroupDBOKeys.scopeIdentifier));
    } else if (ManagedFilter.ONLY_MANAGED.equals(managedFilter)) {
      expectedCount += 2;
      assertEquals(true, document.get(ResourceGroupDBOKeys.managed));
      assertNull(document.get(ResourceGroupDBOKeys.scopeIdentifier));
      if (isNotEmpty(scopeIdentifier)) {
        expectedCount += 1;
        assertEquals(TestScopeLevels.TEST_SCOPE.toString(), document.get(ResourceGroupDBOKeys.allowedScopeLevels));
      }
    }

    expectedCount += 1;
    Document inIdentifierDocument = (Document) document.get(ResourceGroupDBOKeys.identifier);
    List<?> inDocument = (List<?>) inIdentifierDocument.get("$in");
    assertEquals(identifiers, inDocument);
    assertEquals(expectedCount, document.size());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    ResourceGroup resourceGroup = getResourceGroup(5);
    ResourceGroupDBO resourceGroupDBO = toDBO(resourceGroup);
    List<Boolean> managedFilters = Lists.newArrayList(false, true);
    managedFilters.forEach(managedFilter -> {
      when(resourceGroupRepository.deleteByIdentifierAndScopeIdentifier(
               resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier()))
          .thenReturn(Optional.of(resourceGroupDBO));
      Optional<ResourceGroup> resourceGroupOptional =
          resourceGroupDao.delete(resourceGroup.getIdentifier(), resourceGroup.getScopeIdentifier());
      assertTrue(resourceGroupOptional.isPresent());
      assertEquals(resourceGroup, resourceGroupOptional.get());
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    List<Boolean> managedFilters = Lists.newArrayList(false, true);
    managedFilters.forEach(managedFilter -> {
      when(resourceGroupRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
          .thenReturn(Optional.empty());
      Optional<ResourceGroup> resourceGroupOptional = resourceGroupDao.delete(identifier, scopeIdentifier);
      assertFalse(resourceGroupOptional.isPresent());
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListScopeIdentifier() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    List<ResourceGroupDBO> dbResult =
        Lists.newArrayList(toDBO(getResourceGroup(5)), toDBO(getResourceGroup(5)), toDBO(getResourceGroup(5)));
    List<String> scopeIdentifiers = Lists.newArrayList(randomAlphabetic(10), null);
    int invocations = 0;
    for (String scopeIdentifier : scopeIdentifiers) {
      invocations += 1;
      when(resourceGroupRepository.findByScopeIdentifier(scopeIdentifier, PageUtils.getPageRequest(pageRequest)))
          .thenReturn(PageTestUtils.getPage(dbResult, 3));
      PageResponse<ResourceGroup> response = resourceGroupDao.list(pageRequest, scopeIdentifier);
      verify(resourceGroupRepository, times(1))
          .findByScopeIdentifier(scopeIdentifier, PageUtils.getPageRequest(pageRequest));
      verify(resourceGroupRepository, times(invocations)).findByScopeIdentifier(any(), any());
      assertEquals(3, response.getContent().size());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListCriteria() {
    List<ResourceGroupDBO> dbResult =
        Lists.newArrayList(toDBO(getResourceGroup(5)), toDBO(getResourceGroup(5)), toDBO(getResourceGroup(5)));
    List<String> identifiers = new ArrayList<>();
    dbResult.forEach(resourceGroup -> identifiers.add(resourceGroup.getIdentifier()));
    String scopeIdentifier = randomAlphabetic(10);
    Set<ManagedFilter> managedFilters =
        Sets.newHashSet(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    when(resourceGroupRepository.findAllWithCriteria(any())).thenReturn(dbResult);
    int invocations = 0;

    when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    for (ManagedFilter managedFilter : managedFilters) {
      invocations += 1;
      ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
      List<ResourceGroup> resourceGroups = resourceGroupDao.list(identifiers, scopeIdentifier, managedFilter);
      verify(resourceGroupRepository, times(invocations)).findAllWithCriteria(criteriaArgumentCaptor.capture());
      assertEquals(3, resourceGroups.size());
      assertFilterCriteria(identifiers, scopeIdentifier, managedFilter, criteriaArgumentCaptor);
    }

    scopeIdentifier = null;
    for (ManagedFilter managedFilter : managedFilters) {
      ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
      List<ResourceGroup> resourceGroups;
      try {
        resourceGroups = resourceGroupDao.list(identifiers, scopeIdentifier, managedFilter);
        if (!ONLY_MANAGED.equals(managedFilter)) {
          fail();
        }
      } catch (InvalidRequestException exception) {
        if (ONLY_MANAGED.equals(managedFilter)) {
          fail();
        }
        continue;
      }
      invocations += 1;
      verify(resourceGroupRepository, times(invocations)).findAllWithCriteria(criteriaArgumentCaptor.capture());
      assertEquals(3, resourceGroups.size());
      assertFilterCriteria(identifiers, scopeIdentifier, managedFilter, criteriaArgumentCaptor);
    }
  }
}
