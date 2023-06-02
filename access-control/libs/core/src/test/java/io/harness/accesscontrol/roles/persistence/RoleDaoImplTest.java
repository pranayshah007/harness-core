/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.persistence;

import static io.harness.accesscontrol.roles.persistence.RoleDBOMapper.toDBO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.roles.persistence.RoleDBO.RoleDBOKeys;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
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
import com.mongodb.client.result.UpdateResult;
import io.serializer.HObjectMapper;
import java.util.HashSet;
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
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
public class RoleDaoImplTest extends AccessControlCoreTestBase {
  private RoleRepository roleRepository;
  private ScopeService scopeService;
  private RoleDaoImpl roleDao;

  @Before
  public void setup() {
    roleRepository = mock(RoleRepository.class);
    scopeService = mock(ScopeService.class);
    roleDao = spy(new RoleDaoImpl(roleRepository, scopeService));
  }

  private Role getRole(int count) {
    Set<String> permissions = new HashSet<>();
    for (int i = 0; i < count; i++) {
      permissions.add(randomAlphabetic(10));
    }
    return Role.builder()
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .allowedScopeLevels(Sets.newHashSet(TestScopeLevels.TEST_SCOPE.toString()))
        .permissions(permissions)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    Role role = getRole(5);
    RoleDBO roleDBO = toDBO(role);
    when(roleRepository.save(roleDBO)).thenReturn(roleDBO);
    Role savedRole = roleDao.create(role);
    verify(roleRepository, times(1)).save(any());
    assertEquals(role.getIdentifier(), savedRole.getIdentifier());
    assertEquals(role.getName(), savedRole.getName());
    assertEquals(role.getScopeIdentifier(), savedRole.getScopeIdentifier());
    assertEquals(role.getAllowedScopeLevels(), savedRole.getAllowedScopeLevels());
    assertEquals(role.getPermissions(), savedRole.getPermissions());
  }

