/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.persistence;

import static io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.BasicDBList;
import io.serializer.HObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class RoleAssignmentDaoImplTest extends AccessControlCoreTestBase {
  private RoleAssignmentRepository roleAssignmentRepository;
  private RoleAssignmentDaoImpl roleAssignmentDao;

  @Before
  public void setup() {
    roleAssignmentRepository = mock(RoleAssignmentRepository.class);
    roleAssignmentDao = spy(new RoleAssignmentDaoImpl(roleAssignmentRepository));
  }

  private RoleAssignment getRoleAssignment() {
    return RoleAssignment.builder()
        .identifier(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .roleIdentifier(randomAlphabetic(10))
        .principalType(PrincipalType.USER)
        .principalIdentifier(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    RoleAssignment roleAssignment = getRoleAssignment();
    RoleAssignmentDBO roleAssignmentDBO = toDBO(roleAssignment);
    when(roleAssignmentRepository.save(roleAssignmentDBO)).thenReturn(roleAssignmentDBO);
    RoleAssignment savedRoleAssignment = roleAssignmentDao.create(roleAssignment);
    verify(roleAssignmentRepository, times(1)).save(any());
    assertEquals(roleAssignment.getIdentifier(), savedRoleAssignment.getIdentifier());
    assertEquals(roleAssignment.getScopeIdentifier(), savedRoleAssignment.getScopeIdentifier());
    assertEquals(roleAssignment.getRoleIdentifier(), savedRoleAssignment.getRoleIdentifier());
    assertEquals(roleAssignment.getPrincipalIdentifier(), savedRoleAssignment.getPrincipalIdentifier());
    assertEquals(roleAssignment.getPrincipalType(), savedRoleAssignment.getPrincipalType());
    assertEquals(roleAssignment.getResourceGroupIdentifier(), savedRoleAssignment.getResourceGroupIdentifier());
  }

  @Test(expected = DuplicateFieldException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateDuplicate() {
    RoleAssignment roleAssignment = getRoleAssignment();
    RoleAssignmentDBO roleAssignmentDBO = toDBO(roleAssignment);
    when(roleAssignmentRepository.save(roleAssignmentDBO)).thenThrow(new DuplicateKeyException(""));
    roleAssignmentDao.create(roleAssignment);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder().build();
    List<RoleAssignmentDBO> dbResult =
        Lists.newArrayList(toDBO(getRoleAssignment()), toDBO(getRoleAssignment()), toDBO(getRoleAssignment()));
    when(roleAssignmentRepository.findAll(any(), any())).thenReturn(PageTestUtils.getPage(dbResult, dbResult.size()));
    PageResponse<RoleAssignment> result = roleAssignmentDao.list(pageRequest, roleAssignmentFilter);
    assertEquals(dbResult.size(), result.getContent().size());
    for (int i = 0; i < dbResult.size(); i++) {
      assertEquals(dbResult.get(i).getIdentifier(), result.getContent().get(i).getIdentifier());
      assertEquals(dbResult.get(i).getScopeIdentifier(), result.getContent().get(i).getScopeIdentifier());
      assertEquals(dbResult.get(i).getRoleIdentifier(), result.getContent().get(i).getRoleIdentifier());
      assertEquals(dbResult.get(i).getPrincipalIdentifier(), result.getContent().get(i).getPrincipalIdentifier());
      assertEquals(dbResult.get(i).getPrincipalType(), result.getContent().get(i).getPrincipalType());
      assertEquals(
          dbResult.get(i).getResourceGroupIdentifier(), result.getContent().get(i).getResourceGroupIdentifier());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListCriteria() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    RoleAssignmentFilter roleAssignmentFilter = getRoleAssignmentFilter(false);
    RoleAssignmentFilter roleAssignmentFilterClone = (RoleAssignmentFilter) HObjectMapper.clone(roleAssignmentFilter);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(roleAssignmentRepository.findAll(any(), any())).thenReturn(PageTestUtils.getPage(emptyList(), 0));
    roleAssignmentDao.list(pageRequest, roleAssignmentFilter);
    verify(roleAssignmentRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), any());
    assertFilterCriteria(roleAssignmentFilterClone, criteriaArgumentCaptor);

    roleAssignmentFilter = getRoleAssignmentFilter(true);
    roleAssignmentFilterClone = (RoleAssignmentFilter) HObjectMapper.clone(roleAssignmentFilter);
    criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    roleAssignmentDao.list(pageRequest, roleAssignmentFilter);
    verify(roleAssignmentRepository, times(2)).findAll(criteriaArgumentCaptor.capture(), any());
    assertFilterCriteria(roleAssignmentFilterClone, criteriaArgumentCaptor);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    RoleAssignment currentRoleAssignment = RoleAssignment.builder()
                                               .identifier(randomAlphabetic(10))
                                               .scopeIdentifier(randomAlphabetic(10))
                                               .roleIdentifier(randomAlphabetic(10))
                                               .principalType(PrincipalType.USER)
                                               .principalIdentifier(randomAlphabetic(10))
                                               .resourceGroupIdentifier(randomAlphabetic(10))
                                               .disabled(true)
                                               .build();
    RoleAssignmentDBO currentRoleAssignmentDBO = toDBO(currentRoleAssignment);
    RoleAssignment roleAssignmentUpdate =
        RoleAssignment.builder()
            .identifier(currentRoleAssignment.getIdentifier())
            .scopeIdentifier(currentRoleAssignment.getScopeIdentifier())
            .roleIdentifier(currentRoleAssignment.getRoleIdentifier())
            .principalType(currentRoleAssignment.getPrincipalType())
            .principalIdentifier(currentRoleAssignment.getPrincipalIdentifier())
            .resourceGroupIdentifier(currentRoleAssignment.getResourceGroupIdentifier())
            .disabled(false)
            .build();
    RoleAssignment roleAssignmentUpdateClone = (RoleAssignment) HObjectMapper.clone(roleAssignmentUpdate);
    RoleAssignmentDBO roleAssignmentUpdateCloneDBO = toDBO(roleAssignmentUpdateClone);
    roleAssignmentUpdateCloneDBO.setId(currentRoleAssignmentDBO.getId());
    roleAssignmentUpdateCloneDBO.setCreatedAt(currentRoleAssignmentDBO.getCreatedAt());
    roleAssignmentUpdateCloneDBO.setLastModifiedAt(currentRoleAssignmentDBO.getLastModifiedAt());
    RoleAssignmentDBO roleAssignmentForValidation =
        (RoleAssignmentDBO) HObjectMapper.clone(roleAssignmentUpdateCloneDBO);
    when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
             roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.of(currentRoleAssignmentDBO));
    when(roleAssignmentRepository.save(roleAssignmentUpdateCloneDBO)).thenReturn(roleAssignmentUpdateCloneDBO);
    RoleAssignment savedRoleAssignment = roleAssignmentDao.update(roleAssignmentUpdate);
    verify(roleAssignmentRepository, times(1)).save(any());
    assertEquals(roleAssignmentForValidation.getIdentifier(), savedRoleAssignment.getIdentifier());
    assertEquals(roleAssignmentForValidation.getScopeIdentifier(), savedRoleAssignment.getScopeIdentifier());
    assertEquals(roleAssignmentForValidation.getRoleIdentifier(), savedRoleAssignment.getRoleIdentifier());
    assertFalse(savedRoleAssignment.isDisabled());
    assertEquals(roleAssignmentForValidation.getPrincipalIdentifier(), savedRoleAssignment.getPrincipalIdentifier());
    assertEquals(roleAssignmentForValidation.getPrincipalType(), savedRoleAssignment.getPrincipalType());
    assertEquals(
        roleAssignmentForValidation.getResourceGroupIdentifier(), savedRoleAssignment.getResourceGroupIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    RoleAssignment roleAssignmentUpdate = getRoleAssignment();
    when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
             roleAssignmentUpdate.getIdentifier(), roleAssignmentUpdate.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    roleAssignmentDao.update(roleAssignmentUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    RoleAssignment roleAssignment = getRoleAssignment();
    RoleAssignmentDBO roleAssignmentDBO = toDBO(roleAssignment);
    when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
             roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignmentDBO));
    Optional<RoleAssignment> roleAssignmentOptional =
        roleAssignmentDao.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier());
    assertTrue(roleAssignmentOptional.isPresent());
    assertEquals(roleAssignment, roleAssignmentOptional.get());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(roleAssignmentRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(Optional.empty());
    Optional<RoleAssignment> roleAssignmentOptional = roleAssignmentDao.get(identifier, scopeIdentifier);
    assertFalse(roleAssignmentOptional.isPresent());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    RoleAssignment roleAssignment = getRoleAssignment();
    RoleAssignmentDBO roleAssignmentDBO = toDBO(roleAssignment);
    when(roleAssignmentRepository.deleteByIdentifierAndScopeIdentifier(
             roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignmentDBO));
    Optional<RoleAssignment> roleAssignmentOptional =
        roleAssignmentDao.delete(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier());
    assertTrue(roleAssignmentOptional.isPresent());
    assertEquals(roleAssignment, roleAssignmentOptional.get());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(roleAssignmentRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(Optional.empty());
    Optional<RoleAssignment> roleAssignmentOptional = roleAssignmentDao.delete(identifier, scopeIdentifier);
    assertFalse(roleAssignmentOptional.isPresent());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder().build();
    when(roleAssignmentRepository.deleteMulti(any())).thenReturn(17L);
    long result = roleAssignmentDao.deleteMulti(roleAssignmentFilter);
    assertEquals(17L, result);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMultiCriteria() {
    RoleAssignmentFilter roleAssignmentFilter = getRoleAssignmentFilter(false);
    RoleAssignmentFilter roleAssignmentFilterClone = (RoleAssignmentFilter) HObjectMapper.clone(roleAssignmentFilter);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(roleAssignmentRepository.deleteMulti(any())).thenReturn(0L);
    roleAssignmentDao.deleteMulti(roleAssignmentFilter);
    verify(roleAssignmentRepository, times(1)).deleteMulti(criteriaArgumentCaptor.capture());
    assertFilterCriteria(roleAssignmentFilterClone, criteriaArgumentCaptor);

    roleAssignmentFilter = getRoleAssignmentFilter(true);
    roleAssignmentFilterClone = (RoleAssignmentFilter) HObjectMapper.clone(roleAssignmentFilter);
    criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    roleAssignmentDao.deleteMulti(roleAssignmentFilter);
    verify(roleAssignmentRepository, times(2)).deleteMulti(criteriaArgumentCaptor.capture());
    assertFilterCriteria(roleAssignmentFilterClone, criteriaArgumentCaptor);
  }

  private RoleAssignmentFilter getRoleAssignmentFilter(boolean includeChildScopes) {
    String scopeFilter = randomAlphabetic(10);
    Set<String> scopeLevelFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypeFilter = Sets.newHashSet(PrincipalType.USER);
    Set<Principal> principalFilter = Sets.newHashSet(Principal.builder()
                                                         .principalType(PrincipalType.SERVICE_ACCOUNT)
                                                         .principalIdentifier(randomAlphabetic(10))
                                                         .build());
    ManagedFilter managedFilter = ManagedFilter.NO_FILTER;
    Set<Boolean> disabledFilter = Sets.newHashSet(Boolean.TRUE);

    return RoleAssignmentFilter.builder()
        .scopeFilter(scopeFilter)
        .includeChildScopes(includeChildScopes)
        .scopeLevelFilter(scopeLevelFilter)
        .resourceGroupFilter(resourceGroupFilter)
        .roleFilter(roleFilter)
        .principalTypeFilter(principalTypeFilter)
        .principalFilter(principalFilter)
        .managedFilter(managedFilter)
        .disabledFilter(disabledFilter)
        .build();
  }

  private void assertFilterCriteria(
      RoleAssignmentFilter roleAssignmentFilter, ArgumentCaptor<Criteria> criteriaArgumentCaptor) {
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertEquals(2, document.size());
    if (roleAssignmentFilter.isIncludeChildScopes()) {
      BasicDBList orList = (BasicDBList) document.get("$or");
      Document scopeCriteria = (Document) orList.get(0);
      Pattern pattern = (Pattern) scopeCriteria.get(RoleAssignmentDBOKeys.scopeIdentifier);
      assertEquals("^" + roleAssignmentFilter.getScopeFilter(), pattern.toString());
    } else {
      BasicDBList orList = (BasicDBList) document.get("$or");
      Document scopeCriteria = (Document) orList.get(0);
      assertEquals(roleAssignmentFilter.getScopeFilter(), scopeCriteria.get(RoleAssignmentDBOKeys.scopeIdentifier));
    }

    BasicDBList andList = (BasicDBList) document.get("$and");
    Document criteriaDocument = (Document) andList.get(0);

    Document scopeLevelDocument = (Document) criteriaDocument.get(RoleAssignmentDBOKeys.scopeLevel);
    Set<?> inScopeLevelFilter = (Set<?>) scopeLevelDocument.get("$in");
    assertEquals(roleAssignmentFilter.getScopeLevelFilter(), inScopeLevelFilter);
    Document resourceGroupDocument = (Document) criteriaDocument.get(RoleAssignmentDBOKeys.resourceGroupIdentifier);
    Set<?> inResourceGroupFilter = (Set<?>) resourceGroupDocument.get("$in");
    assertEquals(roleAssignmentFilter.getResourceGroupFilter(), inResourceGroupFilter);
    Document roleDocument = (Document) criteriaDocument.get(RoleAssignmentDBOKeys.roleIdentifier);
    Set<?> inRoleFilter = (Set<?>) roleDocument.get("$in");
    assertEquals(roleAssignmentFilter.getRoleFilter(), inRoleFilter);
    Document disabledDocument = (Document) criteriaDocument.get(RoleAssignmentDBOKeys.disabled);
    Set<?> inDisabledFilter = (Set<?>) disabledDocument.get("$in");
    assertEquals(roleAssignmentFilter.getDisabledFilter(), inDisabledFilter);
    Document principalTypeDocument = (Document) criteriaDocument.get(RoleAssignmentDBOKeys.principalType);
    Set<?> inPrincipalTypeFilter = (Set<?>) principalTypeDocument.get("$in");
    assertEquals(roleAssignmentFilter.getPrincipalTypeFilter(), inPrincipalTypeFilter);
  }
}
