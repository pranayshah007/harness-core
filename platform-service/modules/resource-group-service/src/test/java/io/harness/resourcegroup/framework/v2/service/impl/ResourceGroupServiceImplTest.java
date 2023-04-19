/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.service.impl;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.resourcegroup.ResourceGroupPermissions.VIEW_RESOURCEGROUP_PERMISSION;
import static io.harness.resourcegroup.ResourceGroupResourceTypes.RESOURCE_GROUP;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.api.OutboxService;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.repositories.spring.ResourceGroupV2Repository;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ResourceGroup.ResourceGroupBuilder;
import io.harness.resourcegroup.v2.model.ResourceGroup.ResourceGroupKeys;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ResourceGroupServiceImplTest extends ResourceGroupTestBase {
  @Inject private ResourceGroupV2Repository resourceGroupV2Repository;
  @Inject private ResourceGroupValidatorImpl resourceGroupValidatorImpl;
  private ResourceGroupV2Repository resourceGroupV2RepositoryMock;
  private io.harness.resourcegroup.framework.v1.service.ResourceGroupService resourceGroupV1ServiceMock;
  private OutboxService outboxService;
  private TransactionTemplate transactionTemplate;
  private ResourceGroupServiceImpl resourceGroupService;
  private ResourceGroupServiceImpl resourceGroupServiceMockRepo;
  private PageRequest pageRequest;
  private AccessControlClient accessControlClient;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  List<ResourceGroup> resourceGroupList = new ArrayList<>();
  List<ResourceGroup> permittedResourceGroups = new ArrayList<>();
  List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

  @Before
  public void setup() {
    resourceGroupV2RepositoryMock = mock(ResourceGroupV2Repository.class);
    resourceGroupV1ServiceMock = mock(io.harness.resourcegroup.framework.v1.service.ResourceGroupService.class);
    outboxService = mock(OutboxService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    accessControlClient = mock(AccessControlClient.class);

    resourceGroupService = spy(new ResourceGroupServiceImpl(resourceGroupV2Repository, resourceGroupValidatorImpl,
        outboxService, transactionTemplate, accessControlClient));
    resourceGroupServiceMockRepo = spy(new ResourceGroupServiceImpl(resourceGroupV2RepositoryMock,
        resourceGroupValidatorImpl, outboxService, transactionTemplate, accessControlClient));

    pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();

    ResourceGroupBuilder resourceGroupBuilder = ResourceGroup.builder()
                                                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                    .projectIdentifier(PROJECT_IDENTIFIER);

    resourceGroupList.add(resourceGroupBuilder.identifier("RG1").createdAt(1674552111L).build());
    resourceGroupList.add(resourceGroupBuilder.identifier("RG2").createdAt(1674552145L).build());
    resourceGroupList.add(resourceGroupBuilder.identifier("RG3").createdAt(1674552157L).build());

    permittedResourceGroups.add(resourceGroupBuilder.identifier("RG2").build());
    permittedResourceGroups.add(resourceGroupBuilder.identifier("RG1").build());

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(RESOURCE_GROUP)
                                                          .permission("core_resourcegroup_view")
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJECT_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("RG1").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("RG2").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("RG3").build());
  }

  private Set<String> getRandomStrings(int count) {
    Set<String> strings = new HashSet<>();
    for (int i = 0; i < count; i++) {
      strings.add(randomAlphabetic(10));
    }
    return strings;
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.NO_FILTER);

    Criteria criteria =
        getActualGetCriteria(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.NO_FILTER);
    assertGetNoFilterCriteria(criteria, accountIdentifier, orgIdentifier, null, identifier);
  }

  private void assertGetNoFilterCriteria(
      Criteria criteria, String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria expectedCriteria = Criteria.where(ResourceGroupKeys.identifier).is(identifier);
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
        .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    expectedCriteria.orOperator(scopeExpectedCriteria, expectedManagedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetOnlyManagedFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_MANAGED);

    Criteria criteria =
        getActualGetCriteria(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_MANAGED);
    assertGetOnlyManagedFilterCriteria(criteria, accountIdentifier, orgIdentifier, null, identifier);
  }

  private void assertGetOnlyManagedFilterCriteria(
      Criteria criteria, String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria expectedCriteria = Criteria.where(ResourceGroupKeys.identifier).is(identifier);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    if (isNotEmpty(accountIdentifier)) {
      expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
          .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    }
    expectedCriteria.andOperator(expectedManagedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetOnlyCustomFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_CUSTOM);

    Criteria criteria =
        getActualGetCriteria(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_CUSTOM);
    assertGetOnlyCustomFilterCriteria(criteria, accountIdentifier, orgIdentifier, null, identifier);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpsertCustomCriteria() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = ResourceGroup.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .identifier(identifier)
                                      .build();

    resourceGroupService.upsert(resourceGroup, true);
    Criteria criteriaCustom =
        getActualGetCriteria(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_CUSTOM);
    assertGetOnlyCustomFilterCriteria(criteriaCustom, accountIdentifier, orgIdentifier, null, identifier);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpsertManagedCriteria() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = ResourceGroup.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .identifier(identifier)
                                      .harnessManaged(true)
                                      .build();

    resourceGroupService.upsert(resourceGroup, true);
    Criteria criteriaCustom =
        getActualGetCriteria(Scope.of(accountIdentifier, orgIdentifier, null), identifier, ManagedFilter.ONLY_MANAGED);
    assertGetOnlyManagedFilterCriteria(criteriaCustom, accountIdentifier, orgIdentifier, null, identifier);
  }

  private void assertGetOnlyCustomFilterCriteria(
      Criteria criteria, String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria expectedCriteria = Criteria.where(ResourceGroupKeys.identifier).is(identifier);
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    expectedCriteria.andOperator(scopeExpectedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetNullScope() {
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(null, identifier, ManagedFilter.NO_FILTER);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetNullScopeOnlyManagedFilter() {
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(Scope.of(null, null, null), identifier, ManagedFilter.ONLY_MANAGED);

    Criteria criteria = getActualGetCriteria(null, identifier, ManagedFilter.ONLY_MANAGED);
    assertGetOnlyManagedFilterCriteria(criteria, null, null, null, identifier);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetNullScopeOnlyCustomFilter() {
    String identifier = randomAlphabetic(10);
    resourceGroupService.get(null, identifier, ManagedFilter.ONLY_CUSTOM);
  }

  private Criteria getActualGetCriteria(Scope scope, String identifier, ManagedFilter managedFilter) {
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.find(any())).thenReturn(Optional.empty());
    resourceGroupServiceMockRepo.get(scope, identifier, managedFilter);
    verify(resourceGroupV2RepositoryMock).find(criteriaArgumentCaptor.capture());
    return criteriaArgumentCaptor.getValue();
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListScopeFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
        .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    expectedCriteria.andOperator(new Criteria().orOperator(scopeExpectedCriteria, expectedManagedCriteria));
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListScopeOnlyManagedFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .managedFilter(ManagedFilter.ONLY_MANAGED)
                                                        .build();
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
        .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    expectedCriteria.andOperator(expectedManagedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListScopeOnlyCustomFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .managedFilter(ManagedFilter.ONLY_CUSTOM)
                                                        .build();
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    expectedCriteria.andOperator(scopeExpectedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListIdentifierFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    Set<String> identifierFilter = getRandomStrings(5);
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .identifierFilter(identifierFilter)
                                                        .build();
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = Criteria.where(ResourceGroupKeys.identifier).in(identifierFilter);
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
        .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    expectedCriteria.andOperator(new Criteria().orOperator(scopeExpectedCriteria, expectedManagedCriteria));
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListNullScopeAndScopeLevelFilter() {
    Set<String> scopeLevelFilter = getRandomStrings(3);
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().managedFilter(ManagedFilter.ONLY_MANAGED).build();
    resourceGroupFilterDTO.setScopeLevelFilter(scopeLevelFilter);
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    expectedCriteria.and(ResourceGroupKeys.allowedScopeLevels).in(scopeLevelFilter);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();

    expectedCriteria.andOperator(expectedManagedCriteria);
    assertEquals(expectedCriteria, criteria);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListScopeAndScopeLevelFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    Set<String> scopeLevelFilter = getRandomStrings(5);
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    resourceGroupFilterDTO.setScopeLevelFilter(scopeLevelFilter);
    when(accessControlClient.hasAccess(
             ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                 resourceGroupFilterDTO.getProjectIdentifier()),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(true);

    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    if (isNotEmpty(accountIdentifier)) {
      expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
          .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    }
    expectedCriteria.andOperator(new Criteria().orOperator(scopeExpectedCriteria, expectedManagedCriteria));
    assertEquals(expectedCriteria, criteria);
  }

  private Criteria getExpectedScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(ResourceGroupKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(ResourceGroupKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(ResourceGroupKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(ResourceGroupKeys.harnessManaged)
        .ne(true);
  }

  private Criteria getExpectedManagedCriteria() {
    return Criteria.where(ResourceGroupKeys.accountIdentifier)
        .is(null)
        .and(ResourceGroupKeys.orgIdentifier)
        .is(null)
        .and(ResourceGroupKeys.projectIdentifier)
        .is(null)
        .and(ResourceGroupKeys.harnessManaged)
        .is(true);
  }

  private Criteria getActualListCriteria(ResourceGroupFilterDTO resourceGroupFilterDTO) {
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.findAll(any(), any())).thenReturn(PageTestUtils.getPage(emptyList(), 0));
    resourceGroupServiceMockRepo.list(resourceGroupFilterDTO, pageRequest);
    verify(resourceGroupV2RepositoryMock).findAll(criteriaArgumentCaptor.capture(), any());
    return criteriaArgumentCaptor.getValue();
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testDeleteManaged() {
    String identifier = randomAlphabetic(10);
    when(transactionTemplate.execute(any())).thenReturn(true);
    resourceGroupService.deleteManaged(identifier);
    verify(transactionTemplate, times(0)).execute(any());

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.find(any()))
        .thenReturn(Optional.of(ResourceGroup.builder().identifier(identifier).build()));
    resourceGroupServiceMockRepo.deleteManaged(identifier);
    verify(resourceGroupV2RepositoryMock).find(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertGetOnlyManagedFilterCriteria(criteria, null, null, null, identifier);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroupUpdate = ResourceGroup.builder().identifier(identifier).build();
    ResourceGroupDTO resourceGroupUpdateDTO = ResourceGroupMapper.toDTO(resourceGroupUpdate);
    when(transactionTemplate.execute(any())).thenReturn(resourceGroupUpdate);
    try {
      resourceGroupService.update(resourceGroupUpdateDTO, true);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(transactionTemplate, times(0)).execute(any());
    }

    try {
      resourceGroupService.update(resourceGroupUpdateDTO, false);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpdateManagedTrue() {
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup =
        ResourceGroup.builder().identifier(identifier).allowedScopeLevels(new HashSet<>()).build();
    ResourceGroupDTO resourceGroupUpdateDTO = ResourceGroupDTO.builder().identifier(identifier).build();
    resourceGroupUpdateDTO.setAllowedScopeLevels(Sets.newHashSet("account"));
    ResourceGroup resourceGroupUpdate = ResourceGroupMapper.fromDTO(resourceGroupUpdateDTO);
    when(transactionTemplate.execute(any())).thenReturn(resourceGroupUpdate);

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.find(any())).thenReturn(Optional.of(resourceGroup));
    resourceGroupServiceMockRepo.update(resourceGroupUpdateDTO, true);
    verify(resourceGroupV2RepositoryMock).find(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertGetOnlyManagedFilterCriteria(criteria, null, null, null, identifier);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpdateManagedFalse() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = ResourceGroup.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .identifier(identifier)
                                      .allowedScopeLevels(new HashSet<>())
                                      .build();
    ResourceGroupDTO resourceGroupUpdateDTO =
        ResourceGroupDTO.builder().accountIdentifier(accountIdentifier).identifier(identifier).build();

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.find(any())).thenReturn(Optional.of(resourceGroup));
    resourceGroupServiceMockRepo.update(resourceGroupUpdateDTO, false);
    verify(resourceGroupV2RepositoryMock).find(criteriaArgumentCaptor.capture());

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertGetOnlyCustomFilterCriteria(criteria, accountIdentifier, null, null, identifier);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testUpdateManagedFalseScopeLevelUpdateNotAllowed() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroup resourceGroup = ResourceGroup.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .identifier(identifier)
                                      .allowedScopeLevels(Sets.newHashSet("account"))
                                      .build();
    ResourceGroupDTO resourceGroupUpdateDTO =
        ResourceGroupDTO.builder().accountIdentifier(accountIdentifier).identifier(identifier).build();
    resourceGroupUpdateDTO.setAllowedScopeLevels(Sets.newHashSet("account", "organization"));

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(resourceGroupV2RepositoryMock.find(any())).thenReturn(Optional.of(resourceGroup));
    try {
      resourceGroupServiceMockRepo.update(resourceGroupUpdateDTO, false);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(resourceGroupV2RepositoryMock).find(criteriaArgumentCaptor.capture());
    }

    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertGetOnlyCustomFilterCriteria(criteria, accountIdentifier, null, null, identifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withAllViewPermission_withNoIdentifierFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    when(accessControlClient.hasAccess(
             ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                 resourceGroupFilterDTO.getProjectIdentifier()),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(true);
    resourceGroupService.list(resourceGroupFilterDTO, pageRequest);

    Criteria criteria = getActualListCriteria(resourceGroupFilterDTO);
    Criteria expectedCriteria = new Criteria();
    Criteria scopeExpectedCriteria = getExpectedScopeCriteria(accountIdentifier, orgIdentifier, null);
    Criteria expectedManagedCriteria = getExpectedManagedCriteria();
    expectedManagedCriteria.and(ResourceGroupKeys.allowedScopeLevels)
        .is(ScopeLevel.of(accountIdentifier, orgIdentifier, null).toString().toLowerCase());
    expectedCriteria.andOperator(new Criteria().orOperator(scopeExpectedCriteria, expectedManagedCriteria));
    assertEquals(expectedCriteria, criteria);
    verify(resourceGroupV2RepositoryMock, times(1)).findAll(criteria, getPageRequest(pageRequest));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withSelectedViewPermission_withNoIdentifierFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    Criteria criteria = new Criteria();
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();
    ArgumentCaptor<ResourceGroupFilterDTO> resourceGroupFilterDTOArgumentCaptor =
        ArgumentCaptor.forClass(ResourceGroupFilterDTO.class);
    when(accessControlClient.hasAccess(
             ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                 resourceGroupFilterDTO.getProjectIdentifier()),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(false);
    when(resourceGroupServiceMockRepo.getResourceGroupFilterCriteria(resourceGroupFilterDTO)).thenReturn(criteria);
    when(resourceGroupV2RepositoryMock.findAll(criteria, Pageable.unpaged()))
        .thenReturn(PageTestUtils.getPage(resourceGroupList, 0));
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    Page<ResourceGroupResponse> result = resourceGroupServiceMockRepo.list(resourceGroupFilterDTO, pageRequest);

    verify(resourceGroupServiceMockRepo, times(1))
        .getResourceGroupFilterCriteria(resourceGroupFilterDTOArgumentCaptor.capture());
    verify(resourceGroupV2RepositoryMock, times(1)).findAll(criteria, Pageable.unpaged());
    assertThat(result.getTotalElements()).isEqualTo(permittedResourceGroups.size());
    assertThat(
        result.getContent().stream().map(item -> item.getResourceGroup().getIdentifier()).collect(Collectors.toList()))
        .isEqualTo(permittedResourceGroups.stream().map(ResourceGroup::getIdentifier).collect(Collectors.toList()));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListFilter_withSelectedViewPermission_withIdentifierFilter() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    ResourceGroupFilterDTO resourceGroupFilterDTO = ResourceGroupFilterDTO.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .identifierFilter(Set.of("RG1", "RG2", "RG3"))
                                                        .build();
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    when(accessControlClient.hasAccess(
             ResourceScope.of(resourceGroupFilterDTO.getAccountIdentifier(), resourceGroupFilterDTO.getOrgIdentifier(),
                 resourceGroupFilterDTO.getProjectIdentifier()),
             Resource.of(RESOURCE_GROUP, null), VIEW_RESOURCEGROUP_PERMISSION))
        .thenReturn(false);

    when(resourceGroupV2RepositoryMock.findAll(any(), eq(getPageRequest(pageRequest))))
        .thenReturn(PageTestUtils.getPage(permittedResourceGroups, 0));
    when(resourceGroupV2RepositoryMock.findAll(any(), eq(Pageable.unpaged())))
        .thenReturn(PageTestUtils.getPage(resourceGroupList, 0));
    when(accessControlClient.checkForAccessOrThrow(any())).thenReturn(accessCheckResponseDTO);

    final Criteria resourceGroupFilterCriteria =
        resourceGroupServiceMockRepo.getResourceGroupFilterCriteria(resourceGroupFilterDTO);

    Page<ResourceGroupResponse> result = resourceGroupServiceMockRepo.list(resourceGroupFilterDTO, pageRequest);

    verify(resourceGroupV2RepositoryMock, times(1)).findAll(resourceGroupFilterCriteria, Pageable.unpaged());
    verify(resourceGroupV2RepositoryMock, times(0)).findAll(resourceGroupFilterCriteria, getPageRequest(pageRequest));
  }
}
