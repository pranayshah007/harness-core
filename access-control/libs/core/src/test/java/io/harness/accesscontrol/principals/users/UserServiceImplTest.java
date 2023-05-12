/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

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
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.users.persistence.UserDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class UserServiceImplTest extends AccessControlCoreTestBase {
  private UserDao userDao;
  private RoleAssignmentService roleAssignmentService;
  private TransactionTemplate transactionTemplate;
  private UserService userService;

  @Before
  public void setup() {
    userDao = mock(UserDao.class);
    roleAssignmentService = mock(RoleAssignmentService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    userService = spy(new UserServiceImpl(userDao, roleAssignmentService, transactionTemplate));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSaveAll() {
    List<User> users = Lists.newArrayList(User.builder().build());
    when(userDao.saveAll(users)).thenReturn(1L);
    assertEquals(1L, userService.saveAll(users));
    verify(userDao, times(1)).saveAll(users);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateIfNotPresent() {
    User user = User.builder().build();
    when(userDao.createIfNotPresent(user)).thenReturn(user);
    assertEquals(user, userService.createIfNotPresent(user));
    verify(userDao, times(1)).createIfNotPresent(user);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().build();
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageResponse.getEmptyPageResponse(pageRequest);
    when(userDao.list(pageRequest, scopeIdentifier)).thenReturn(pageResponse);
    assertEquals(pageResponse, userService.list(pageRequest, scopeIdentifier));
    verify(userDao, times(1)).list(pageRequest, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<User> userOptional = Optional.of(User.builder().build());
    when(userDao.get(identifier, scopeIdentifier)).thenReturn(userOptional);
    assertEquals(userOptional, userService.get(identifier, scopeIdentifier));
    verify(userDao, times(1)).get(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresent() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<User> userOptional = Optional.of(User.builder().build());
    RoleAssignmentFilter filter =
        RoleAssignmentFilter.builder()
            .scopeFilter(scopeIdentifier)
            .principalFilter(Sets.newHashSet(
                Principal.builder().principalType(PrincipalType.USER).principalIdentifier(identifier).build()))
            .build();
    when(userDao.get(identifier, scopeIdentifier)).thenReturn(userOptional);
    when(transactionTemplate.execute(any())).thenReturn(userOptional.get());
    assertEquals(userOptional, userService.deleteIfPresent(identifier, scopeIdentifier));
    verify(userDao, times(1)).get(identifier, scopeIdentifier);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteIfPresentNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<User> userOptional = Optional.empty();
    RoleAssignmentFilter filter =
        RoleAssignmentFilter.builder()
            .scopeFilter(scopeIdentifier)
            .principalFilter(Sets.newHashSet(
                Principal.builder().principalType(PrincipalType.USER).principalIdentifier(identifier).build()))
            .build();
    when(userDao.get(identifier, scopeIdentifier)).thenReturn(userOptional);
    when(roleAssignmentService.deleteMulti(filter)).thenReturn(1L);
    assertEquals(userOptional, userService.deleteIfPresent(identifier, scopeIdentifier));
    verify(userDao, times(1)).get(identifier, scopeIdentifier);
    verify(roleAssignmentService, times(1)).deleteMulti(filter);
  }
}
