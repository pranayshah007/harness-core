/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserGroupDaoImplTest extends AccessControlCoreTestBase {
  private UserGroupRepository userGroupRepository;
  private UserGroupDao userGroupDao;

  @Before
  public void setup() {
    userGroupRepository = mock(UserGroupRepository.class);
    userGroupDao = spy(new UserGroupDaoImpl(userGroupRepository));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertUpdate() {
    UserGroup userGroup = UserGroup.builder().lastModifiedAt(1L).build();
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().lastModifiedAt(0L).build();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(
             userGroup.getIdentifier(), userGroup.getScopeIdentifier()))
        .thenReturn(Optional.of(userGroupDBO));
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
    assertEquals(0L, userGroupDao.upsert(userGroup).getLastModifiedAt().longValue());
    verify(userGroupRepository, times(1))
        .findByIdentifierAndScopeIdentifier(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsertCreate() {
    UserGroup userGroup = UserGroup.builder().lastModifiedAt(1L).build();
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().lastModifiedAt(1L).build();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(
             userGroup.getIdentifier(), userGroup.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
    assertEquals(1L, userGroupDao.upsert(userGroup).getLastModifiedAt().longValue());
    verify(userGroupRepository, times(1))
        .findByIdentifierAndScopeIdentifier(userGroup.getIdentifier(), userGroup.getScopeIdentifier());
    when(userGroupRepository.save(userGroupDBO)).thenReturn(userGroupDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(1).build();
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageUtils.getNGPageResponse(Page.empty());
    when(userGroupRepository.findByScopeIdentifier(scopeIdentifier, pageable)).thenReturn(Page.empty());
    assertEquals(pageResponse, userGroupDao.list(pageRequest, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByScopeIdentifier(scopeIdentifier, pageable);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroupDBO> userGroupDBOOptional = Optional.of(UserGroupDBO.builder().build());
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(userGroupDBOOptional);
    assertEquals(Optional.of(UserGroup.builder().build()), userGroupDao.get(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroupDBO> userGroupDBOOptional = Optional.empty();
    when(userGroupRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(userGroupDBOOptional);
    assertEquals(Optional.empty(), userGroupDao.get(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    UserGroupDBO userGroupDBO = UserGroupDBO.builder().build();
    when(userGroupRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(Optional.of(userGroupDBO));
    assertEquals(Optional.of(UserGroup.builder().build()), userGroupDao.delete(identifier, scopeIdentifier));
    verify(userGroupRepository, times(1)).deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }
}
