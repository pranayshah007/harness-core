/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class UserGroupServiceImplTest extends AccessControlCoreTestBase {
  private UserGroupDao userGroupDao;
  private RoleAssignmentService roleAssignmentService;
  private TransactionTemplate transactionTemplate;
  private UserGroupService userGroupService;
  private ScopeService scopeService;

  @Before
  public void setup() {
    userGroupDao = mock(UserGroupDao.class);
    roleAssignmentService = mock(RoleAssignmentService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    scopeService = mock(ScopeService.class);
    userGroupService =
        spy(new UserGroupServiceImpl(userGroupDao, roleAssignmentService, scopeService, transactionTemplate));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpsert() {
    UserGroup userGroup = UserGroup.builder().build();
    when(userGroupDao.upsert(userGroup)).thenReturn(userGroup);
    assertEquals(userGroup, userGroupService.upsert(userGroup));
    verify(userGroupDao, times(1)).upsert(userGroup);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().build();
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageResponse.getEmptyPageResponse(pageRequest);
    when(userGroupDao.list(pageRequest, scopeIdentifier)).thenReturn(pageResponse);
    assertEquals(pageResponse, userGroupService.list(pageRequest, scopeIdentifier));
    verify(userGroupDao, times(1)).list(pageRequest, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroup> userGroupOptional = Optional.of(UserGroup.builder().build());
    when(userGroupDao.get(identifier, scopeIdentifier)).thenReturn(userGroupOptional);
    assertEquals(userGroupOptional, userGroupService.get(identifier, scopeIdentifier));
    verify(userGroupDao, times(1)).get(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresent() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroup> userGroupOptional = Optional.of(UserGroup.builder().build());
    when(userGroupDao.get(identifier, scopeIdentifier)).thenReturn(userGroupOptional);
    when(transactionTemplate.execute(any())).thenReturn(userGroupOptional.get());
    userGroupService.deleteIfPresent(identifier, scopeIdentifier);
    verify(userGroupDao, times(1)).get(identifier, scopeIdentifier);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresentNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroup> userGroupOptional = Optional.empty();
    when(userGroupDao.get(identifier, scopeIdentifier)).thenReturn(userGroupOptional);
    userGroupService.deleteIfPresent(identifier, scopeIdentifier);
    verify(userGroupDao, times(1)).get(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroup> userGroupOptional = Optional.of(UserGroup.builder().build());
    when(userGroupDao.get(identifier, scopeIdentifier)).thenReturn(userGroupOptional);
    when(transactionTemplate.execute(any())).thenReturn(userGroupOptional.get());
    userGroupService.delete(identifier, scopeIdentifier);
    verify(userGroupDao, times(1)).get(identifier, scopeIdentifier);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserGroup> userGroupOptional = Optional.empty();
    when(userGroupDao.get(identifier, scopeIdentifier)).thenReturn(userGroupOptional);
    userGroupService.delete(identifier, scopeIdentifier);
  }
}