  @Test(expected = DuplicateFieldException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateDuplicate() {
    Role role = getRole(5);
    RoleDBO roleDBO = toDBO(role);
    when(roleRepository.save(roleDBO)).thenThrow(new DuplicateKeyException(""));
    roleDao.create(role);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    RoleFilter roleFilter = RoleFilter.builder().managedFilter(ManagedFilter.NO_FILTER).build();
    List<RoleDBO> dbResult = Lists.newArrayList(toDBO(getRole(5)), toDBO(getRole(5)), toDBO(getRole(5)));
    when(roleRepository.findAll(any(), any())).thenReturn(PageTestUtils.getPage(dbResult, dbResult.size()));
    when(scopeService.buildScopeFromScopeIdentifier(roleFilter.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    PageResponse<Role> result = roleDao.list(pageRequest, roleFilter, true);
    assertEquals(dbResult.size(), result.getContent().size());
    for (int i = 0; i < dbResult.size(); i++) {
      assertEquals(dbResult.get(i).getIdentifier(), result.getContent().get(i).getIdentifier());
      assertEquals(dbResult.get(i).getName(), result.getContent().get(i).getName());
      assertEquals(dbResult.get(i).getScopeIdentifier(), result.getContent().get(i).getScopeIdentifier());
      assertEquals(dbResult.get(i).getAllowedScopeLevels(), result.getContent().get(i).getAllowedScopeLevels());
      assertEquals(dbResult.get(i).getPermissions(), result.getContent().get(i).getPermissions());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testListCriteria() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    List<Boolean> includeChildScopesFilters = Lists.newArrayList(false, true);
    List<ManagedFilter> managedFilters =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (Boolean includeChildScopesFilter : includeChildScopesFilters) {
      for (ManagedFilter managedFilter : managedFilters) {
        invocations += 1;
        RoleFilter roleFilter = getRoleFilter(includeChildScopesFilter, managedFilter, 2);
        RoleFilter roleFilterClone = (RoleFilter) HObjectMapper.clone(roleFilter);
        ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
        when(roleRepository.findAll(any(), any())).thenReturn(PageTestUtils.getPage(emptyList(), 0));
        when(scopeService.buildScopeFromScopeIdentifier(roleFilter.getScopeIdentifier()))
            .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
        roleDao.list(pageRequest, roleFilter, false);
        verify(roleRepository, times(invocations)).findAll(criteriaArgumentCaptor.capture(), any());
        assertFilterCriteria(roleFilterClone, criteriaArgumentCaptor);
      }
    }
  }

  private RoleFilter getRoleFilter(boolean includeChildScopes, ManagedFilter managedFilter, int permissionsCount) {
    String scopeFilter = randomAlphabetic(10);
    Set<String> scopeLevelFilter = new HashSet<>();
    if (includeChildScopes) {
      scopeLevelFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    }
    Set<String> permissionFilter = new HashSet<>();
    for (int i = 0; i < permissionsCount; i++) {
      permissionFilter.add(randomAlphabetic(10));
    }
    return RoleFilter.builder()
        .scopeIdentifier(scopeFilter)
        .scopeLevelsFilter(includeChildScopes ? scopeLevelFilter : new HashSet<>())
        .managedFilter(managedFilter)
        .identifierFilter(Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10)))
        .permissionFilter(permissionFilter)
        .includeChildScopes(includeChildScopes)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    Role currentRole = getRole(5);
    RoleDBO currentRoleDBO = RoleDBOMapper.toDBO(currentRole);
    Set<String> newPermissions = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      newPermissions.add(randomAlphabetic(10));
    }
    Role roleUpdate = Role.builder()
                          .identifier(currentRole.getIdentifier())
                          .name(currentRole.getName())
                          .scopeIdentifier(currentRole.getScopeIdentifier())
                          .allowedScopeLevels(currentRole.getAllowedScopeLevels())
                          .permissions(newPermissions)
                          .build();
    Role roleUpdateClone = (Role) HObjectMapper.clone(roleUpdate);
    RoleDBO roleUpdateCloneDBO = RoleDBOMapper.toDBO(roleUpdateClone);
    roleUpdateCloneDBO.setId(currentRoleDBO.getId());
    RoleDBO roleForValidation = (RoleDBO) HObjectMapper.clone(roleUpdateCloneDBO);
    when(roleRepository.find(any())).thenReturn(Optional.of(currentRoleDBO));
    when(scopeService.buildScopeFromScopeIdentifier(currentRole.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    when(roleRepository.save(roleUpdateCloneDBO)).thenReturn(roleUpdateCloneDBO);
    Role savedRole = roleDao.update(roleUpdate);
    verify(roleRepository, times(1)).save(any());
    assertEquals(roleForValidation.getIdentifier(), savedRole.getIdentifier());
    assertEquals(roleForValidation.getScopeIdentifier(), savedRole.getScopeIdentifier());
    assertEquals(roleForValidation.getAllowedScopeLevels(), savedRole.getAllowedScopeLevels());
    assertEquals(roleForValidation.getName(), savedRole.getName());
    assertEquals(roleForValidation.getPermissions(), savedRole.getPermissions());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    Role roleUpdate = getRole(5);
    when(roleRepository.find(any())).thenReturn(Optional.empty());
    when(scopeService.buildScopeFromScopeIdentifier(roleUpdate.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    roleDao.update(roleUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    Role role = getRole(5);
    RoleDBO roleDBO = RoleDBOMapper.toDBO(role);
    when(scopeService.buildScopeFromScopeIdentifier(role.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    when(roleRepository.find(any())).thenReturn(Optional.of(roleDBO));
    Set<ManagedFilter> managedFilters =
        Sets.newHashSet(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    managedFilters.forEach(managedFilter -> {
      Optional<Role> roleOptional = roleDao.get(role.getIdentifier(), role.getScopeIdentifier(), managedFilter);
      assertTrue(roleOptional.isPresent());
      assertEquals(role, roleOptional.get());
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
    when(roleRepository.find(any())).thenReturn(Optional.empty());
    Set<ManagedFilter> managedFilters =
        Sets.newHashSet(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    managedFilters.forEach(managedFilter -> {
      Optional<Role> roleOptional = roleDao.get(identifier, scopeIdentifier, managedFilter);
      assertFalse(roleOptional.isPresent());
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
      when(roleRepository.find(any())).thenReturn(Optional.empty());
      when(scopeService.buildScopeFromScopeIdentifier(scopeIdentifier))
          .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
      roleDao.get(identifier, scopeIdentifier, managedFilter);
      verify(roleRepository, times(invocations)).find(criteriaArgumentCaptor.capture());
      assertFilterCriteria(identifier, scopeIdentifier, managedFilter, criteriaArgumentCaptor);
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    Role role = getRole(5);
    RoleDBO roleDBO = RoleDBOMapper.toDBO(role);
    List<Boolean> managedFilters = Lists.newArrayList(false, true);
    managedFilters.forEach(managedFilter -> {
      when(roleRepository.deleteByIdentifierAndScopeIdentifierAndManaged(
               role.getIdentifier(), role.getScopeIdentifier(), managedFilter))
          .thenReturn(Optional.of(roleDBO));
      Optional<Role> roleOptional = roleDao.delete(role.getIdentifier(), role.getScopeIdentifier(), managedFilter);
      assertTrue(roleOptional.isPresent());
      assertEquals(role, roleOptional.get());
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
      when(roleRepository.deleteByIdentifierAndScopeIdentifierAndManaged(identifier, scopeIdentifier, managedFilter))
          .thenReturn(Optional.empty());
      Optional<Role> roleOptional = roleDao.delete(identifier, scopeIdentifier, managedFilter);
      assertFalse(roleOptional.isPresent());
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    RoleFilter roleFilter = RoleFilter.builder().managedFilter(ManagedFilter.ONLY_CUSTOM).build();
    when(roleRepository.deleteMulti(any())).thenReturn(17L);
    long result = roleDao.deleteMulti(roleFilter);
    assertEquals(17L, result);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMultiCriteria() {
    List<Boolean> includeChildScopesFilters = Lists.newArrayList(false, true);
    List<ManagedFilter> managedFilters =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (Boolean includeChildScopesFilter : includeChildScopesFilters) {
      for (ManagedFilter managedFilter : managedFilters) {
        invocations += 1;
        RoleFilter roleFilter = getRoleFilter(includeChildScopesFilter, managedFilter, 2);
        RoleFilter roleFilterClone = (RoleFilter) HObjectMapper.clone(roleFilter);
        ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
        when(roleRepository.deleteMulti(any())).thenReturn(17L);
        when(scopeService.buildScopeFromScopeIdentifier(roleFilter.getScopeIdentifier()))
            .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
        roleDao.deleteMulti(roleFilter);
        verify(roleRepository, times(invocations)).deleteMulti(criteriaArgumentCaptor.capture());
        assertFilterCriteria(roleFilterClone, criteriaArgumentCaptor);
      }
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddPermissionToRoles() {
    String permissionIdentifier = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    when(roleRepository.updateMulti(any(), any())).thenReturn(UpdateResult.acknowledged(17L, 17L, null));
    doReturn(new Criteria()).when(roleDao).createCriteriaFromFilter(any(), eq(false));

    roleDao.addPermissionToRoles(permissionIdentifier, RoleFilter.builder().build());

    verify(roleDao, times(1)).createCriteriaFromFilter(any(), eq(false));
    verify(roleRepository, times(1)).updateMulti(criteriaArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaDocument = criteria.getCriteriaObject();
    Document permissionCriteriaDocument = (Document) criteriaDocument.get(RoleDBOKeys.permissions);
    String permissionFilter = (String) permissionCriteriaDocument.get("$ne");
    assertEquals(permissionIdentifier, permissionFilter);
    Update update = updateArgumentCaptor.getValue();
    Document updateDocument = update.getUpdateObject();
    Document pushPermissionDocument = (Document) updateDocument.get("$push");
    String pushPermission = (String) pushPermissionDocument.get(RoleDBOKeys.permissions);
    assertEquals(permissionIdentifier, pushPermission);
    assertEquals(1, updateDocument.size());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testRemovePermissionFromRoles() {
    String permissionIdentifier = randomAlphabetic(10);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    when(roleRepository.updateMulti(any(), any())).thenReturn(UpdateResult.acknowledged(17L, 17L, null));
    doReturn(new Criteria()).when(roleDao).createCriteriaFromFilter(any(), eq(false));

    roleDao.removePermissionFromRoles(permissionIdentifier, RoleFilter.builder().build());

    verify(roleDao, times(1)).createCriteriaFromFilter(any(), eq(false));
    verify(roleRepository, times(1)).updateMulti(criteriaArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document criteriaDocument = criteria.getCriteriaObject();
    assertEquals(permissionIdentifier, criteriaDocument.get(RoleDBOKeys.permissions));
    Update update = updateArgumentCaptor.getValue();
    Document updateDocument = update.getUpdateObject();
    Document pullPermissionDocument = (Document) updateDocument.get("$pull");
    String pullPermission = (String) pullPermissionDocument.get(RoleDBOKeys.permissions);
    assertEquals(permissionIdentifier, pullPermission);
    assertEquals(1, updateDocument.size());
  }

  private void assertFilterCriteria(String identifier, String scopeIdentifier, ManagedFilter managedFilter,
      ArgumentCaptor<Criteria> criteriaArgumentCaptor) {
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    int expectedCount = 0;

    if (ManagedFilter.NO_FILTER.equals(managedFilter)) {
      expectedCount += 1;
      Document scopeDocument = (Document) document.get(RoleDBOKeys.scopeIdentifier);
      List<?> inScopeFilter = (List<?>) scopeDocument.get("$in");
      assertEquals(Lists.newArrayList(scopeIdentifier, null), inScopeFilter);
    } else if (ManagedFilter.ONLY_CUSTOM.equals(managedFilter)) {
      expectedCount += 2;
      assertEquals(false, document.get(RoleDBOKeys.managed));
      assertEquals(scopeIdentifier, document.get(RoleDBOKeys.scopeIdentifier));
    } else if (ManagedFilter.ONLY_MANAGED.equals(managedFilter)) {
      expectedCount += 2;
      assertEquals(true, document.get(RoleDBOKeys.managed));
      assertNull(document.get(RoleDBOKeys.scopeIdentifier));
    }

    expectedCount += 1;
    assertEquals(identifier, document.get(RoleDBOKeys.identifier));

    if (isNotEmpty(scopeIdentifier)) {
      expectedCount += 1;
      assertEquals(TestScopeLevels.TEST_SCOPE.toString(), document.get(RoleDBOKeys.allowedScopeLevels));
    }

    assertEquals(expectedCount, document.size());
  }

  private void assertFilterCriteria(RoleFilter roleFilter, ArgumentCaptor<Criteria> criteriaArgumentCaptor) {
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    int expectedCount = 0;

    if (ManagedFilter.NO_FILTER.equals(roleFilter.getManagedFilter())) {
      expectedCount += 1;
      Document scopeDocument = (Document) document.get(RoleDBOKeys.scopeIdentifier);
      List<?> inScopeFilter = (List<?>) scopeDocument.get("$in");
      assertEquals(Lists.newArrayList(roleFilter.getScopeIdentifier(), null), inScopeFilter);
    } else if (ManagedFilter.ONLY_CUSTOM.equals(roleFilter.getManagedFilter())) {
      expectedCount += 2;
      assertEquals(false, document.get(RoleDBOKeys.managed));
      if (roleFilter.isIncludeChildScopes()) {
        Pattern pattern = (Pattern) document.get(RoleDBOKeys.scopeIdentifier);
        assertEquals("^" + roleFilter.getScopeIdentifier(), pattern.toString());
      } else {
        assertEquals(roleFilter.getScopeIdentifier(), document.get(RoleDBOKeys.scopeIdentifier));
      }
    } else if (ManagedFilter.ONLY_MANAGED.equals(roleFilter.getManagedFilter())) {
      expectedCount += 2;
      assertEquals(true, document.get(RoleDBOKeys.managed));
      assertNull(document.get(RoleDBOKeys.scopeIdentifier));
    }

    if (isNotEmpty(roleFilter.getScopeLevelsFilter())) {
      expectedCount += 1;
      Document scopeLevelDocument = (Document) document.get(RoleDBOKeys.allowedScopeLevels);
      Set<?> inScopeLevelFilter = (Set<?>) scopeLevelDocument.get("$in");
      assertEquals(roleFilter.getScopeLevelsFilter(), inScopeLevelFilter);
    }

    if (isNotEmpty(roleFilter.getScopeIdentifier()) && !roleFilter.isIncludeChildScopes()) {
      expectedCount += 1;
      assertEquals(TestScopeLevels.TEST_SCOPE.toString(), document.get(RoleDBOKeys.allowedScopeLevels));
    }

    if (isNotEmpty(roleFilter.getIdentifierFilter())) {
      expectedCount += 1;
      Document identifierDocument = (Document) document.get(RoleDBOKeys.identifier);
      Set<?> inIdentifierFilter = (Set<?>) identifierDocument.get("$in");
      assertEquals(roleFilter.getIdentifierFilter(), inIdentifierFilter);
    }

    if (isNotEmpty(roleFilter.getPermissionFilter())) {
      expectedCount += 1;
      Document permissionDocument = (Document) document.get(RoleDBOKeys.permissions);
      Set<?> inPermissionFilter = (Set<?>) permissionDocument.get("$in");
      assertEquals(roleFilter.getPermissionFilter(), inPermissionFilter);
    }

    assertEquals(expectedCount, document.size());
  }
}
