/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users.persistence;

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
import io.harness.accesscontrol.principals.users.User;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserDaoImplTest extends AccessControlCoreTestBase {
  private UserRepository userRepository;
  private UserDao userDao;

  @Before
  public void setup() {
    userRepository = mock(UserRepository.class);
    userDao = spy(new UserDaoImpl(userRepository));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateIfNotPresentAlreadyPresent() {
    User user = User.builder().build();
    UserDBO userDBO = UserDBO.builder().build();
    when(userRepository.findByIdentifierAndScopeIdentifier(user.getIdentifier(), user.getScopeIdentifier()))
        .thenReturn(Optional.of(userDBO));
    assertEquals(user, userDao.createIfNotPresent(user));
    verify(userRepository, times(1))
        .findByIdentifierAndScopeIdentifier(user.getIdentifier(), user.getScopeIdentifier());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateIfNotPresentNotPresent() {
    User user = User.builder().build();
    UserDBO userDBO = UserDBO.builder().build();
    when(userRepository.findByIdentifierAndScopeIdentifier(user.getIdentifier(), user.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    when(userRepository.save(userDBO)).thenReturn(userDBO);
    assertEquals(user, userDao.createIfNotPresent(user));
    verify(userRepository, times(1))
        .findByIdentifierAndScopeIdentifier(user.getIdentifier(), user.getScopeIdentifier());
    verify(userRepository, times(1)).save(userDBO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSaveAll() {
    List<User> users = Lists.newArrayList(User.builder().build());
    List<UserDBO> userDBOs = Lists.newArrayList(UserDBO.builder().build());
    when(userRepository.insertAllIgnoringDuplicates(userDBOs)).thenReturn(1L);
    assertEquals(1L, userDao.saveAll(users));
    verify(userRepository, times(1)).insertAllIgnoringDuplicates(userDBOs);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(1).build();
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    String scopeIdentifier = randomAlphabetic(10);
    PageResponse pageResponse = PageUtils.getNGPageResponse(Page.empty());
    when(userRepository.findByScopeIdentifier(scopeIdentifier, pageable)).thenReturn(Page.empty());
    assertEquals(pageResponse, userDao.list(pageRequest, scopeIdentifier));
    verify(userRepository, times(1)).findByScopeIdentifier(scopeIdentifier, pageable);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserDBO> userDBOOptional = Optional.of(UserDBO.builder().build());
    when(userRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier)).thenReturn(userDBOOptional);
    assertEquals(Optional.of(User.builder().build()), userDao.get(identifier, scopeIdentifier));
    verify(userRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Optional<UserDBO> userDBOOptional = Optional.empty();
    when(userRepository.findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier)).thenReturn(userDBOOptional);
    assertEquals(Optional.empty(), userDao.get(identifier, scopeIdentifier));
    verify(userRepository, times(1)).findByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    UserDBO userDBO = UserDBO.builder().build();
    when(userRepository.deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier))
        .thenReturn(Optional.of(userDBO));
    assertEquals(Optional.of(User.builder().build()), userDao.delete(identifier, scopeIdentifier));
    verify(userRepository, times(1)).deleteByIdentifierAndScopeIdentifier(identifier, scopeIdentifier);
  }
}
